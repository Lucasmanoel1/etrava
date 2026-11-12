package etrava.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import etrava.sim.content.AssetManifest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * As fontes do jogo (D-077): geradas do TTF no tamanho exato em que aparecem, para o texto
 * grande não borrar (RNF-040). IDs lógicos {@code fonte.texto} e {@code fonte.titulo} no
 * manifesto; se o arquivo faltar ou não carregar, entra a fonte embutida do libGDX, esticada,
 * e um aviso no log (RF-421).
 */
public final class Fonts {
    private static final Logger LOG = LoggerFactory.getLogger(Fonts.class);
    /** Altura do texto corrido no tamanho normal, em pixels de tela. */
    public static final int BASE_SIZE = 19;
    private static final String CHARS = FreeTypeFontGenerator.DEFAULT_CHARS + "—–…−→‘’“”•";

    private final Path assetsDir;
    private final AssetManifest manifest;

    public Fonts(Path assetsDir, AssetManifest manifest) {
        this.assetsDir = assetsDir;
        this.manifest = manifest;
    }

    /** Texto das telas de papel: sem sombra, para a tinta ficar limpa. */
    public BitmapFont text(float scale) {
        return make("fonte.texto", Math.round(BASE_SIZE * scale), false, scale);
    }

    /** Texto solto sobre o cenário (relógio, avisos, balões): com uma sombra escura, para ler sobre qualquer fundo. */
    public BitmapFont overlay(float scale) {
        return make("fonte.texto", Math.round(BASE_SIZE * scale), true, scale);
    }

    /** O nome do jogo no menu. */
    public BitmapFont title() {
        return make("fonte.titulo", 76, true, 4f);
    }

    private BitmapFont make(String id, int size, boolean shadow, float fallbackScale) {
        AssetManifest.Entry e = manifest.get(id);
        Path file = e == null || e.file() == null ? null : assetsDir.resolve(e.file());
        if (file != null && Files.exists(file)) {
            FreeTypeFontGenerator generator = null;
            try {
                generator = new FreeTypeFontGenerator(Gdx.files.absolute(file.toString()));
                FreeTypeFontParameter p = new FreeTypeFontParameter();
                p.size = size;
                p.characters = CHARS;
                p.kerning = true;
                p.minFilter = Texture.TextureFilter.Linear;
                p.magFilter = Texture.TextureFilter.Linear;
                if (shadow) {
                    p.shadowOffsetX = 1;
                    p.shadowOffsetY = 1;
                    p.shadowColor = new Color(0.05f, 0.04f, 0.05f, 0.85f);
                }
                return generator.generateFont(p);
            } catch (RuntimeException | UnsatisfiedLinkError ex) {
                LOG.warn("Fonte '{}' não carregou ({}); usando a fonte embutida", id, ex.getMessage());
            } finally {
                if (generator != null) generator.dispose();
            }
        } else {
            LOG.warn("Fonte '{}' ausente; usando a fonte embutida", id);
        }
        BitmapFont fallback = new BitmapFont();
        fallback.getData().setScale(fallbackScale);
        return fallback;
    }
}
