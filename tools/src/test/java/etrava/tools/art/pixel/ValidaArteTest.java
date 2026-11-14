package etrava.tools.art.pixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import etrava.sim.content.AssetManifest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** VT-15: o pé de apoio acompanha o chão no parado e no andar. */
class ValidaArteTest {
    private static final double STRIDE = 1.2;
    private static final int N = 8;

    private static AssetManifest.Entry entrada() {
        return new AssetManifest.Entry("personagem.teste", "spritesheet", "x.png", null, null, null,
                new int[] {64, 80}, 8, Map.of(
                        "parado", new AssetManifest.Anim(0, N, 5, null),
                        "andar", new AssetManifest.Anim(N, N, 10, STRIDE)),
                null, null, null, 2, null, "x.json", "comum", null, null);
    }

    /**
     * Folha sintética: no andar, o pé de apoio recua exatamente o que o chão corre e o outro avança;
     * no parado, os dois ficam quietos. {@code escorrega} soma px ao pé de apoio de um quadro.
     */
    private static String json(double escorrega) {
        StringBuilder sb = new StringBuilder("{\"points\":{");
        boolean primeiro = true;
        for (String anim : List.of("parado", "andar")) {
            double passo = anim.equals("andar") ? STRIDE / N : 0;
            for (int d = 0; d < 8; d++) {
                double a = d * Math.PI / 4, cx = Math.cos(a), cy = Math.sin(a);
                double vx = -passo * 32 * (cx - cy), vy = -passo * 16 * (cx + cy);
                double ex = 20, ey = 70, dx = 30, dy = 70;
                for (int f = 0; f < N; f++) {
                    if (!primeiro) sb.append(',');
                    primeiro = false;
                    sb.append(String.format(Locale.ROOT, "\"%s/%d/%d\":{\"pe_e\":[%.3f,%.3f],\"pe_d\":[%.3f,%.3f]}",
                            anim, d, f, ex, ey, dx, dy));
                    // metade do ciclo o pé esquerdo apoia; a outra, o direito; o que está no ar volta o dobro
                    boolean esquerdoApoia = f < N / 2;
                    double extra = anim.equals("andar") && d == 3 && f == 2 ? escorrega : 0;
                    if (esquerdoApoia) {
                        ex += vx + extra;
                        ey += vy;
                        dx -= vx;
                        dy -= vy;
                    } else {
                        dx += vx;
                        dy += vy;
                        ex -= vx;
                        ey -= vy;
                    }
                }
            }
        }
        return sb.append("}}").toString();
    }

    @Test
    void peQueAcompanhaOChaoPassa(@TempDir Path dir) throws Exception {
        Path meta = dir.resolve("x.json");
        Files.writeString(meta, json(0));
        assertEquals(List.of(), ValidaArte.pes(meta, entrada()));
    }

    @Test
    void peQueEscorregaFalha(@TempDir Path dir) throws Exception {
        Path meta = dir.resolve("x.json");
        Files.writeString(meta, json(2.5));
        List<String> erros = ValidaArte.pes(meta, entrada());
        assertEquals(1, erros.size(), erros.toString());
        assertTrue(erros.get(0).startsWith("andar: o pé de apoio desliza 2.5 px (direção/quadro 3/2)"), erros.get(0));
    }

    @Test
    void folhaSemPesFicaDeFora(@TempDir Path dir) throws Exception {
        Path meta = dir.resolve("x.json");
        Files.writeString(meta, "{\"points\":{\"parado/0/0\":{\"cabeca\":[1,2]}}}");
        assertEquals(List.of(), ValidaArte.pes(meta, entrada()));
    }
}
