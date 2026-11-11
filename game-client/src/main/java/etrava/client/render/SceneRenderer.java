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
    private static final int ART = IsoProjection.ART_SCALE;

    /** Um personagem a desenhar, já com a posição interpolada. */
    public static final class ActorDraw {
        public String id;
        public String sprite;
        public double x, y, facing;
        public boolean moving;
    }

    private record TileDraw(int tx, int ty, TextureRegion region, int priority) {}

    private record Lamp(double x, double y, double z, float radius, float r, float g, float b) {}

    /** @param stateKey chave do prop com variantes (null = sempre o mesmo sprite); {@code def} é a definição dele */
    private record Static(Assets.Sprite sprite, double x, double y, double x0, double y0, double x1, double y1,
            double key, String stateKey, PropDef def) {}

    private final Assets assets;
    private final DayNight dayNight;
    private final List<TileDraw> tiles = new ArrayList<>();
    private final List<Static> statics = new ArrayList<>();
    private final List<Lamp> lamps = new ArrayList<>();
    private final SpriteBatch batch = new SpriteBatch();
    private final Matrix4 screenMatrix = new Matrix4();
    private final Texture lightTexture;
    private final Texture shadowTexture;
    private ShaderProgram postShader;
    private FrameBuffer sceneFbo, lightFbo, glowFbo;
    private int width, height;
    private final List<List<ActorDraw>> buckets = new ArrayList<>();

    /** Como saber a variante atual de um prop com chave (árvore viva, morta, cortada); null = estado inicial. */
    public java.util.function.Function<String, String> propState = key -> null;
    public boolean lightingEnabled = true;
    public boolean postEnabled = true;

    public SceneRenderer(AreaDef area, Map<String, PropDef> props, Assets assets, DayNight dayNight) {
        this.assets = assets;
        this.dayNight = dayNight;

        for (int ty = 0; ty < area.height(); ty++) {
            for (int tx = 0; tx < area.width(); tx++) {
                String terrain = area.terrainAt(tx, ty);
                if (AreaDef.NO_TERRAIN.equals(terrain)) continue;
                Assets.Tiles t = assets.tiles(terrain);
                int variant = Math.floorMod(tx * 73856093 ^ ty * 19349663, t.variants().length);
                tiles.add(new TileDraw(tx, ty, t.variants()[variant], t.priority()));
            }
        }
        // terrenos de prioridade maior são desenhados depois e avançam sobre os vizinhos
        tiles.sort(Comparator.comparingInt(TileDraw::priority));

        for (PropPlacement p : area.props()) {
            PropDef def = props.get(p.propId());
            double hw = def != null ? def.width() / 2 : 0.3, hd = def != null ? def.depth() / 2 : 0.3;
            boolean variants = def != null && p.key() != null && !def.stateMap().isEmpty();
            Assets.Sprite sprite = assets.sprite(def == null ? "prop." + p.propId()
                    : variants ? def.spriteFor(def.initialState()) : def.spriteId());
            statics.add(new Static(sprite, p.x(), p.y(), p.x() - hw, p.y() - hd, p.x() + hw, p.y() + hd, p.x() + p.y(),
                    variants ? p.key() : null, def));
            if (def == null) continue;
            for (PropDef.Light l : def.lightList()) {
                double[] o = l.offset(), c = l.color();
                lamps.add(new Lamp(p.x() + o[0], p.y() + o[1], o[2], (float) l.radius(),
                        (float) c[0], (float) c[1], (float) c[2]));
            }
        }
        statics.sort(Comparator.comparingDouble(Static::key));
        for (int i = 0; i <= statics.size(); i++) buckets.add(new ArrayList<>());

        lightTexture = radial(256, false);
        shadowTexture = radial(64, true);
        ShaderProgram.pedantic = false;
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
        float night = lightingEnabled ? (float) light.night() : 0f;
        float[] bounds = camera.visibleBounds();
        ActorDraw player = actors.isEmpty() ? null : actors.get(0);

        // ---- 1. cena
        sceneFbo.begin();
        ScreenUtils.clear(0.06f, 0.07f, 0.06f, 1f);
        batch.setProjectionMatrix(camera.combined());
        batch.enableBlending();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();
        drawGround(bounds);
        if (hasDestination) {
            batch.setColor(0f, 0f, 0f, 0.45f);
            batch.draw(shadowTexture, sx(destX, destY) - 16, sy(destX, destY, 0) - 8, 32, 16);
        }
        batch.setColor(1f, 1f, 1f, 0.55f);
        for (ActorDraw a : actors) batch.draw(shadowTexture, sx(a.x, a.y) - 26, sy(a.x, a.y, 0) - 13, 52, 26);
        batch.setColor(1f, 1f, 1f, 1f);
        drawSorted(actors, player, bounds, animTime, false);
        batch.end();
        sceneFbo.end();

        // ---- 2. luz
        if (lightingEnabled) {
            lightFbo.begin();
            ScreenUtils.clear((float) light.ambient()[0], (float) light.ambient()[1], (float) light.ambient()[2], 1f);
            if (night > 0.01f) {
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                batch.begin();
                for (Lamp l : lamps) drawLight(l.x(), l.y(), l.z(), l.radius(), l.r(), l.g(), l.b(), night, bounds);
                // um resto de claridade em volta do jogador, para ele não sumir no escuro
                if (player != null) drawLight(player.x, player.y, 1.0, 3.2f, 0.55f, 0.58f, 0.70f, night * 0.6f, bounds);
                batch.end();
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
                drawSorted(actors, player, bounds, animTime, true);
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
    }

    private void drawFlipped(Texture texture) {
        batch.draw(texture, 0, 0, width, height, 0, 0, texture.getWidth(), texture.getHeight(), false, true);
    }

    private void drawGround(float[] b) {
        for (TileDraw t : tiles) {
            float cx = sx(t.tx() + 0.5, t.ty() + 0.5), cy = sy(t.tx() + 0.5, t.ty() + 0.5, 0);
            float w = t.region().getRegionWidth() * ART, h = t.region().getRegionHeight() * ART;
            if (cx + w / 2 < b[0] || cx - w / 2 > b[2] || cy + h / 2 < b[1] || cy - h / 2 > b[3]) continue;
            batch.draw(t.region(), cx - w / 2, cy - h / 2, w, h);
        }
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
    private void drawSorted(List<ActorDraw> actors, ActorDraw player, float[] b, float animTime, boolean glow) {
        for (List<ActorDraw> bucket : buckets) bucket.clear();
        for (ActorDraw a : actors) {
            int index = statics.size();
            for (int i = 0; i < statics.size(); i++) {
                Static s = statics.get(i);
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
                    if (glow) batch.setColor(0f, 0f, 0f, 1f);
                    drawActor(a, animTime);
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
            TextureRegion region = sp.region();
            float w = sp.region().getRegionWidth() * ART, h = sp.region().getRegionHeight() * ART;
            float left = snap(sx(s.x(), s.y()) - sp.anchorX() * ART);
            float bottom = snap(sy(s.x(), s.y(), 0) - (h - sp.anchorY() * ART));
            if (left + w < b[0] || left > b[2] || bottom + h < b[1] || bottom > b[3]) continue;
            float alpha = 1f;
            if (playerDrawn && player != null) { // objeto na frente do jogador e cobrindo-o (RF-023)
                float px = sx(player.x, player.y), py = sy(player.x, player.y, 0.9);
                if (px > left + w * 0.12f && px < left + w * 0.88f && py > bottom + 8 && py < bottom + h * 0.92f) {
                    alpha = OCCLUDED_ALPHA;
                }
            }
            if (glow) {
                batch.setColor(0f, 0f, 0f, alpha);
                batch.draw(region, left, bottom, w, h);
                if (sp.emissive() != null) {
                    batch.setColor(1f, 1f, 1f, alpha);
                    batch.draw(sp.emissive(), left, bottom, w, h);
                }
            } else {
                batch.setColor(1f, 1f, 1f, alpha);
                batch.draw(region, left, bottom, w, h);
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawActor(ActorDraw a, float animTime) {
        Assets.Sheet sheet = assets.sheet(a.sprite);
        int dirs = sheet.directions();
        int dir = Math.floorMod((int) Math.round(a.facing / (Math.PI * 2 / dirs)), dirs);
        var anim = sheet.anim(a.moving ? "walk" : "idle");
        int frame = anim.start() + (int) (animTime * anim.fps()) % Math.max(1, anim.frames());
        float x = snap(sx(a.x, a.y) - sheet.anchorX() * ART);
        float y = snap(sy(a.x, a.y, 0) - (sheet.frameH() - sheet.anchorY()) * ART);
        batch.draw(sheet.texture(), x, y, sheet.frameW() * ART, sheet.frameH() * ART,
                frame * sheet.frameW(), dir * sheet.frameH(), sheet.frameW(), sheet.frameH(), false, false);
    }

    @Override
    public void dispose() {
        batch.dispose();
        lightTexture.dispose();
        shadowTexture.dispose();
        if (postShader != null) postShader.dispose();
        if (sceneFbo != null) sceneFbo.dispose();
        if (lightFbo != null) lightFbo.dispose();
        if (glowFbo != null) glowFbo.dispose();
    }
}
