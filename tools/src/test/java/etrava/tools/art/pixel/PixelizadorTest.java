package etrava.tools.art.pixel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import etrava.tools.art.PixelArt;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pixelizador da Fase V (SPEC-visual §5.2) com passes sintéticos — sem Blender. */
class PixelizadorTest {
    static final Paleta PALETA = Paleta.carregar(Path.of(System.getProperty("etrava.assets", "../assets"))
            .toAbsolutePath().getParent().resolve("tools/art/paleta.yaml"));
    static final Map<Integer, Pixelizador.Material> MATS = Map.of(
            1, new Pixelizador.Material(1, "camisa", "linho", 0.3, false, false, true, 0, 1, null, null, true),
            2, new Pixelizador.Material(2, "calca", "tecido_marrom", 0.3, false, false, true, 0, 1, null, null, true),
            3, new Pixelizador.Material(3, "vidro", "vidro", 0.0, false, true, false, 0, 1, null, "fogo", false));

    /** Um disco do material 1 (com a metade de baixo do material 2), luz caindo da esquerda, 4× superamostrado. */
    static Passes disco() {
        int ss = 4, w = 24 * ss, h = 24 * ss;
        Passes p = new Passes(w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double dx = (x + 0.5) / ss - 12, dy = (y + 0.5) / ss - 12;
                if (dx * dx + dy * dy > 64) continue;
                int mat = dy > 3 ? 2 : 1;
                double luz = Math.max(0, Math.min(1, 0.5 - dx / 16));
                double det = 0.5 + 0.2 * Math.sin(x * 0.7) * Math.cos(y * 0.5);
                p.set(x, y, mat, 0.5 + dy * 0.001, luz, 0.9, det);
            }
        return p;
    }

    @Test
    void paletaV3ContemTodaAPaletaDoM8() {
        for (int c : PixelArt.PALETTE) assertTrue(PALETA.contem(0xFF000000 | c), String.format("falta %06X", c));
        assertTrue(PALETA.cores().size() <= 110, "paleta v3 com cores demais: " + PALETA.cores().size());
    }

    @Test
    void mesmoPasseDaMesmosBytes() {
        Pixelizador px = new Pixelizador(PALETA);
        var a = px.pixelizar(disco(), MATS, Pixelizador.Parametros.padrao(), List.of());
        var b = px.pixelizar(disco(), MATS, Pixelizador.Parametros.padrao(), List.of());
        assertArrayEquals(pixels(a.cor()), pixels(b.cor()));
    }

    @Test
    void nenhumPixelForaDaPaleta() {
        var r = new Pixelizador(PALETA).pixelizar(disco(), MATS, Pixelizador.Parametros.padrao(),
                List.of(new Pixelizador.Decalque(12, 8, PALETA.rampa("olho")[0])));
        int opacos = 0;
        for (int c : pixels(r.cor())) {
            if ((c >>> 24) == 0) continue;
            opacos++;
            assertTrue(PALETA.contem(c), String.format("cor fora da paleta: %08X", c));
        }
        assertTrue(opacos > 150, "o disco sumiu: " + opacos);
    }

    @Test
    void contornoFecha() {
        var r = new Pixelizador(PALETA).pixelizar(disco(), MATS, Pixelizador.Parametros.padrao(), List.of());
        BufferedImage img = r.cor();
        int w = img.getWidth(), h = img.getHeight();
        int[] mat = r.materialDoPixel();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (mat[y * w + x] == 0) continue;
                // nenhum pixel do objeto encosta direto no vazio: entre eles sempre há contorno
                int[][] viz = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] d : viz) {
                    int nx = x + d[0], ny = y + d[1];
                    assertTrue(nx >= 0 && ny >= 0 && nx < w && ny < h && (img.getRGB(nx, ny) >>> 24) == 255,
                            "contorno aberto em (" + x + ", " + y + ")");
                }
            }
    }

    @Test
    void votoNaoMisturaCores() {
        // um pixel com 9 subpixels do material 1 e 7 do 2: fica com a cor de 1, nunca uma média
        Passes p = new Passes(12, 12);
        for (int y = 0; y < 12; y++)
            for (int x = 0; x < 12; x++) p.set(x, y, ((y % 4) * 4 + (x % 4)) < 9 ? 1 : 2, 0.5, 0.6, 1, 0.5);
        var r = new Pixelizador(PALETA).pixelizar(p, MATS, new Pixelizador.Parametros(4, 0.36, 0.56, 1, false, 0), List.of());
        int centro = r.cor().getRGB(1, 1);
        boolean daRampa = false;
        for (int c : PALETA.rampa("linho")) daRampa |= c == centro;
        assertTrue(daRampa, String.format("%08X não é da rampa do linho", centro));
    }

    @Test
    void sombraNoChaoViraPretoTranslucido() {
        Passes p = new Passes(8, 8);
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++) p.set(x, y, Pixelizador.INDICE_SOMBRA, 0.5, x < 4 ? 0.0 : 0.9, 1, 0.5);
        var r = new Pixelizador(PALETA).pixelizar(p, MATS, Pixelizador.Parametros.padrao(), List.of());
        assertEquals(Pixelizador.COR_SOMBRA, r.cor().getRGB(0, 0), "chão na sombra");
        assertEquals(0, r.cor().getRGB(1, 0) >>> 24, "chão no sol some");
    }

    @Test
    void emissivoVaiParaABrilho() {
        Passes p = new Passes(8, 8);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) p.set(x, y, 3, 0.5, 0.5, 1, 0.5);
        var r = new Pixelizador(PALETA).pixelizar(p, MATS, Pixelizador.Parametros.padrao(), List.of());
        assertTrue(r.brilho() != null && (r.brilho().getRGB(0, 0) >>> 24) == 255, "janela sem brilho");
        assertTrue(PALETA.contem(r.brilho().getRGB(1, 1)));
    }

    static int[] pixels(BufferedImage img) {
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }
}
