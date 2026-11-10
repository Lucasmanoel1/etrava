package etrava.client.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
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

    /** Sprite com ponto de apoio no chão; {@code emissive} (opcional) acende à noite. */
    public record Sprite(TextureRegion region, float anchorX, float anchorY, TextureRegion emissive) {}

    /** Variações de um tipo de terreno. */
    public record Tiles(TextureRegion[] variants, int priority) {}

    /** Folha de sprites de personagem: linhas = direções, colunas = quadros. */
    public record Sheet(Texture texture, int frameW, int frameH, float anchorX, float anchorY, int directions,
            Map<String, AssetManifest.Anim> animations) {

        public AssetManifest.Anim anim(String name) {
            AssetManifest.Anim a = animations.get(name);
            return a != null ? a : new AssetManifest.Anim(0, 1, 1);
        }
    }

    private final Path root;
    private final AssetManifest manifest;
    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<String, Sprite> sprites = new HashMap<>();
    private final Map<String, Tiles> tiles = new HashMap<>();
    private final Map<String, Sheet> sheets = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private Texture checker;

    public Assets(Path assetsDir) {
        this.root = assetsDir;
        this.manifest = AssetManifest.load(assetsDir);
        for (String p : manifest.problems()) LOG.warn("Manifesto: {}", p);
        LOG.info("Manifesto de assets: {} entradas", manifest.entries().size());
    }

    public Sprite sprite(String id) {
        return sprites.computeIfAbsent(id, key -> {
            AssetManifest.Entry e = manifest.get(key);
            Texture tex = e == null ? missing(key, "ID fora do manifesto") : load(key, e.file());
            if (e == null || tex == null) {
                TextureRegion r = new TextureRegion(checker(), 0, 0, 48, 72);
                return new Sprite(r, 24, 72, null);
            }
            float ax = e.anchor() != null ? (float) e.anchor()[0] : tex.getWidth() / 2f;
            float ay = e.anchor() != null ? (float) e.anchor()[1] : tex.getHeight();
            Texture glow = e.emissive() != null ? load(key, e.emissive()) : null;
            return new Sprite(new TextureRegion(tex), ax, ay, glow != null ? new TextureRegion(glow) : null);
        });
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
            return new Tiles(list.toArray(new TextureRegion[0]), priority);
        });
    }

    public Sheet sheet(String id) {
        return sheets.computeIfAbsent(id == null ? "(sem sprite)" : id, key -> {
            AssetManifest.Entry e = manifest.get(key);
            Texture tex = e == null ? missing(key, "ID fora do manifesto") : load(key, e.file());
            if (e == null || tex == null || e.frame() == null) {
                Texture t = placeholderCharacter();
                return new Sheet(t, t.getWidth(), t.getHeight(), t.getWidth() / 2f, t.getHeight() - 6, 1, Map.of());
            }
            float ax = e.anchor() != null ? (float) e.anchor()[0] : e.frame()[0] / 2f;
            float ay = e.anchor() != null ? (float) e.anchor()[1] : e.frame()[1];
            return new Sheet(tex, e.frame()[0], e.frame()[1], ax, ay,
                    e.directions() != null ? e.directions() : 1,
                    e.animations() != null ? e.animations() : Map.of());
        });
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
                tex = new Texture(Gdx.files.absolute(path.toString()), true);
                tex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
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
            Pixmap px = new Pixmap(64, 96, Pixmap.Format.RGBA8888);
            for (int y = 0; y < 96; y += 8)
                for (int x = 0; x < 64; x += 8) {
                    px.setColor(((x + y) / 8) % 2 == 0 ? new Color(0.85f, 0.1f, 0.75f, 1f) : new Color(0.1f, 0.1f, 0.1f, 1f));
                    px.fillRectangle(x, y, 8, 8);
                }
            checker = new Texture(px);
            px.dispose();
            textures.put("(placeholder xadrez)", checker);
        }
        return checker;
    }

    private Texture placeholderTile(String terrain) {
        int hash = terrain.hashCode();
        Color c = new Color(0.35f + (hash & 0xFF) / 640f, 0.35f + ((hash >> 8) & 0xFF) / 640f,
                0.35f + ((hash >> 16) & 0xFF) / 640f, 1f);
        Pixmap px = new Pixmap(128, 64, Pixmap.Format.RGBA8888);
        px.setColor(c);
        px.fillTriangle(0, 32, 64, 0, 128, 32);
        px.fillTriangle(0, 32, 64, 64, 128, 32);
        Texture t = new Texture(px);
        px.dispose();
        textures.put("(placeholder tile " + terrain + ")", t);
        return t;
    }

    private Texture placeholderCharacter() {
        String key = "(placeholder personagem)";
        if (textures.get(key) != null) return textures.get(key);
        Pixmap px = new Pixmap(48, 104, Pixmap.Format.RGBA8888);
        px.setColor(0.72f, 0.52f, 0.24f, 1f);
        px.fillRectangle(13, 22, 22, 74);
        px.fillCircle(24, 96, 11);
        px.setColor(0.86f, 0.70f, 0.55f, 1f);
        px.fillCircle(24, 13, 11);
        Texture t = new Texture(px);
        px.dispose();
        textures.put(key, t);
        return t;
    }

    @Override
    public void dispose() {
        for (Texture t : textures.values()) if (t != null) t.dispose();
    }
}
