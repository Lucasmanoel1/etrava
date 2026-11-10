package etrava.tools.art;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Utilidades da arte provisória gerada por código: projeção isométrica 2:1
 * (mesmas medidas do cliente), ruído periódico e cores.
 * Em imagem o eixo y cresce para baixo.
 */
final class ArtKit {
    static final double HALF_W = 64, HALF_H = 32, Z_PX = 56;

    // Paleta de Pedra-Mansa (PRD 5.4): ocre, ferrugem, verde-oliva desbotado, cinza quente.
    static final Color OCHRE = new Color(0x9C7A45);
    static final Color RUST = new Color(0x8A4E2E);
    static final Color OLIVE = new Color(0x6B7045);
    static final Color WARM_GREY = new Color(0x7F776C);
    static final Color STRAW = new Color(0xB89A5A);
    static final Color DARK_WOOD = new Color(0x4A3526);
    static final Color PLASTER = new Color(0xB9A888);
    static final Color OUTLINE = new Color(0x241C18);

    private ArtKit() {}

    static double sx(double x, double y) { return (x - y) * HALF_W; }
    static double sy(double x, double y, double z) { return (x + y) * HALF_H - z * Z_PX; }

    static BufferedImage image(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return g;
    }

    /**
     * Transformação que leva coordenadas locais de uma face plana (u, v em metros)
     * para a tela: origem 3D + vetor do eixo u + vetor do eixo v, todos em mundo.
     */
    static AffineTransform face(double ox, double oy, double oz, double ux, double uy, double uz,
            double vx, double vy, double vz, double offX, double offY) {
        double m00 = sx(ux, uy), m10 = sy(ux, uy, uz);
        double m01 = sx(vx, vy), m11 = sy(vx, vy, vz);
        return new AffineTransform(m00, m10, m01, m11, offX + sx(ox, oy), offY + sy(ox, oy, oz));
    }

    // ---------------------------------------------------------------- cores

    static Color mix(Color a, Color b, double t) {
        t = clamp(t);
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    static Color shade(Color c, double factor) {
        return new Color(ch(c.getRed() * factor), ch(c.getGreen() * factor), ch(c.getBlue() * factor), c.getAlpha());
    }

    static Color alpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), ch(255 * a));
    }

    /** Pequena variação de cor, para a textura não ficar chapada. */
    static Color jitter(Color c, java.util.Random rnd, double amount) {
        double f = 1 + (rnd.nextDouble() * 2 - 1) * amount;
        double warm = (rnd.nextDouble() * 2 - 1) * amount * 20;
        return new Color(ch(c.getRed() * f + warm), ch(c.getGreen() * f), ch(c.getBlue() * f - warm), c.getAlpha());
    }

    static int ch(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }
    static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    static double smooth(double t) { return t * t * (3 - 2 * t); }

    // ---------------------------------------------------------------- ruído

    private static double hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    /** Ruído de valor que se repete a cada {@code period} unidades nos dois eixos. */
    static double noise(double x, double y, int period, int seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = smooth(x - x0), fy = smooth(y - y0);
        double a = hash(Math.floorMod(x0, period), Math.floorMod(y0, period), seed);
        double b = hash(Math.floorMod(x0 + 1, period), Math.floorMod(y0, period), seed);
        double c = hash(Math.floorMod(x0, period), Math.floorMod(y0 + 1, period), seed);
        double d = hash(Math.floorMod(x0 + 1, period), Math.floorMod(y0 + 1, period), seed);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    /** Soma de oitavas do ruído periódico; x e y em [0, 1) cobrem um período. */
    static double fbm(double x, double y, int baseCells, int octaves, int seed) {
        double sum = 0, amp = 0.5, total = 0;
        int cells = baseCells;
        for (int i = 0; i < octaves; i++) {
            sum += noise(x * cells, y * cells, cells, seed + i * 31) * amp;
            total += amp;
            amp *= 0.5;
            cells *= 2;
        }
        return sum / total;
    }
}
