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
        return text(scale, 1);
    }

    /** Como {@link #text(float)}, para um Stage com escala {@code k} ({@link Escala}): gerada em k vezes e reduzida a 1/k. */
    public BitmapFont text(float scale, int k) {
        return make("fonte.texto", Math.round(BASE_SIZE * scale * k), false, scale, k);
    }

    /** Texto solto sobre o cenário (relógio, avisos, balões): com uma sombra escura, para ler sobre qualquer fundo. */
    public BitmapFont overlay(float scale) {
        return overlay(scale, 1);
    }

    public BitmapFont overlay(float scale, int k) {
        return make("fonte.texto", Math.round(BASE_SIZE * scale * k), true, scale, k);
    }

    /** O nome do jogo no menu. */
    public BitmapFont title() {
        return title(1);
    }

    public BitmapFont title(int k) {
        return title(1f, k);
    }

    /**
     * O nome do jogo acompanhando a altura da tela como o texto: tamanho físico = 76 × {@code porAltura}
     * (altura/720, no mínimo 1); gerado nesse tamanho e reduzido a 1/k ao entrar no Stage.
     */
    public BitmapFont title(float porAltura, int k) {
        return make("fonte.titulo", Math.round(76 * porAltura), true, 4f * porAltura, k);
    }

    /**
     * @param size tamanho físico, em pixels de tela (arredondado aqui, e não antes de multiplicar por k,
     *             para a fonte acompanhar a altura da tela de pixel em pixel)
     * @param k    escala da interface: a fonte entra no Stage com escala de 1/k e ocupa {@code size / k} unidades
     */
    private BitmapFont make(String id, int size, boolean shadow, float fallbackScale, int k) {
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
                BitmapFont font = generator.generateFont(p);
                if (k > 1) font.getData().setScale(1f / k);
                return font;
            } catch (RuntimeException | UnsatisfiedLinkError ex) {
                LOG.warn("Fonte '{}' não carregou ({}); usando a fonte embutida", id, ex.getMessage());
            } finally {
                if (generator != null) generator.dispose();
            }
        } else {
            LOG.warn("Fonte '{}' ausente; usando a fonte embutida", id);
        }
        BitmapFont fallback = new BitmapFont();
        fallback.getData().setScale(fallbackScale / k);
        return fallback;
    }
}
