package etrava.tools.art.pixel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Pintor de folhagem (D-100): redesenha os tufos de uma copa como o pixel artista faz — cachos
 * de folha carimbados um a um, cada cacho de um tom só (o da luz onde ele cai) com a sua luz no
 * alto à esquerda e a sua sombra embaixo. Nada de tom por pixel nem de esfera lisa.
 *
 * <ol>
 *   <li>grade de carimbos presa ao tufo (a âncora vem do render, por quadro): o desenho anda
 *       com o vento em vez de cintilar;</li>
 *   <li>carimbos de cima para baixo: o cacho de baixo cobre a sombra do de cima, e o que fica à
 *       vista é a borda clara de cada cacho contra o escuro do outro — a "escama" da copa;</li>
 *   <li>na silhueta o cacho pode passar da forma em até {@code borda} px: a borda da copa fica
 *       recortada em folhas, e não lisa;</li>
 *   <li>sob um tufo da frente, o de trás escurece um tom (o vão entre as massas);</li>
 *   <li>limpeza: pixel sozinho no meio de outra cor some.</li>
 * </ol>
 * Determinístico (hash inteiro da grade, ordem fixa).
 */
final class Folhagem {
    private Folhagem() {}

    /** Um tufo: a rampa, os pincéis, a âncora na tela e a semente da grade. */
    record Tufo(int[] tons, Pinceis.Conjunto conjunto, int ox, int oy, int semente) {}

    private record Carimbo(int x, int y, int mat, int pincel, float prof) {}

    private static final float MARGEM_PROF = 0.002f;
    /** Diferença de profundidade (unidades do passe: 1 m = 0,025) que faz o vão sob o tufo da frente. */
    private static final float MARGEM_VAO = 0.008f;

    static void pintar(int w, int h, int[] id, float[] prof, double[] valor, int[] cor, Map<Integer, Tufo> tufos,
            int vazio, int sombraChao) {
        if (tufos.isEmpty()) return;
        int n = w * h;
        int[] id0 = id.clone();
        int[] tom = new int[n];
        Arrays.fill(tom, -1);
        // tom de base (o que nenhum carimbo cobrir fica com ele)
        for (int i = 0; i < n; i++) {
            Tufo t = tufos.get(id0[i]);
            if (t == null) continue;
            tom[i] = tomDe(valor[i], t.tons().length);
            cor[i] = t.tons()[tom[i]];
        }

        // 1. grade de carimbos por tufo
        List<Carimbo> carimbos = new ArrayList<>();
        for (Map.Entry<Integer, Tufo> e : tufos.entrySet()) {
            int mat = e.getKey();
            Tufo t = e.getValue();
            int x0 = w, y0 = h, x1 = -1, y1 = -1;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if (id0[y * w + x] == mat) {
                        x0 = Math.min(x0, x);
                        y0 = Math.min(y0, y);
                        x1 = Math.max(x1, x);
                        y1 = Math.max(y1, y);
                    }
            if (x1 < 0) continue;
            int px = t.conjunto().passoX(), py = t.conjunto().passoY();
            int gy0 = Math.floorDiv(y0 - t.oy(), py) - 1, gy1 = Math.floorDiv(y1 - t.oy(), py) + 1;
            int gx0 = Math.floorDiv(x0 - t.ox(), px) - 2, gx1 = Math.floorDiv(x1 - t.ox(), px) + 2;
            int np = t.conjunto().pinceis().size();
            for (int gy = gy0; gy <= gy1; gy++) {
                for (int gx = gx0; gx <= gx1; gx++) {
                    int hh = mistura(t.semente(), gx, gy);
                    int x = t.ox() + gx * px + ((gy & 1) != 0 ? px / 2 : 0) + (hh % 5) - 2;
                    int y = t.oy() + gy * py + ((hh >>> 4) % 3) - 1;
                    if (x < 0 || y < 0 || x >= w || y >= h) continue;
                    int o = y * w + x;
                    if (id0[o] != mat) continue;
                    carimbos.add(new Carimbo(x, y, mat, (hh >>> 8) % np, prof[o]));
                }
            }
        }
        carimbos.sort(Comparator.comparingInt(Carimbo::y).thenComparingInt(Carimbo::x).thenComparingInt(Carimbo::mat));

        // 2. carimbar
        for (Carimbo c : carimbos) {
            Tufo t = tufos.get(c.mat());
            int nt = t.tons().length;
            int base = tomDe(valor[c.y() * w + c.x()], nt);
            Pinceis.Pincel p = t.conjunto().pinceis().get(c.pincel());
            int borda = t.conjunto().borda();
            for (int k = 0; k < p.nivel().length; k++) {
                int x = c.x() + p.dx()[k], y = c.y() + p.dy()[k];
                if (x < 0 || y < 0 || x >= w || y >= h) continue;
                int q = y * w + x;
                boolean dentro = id[q] == c.mat();
                if (!dentro) {
                    // fora do próprio tufo: só por cima de vazio, sombra no chão ou coisa mais longe,
                    // e só perto da forma original (a borda recortada)
                    boolean livre = id[q] == vazio || id[q] == sombraChao || prof[q] > c.prof() + MARGEM_PROF;
                    if (!livre || !perto(id0, w, h, x, y, c.mat(), borda)) continue;
                }
                // luz e brilho do cacho só onde a luz já é boa: na sombra o cacho é chapado
                int tt = switch (p.nivel()[k]) {
                    case Pinceis.SOMBRA -> base - 1;
                    case Pinceis.LUZ -> base >= nt - 4 ? base + 1 : base;
                    case Pinceis.BRILHO -> base >= nt - 3 ? base + 2 : base >= nt - 4 ? base + 1 : base;
                    default -> base;
                };
                tt = Math.max(0, Math.min(nt - 1, tt));
                cor[q] = t.tons()[tt];
                tom[q] = tt;
                id[q] = c.mat();
                if (!dentro) prof[q] = c.prof();
            }
        }

        // 3. vão sob o tufo da frente: o pixel logo abaixo de outro tufo mais perto escurece um tom
        int[] corAntes = cor.clone();
        for (int y = 1; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int q = y * w + x, a = q - w;
                Tufo t = tufos.get(id[q]);
                if (t == null || tom[q] <= 0) continue;
                if (id[a] == id[q] || !tufos.containsKey(id[a])) continue;
                if (prof[a] < prof[q] - MARGEM_VAO) {
                    tom[q] = Math.max(0, tom[q] - 1);
                    cor[q] = t.tons()[tom[q]];
                }
            }
        }

        // 4. limpeza: pixel cercado pelos quatro lados por uma mesma outra cor de folha
        corAntes = cor.clone();
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int o = y * w + x;
                if (!tufos.containsKey(id[o])) continue;
                int c0 = corAntes[o - 1];
                if (c0 == corAntes[o] || corAntes[o + 1] != c0 || corAntes[o - w] != c0 || corAntes[o + w] != c0) continue;
                if (!tufos.containsKey(id[o - 1]) || !tufos.containsKey(id[o + 1]) || !tufos.containsKey(id[o - w])
                        || !tufos.containsKey(id[o + w])) continue;
                cor[o] = c0;
            }
        }
    }

    /**
     * Tom de base do cacho para o valor: poucas faixas largas (do tom 2 ao penúltimo), como o pixel
     * artista; o 0 fica para o contorno, o 1 para o vão fundo (sombra dos cachos) e o último para o
     * brilho.
     */
    static int tomDe(double v, int n) {
        int lo = Math.min(2, n - 2), hi = Math.max(lo, n - 2), faixas = hi - lo + 1;
        int t = (int) Math.floor(Math.max(0, Math.min(0.9999, v)) * faixas);
        return lo + Math.min(faixas - 1, t);
    }

    private static boolean perto(int[] id0, int w, int h, int x, int y, int mat, int borda) {
        for (int dy = -borda; dy <= borda; dy++) {
            for (int dx = -borda; dx <= borda; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                if (id0[ny * w + nx] == mat) return true;
            }
        }
        return false;
    }

    static int mistura(int a, int b, int c) {
        int h = a * 0x9E3779B1 ^ b * 0x85EBCA6B ^ c * 0xC2B2AE35;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return h & 0x7FFFFFFF;
    }
}
