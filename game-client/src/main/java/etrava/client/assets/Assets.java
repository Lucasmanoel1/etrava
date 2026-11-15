package etrava.client.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import etrava.sim.content.AssetManifest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolve IDs lógicos de assets em texturas (RF-420). Se o ID não está no
 * manifesto, o arquivo não existe ou não carrega, devolve um placeholder e
 * avisa uma vez no log — nunca derruba o jogo (RF-421).
 */
public final class Assets implements Disposable {
    private static final Logger LOG = LoggerFactory.getLogger(Assets.class);

    /**
     * Sprite com ponto de apoio no chão, em pixels de arte; {@code glow} (opcional) acende à noite.
     * {@code px}: pixels de mundo por pixel de arte (4 na arte do M8, 2 na da Fase V, D-086).
     * Com mais de um quadro, é um laço (vento, fogo) a {@code fps}. {@code world} (V5): pontos em metros a
     * partir do centro da base, vindos do JSON da folha (luzN = janela acesa, fumacaN = boca da chaminé).
     */
    public record Sprite(TextureRegion[] frames, TextureRegion[] glow, float anchorX, float anchorY, int px, float fps,
            Map<String, double[]> world) {
        public TextureRegion region() { return frames[0]; }

        public TextureRegion emissive() { return glow == null ? null : glow[0]; }

        /** Quadro do laço no instante {@code t} (segundos), com a fase própria do objeto (0 a 1). */
        public int frameAt(float t, float phase) {
            if (frames.length <= 1) return 0;
            return Math.floorMod((int) Math.floor(t * fps + phase * frames.length), frames.length);
        }
    }

    /**
     * Variações de um tipo de terreno. {@code world} (arte da Fase V): uma textura repetível alinhada
     * ao mundo, desenhada por máscara em vez de losangos (SPEC-visual §7.2).
     */
    public record Tiles(TextureRegion[] variants, int priority, int px, Texture world) {}

    /** Folha de sprites de personagem: linhas = direções, colunas = quadros. */
    public record Sheet(Texture texture, int frameW, int frameH, float anchorX, float anchorY, int directions,
            Map<String, AssetManifest.Anim> animations, int px) {

        public AssetManifest.Anim anim(String name) {
            AssetManifest.Anim a = animations.get(name);
            return a != null ? a : new AssetManifest.Anim(0, 1, 1);
        }

        public boolean has(String name) { return animations.containsKey(name); }
    }

    private final Path root;
    private final AssetManifest manifest;
    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<String, Sprite[]> variants = new HashMap<>();
    private final Map<String, Tiles> tiles = new HashMap<>();
    private final Map<String, Sheet> sheets = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private Texture checker;
    /**
     * Atlas dos sprites do mundo (D-099): cada quadro de cada sprite vai para páginas grandes, para
     * o lote não quebrar a cada troca de textura (eram ~2.200 chamadas de desenho por quadro).
     */
    /**
     * Página do atlas: 4096² quando a placa aceita (toda a arte numa textura só: o lote não quebra
     * na ordem de profundidade), senão 2048² (D-099, D-100).
     */
    private static final int PAGINA = paginaDoAtlas();
    private final PixmapPacker packer = new PixmapPacker(PAGINA, PAGINA, Pixmap.Format.RGBA8888, 2, false);
    /**
     * Atlas indexado (D-103): a arte é de paleta única, então cada pixel guarda só o índice da cor
     * (1 byte, textura de alfa) e a cor sai da {@link #paleta()} no shader {@code paleta.frag}. Um
     * quarto da memória — o que paga o vento de 96 quadros. Sprite com cor que não cabe na paleta
     * (mais de 255 cores no total) fica no atlas RGBA.
     */
    private final PixmapPacker packerIdx = new PixmapPacker(PAGINA, PAGINA, Pixmap.Format.Alpha, 2, false);
    /** Cor RGBA8888 → índice (1..255); sem caixa de Integer: a conversão passa por dezenas de milhões de pixels. */
    private final com.badlogic.gdx.utils.IntIntMap paletaIdx = new com.badlogic.gdx.utils.IntIntMap();
    private final com.badlogic.gdx.utils.IntArray paletaCores = new com.badlogic.gdx.utils.IntArray();
    private final Map<String, Boolean> arquivoIndexado = new HashMap<>();
    private final Set<Texture> indexadas = new HashSet<>();
    private Texture paletaTex;
    private boolean paletaSuja = true;

    private static int paginaDoAtlas() {
        try {
            java.nio.IntBuffer b = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(16);
            com.badlogic.gdx.Gdx.gl.glGetIntegerv(com.badlogic.gdx.graphics.GL20.GL_MAX_TEXTURE_SIZE, b);
            // D-107: página grande = menos trocas de textura = menos chamadas de desenho; memória não tem teto
            int max = b.get(0);
            return max >= 8192 ? 8192 : max >= 4096 ? 4096 : 2048;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return 2048;   // sem contexto GL (testes)
        }
    }
    private final java.util.List<Object[]> pending = new java.util.ArrayList<>();
    private boolean deferUpload;

    public Assets(Path assetsDir) {
        this.root = assetsDir;
        this.manifest = AssetManifest.load(assetsDir);
        for (String p : manifest.problems()) LOG.warn("Manifesto: {}", p);
        LOG.info("Manifesto de assets: {} entradas", manifest.entries().size());
    }

    public AssetManifest manifest() { return manifest; }

    /** Pasta assets/ (configurações de apresentação: decoração, luz). */
    public Path root() { return root; }

    /** Empacota de uma vez todos os sprites do manifesto (uma subida por página, não uma por sprite). */
    public void preloadSprites() {
        deferUpload = true;
        int n = 0;
        // V5: do menor para o maior (pelo tamanho do arquivo). Casas, objetos e decoração ficam juntos
        // na primeira página e só as árvores (192 quadros cada) ocupam as outras: o lote quebra menos
        // na ordem de profundidade (eram 276 chamadas por quadro na vila com as casas novas).
        java.util.List<AssetManifest.Entry> ordem = new java.util.ArrayList<>();
        for (AssetManifest.Entry e : manifest.entries().values()) if ("sprite".equals(e.type())) ordem.add(e);
        Map<String, Long> peso = new HashMap<>();
        for (AssetManifest.Entry e : ordem) {
            long t = 0;
            try {
                if (e.file() != null) t = Files.size(root.resolve(e.file()));
            } catch (java.io.IOException | RuntimeException ex) {
                t = 0;
            }
            peso.put(e.id(), t);
        }
        ordem.sort(java.util.Comparator.comparingLong(e -> peso.get(e.id())));
        for (AssetManifest.Entry e : ordem) {
            variants(e.id());
            n++;
        }
        deferUpload = false;
        flushAtlas();
        LOG.info("Atlas: {} sprites; {} página(s) indexada(s) e {} RGBA de {}×{}; paleta com {} cores",
                n, packerIdx.getPages().size, packer.getPages().size, PAGINA, PAGINA, paletaIdx.size);
    }

    private TextureRegion packed(String id, String file, int frame, int frames) {
        return packed(id, file, frame, frames, false);
    }

    /** @param indexar pode ir para o atlas indexado (só sprites do mundo; a interface usa o shader padrão) */
    private TextureRegion packed(String id, String file, int frame, int frames, boolean indexar) {
        String name = file + "#" + frame;
        TextureRegion r = new TextureRegion();
        boolean idx;
        if (packer.getRect(name) == null && packerIdx.getRect(name) == null) {
            Pixmap whole = pixmap(id, file);
            if (whole == null) return null;
            idx = indexar && indexavel(file, whole);
            int fw = whole.getWidth() / frames, h = whole.getHeight();
            Pixmap one;
            if (idx) {
                one = new Pixmap(fw, h, Pixmap.Format.Alpha);
                one.setBlending(Pixmap.Blending.None);
                java.nio.ByteBuffer src = whole.getPixels(), dst = one.getPixels();
                int W = whole.getWidth();
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < fw; x++)
                        dst.put(y * fw + x, (byte) indice(rgba(src, (y * W + frame * fw + x) * 4)));
                packerIdx.pack(name, one);
            } else {
                one = new Pixmap(fw, h, Pixmap.Format.RGBA8888);
                one.setBlending(Pixmap.Blending.None);
                one.drawPixmap(whole, 0, 0, frame * fw, 0, fw, h);
                packer.pack(name, one);
            }
            one.dispose();
        }
        pending.add(new Object[] {r, name});
        if (!deferUpload) flushAtlas();
        return r;
    }

    private static int rgba(java.nio.ByteBuffer b, int i) {
        return (b.get(i) & 0xFF) << 24 | (b.get(i + 1) & 0xFF) << 16 | (b.get(i + 2) & 0xFF) << 8 | (b.get(i + 3) & 0xFF);
    }

    /** O arquivo inteiro cabe na paleta? Se sim, as cores novas entram nela (índice 0 = vazio). */
    private boolean indexavel(String file, Pixmap pm) {
        return arquivoIndexado.computeIfAbsent(file, f -> {
            if (pm.getFormat() != Pixmap.Format.RGBA8888) return false;
            java.nio.ByteBuffer b = pm.getPixels();
            com.badlogic.gdx.utils.IntArray novas = new com.badlogic.gdx.utils.IntArray();
            com.badlogic.gdx.utils.IntSet vistas = new com.badlogic.gdx.utils.IntSet();
            int n = pm.getWidth() * pm.getHeight(), ultima = 0;
            for (int i = 0; i < n; i++) {
                int c = rgba(b, i * 4);
                if ((c & 0xFF) == 0 || c == ultima) continue;
                ultima = c;
                if (paletaIdx.containsKey(c) || !vistas.add(c)) continue;
                novas.add(c);
                if (paletaIdx.size + novas.size > 255) {
                    LOG.info("Atlas: {} fica em RGBA (a paleta passaria de 255 cores)", file);
                    return false;
                }
            }
            for (int k = 0; k < novas.size; k++) {
                paletaCores.add(novas.get(k));
                paletaIdx.put(novas.get(k), paletaCores.size);
            }
            if (novas.size > 0) paletaSuja = true;
            return true;
        });
    }

    /** Índice da cor RGBA8888 (0 = vazio). */
    private int indice(int rgba) {
        if ((rgba & 0xFF) == 0) return 0;
        return paletaIdx.get(rgba, 0);
    }

    /** Textura 256×1 com a cor de cada índice (0 = transparente), para {@code paleta.frag}. */
    public Texture paleta() {
        if (paletaSuja || paletaTex == null) {
            Pixmap pm = new Pixmap(256, 1, Pixmap.Format.RGBA8888);
            pm.setBlending(Pixmap.Blending.None);
            pm.setColor(0, 0, 0, 0);
            pm.fill();
            for (int k = 0; k < paletaCores.size; k++) pm.drawPixel(k + 1, 0, paletaCores.get(k));
            if (paletaTex == null) {
                paletaTex = new Texture(pm);
                paletaTex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            } else {
                paletaTex.draw(pm, 0, 0);
            }
            pm.dispose();
            paletaSuja = false;
        }
        return paletaTex;
    }

    /** A textura é uma página do atlas indexado (desenhar com {@code paleta.frag})? */
    public boolean indexada(Texture t) {
        return t != null && indexadas.contains(t);
    }

    private final Map<String, Pixmap> pixmaps = new HashMap<>();

    private Pixmap pixmap(String id, String file) {
        if (pixmaps.containsKey(file)) return pixmaps.get(file);
        Path path = root.resolve(file);
        Pixmap pm = null;
        if (!Files.exists(path)) missing(id, "arquivo ausente: " + file);
        else {
            try {
                pm = new Pixmap(Gdx.files.absolute(path.toString()));
            } catch (RuntimeException ex) {
                missing(id, "falha ao carregar " + file + ": " + ex.getMessage());
            }
        }
        pixmaps.put(file, pm);
        return pm;
    }

    private void flushAtlas() {
        packer.updatePageTextures(Texture.TextureFilter.Linear, Texture.TextureFilter.Nearest, false);
        // índice não se filtra (misturaria cores): a média da vista de longe é feita no shader
        packerIdx.updatePageTextures(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest, false);
        for (Object[] p : pending) {
            String name = (String) p[1];
            TextureRegion r = (TextureRegion) p[0];
            PixmapPacker origem = packerIdx.getRect(name) != null ? packerIdx : packer;
            PixmapPacker.PixmapPackerRectangle rc = origem.getRect(name);
            Texture t = origem.getPage(name).getTexture();
            if (origem == packerIdx) indexadas.add(t);
            r.setTexture(t);
            r.setRegion(rc.getX(), rc.getY(), rc.getWidth(), rc.getHeight());
        }
        pending.clear();
        for (Pixmap pm : pixmaps.values()) if (pm != null) pm.dispose();
        pixmaps.clear();
    }

    public Sprite sprite(String id) {
        return variants(id)[0];
    }

    /** Todas as variantes de um sprite (ao menos uma); o cliente escolhe pela posição. */
    public Sprite[] variants(String id) {
        return variants.computeIfAbsent(id, key -> {
            AssetManifest.Entry e = manifest.get(key);
            if (e == null) {
                missing(key, "ID fora do manifesto");
                return new Sprite[] {placeholderSprite()};
            }
            java.util.List<String> files = e.variants() != null && !e.variants().isEmpty() ? e.variants()
                    : java.util.Collections.singletonList(e.file());
            java.util.List<Sprite> list = new java.util.ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                String file = files.get(i);
                Pixmap whole = pixmap(key, file);
                if (whole == null) continue;
                int tw = whole.getWidth(), th = whole.getHeight();
                String glowFile = e.emissive() == null ? null : i == 0 ? e.emissive() : file.replace(".png", "_brilho.png");
                boolean glow = glowFile != null && Files.exists(root.resolve(glowFile));
                int n = e.frames() != null && e.frames() > 1 ? e.frames() : 1;
                int fw = tw / n;
                TextureRegion[] fr = new TextureRegion[n];
                TextureRegion[] gl = glow ? new TextureRegion[n] : null;
                boolean defer = deferUpload;
                deferUpload = true; // um sprite inteiro antes de subir
                // só a arte nova do mundo vai para o atlas indexado; ícones são desenhados pela interface
                boolean indexar = e.scaleOr() == 2 && !key.startsWith("icone.");
                for (int f = 0; f < n; f++) {
                    fr[f] = packed(key, file, f, n, indexar);
                    if (gl != null) gl[f] = packed(key, glowFile, f, n, indexar);
                }
                deferUpload = defer;
                if (!defer) flushAtlas();
                float ax = e.anchor() != null ? (float) e.anchor()[0] : fw / 2f;
                float ay = e.anchor() != null ? (float) e.anchor()[1] : th;
                list.add(new Sprite(fr, gl, ax, ay, e.scaleOr(), e.fps() != null ? e.fps().floatValue() : 6f,
                        key.startsWith("prop.") ? worldPoints(file) : Map.of()));
            }
            if (list.isEmpty()) list.add(placeholderSprite());
            return list.toArray(new Sprite[0]);
        });
    }

    private Sprite placeholderSprite() {
        TextureRegion r = new TextureRegion(checker(), 0, 0, 12, 18);
        return new Sprite(new TextureRegion[] {r}, null, 6, 18, 4, 1f, Map.of());
    }

    /** Pontos {@code world} do JSON que acompanha a folha (SPEC-visual §7.1); vazio se não houver. */
    private Map<String, double[]> worldPoints(String file) {
        Path json = root.resolve(file.replace(".png", ".json"));
        if (!Files.exists(json)) return Map.of();
        try {
            com.badlogic.gdx.utils.JsonValue w = new com.badlogic.gdx.utils.JsonReader()
                    .parse(Files.readString(json)).get("world");
            if (w == null) return Map.of();
            Map<String, double[]> out = new java.util.TreeMap<>();
            for (com.badlogic.gdx.utils.JsonValue v = w.child; v != null; v = v.next) out.put(v.name, v.asDoubleArray());
            return out;
        } catch (Exception ex) {
            LOG.warn("pontos do asset ilegíveis em {}: {}", json, ex.toString());
            return Map.of();
        }
    }

    public Tiles tiles(String terrain) {
        return tiles.computeIfAbsent(terrain, key -> {
            String id = "tile." + key;
            AssetManifest.Entry e = manifest.get(id);
            java.util.List<TextureRegion> list = new java.util.ArrayList<>();
            if (e == null) missing(id, "ID fora do manifesto");
            else if (e.files() != null) {
                for (String f : e.files()) {
                    Texture t = load(id, f);
                    if (t != null) list.add(new TextureRegion(t));
                }
            } else if (e.file() != null) {
                Texture t = load(id, e.file());
                if (t != null) list.add(new TextureRegion(t));
            }
            if (list.isEmpty()) list.add(new TextureRegion(placeholderTile(key)));
            int priority = e != null && e.priority() != null ? e.priority() : 0;
            int px = e != null ? e.scaleOr() : 4;
            Texture world = null;
            if (e != null && px == 2 && e.files() == null && list.get(0).getTexture() != null) {
                // textura de mundo (Fase V): repete alinhada ao mundo, sem losango
                world = list.get(0).getTexture();
                world.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            }
            return new Tiles(list.toArray(new TextureRegion[0]), priority, px, world);
        });
    }

    /** O manifesto tem esta entrada (sem carregar nada)? */
    public boolean temEntrada(String id) {
        return id != null && manifest.get(id) != null;
    }

    public Sheet sheet(String id) {
        return sheets.computeIfAbsent(id == null ? "(sem sprite)" : id, key -> {
            AssetManifest.Entry e = manifest.get(key);
            Texture tex = e == null ? missing(key, "ID fora do manifesto") : load(key, e.file());
            if (e == null || tex == null || e.frame() == null) {
                Texture t = placeholderCharacter();
                return new Sheet(t, t.getWidth(), t.getHeight(), t.getWidth() / 2f, t.getHeight() - 1, 1, Map.of(), 4);
            }
            float ax = e.anchor() != null ? (float) e.anchor()[0] : e.frame()[0] / 2f;
            float ay = e.anchor() != null ? (float) e.anchor()[1] : e.frame()[1];
            return new Sheet(tex, e.frame()[0], e.frame()[1], ax, ay,
                    e.directions() != null ? e.directions() : 1,
                    e.animations() != null ? e.animations() : Map.of(), e.scaleOr());
        });
    }

    /** Retrato de NPC para a tela de diálogo; xadrez se faltar. */
    public Texture portrait(String id) {
        AssetManifest.Entry e = manifest.get(id);
        Texture tex = e == null ? missing(id, "ID fora do manifesto") : load(id, e.file());
        return tex != null ? tex : checker();
    }

    /** Textura inteira de uma ilustração ou outra imagem do manifesto; null se faltar (o chamador decide o que pôr). */
    public Texture texture(String id) {
        AssetManifest.Entry e = manifest.get(id);
        if (e == null) return missing(id, "ID fora do manifesto");
        return load(id, e.file());
    }

    private Texture load(String id, String file) {
        if (file == null) return missing(id, "entrada sem arquivo");
        if (textures.containsKey(file)) return textures.get(file);
        Path path = root.resolve(file);
        Texture tex = null;
        if (!Files.exists(path)) {
            missing(id, "arquivo ausente: " + file);
        } else {
            try {
                tex = new Texture(Gdx.files.absolute(path.toString()));
                // pixel art: ampliar sem filtro; reduzir (só na vista de longe, D-098) com média
                tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Nearest);
            } catch (RuntimeException ex) {
                missing(id, "falha ao carregar " + file + ": " + ex.getMessage());
            }
        }
        textures.put(file, tex);
        return tex;
    }

    private Texture missing(String id, String reason) {
        if (warned.add(id)) LOG.warn("Asset '{}': {} — usando placeholder", id, reason);
        return null;
    }

    // ---------------------------------------------------------------- placeholders

    private Texture checker() {
        if (checker == null) {
            Pixmap px = new Pixmap(16, 24, Pixmap.Format.RGBA8888);
            for (int y = 0; y < 24; y += 2)
                for (int x = 0; x < 16; x += 2) {
                    px.setColor(((x + y) / 2) % 2 == 0 ? new Color(0.85f, 0.1f, 0.75f, 1f) : new Color(0.1f, 0.1f, 0.1f, 1f));
                    px.fillRectangle(x, y, 2, 2);
                }
            checker = new Texture(px);
            checker.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            px.dispose();
            textures.put("(placeholder xadrez)", checker);
        }
        return checker;
    }

    private Texture placeholderTile(String terrain) {
        int hash = terrain.hashCode();
        Color c = new Color(0.35f + (hash & 0xFF) / 640f, 0.35f + ((hash >> 8) & 0xFF) / 640f,
                0.35f + ((hash >> 16) & 0xFF) / 640f, 1f);
        Pixmap px = new Pixmap(32, 16, Pixmap.Format.RGBA8888);
        px.setColor(c);
        px.fillTriangle(0, 8, 16, 0, 32, 8);
        px.fillTriangle(0, 8, 16, 16, 32, 8);
        Texture t = new Texture(px);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        px.dispose();
        textures.put("(placeholder tile " + terrain + ")", t);
        return t;
    }

    private Texture placeholderCharacter() {
        String key = "(placeholder personagem)";
        if (textures.get(key) != null) return textures.get(key);
        Pixmap px = new Pixmap(12, 26, Pixmap.Format.RGBA8888);
        px.setColor(0.72f, 0.52f, 0.24f, 1f);
        px.fillRectangle(3, 6, 6, 19);
        px.setColor(0.86f, 0.70f, 0.55f, 1f);
        px.fillRectangle(3, 0, 6, 6);
        Texture t = new Texture(px);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        px.dispose();
        textures.put(key, t);
        return t;
    }

    @Override
    public void dispose() {
        for (Texture t : textures.values()) if (t != null) t.dispose();
        packer.dispose();
        packerIdx.dispose();
        if (paletaTex != null) paletaTex.dispose();
    }
}
