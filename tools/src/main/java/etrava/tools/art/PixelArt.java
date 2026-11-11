package etrava.tools.art;

import java.awt.image.BufferedImage;

/**
 * Converte uma imagem pintada em pixel art (D-033): reduz {@link #SCALE} vezes,
 * deixa cada pixel totalmente opaco ou transparente e troca as cores pelas mais
 * próximas de uma paleta única. É a paleta compartilhada que faz arte de origens
 * diferentes (código, IA, mão) parecer do mesmo jogo.
 */
public final class PixelArt {
    /** Um pixel de arte vale 4×4 pixels de mundo: o tile de 1 m tem 32×16 px de arte. */
    public static final int SCALE = 4;
    private static final int OUTLINE = 0xFF14101A;
    /** Sombra projetada no chão: preto translúcido, uma cor só. */
    private static final int SHADOW = 0x5A000000;

    /** Paleta do ETRAVA: tons terrosos e dessaturados, com âmbar de lamparina como único brilho. */
    public static final int[] PALETTE = {
            0x14101A, 0x1E1A22, 0x2A2428,                               // quase pretos
            0x3A3634, 0x524C46, 0x6B645A, 0x857D70, 0xA39A8A, 0xC4BBA8, // cinzas quentes (pedra)
            0x3B4048, 0x565C66, 0x7A808A,                               // cinzas frios (ferro, ardósia)
            0x3D2A1C, 0x5A3F26, 0x7A5632, 0x9C7A45, 0xB8975A, 0xD4B878, 0xE8D8A0, // terra, ocre, palha
            0x4A1E1A, 0x6E2C22, 0x8A4E2E, 0xA85F38, 0xC47A48,           // ferrugem
            0x2E2018, 0x4A3526, 0x624632, 0x7C5A40,                     // madeira
            0x1E2A1C, 0x2E3C24, 0x44502E, 0x5A6438, 0x6B7045, 0x868C52, 0xA4A866, // verde-oliva
            0x7A4E3A, 0xA06C50, 0xC08A68, 0xD8A884, 0xECC8A4,           // pele
            0x1C2238, 0x2A3350, 0x3E4A6E, 0x5A6890,                     // azuis (noite, tecido)
            0x8C7E66, 0xB9A888, 0xD8CCB0, 0xEEE6D2,                     // reboco, linho
            0xC86428, 0xF09A3C, 0xFFC864, 0xFFE9A8,                     // fogo e lamparina
            0x9C2A2A,                                                   // sangue, estandarte
    };

    private PixelArt() {}

    /**
     * @param outline contorna a silhueta com um pixel escuro (objetos e personagens; não tiles)
     */
    public static BufferedImage pixelize(BufferedImage src, boolean outline) {
        int w = src.getWidth() / SCALE, h = src.getHeight() / SCALE;
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double a = 0, r = 0, g = 0, b = 0;
                for (int dy = 0; dy < SCALE; dy++) {
                    for (int dx = 0; dx < SCALE; dx++) {
                        int p = src.getRGB(x * SCALE + dx, y * SCALE + dy);
                        double pa = (p >>> 24) / 255.0;
                        a += pa;
                        r += ((p >> 16) & 0xFF) * pa;
                        g += ((p >> 8) & 0xFF) * pa;
                        b += (p & 0xFF) * pa;
                    }
                }
                double coverage = a / (SCALE * SCALE);
                if (coverage < 0.10) continue;
                double cr = r / a, cg = g / a, cb = b / a;
                if (coverage < 0.5) {
                    // meio transparente e escuro = sombra no chão; o resto é borda e some
                    if (cr + cg + cb < 60) out[y * w + x] = SHADOW;
                    continue;
                }
                out[y * w + x] = 0xFF000000 | nearest(cr, cg, cb);
            }
        }
        if (outline) addOutline(out, w, h);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, out, 0, w);
        return img;
    }

    /**
     * Redimensiona para exatamente {@code targetW}×{@code targetH} por média de área e
     * troca as cores pelas da paleta. Ignora o alfa (a origem é tratada como opaca),
     * sem contorno e sem pontilhado. Serve para retratos pintados.
     */
    public static BufferedImage pixelize(BufferedImage src, int targetW, int targetH) {
        int sw = src.getWidth(), sh = src.getHeight();
        double fx = (double) sw / targetW, fy = (double) sh / targetH;
        int[] out = new int[targetW * targetH];
        for (int y = 0; y < targetH; y++) {
            double y0 = y * fy, y1 = (y + 1) * fy;
            for (int x = 0; x < targetW; x++) {
                double x0 = x * fx, x1 = (x + 1) * fx;
                double r = 0, g = 0, b = 0, wsum = 0;
                for (int sy = (int) Math.floor(y0); sy < Math.min(sh, (int) Math.ceil(y1)); sy++) {
                    double wy = Math.min(y1, sy + 1) - Math.max(y0, sy);
                    if (wy <= 0) continue;
                    for (int sx = (int) Math.floor(x0); sx < Math.min(sw, (int) Math.ceil(x1)); sx++) {
                        double wx = Math.min(x1, sx + 1) - Math.max(x0, sx);
                        if (wx <= 0) continue;
                        int p = src.getRGB(sx, sy);
                        double wgt = wx * wy;
                        r += ((p >> 16) & 0xFF) * wgt;
                        g += ((p >> 8) & 0xFF) * wgt;
                        b += (p & 0xFF) * wgt;
                        wsum += wgt;
                    }
                }
                if (wsum <= 0) wsum = 1;
                out[y * targetW + x] = 0xFF000000 | nearest(r / wsum, g / wsum, b / wsum);
            }
        }
        BufferedImage img = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, targetW, targetH, out, 0, targetW);
        return img;
    }

    /** Pinta de escuro os pixels vazios (ou de sombra) encostados na silhueta. */
    private static void addOutline(int[] px, int w, int h) {
        int[] src = px.clone();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (solid(src[y * w + x])) continue;
                boolean touches = (x > 0 && solid(src[y * w + x - 1])) || (x < w - 1 && solid(src[y * w + x + 1]))
                        || (y > 0 && solid(src[(y - 1) * w + x])) || (y < h - 1 && solid(src[(y + 1) * w + x]));
                if (touches) px[y * w + x] = OUTLINE;
            }
        }
    }

    private static boolean solid(int argb) {
        return (argb >>> 24) == 0xFF;
    }

    /** Cor da paleta mais próxima, com pesos que imitam a sensibilidade do olho. */
    static int nearest(double r, double g, double b) {
        int best = PALETTE[0];
        double bestD = Double.MAX_VALUE;
        for (int c : PALETTE) {
            double pr = (c >> 16) & 0xFF, pg = (c >> 8) & 0xFF, pb = c & 0xFF;
            double mean = (r + pr) / 2, dr = r - pr, dg = g - pg, db = b - pb;
            double d = (2 + mean / 256) * dr * dr + 4 * dg * dg + (2 + (255 - mean) / 256) * db * db;
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }
}
