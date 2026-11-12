package etrava.client.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chão da Fase V (SPEC-visual §7.2): cada terreno é uma textura repetível alinhada ao mundo, e
 * cada tile a mostra através de uma máscara — o losango de 64×32 px de arte mais uma sangria de
 * borda irregular, em pixel inteiro. O terreno de prioridade maior é desenhado depois e a sangria
 * dele avança sobre o vizinho: a divisa entre grama e terra fica ondulada, sem degrau de tile.
 * Na borda da máscara há um anel de 1 px mais escuro (a grama "pisa" sobre a terra). Cada terreno
 * é desenhado em duas passadas: com anel e depois só o miolo; o miolo dos vizinhos do mesmo terreno
 * cobre o anel, que só fica onde o vizinho é outro terreno.
 *
 * <p>As máscaras são feitas aqui, por ruído determinístico: oito variantes, escolhidas pelo tile.
 */
final class GroundMasks implements Disposable {
    private static final Logger LOG = LoggerFactory.getLogger(GroundMasks.class);
    /** Máscara em px de arte: o losango (64×32) no meio de uma área de 128×64. */
    static final int W = 128, H = 64, VARIANTS = 8;
    /** Quanto a borda pode avançar além do losango, em frações do meio-losango. */
    private static final double BLEED = 0.8;

    private final Texture texture;
    private final TextureRegion[] masks = new TextureRegion[VARIANTS];
    private final TextureRegion[] cores = new TextureRegion[VARIANTS];
    private final ShaderProgram shader;

    private GroundMasks(Texture texture, ShaderProgram shader) {
        this.texture = texture;
        this.shader = shader;
        for (int i = 0; i < VARIANTS; i++) {
            masks[i] = new TextureRegion(texture, i * W, 0, W, H);
            cores[i] = new TextureRegion(texture, i * W, H, W, H);
        }
    }

    static GroundMasks create() {
        ShaderProgram.pedantic = false;
        ShaderProgram sh = new ShaderProgram(Gdx.files.classpath("shaders/ground.vert"), Gdx.files.classpath("shaders/ground.frag"));
        if (!sh.isCompiled()) {
            LOG.warn("Shader do chão não compilou; chão novo desligado: {}", sh.getLog());
            sh.dispose();
            return null;
        }
        Pixmap px = new Pixmap(W * VARIANTS, H * 2, Pixmap.Format.RGBA8888);
        px.setBlending(Pixmap.Blending.None);
        for (int v = 0; v < VARIANTS; v++) paint(px, v);
        Texture t = new Texture(px);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        px.dispose();
        return new GroundMasks(t, sh);
    }

    /** Desenha a variante v: alfa 1 dentro; vermelho 1 no miolo e 0,78 no anel da borda. */
    private static void paint(Pixmap px, int v) {
        boolean[] in = new boolean[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double dx = (x + 0.5 - W / 2.0) / 32.0, dy = (y + 0.5 - H / 2.0) / 16.0;
                double d = Math.abs(dx) + Math.abs(dy);           // 1 = borda do losango
                // ruído em blocos de 2 px de arte, para a borda ter cara de tufo e não de serrilha
                double n = noise((x >> 1) * 0.30 + v * 17.3, (y >> 1) * 0.6 + v * 5.1) * 0.7
                        + noise((x >> 1) * 1.1 + v * 3.7, (y >> 1) * 2.2 - v * 9.2) * 0.3;
                n = Math.max(0, Math.min(1, (n - 0.35) / 0.4));       // mais contraste: lobos e recuos
                n = n * n * (3 - 2 * n);
                in[y * W + x] = d <= 1.0 + BLEED * n;
            }
        }
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int o = y * W + x;
                if (!in[o]) {
                    px.drawPixel(v * W + x, y, 0);
                    continue;
                }
                boolean rim = x == 0 || y == 0 || x == W - 1 || y == H - 1
                        || !in[o - 1] || !in[o + 1] || !in[o - W] || !in[o + W];
                int r = rim ? 199 : 255;
                px.drawPixel(v * W + x, y, (r << 24) | (255 << 16) | (255 << 8) | 0xFF);
                px.drawPixel(v * W + x, H + y, rim ? 0 : 0xFFFFFFFF);
            }
        }
    }

    /** Ruído de valor suave em [0, 1], determinístico. */
    private static double noise(double x, double y) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(xi, yi), b = hash(xi + 1, yi), c = hash(xi, yi + 1), d = hash(xi + 1, yi + 1);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    private static double hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }

    /** Máscara do tile: com o anel escuro ({@code rim}) ou só o miolo. */
    TextureRegion mask(int tx, int ty, boolean rim) {
        int h = tx * 73856093 ^ ty * 19349663;
        int i = Math.floorMod(h ^ (h >>> 7), VARIANTS);
        return rim ? masks[i] : cores[i];
    }

    ShaderProgram shader() {
        return shader;
    }

    @Override
    public void dispose() {
        texture.dispose();
        shader.dispose();
    }
}
