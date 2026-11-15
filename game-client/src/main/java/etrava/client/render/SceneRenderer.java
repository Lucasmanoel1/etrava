package etrava.client.render;

import static etrava.client.render.IsoProjection.snap;
import static etrava.client.render.IsoProjection.sx;
import static etrava.client.render.IsoProjection.sy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import etrava.client.assets.Assets;
import etrava.client.camera.CameraRig;
import etrava.sim.content.AreaDef;
import etrava.sim.content.PropDef;
import etrava.sim.content.PropPlacement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Desenha uma área em 2D isométrico (D-018), em quatro etapas:
 * <ol>
 *   <li>cena: chão, sombras e sprites de trás para a frente;</li>
 *   <li>luz: cor do céu pela hora + manchas de luz de janelas e lamparinas;</li>
 *   <li>cena × luz, e por cima o que brilha sozinho (janelas acesas);</li>
 *   <li>pós-processamento: saturação, contraste e vinheta.</li>
 * </ol>
 */
public final class SceneRenderer implements Disposable {
    private static final Logger LOG = LoggerFactory.getLogger(SceneRenderer.class);
    private static final float OCCLUDED_ALPHA = 0.42f;
    /** Chão por máscara (Fase V); null se o shader não compilou (cai no desenho antigo). */
    private GroundMasks ground;

    /** Um personagem a desenhar, já com a posição interpolada. */
    public static final class ActorDraw {
        public String id;
        public String sprite;
        public double x, y, facing;
        public boolean moving;
        /** Acabou de levar um golpe (0 a 1): o sprite pisca avermelhado. */
        public float flash;
        /** Golpe em andamento, de 0 (começa a erguer o braço) a 1 (terminou); negativo = não está golpeando. */
        public float attack = -1f;
        /** Metros andados desde que apareceu (o quadro do andar sai daqui: o pé não desliza). */
        public double travel;
        /** Velocidade suavizada, em m/s (andar ou correr). */
        public double speed;
        /** O que está fazendo pela agenda ({@code ActorView.activity()}); decide a animação parada (V2). */
        public String activity = "";
        /** Camadas desenhadas por cima do corpo, no mesmo quadro e nesta ordem: cabeça, mão principal,
         * mão secundária (jogador, V2/V3). Vazia = nenhuma. */
        public List<String> camadas = List.of();
        /** O golpe a usar quando {@link #attack} ≥ 0 (o jogador escolhe pela arma, V3); null = o da folha. */
        public String golpe;
        /** Recuo de quem levou o golpe, de 1 (acabou de levar) a 0; 0 = nada (V3). */
        public float dano;
        /** Animação de uma vez só que manda sobre as outras (cair, levantar, morrer, desfazer), com o
         * progresso de 0 a 1; depois de 1 fica no último quadro de {@link #fim}, se houver (V3). */
        public String unica;
        public float unicaT;
        public String fim;
        /** Altura do desenho (m): quem dorme na cama fica sobre o colchão. Posta a cada quadro. */
        double z;
        /** Banco ou cama em que está (desenhado por cima dele, não atrás). */
        Static assento;
        /** A animação de quem está parado, já conferida com o lugar (assentar); null = parado. */
        String animParada;
    }

    /** Acima disto (m/s) o personagem corre, se a folha tiver a animação. */
    private static final double RUN_SPEED = 2.4;

    /** {@code shade}: 1 dentro do mapa; cai até 0 na faixa de fora, que some na penumbra. */
    private record TileDraw(int tx, int ty, TextureRegion region, int priority, float shade, int px, Texture world) {}

    private record Lamp(double x, double y, double z, float radius, float r, float g, float b) {}

    /** @param stateKey chave do prop com variantes (null = sempre o mesmo sprite); {@code def} é a definição dele */
    private record Static(Assets.Sprite sprite, double x, double y, double x0, double y0, double x1, double y1,
            double key, String stateKey, PropDef def, float shade, float phase) {}

    /** Variante do sprite pela posição (SPEC-visual §7.1): o mapa não muda, a vila fica variada. */
    private Assets.Sprite pick(String id, double x, double y) {
        id = especies.escolher(id, x, y, posHash(x, y) >>> 9);
        Assets.Sprite[] v = assets.variants(id);
        return v[Math.floorMod(posHash(x, y), v.length)];
    }

    private static int posHash(double x, double y) {
        int h = (int) Math.floor(x * 8) * 73856093 ^ (int) Math.floor(y * 8) * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return h ^ (h >>> 15);
    }

    private static float phaseOf(double x, double y) {
        return Math.floorMod(posHash(x, y) >>> 3, 1000) / 1000f;
    }

    private final Assets assets;
    /** V4: qual espécie de árvore vai em cada lugar (área, vizinhança, posição). */
    private final Especies especies;
    private final DayNight dayNight;
    private final List<TileDraw> tiles = new ArrayList<>();
    private final List<Static> statics = new ArrayList<>();
    /** Decalques deitados no chão (pedrinhas): desenhados logo depois do chão, sem ordenação. */
    private final List<Static> flats = new ArrayList<>();
    private final List<Lamp> lamps = new ArrayList<>();
    private final SpriteBatch batch = new SpriteBatch();

    /** Chamadas de desenho acumuladas desde o início (o benchmark divide pelos quadros). */
    public int totalRenderCalls() {
        return batch.totalRenderCalls;
    }
    private final Matrix4 screenMatrix = new Matrix4();
    private final Texture lightTexture;
    /** V3: um pixel branco, para as partículas (pintadas com a cor da paleta). */
    private final Texture branco;
    /** V3: efeitos de combate (impacto, gotas, poeira, aviso do golpe). */
    public final Particulas particulas = new Particulas();
    /** V4: folhas, pássaros, borboletas, vaga-lumes e mariposas (só ao ar livre). */
    private final Ambiente ambiente;
    /** Opção "Detalhes do ambiente": baixo desliga a vida ambiente. */
    public boolean ambienteLigado = true;
    private final List<Ambiente.Ponto> pontosArvore = new ArrayList<>(), pontosFlor = new ArrayList<>(),
            pontosLampiao = new ArrayList<>(), pontosFumaca = new ArrayList<>();
    private final Texture shadowTexture;
    private ShaderProgram postShader, silhouetteShader, paletaShader;
    private final Animacoes animacoes;
    private final AreaDef area;
    /** Shader em uso no lote dentro de drawSorted (troca por sprite indexado, D-103). */
    private ShaderProgram shaderAtual;
    private float reduzAtual = -1f, silhuetaAtual = -1f;
    /** Zoom do quadro (0 = vista de longe): decide se o sprite indexado precisa da média 2x2. */
    private int escala = 1;
    private FrameBuffer sceneFbo, lightFbo, glowFbo, maskFbo;
    /** Modos de {@link #drawSorted}: cena, brilho, máscara de todos os personagens, máscara só do jogador. */
    private static final int SCENE = 0, GLOW = 1, MASK_ALL = 2, MASK_PLAYER = 3;
    /** Teto da luz de presença (D-090): o reforço nunca deixa o personagem mais claro que isto. */
    private static final float PRESENCE_CAP = 0.88f;
    /** Reforço da luz de presença dentro de casa, sobre a penumbra fixa do interior. */
    private static final double INTERIOR_PRESENCE = 1.25;
    /** GL_MAX (0x8008): OpenGL 1.4+/ES 3; a constante não existe no GL20 do libGDX. */
    private static final int GL_MAX = 0x8008;
    /** Luz de presença ligada (console: {@code presenca on/off}, para comparar). */
    public boolean presenceEnabled = true;
    /** Pedido de medição (VT-03): no próximo quadro, guarda a máscara do jogador em {@link #playerMask}. */
    public boolean capturePlayerMask;
    /** Máscara do jogador do último pedido: branco onde ele aparece na tela; linhas de baixo para cima. */
    public Pixmap playerMask;
    private int width, height;
    private final List<List<ActorDraw>> buckets = new ArrayList<>();

    /** Como saber a variante atual de um prop com chave (árvore viva, morta, cortada); null = estado inicial. */
    public java.util.function.Function<String, String> propState = key -> null;
    public boolean lightingEnabled = true;
    public boolean postEnabled = true;
    /** Área escura (ruína, porão): ambiente quase preto a qualquer hora, luzes sempre acesas (RF-008). */
    public boolean darkArea;
    /** Raio de luz do personagem, em metros, segundo a simulação; infinito = dia claro. */
    public double playerLight = Double.POSITIVE_INFINITY;
    /** O personagem leva uma lamparina: a luz dele é quente. */
    public boolean playerLamp;
    private static final float[] DARK_AMBIENT = {0.03f, 0.03f, 0.04f};
    /** Dentro de casa (D-068): penumbra quente, a mesma a qualquer hora; lareira e lampião sempre acesos. */
    public boolean interior;
    private static final float[] INTERIOR_AMBIENT = {0.50f, 0.42f, 0.34f};
    private static final float INTERIOR_NIGHT = 0.7f;

    /** Largura, em tiles, da faixa desenhada além da borda do mapa nas áreas ao ar livre (D-079). */
    private static final int MARGIN = 14;

    /**
     * @param outdoor área ao ar livre: o chão continua além da borda do mapa, escurecendo, com mato cada vez
     *                mais fechado — o mundo segue, só não se anda até lá. Interiores e ruínas acabam onde acabam.
     */
    public SceneRenderer(AreaDef area, Map<String, PropDef> props, Assets assets, DayNight dayNight, boolean outdoor) {
        this.assets = assets;
        this.animacoes = Animacoes.carregar(assets.root());
        this.area = area;
        this.dayNight = dayNight;
        this.especies = outdoor ? Especies.carregar(assets.root(), area.id(), area.props(), assets::temEntrada)
                : Especies.vazia();

        int margin = outdoor ? MARGIN : 0;
        List<Decoration.Borda> borda = outdoor ? Decoration.borda(assets.root()) : List.of();
        for (int ty = -margin; ty < area.height() + margin; ty++) {
            for (int tx = -margin; tx < area.width() + margin; tx++) {
                int cx = Math.max(0, Math.min(area.width() - 1, tx)), cy = Math.max(0, Math.min(area.height() - 1, ty));
                String terrain = area.terrainAt(cx, cy); // fora do mapa, repete a borda: a estrada segue adiante
                if (AreaDef.NO_TERRAIN.equals(terrain)) continue;
                int out = Math.max(Math.max(-tx, tx - (area.width() - 1)), Math.max(-ty, ty - (area.height() - 1)));
                float shade = out <= 0 ? 1f : (float) Math.pow(Math.max(0f, 1f - out / (float) MARGIN), 1.6);
                Assets.Tiles t = assets.tiles(terrain);
                int hash = tx * 73856093 ^ ty * 19349663;
                tiles.add(new TileDraw(tx, ty, t.variants()[Math.floorMod(hash, t.variants().length)], t.priority(), shade,
                        t.px(), t.world()));
                // mato na faixa de fora: mais fechado quanto mais longe, e nunca em cima da estrada
                if (out > 1 && "grama".equals(terrain) && Math.floorMod(hash >> 4, 100) < 2 + out * 2) {
                    double x = tx + 0.5, y = ty + 0.5;
                    Assets.Sprite tree = pick(Math.floorMod(hash >> 11, 2) == 0 ? "prop.arvore" : "prop.arvore_b", x, y);
                    if (out < 5) pontosArvore.add(new Ambiente.Ponto(x, y, 4.0));
                    statics.add(new Static(tree, x, y, x - 0.3, y - 0.3, x + 0.3, y + 0.3, x + y, null, null, shade,
                            phaseOf(x, y)));
                }
                // V4: sub-bosque além da borda — arbustos, samambaia, capim e troncos caídos
                if (out >= 1 && "grama".equals(terrain)) {
                    int k = 0;
                    for (Decoration.Borda b : borda) {
                        k++;
                        if (out < b.desde()) continue;
                        java.util.SplittableRandom r = new java.util.SplittableRandom(hash * 31L + k * 977L);
                        double ch = b.chance() * Math.min(1.0, (out - b.desde() + 1) / 5.0);
                        int n = (int) Math.floor(ch + r.nextDouble());
                        for (int i = 0; i < n; i++) {
                            double x = tx + 0.1 + r.nextDouble() * 0.8, y = ty + 0.1 + r.nextDouble() * 0.8;
                            statics.add(new Static(pick(b.sprite(), x, y), x, y, x - 0.05, y - 0.05, x + 0.05, y + 0.05,
                                    x + y, null, null, shade, phaseOf(x, y)));
                        }
                    }
                }
            }
        }
        // terrenos de prioridade maior são desenhados depois e avançam sobre os vizinhos
        tiles.sort(Comparator.comparingInt(TileDraw::priority));

        for (PropPlacement p : area.props()) {
            PropDef def = props.get(p.propId());
            double hw = def != null ? def.width() / 2 : 0.3, hd = def != null ? def.depth() / 2 : 0.3;
            boolean variants = def != null && p.key() != null && !def.stateMap().isEmpty();
            Assets.Sprite sprite = pick(def == null ? "prop." + p.propId()
                    : variants ? def.spriteFor(def.initialState()) : def.spriteId(), p.x(), p.y());
            statics.add(new Static(sprite, p.x(), p.y(), p.x() - hw, p.y() - hd, p.x() + hw, p.y() + hd, p.x() + p.y(),
                    variants ? p.key() : null, def, 1f, phaseOf(p.x(), p.y())));
            if (def == null) continue;
            if ("vegetacao".equals(def.category())) pontosArvore.add(new Ambiente.Ponto(p.x(), p.y(), 4.0));
            // V5 (SPEC-visual §7.1): onde ficam as janelas e a chaminé vem do JSON da arte (a variante
            // desta posição); o props.yaml continua dizendo o raio e a cor. Sem pontos, vale o props.yaml.
            boolean daArte = false;
            for (Map.Entry<String, double[]> w : sprite.world().entrySet()) {
                double[] o = w.getValue();
                if (w.getKey().startsWith("fumaca")) pontosFumaca.add(new Ambiente.Ponto(p.x() + o[0], p.y() + o[1], o[2]));
                else if (w.getKey().startsWith("luz") && !def.lightList().isEmpty()) {
                    PropDef.Light l = def.lightList().get(0);
                    double[] c = l.color();
                    lamps.add(new Lamp(p.x() + o[0], p.y() + o[1], o[2], (float) l.radius(),
                            (float) c[0], (float) c[1], (float) c[2]));
                    daArte = true;
                }
            }
            if (!daArte) for (PropDef.Light l : def.lightList()) {
                double[] o = l.offset(), c = l.color();
                lamps.add(new Lamp(p.x() + o[0], p.y() + o[1], o[2], (float) l.radius(),
                        (float) c[0], (float) c[1], (float) c[2]));
            }
            if ("lampiao".equals(p.propId())) pontosLampiao.add(new Ambiente.Ponto(p.x() + 0.2, p.y() - 0.2, 2.05));
        }
        // decoração do chão (Fase V): tufos e flores entram na ordenação; pedrinhas ficam deitadas
        if (outdoor) {
            List<Decoration.Box> obstacles = new ArrayList<>();
            for (Static st : statics) if (st.def() != null) obstacles.add(new Decoration.Box(st.x0(), st.y0(), st.x1(), st.y1()));
            // V4: nada a 1 m de porta (o mato não esconde a entrada)
            for (Map.Entry<String, etrava.sim.nav.Pathfinder.Point> e : area.spots().entrySet()) {
                if (!e.getKey().contains("porta")) continue;
                double px = e.getValue().x(), py = e.getValue().y();
                obstacles.add(new Decoration.Box(px - 0.5, py - 0.5, px + 0.5, py + 0.5));
            }
            for (Decoration.Piece d : Decoration.place(assets.root(), area.id(), area, obstacles)) {
                Assets.Sprite sp = pick(d.sprite(), d.x(), d.y());
                if (d.sprite().startsWith("deco.flor")) pontosFlor.add(new Ambiente.Ponto(d.x(), d.y(), 0));
                Static st = new Static(sp, d.x(), d.y(), d.x() - 0.05, d.y() - 0.05, d.x() + 0.05, d.y() + 0.05,
                        d.x() + d.y(), null, null, 1f, phaseOf(d.x(), d.y()));
                if (d.flat()) flats.add(st);
                else statics.add(st);
            }
        }
        if (outdoor) {
            ambiente = new Ambiente(pontosArvore, pontosFlor, pontosLampiao, pontosFumaca);
        } else {
            ambiente = null;
        }
        statics.sort(Comparator.comparingDouble(Static::key));
        for (int i = 0; i <= statics.size(); i++) buckets.add(new ArrayList<>());

        ground = GroundMasks.create();
        lightTexture = radial(256, false);
        shadowTexture = radial(64, true);
        Pixmap um = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        um.setColor(1f, 1f, 1f, 1f);
        um.fill();
        branco = new Texture(um);
        um.dispose();
        ShaderProgram.pedantic = false;
        silhouetteShader = new ShaderProgram(Gdx.files.classpath("shaders/post.vert"),
                Gdx.files.classpath("shaders/silhouette.frag"));
        if (!silhouetteShader.isCompiled()) {
            LOG.warn("Shader de silhueta não compilou; sem luz de presença: {}", silhouetteShader.getLog());
            silhouetteShader.dispose();
            silhouetteShader = null;
        }
        paletaShader = new ShaderProgram(Gdx.files.classpath("shaders/post.vert"), Gdx.files.classpath("shaders/paleta.frag"));
        if (!paletaShader.isCompiled()) {
            LOG.error("Shader de paleta não compilou; sprites indexados vão sair errados: {}", paletaShader.getLog());
            paletaShader.dispose();
            paletaShader = null;
        }
        postShader = new ShaderProgram(Gdx.files.classpath("shaders/post.vert"), Gdx.files.classpath("shaders/post.frag"));
        if (!postShader.isCompiled()) {
            LOG.warn("Shader de pós-processamento não compilou; seguindo sem ele: {}", postShader.getLog());
            postShader.dispose();
            postShader = null;
        }
    }

    /** Mancha redonda que some para a borda: branca (luz) ou preta (sombra). */
    private static Texture radial(int size, boolean black) {
        Pixmap px = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        px.setBlending(Pixmap.Blending.None);
        float c = black ? 0f : 1f, half = size / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float d = (float) Math.hypot(x + 0.5f - half, y + 0.5f - half) / half;
                float a = Math.max(0f, 1f - d);
                px.setColor(c, c, c, a * a * (3 - 2 * a));
                px.drawPixel(x, y);
            }
        }
        Texture t = new Texture(px);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        px.dispose();
        return t;
    }

    public void resize(int w, int h) {
        if (w <= 0 || h <= 0 || (w == width && h == height)) return;
        width = w;
        height = h;
        if (sceneFbo != null) sceneFbo.dispose();
        if (lightFbo != null) lightFbo.dispose();
        if (glowFbo != null) glowFbo.dispose();
        if (maskFbo != null) maskFbo.dispose();
        maskFbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        glowFbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        glowFbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        sceneFbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        lightFbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        sceneFbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        lightFbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        screenMatrix.setToOrtho2D(0, 0, w, h);
    }

    /**
     * @param actors   personagens a desenhar; o primeiro é o jogador
     * @param hour     hora do dia, de 0 a 24, com fração
     * @param animTime segundos de jogo, para escolher o quadro das animações
     */
    public void render(CameraRig camera, List<ActorDraw> actors, double hour, float animTime,
            boolean hasDestination, double destX, double destY) {
        if (sceneFbo == null) resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        DayNight.Key light = dayNight.at(hour);
        float night = lightingEnabled ? (darkArea ? 1f : interior ? INTERIOR_NIGHT : (float) light.night()) : 0f;
        float[] bounds = camera.visibleBounds();
        escala = camera.pixelScale();
        for (ActorDraw a : actors) assentar(a, animTime);
        ActorDraw player = actors.isEmpty() ? null : actors.get(0);

        // ---- 1. cena
        sceneFbo.begin();
        ScreenUtils.clear(0.06f, 0.07f, 0.06f, 1f);
        batch.setProjectionMatrix(camera.combined());
        batch.enableBlending();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();
        drawGround(bounds);
        drawFlats(bounds, animTime);
        if (hasDestination) {
            batch.setColor(0f, 0f, 0f, 0.45f);
            batch.draw(shadowTexture, sx(destX, destY) - 16, sy(destX, destY, 0) - 8, 32, 16);
        }
        batch.setColor(1f, 1f, 1f, 0.55f);
        for (ActorDraw a : actors) batch.draw(shadowTexture, sx(a.x, a.y) - 26, sy(a.x, a.y, 0) - 13, 52, 26);
        batch.setColor(1f, 1f, 1f, 1f);
        drawSorted(actors, player, bounds, animTime, SCENE);
        usarShader(null, 0, false);
        particulas.atualizar(Gdx.graphics.getDeltaTime());
        particulas.desenhar(batch, branco, bounds);
        if (ambiente != null && ambienteLigado) {
            // vento global: rajadas lentas por soma de senos (o mesmo para a cena toda)
            double vento = 0.5 + 0.3 * Math.sin(animTime * 0.21) + 0.2 * Math.sin(animTime * 0.53 + 1.3);
            ambiente.atualizar(Gdx.graphics.getDeltaTime(), darkArea ? 1 : light.night(), Math.max(0, vento), bounds);
            ambiente.desenhar(batch, branco, bounds, animTime);
        }
        batch.end();
        sceneFbo.end();

        // luz de presença (D-090): onde um personagem aparece (sem o que está na frente dele),
        // a luz não cai abaixo de um piso — o próprio céu da hora, reforçado. À noite fica azulada.
        float[] floor = presenceFloor(light);
        boolean presence = floor != null && silhouetteShader != null;
        if (presence) renderMask(camera, actors, player, bounds, animTime, MASK_ALL);

        // ---- 2. luz
        if (lightingEnabled) {
            lightFbo.begin();
            if (darkArea) ScreenUtils.clear(DARK_AMBIENT[0], DARK_AMBIENT[1], DARK_AMBIENT[2], 1f);
            else if (interior) ScreenUtils.clear(INTERIOR_AMBIENT[0], INTERIOR_AMBIENT[1], INTERIOR_AMBIENT[2], 1f);
            else ScreenUtils.clear((float) light.ambient()[0], (float) light.ambient()[1], (float) light.ambient()[2], 1f);
            if (night > 0.01f) {
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                batch.begin();
                for (Lamp l : lamps) drawLight(l.x(), l.y(), l.z(), l.radius(), l.r(), l.g(), l.b(), night, bounds);
                if (ambiente != null && ambienteLigado)
                    ambiente.luzes(v -> drawLight(v[0], v[1], v[2], 0.35f, 0.85f, 0.80f, 0.45f, night * (float) v[3], bounds));
                // o raio de luz do personagem (RF-008): um resto de claridade, ou a lamparina na mão
                if (player != null && (!interior || playerLamp)) {
                    float radius = Double.isInfinite(playerLight) ? 3.2f : (float) playerLight;
                    // V3: a lamparina vai na mão esquerda; a luz sai de lá (30 cm ao lado, 10 cm à frente)
                    double lx = player.x + 0.30 * Math.sin(player.facing) + 0.10 * Math.cos(player.facing);
                    double ly = player.y - 0.30 * Math.cos(player.facing) + 0.10 * Math.sin(player.facing);
                    if (playerLamp) drawLight(lx, ly, 0.8, radius, 1.0f, 0.74f, 0.42f, night, bounds);
                    else drawLight(player.x, player.y, 1.0, radius, 0.55f, 0.58f, 0.70f, night * 0.6f, bounds);
                }
                batch.end();
            }
            if (presence) { // máximo entre a luz que já há e o piso, só nos pixels dos personagens
                Gdx.gl.glBlendEquation(GL_MAX);
                batch.setProjectionMatrix(screenMatrix);
                batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE);
                batch.setColor(floor[0], floor[1], floor[2], 1f);
                batch.begin();
                drawFlipped(maskFbo.getColorBufferTexture());
                batch.end();
                Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
                batch.setColor(1f, 1f, 1f, 1f);
                batch.setProjectionMatrix(camera.combined());
            }
            lightFbo.end();

            // brilhos (janelas, lamparinas) num buffer à parte: os sprites comuns entram
            // em preto, para tapar o brilho do que está atrás deles
            if (night > 0.01f) {
                glowFbo.begin();
                ScreenUtils.clear(0f, 0f, 0f, 1f);
                batch.setProjectionMatrix(camera.combined());
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                batch.begin();
                drawSorted(actors, player, bounds, animTime, GLOW);
                batch.end();
                glowFbo.end();
            }

            // ---- 3. cena × luz, depois o que brilha sozinho
            sceneFbo.begin();
            batch.setProjectionMatrix(screenMatrix);
            batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO);
            batch.setColor(1f, 1f, 1f, 1f);
            batch.begin();
            drawFlipped(lightFbo.getColorBufferTexture());
            batch.end();
            if (night > 0.01f) {
                batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE);
                batch.setColor(night, night, night, 1f);
                batch.begin();
                drawFlipped(glowFbo.getColorBufferTexture());
                batch.end();
                batch.setColor(1f, 1f, 1f, 1f);
            }
            sceneFbo.end();
        }

        // ---- 4. tela, com pós-processamento
        boolean post = postEnabled && postShader != null;
        batch.setProjectionMatrix(screenMatrix);
        batch.disableBlending();
        batch.setColor(1f, 1f, 1f, 1f);
        if (post) batch.setShader(postShader);
        batch.begin();
        if (post) {
            postShader.setUniformf("u_saturation", (float) light.saturation());
            postShader.setUniformf("u_contrast", (float) light.contrast());
            postShader.setUniformf("u_vignette", (float) light.vignette());
        }
        drawFlipped(sceneFbo.getColorBufferTexture());
        batch.end();
        batch.setShader(null);
        batch.enableBlending();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        if (capturePlayerMask && silhouetteShader != null && player != null) {
            capturePlayerMask = false;
            renderMask(camera, actors, player, bounds, animTime, MASK_PLAYER);
            maskFbo.begin();
            if (playerMask != null) playerMask.dispose();
            playerMask = Pixmap.createFromFrameBuffer(0, 0, width, height);
            maskFbo.end();
        }
    }

    /**
     * Piso de luz dos personagens nesta hora, ou null se não há o que reforçar (dia claro,
     * luz desligada, área escura — onde enxergar depende da lamparina, RF-008).
     */
    private float[] presenceFloor(DayNight.Key light) {
        if (!presenceEnabled || !lightingEnabled || darkArea) return null;
        double[] base = interior ? new double[] {INTERIOR_AMBIENT[0], INTERIOR_AMBIENT[1], INTERIOR_AMBIENT[2]}
                : light.ambient();
        double k = interior ? INTERIOR_PRESENCE : light.actorLift();
        if (k <= 1.001) return null;
        float[] f = new float[3];
        boolean raises = false;
        for (int i = 0; i < 3; i++) {
            f[i] = (float) Math.min(PRESENCE_CAP, base[i] * k);
            raises |= f[i] > base[i] + 0.01;
        }
        return raises ? f : null;
    }

    /** Silhuetas na máscara: personagens brancos, o que fica na frente deles em preto. */
    private void renderMask(CameraRig camera, List<ActorDraw> actors, ActorDraw player, float[] bounds, float animTime,
            int mode) {
        maskFbo.begin();
        ScreenUtils.clear(0f, 0f, 0f, 0f);
        batch.setProjectionMatrix(camera.combined());
        batch.setShader(silhouetteShader);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();
        drawSorted(actors, player, bounds, animTime, mode);
        batch.end();
        batch.setShader(null);
        maskFbo.end();
    }

    private void drawFlipped(Texture texture) {
        batch.draw(texture, 0, 0, width, height, 0, 0, texture.getWidth(), texture.getHeight(), false, true);
    }

    private void drawGround(float[] b) {
        // os tiles estão em ordem de prioridade; cada grupo de terreno novo (textura de mundo) sai em
        // duas passadas — com o anel da borda e depois só o miolo (GroundMasks)
        int i = 0;
        while (i < tiles.size()) {
            TileDraw first = tiles.get(i);
            int j = i;
            while (j < tiles.size() && tiles.get(j).priority() == first.priority() && tiles.get(j).world() == first.world()) j++;
            if (first.world() != null && ground != null) {
                batch.setShader(ground.shader());
                batch.flush();
                first.world().bind(1);
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
                ground.shader().setUniformi("u_terrain", 1);
                ground.shader().setUniformf("u_size", first.world().getWidth(), first.world().getHeight());
                for (int pass = 0; pass < 2; pass++) {
                    for (int k = i; k < j; k++) {
                        TileDraw t = tiles.get(k);
                        float cx = sx(t.tx() + 0.5, t.ty() + 0.5), cy = sy(t.tx() + 0.5, t.ty() + 0.5, 0);
                        TextureRegion m = ground.mask(t.tx(), t.ty(), pass == 0);
                        float w = m.getRegionWidth() * 2f, h = m.getRegionHeight() * 2f;
                        if (cx + w / 2 < b[0] || cx - w / 2 > b[2] || cy + h / 2 < b[1] || cy - h / 2 > b[3]) continue;
                        batch.setColor(t.shade(), t.shade(), t.shade(), 1f);
                        batch.draw(m, cx - w / 2, cy - h / 2, w, h);
                    }
                }
                batch.flush();
                batch.setShader(null);
            } else {
                for (int k = i; k < j; k++) {
                    TileDraw t = tiles.get(k);
                    float cx = sx(t.tx() + 0.5, t.ty() + 0.5), cy = sy(t.tx() + 0.5, t.ty() + 0.5, 0);
                    float w = t.region().getRegionWidth() * t.px(), h = t.region().getRegionHeight() * t.px();
                    if (cx + w / 2 < b[0] || cx - w / 2 > b[2] || cy + h / 2 < b[1] || cy - h / 2 > b[3]) continue;
                    batch.setColor(t.shade(), t.shade(), t.shade(), 1f);
                    batch.draw(t.region(), cx - w / 2, cy - h / 2, w, h);
                }
            }
            i = j;
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawFlats(float[] b, float animTime) {
        for (Static s : flats) {
            Assets.Sprite sp = s.sprite();
            TextureRegion region = sp.frames()[sp.frameAt(animTime, s.phase())];
            int px = sp.px();
            float w = region.getRegionWidth() * px, h = region.getRegionHeight() * px;
            float left = snap(sx(s.x(), s.y()) - sp.anchorX() * px);
            float bottom = snap(sy(s.x(), s.y(), 0) - (h - sp.anchorY() * px));
            if (left + w < b[0] || left > b[2] || bottom + h < b[1] || bottom > b[3]) continue;
            usarShader(region, px, false);
            batch.draw(region, left, bottom, w, h);
        }
        if (shaderAtual != null) batch.setShader(null);
        shaderAtual = null;
    }

    private void drawLight(double x, double y, double z, float radius, float r, float g, float b, float intensity,
            float[] bounds) {
        float w = radius * 2 * 90f, h = radius * 2 * 62f;
        float cx = sx(x, y), cy = sy(x, y, z * 0.6);
        if (cx + w / 2 < bounds[0] || cx - w / 2 > bounds[2] || cy + h / 2 < bounds[1] || cy - h / 2 > bounds[3]) return;
        batch.setColor(r, g, b, intensity);
        batch.draw(lightTexture, cx - w / 2, cy - h / 2, w, h);
    }

    /**
     * Sprites de trás para a frente. Um personagem está na frente de um objeto se
     * estiver a leste ou ao sul da base dele; é desenhado logo antes do primeiro
     * objeto que o encobre. Com {@code glow}, tudo sai em preto, menos as camadas de brilho.
     */
    private void drawSorted(List<ActorDraw> actors, ActorDraw player, float[] b, float animTime, int mode) {
        boolean glow = mode == GLOW, mask = mode == MASK_ALL || mode == MASK_PLAYER;
        ShaderProgram base = mask ? silhouetteShader : null;
        shaderAtual = base;
        for (List<ActorDraw> bucket : buckets) bucket.clear();
        for (ActorDraw a : actors) {
            int index = statics.size();
            for (int i = 0; i < statics.size(); i++) {
                Static s = statics.get(i);
                if (s == a.assento) continue;
                if (a.x < s.x1() && a.y < s.y1() && s.key() > a.x + a.y - 12) {
                    index = i;
                    break;
                }
            }
            buckets.get(index).add(a);
        }
        boolean playerDrawn = false;
        for (int i = 0; i <= statics.size(); i++) {
            List<ActorDraw> bucket = buckets.get(i);
            if (!bucket.isEmpty()) {
                bucket.sort(Comparator.comparingDouble(a -> a.x + a.y));
                for (ActorDraw a : bucket) {
                    float lit = mode == MASK_ALL || a == player ? 1f : 0f;
                    if (glow) batch.setColor(0f, 0f, 0f, 1f);
                    else if (mask) batch.setColor(lit, lit, lit, 1f);
                    else if (a.flash > 0) batch.setColor(1f, 1f - a.flash * 0.55f, 1f - a.flash * 0.6f, 1f);
                    usarShader(null, 0, mask);
                    drawActor(a, animTime);
                    if (!glow) batch.setColor(1f, 1f, 1f, 1f);
                    if (a == player) playerDrawn = true;
                }
            }
            if (i == statics.size()) break;
            Static s = statics.get(i);
            Assets.Sprite sp = s.sprite();
            if (s.stateKey() != null) { // o estado mora na simulação; aqui só se escolhe a imagem (RF-043)
                String state = propState.apply(s.stateKey());
                if (state != null) sp = assets.sprite(s.def().spriteFor(state));
            }
            int f = sp.frameAt(animTime, s.phase());
            TextureRegion region = sp.frames()[f];
            int px = sp.px();
            float w = region.getRegionWidth() * px, h = region.getRegionHeight() * px;
            float left = snap(sx(s.x(), s.y()) - sp.anchorX() * px);
            float bottom = snap(sy(s.x(), s.y(), 0) - (h - sp.anchorY() * px));
            if (left + w < b[0] || left > b[2] || bottom + h < b[1] || bottom > b[3]) continue;
            float alpha = 1f;
            if (playerDrawn && player != null) { // objeto na frente do jogador e cobrindo-o (RF-023)
                float ppx = sx(player.x, player.y), ppy = sy(player.x, player.y, 0.9);
                if (ppx > left + w * 0.12f && ppx < left + w * 0.88f && ppy > bottom + 8 && ppy < bottom + h * 0.92f) {
                    alpha = OCCLUDED_ALPHA;
                }
            }
            usarShader(region, px, mask);
            if (mask) { // o que está na frente tapa o personagem (translúcido tapa em parte)
                batch.setColor(0f, 0f, 0f, alpha);
                batch.draw(region, left, bottom, w, h);
            } else if (glow) {
                batch.setColor(0f, 0f, 0f, alpha);
                batch.draw(region, left, bottom, w, h);
                if (sp.glow() != null) {
                    usarShader(sp.glow()[f], px, false);
                    batch.setColor(1f, 1f, 1f, alpha);
                    batch.draw(sp.glow()[f], left, bottom, w, h);
                }
            } else {
                batch.setColor(s.shade(), s.shade(), s.shade(), alpha);
                batch.draw(region, left, bottom, w, h);
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
        if (shaderAtual != base) batch.setShader(base);
        shaderAtual = base;
    }

    /**
     * Shader do próximo desenho dentro de drawSorted: paleta.frag para página do atlas indexado
     * (com a média 2x2 quando um pixel de arte fica menor que um de tela), o shader do passe para
     * o resto. Só troca (e esvazia o lote) quando muda.
     */
    private void usarShader(TextureRegion r, int px, boolean mask) {
        boolean idx = r != null && paletaShader != null && assets.indexada(r.getTexture());
        ShaderProgram alvo = idx ? paletaShader : (mask ? silhouetteShader : null);
        float reduz = idx && px * (escala == 0 ? 0.25f : escala / 2f) < 0.99f ? 1f : 0f;
        float silh = mask ? 1f : 0f;
        if (alvo != shaderAtual) {
            batch.setShader(alvo);
            shaderAtual = alvo;
            if (idx) {
                assets.paleta().bind(1);
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
                paletaShader.setUniformi("u_paleta", 1);
                paletaShader.setUniformf("u_texel", 1f / r.getTexture().getWidth(), 1f / r.getTexture().getHeight());
                paletaShader.setUniformf("u_reduz", reduz);
                paletaShader.setUniformf("u_silhueta", silh);
                reduzAtual = reduz;
                silhuetaAtual = silh;
            }
        } else if (idx && (reduz != reduzAtual || silh != silhuetaAtual)) {
            batch.flush();
            paletaShader.setUniformf("u_reduz", reduz);
            paletaShader.setUniformf("u_silhueta", silh);
            reduzAtual = reduz;
            silhuetaAtual = silh;
        }
    }

    /** Atividade de cada ator e desde quando (segundos de animTime): {@code sentar} toca uma vez. */
    private final Map<String, String> atividadeAtual = new HashMap<>();
    private final Map<String, Float> atividadeDesde = new HashMap<>();

    private float desde(ActorDraw a, String chave, float animTime) {
        if (!chave.equals(atividadeAtual.get(a.id))) {
            atividadeAtual.put(a.id, chave);
            atividadeDesde.put(a.id, animTime);
        }
        return animTime - atividadeDesde.getOrDefault(a.id, animTime);
    }

    private static final double ALTURA_COLCHAO = 0.55;

    /**
     * Decide a animação de quem está parado: a da atividade da agenda (animacoes.yaml), mas só se
     * o lugar dela estiver ali (V2). Martelo pede bigorna ou forja; bancada pede bancada, balcão ou
     * mesa; trabalho no chão pede plantio, feno, sacos...; sentado pede banco; dormir pede cama.
     * Longe do lugar, fica em {@code parado}. Perto, vira-se para o objeto, e quem senta ou deita é
     * desenhado nele: no ponto do eixo comprido mais perto, de costas para o assento (deitado ao
     * longo da cama). Só desenho: a simulação continua com o NPC no lugar da agenda.
     */
    private void assentar(ActorDraw a, float animTime) {
        a.z = 0;
        a.assento = null;
        a.animParada = null;
        if (a.moving || a.attack >= 0 || a.unica != null || a.sprite == null) return;
        Assets.Sheet sheet = assets.sheet(a.sprite);
        if (sheet == null || !sheet.has("parado")) return;
        String anim = animacoes.escolher(a.id, a.activity, animTime, Animacoes.fase(a.id));
        if (!sheet.has(anim)) return;
        Animacoes.Lugar lugar = animacoes.lugar(anim);
        if (lugar == null) {
            a.animParada = anim;
            return;
        }
        Static melhor = null;
        double md = lugar.alcance();
        for (Static st : statics) {
            if (st.def() == null || !lugar.props().contains(st.def().id())) continue;
            double cx = Math.max(st.x0(), Math.min(st.x1(), a.x)), cy = Math.max(st.y0(), Math.min(st.y1(), a.y));
            double d = Math.hypot(a.x - cx, a.y - cy);
            if (d < md) {
                md = d;
                melhor = st;
            }
        }
        boolean noTerreno = !lugar.terrenos().isEmpty()
                && lugar.terrenos().contains(area.terrainAt((int) Math.floor(a.x), (int) Math.floor(a.y)));
        if (melhor == null && !noTerreno) return;           // fora do lugar: parado
        a.animParada = anim;
        if (melhor == null) return;                         // no plantio: trabalha onde está
        boolean acomoda = anim.equals("sentado") || anim.equals("dormir");
        if (!acomoda) {                                     // de frente para a bigorna, a bancada, o feno
            a.facing = Math.atan2(melhor.y() - a.y, melhor.x() - a.x);
            return;
        }
        boolean cama = anim.equals("dormir");
        boolean longoEmX = melhor.x1() - melhor.x0() >= melhor.y1() - melhor.y0();
        double margem = 0.3;
        if (longoEmX) {
            double lado = Math.signum(a.y - melhor.y());
            a.x = Math.max(melhor.x0() + margem, Math.min(melhor.x1() - margem, a.x));
            a.y = melhor.y();
            a.facing = cama ? 0 : (lado >= 0 ? Math.PI / 2 : -Math.PI / 2);
        } else {
            double lado = Math.signum(a.x - melhor.x());
            a.y = Math.max(melhor.y0() + margem, Math.min(melhor.y1() - margem, a.y));
            a.x = melhor.x();
            a.facing = cama ? Math.PI / 2 : (lado >= 0 ? 0 : Math.PI);
        }
        if (cama) a.z = ALTURA_COLCHAO;
        a.assento = melhor;
    }

    private void drawActor(ActorDraw a, float animTime) {
        Assets.Sheet sheet = assets.sheet(a.sprite);
        int dirs = sheet.directions();
        int dir = Math.floorMod((int) Math.round(a.facing / (Math.PI * 2 / dirs)), dirs);
        int frame;
        if (sheet.has("parado")) { // folha da Fase V: nomes em português, passo pela distância
            double fase = Animacoes.fase(a.id);
            String name;
            float tempoNela = 0;
            boolean umaVez = false;
            String golpe = a.attack >= 0 ? golpeDe(a, sheet) : null;
            if (a.unica != null && sheet.has(a.unica)) {          // V3: cair, levantar, morrer, desfazer
                name = a.unica;
                umaVez = true;
                if (a.unicaT >= 1f && a.fim != null && sheet.has(a.fim)) name = a.fim;
                tempoNela = Math.min(1f, a.unicaT);
            } else if (golpe != null) name = golpe;
            else if (a.dano > 0 && sheet.has("dano")) {
                name = "dano";
                umaVez = true;
                tempoNela = 1f - a.dano;
            } else if (a.moving) {
                name = a.speed > RUN_SPEED && sheet.has("correr") ? "correr" : "andar";
                desde(a, "", animTime);
            } else {
                // parado: a animação sai da atividade da agenda (V2, assets/config/animacoes.yaml)
                name = a.animParada != null && sheet.has(a.animParada) ? a.animParada : "parado";
                tempoNela = desde(a, name, animTime);
                if (name.equals("sentado") && sheet.has("sentar")) {
                    var s = sheet.anim("sentar");
                    if (tempoNela < s.frames() / Math.max(1f, s.fps())) {
                        name = "sentar";
                        umaVez = true;
                    }
                }
            }
            var anim = sheet.anim(name);
            int n = Math.max(1, anim.frames());
            if (name.equals(golpe)) frame = anim.start() + Math.min(n - 1, (int) (a.attack * n));
            else if (umaVez && (a.unica != null || name.equals("dano"))) // progresso 0..1 → quadro
                frame = anim.start() + Math.min(n - 1, (int) (tempoNela * n));
            else if (umaVez) frame = anim.start() + Math.min(n - 1, (int) (tempoNela * anim.fps()));
            else if (anim.stride() != null && anim.stride() > 0)
                frame = anim.start() + Math.floorMod((int) Math.floor(a.travel / anim.stride() * n), n);
            // fase própria: o bando não respira (nem martela) junto
            else frame = anim.start() + Math.floorMod((int) (animTime * anim.fps() + fase * n), n);
        } else {
            boolean striking = a.attack >= 0 && sheet.animations().containsKey("attack");
            var anim = sheet.anim(striking ? "attack" : a.moving ? "walk" : "idle");
            frame = striking
                    ? anim.start() + Math.min(anim.frames() - 1, (int) (a.attack * anim.frames()))
                    : anim.start() + (int) (animTime * anim.fps()) % Math.max(1, anim.frames());
        }
        int px = sheet.px();
        float x = snap(sx(a.x, a.y) - sheet.anchorX() * px);
        float y = snap(sy(a.x, a.y, a.z) - (sheet.frameH() - sheet.anchorY()) * px);
        batch.draw(sheet.texture(), x, y, sheet.frameW() * px, sheet.frameH() * px,
                frame * sheet.frameW(), dir * sheet.frameH(), sheet.frameW(), sheet.frameH(), false, false);
        // camadas (SPEC-visual §6.4): mesma grade de quadros, a âncora da própria camada; cabeça,
        // mão principal e mão secundária, nesta ordem
        for (String camada : a.camadas) {
            Assets.Sheet c = assets.sheet(camada);
            if (c == null || c.directions() != sheet.directions() || !c.has("parado")) continue;
            int cpx = c.px();
            float cx = snap(sx(a.x, a.y) - c.anchorX() * cpx);
            float cy = snap(sy(a.x, a.y, a.z) - (c.frameH() - c.anchorY()) * cpx);
            batch.draw(c.texture(), cx, cy, c.frameW() * cpx, c.frameH() * cpx,
                    frame * c.frameW(), dir * c.frameH(), c.frameW(), c.frameH(), false, false);
        }
    }

    /** O golpe de quem está golpeando: o pedido (jogador, pela arma) ou o primeiro que a folha tem. */
    private static String golpeDe(ActorDraw a, Assets.Sheet sheet) {
        if (a.golpe != null && sheet.has(a.golpe)) return a.golpe;
        for (String g : GOLPES) if (sheet.has(g)) return g;
        return null;
    }

    private static final String[] GOLPES = {"golpe_corte", "bote", "golpe", "golpe_estocada", "golpe_pesado"};

    @Override
    public void dispose() {
        batch.dispose();
        lightTexture.dispose();
        branco.dispose();
        shadowTexture.dispose();
        if (postShader != null) postShader.dispose();
        if (ground != null) ground.dispose();
        if (silhouetteShader != null) silhouetteShader.dispose();
        if (paletaShader != null) paletaShader.dispose();
        if (maskFbo != null) maskFbo.dispose();
        if (playerMask != null) playerMask.dispose();
        if (sceneFbo != null) sceneFbo.dispose();
        if (lightFbo != null) lightFbo.dispose();
        if (glowFbo != null) glowFbo.dispose();
    }
}
