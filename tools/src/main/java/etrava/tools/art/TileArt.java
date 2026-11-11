package etrava.tools.art;

import static etrava.tools.art.ArtKit.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Tiles de chão. Cada imagem tem 256×128 px: o losango de 128×64 no centro e uma
 * borda que some aos poucos, para terrenos diferentes se misturarem sem corte seco.
 * A textura se repete a cada tile, então tiles vizinhos emendam.
 */
final class TileArt {
    static final int W = 256, H = 128;

    private TileArt() {}

    static BufferedImage grass(int variant) {
        Random rnd = new Random(1000 + variant);
        BufferedImage img = base((u, v) -> {
            double n = fbm(u, v, 4, 4, 11 + variant);
            double patch = fbm(u + 0.37, v + 0.11, 2, 2, 91);
            Color c = mix(shade(OLIVE, 0.80), mix(OLIVE, OCHRE, 0.35), n);
            return mix(c, shade(OCHRE, 0.85), smooth(clamp((patch - 0.58) * 4)) * 0.5);
        });
        Graphics2D g = graphics(img);
        for (int i = 0; i < 520; i++) {
            double u = rnd.nextDouble(), v = rnd.nextDouble();
            double len = 3 + rnd.nextDouble() * 6, lean = (rnd.nextDouble() - 0.5) * 3;
            Color c = rnd.nextDouble() < 0.18
                    ? jitter(mix(OCHRE, STRAW, rnd.nextDouble()), rnd, 0.1)
                    : jitter(mix(shade(OLIVE, 0.7), shade(OLIVE, 1.25), rnd.nextDouble()), rnd, 0.08);
            g.setColor(alpha(c, 0.75));
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            wrapped(u, v, (x, y) -> g.draw(new Line2D.Double(x, y, x + lean, y - len)));
        }
        g.dispose();
        return mask(img, 5 + variant);
    }

    static BufferedImage dirt(int variant) {
        Random rnd = new Random(2000 + variant);
        BufferedImage img = base((u, v) -> {
            double n = fbm(u, v, 4, 4, 23 + variant);
            double rut = Math.abs(Math.sin((u - v) * Math.PI * 2 + n * 3));
            Color c = mix(shade(OCHRE, 0.72), mix(OCHRE, RUST, 0.25), n);
            return shade(c, 0.9 + 0.12 * rut);
        });
        Graphics2D g = graphics(img);
        for (int i = 0; i < 70; i++) {
            double u = rnd.nextDouble(), v = rnd.nextDouble();
            double r = 1 + rnd.nextDouble() * 2.2;
            Color c = jitter(mix(WARM_GREY, OCHRE, rnd.nextDouble() * 0.6), rnd, 0.12);
            wrapped(u, v, (x, y) -> {
                g.setColor(alpha(OUTLINE, 0.35));
                g.fill(new Ellipse2D.Double(x - r, y - r * 0.5 + 1, r * 2, r));
                g.setColor(c);
                g.fill(new Ellipse2D.Double(x - r, y - r * 0.5, r * 2, r));
            });
        }
        g.dispose();
        return mask(img, 9 + variant);
    }

    /** Calçamento de pedra irregular. */
    static BufferedImage cobble(int variant) {
        Random rnd = new Random(3000 + variant);
        BufferedImage img = base((u, v) -> shade(mix(OUTLINE, WARM_GREY, 0.35), 0.85 + 0.2 * fbm(u, v, 8, 2, 5)));
        Graphics2D g = graphics(img);
        int cells = 4;
        for (int cy = 0; cy < cells; cy++) {
            for (int cx = 0; cx < cells; cx++) {
                double u = (cx + 0.5 + (rnd.nextDouble() - 0.5) * 0.35) / cells;
                double v = (cy + 0.5 + (rnd.nextDouble() - 0.5) * 0.35) / cells;
                double rw = (0.36 + rnd.nextDouble() * 0.12) / cells * HALF_W * 2;
                double rh = rw * 0.5;
                Color c = jitter(mix(WARM_GREY, OCHRE, rnd.nextDouble() * 0.35), rnd, 0.1);
                double rot = (rnd.nextDouble() - 0.5) * 0.5;
                wrapped(u, v, (x, y) -> {
                    AffineTransform old = g.getTransform();
                    g.rotate(rot, x, y);
                    g.setColor(shade(c, 0.7));
                    g.fill(new Ellipse2D.Double(x - rw, y - rh + 1.5, rw * 2, rh * 2));
                    g.setColor(c);
                    g.fill(new Ellipse2D.Double(x - rw, y - rh, rw * 2, rh * 2));
                    g.setColor(alpha(shade(c, 1.25), 0.55));
                    g.fill(new Ellipse2D.Double(x - rw * 0.7, y - rh * 0.8, rw * 1.1, rh * 0.9));
                    g.setTransform(old);
                });
            }
        }
        g.dispose();
        return mask(img, 3 + variant);
    }

    /** Assoalho de tábuas (interiores), correndo no eixo x. */
    static BufferedImage planks(int variant) {
        Random rnd = new Random(4000 + variant);
        double[] tone = new double[8];
        for (int i = 0; i < tone.length; i++) tone[i] = 0.85 + rnd.nextDouble() * 0.25;
        BufferedImage img = base((u, v) -> {
            int row = (int) Math.floor(v * 4) % 4;
            double inRow = v * 4 - Math.floor(v * 4);
            double seam = (u * 2 + row * 0.37) % 1.0;
            Color wood = mix(new Color(0x4A3526), new Color(0x7C5A40), 0.35 + fbm(u, v * 0.3, 8, 2, 40 + variant) * 0.4);
            wood = shade(wood, tone[row + (seam < 0.5 ? 0 : 4)]);
            if (inRow < 0.07 || seam < 0.012) wood = shade(wood, 0.55);
            return wood;
        });
        return mask(img, 20 + variant);
    }

    /** Lajotas de pedra (taverna, moinho). */
    static BufferedImage flagstone(int variant) {
        Random rnd = new Random(5000 + variant);
        BufferedImage img = base((u, v) -> shade(new Color(0x3A3634), 0.9 + 0.15 * fbm(u, v, 8, 2, 7)));
        Graphics2D g = graphics(img);
        int cells = 2;
        for (int cy = 0; cy < cells; cy++) {
            for (int cx = 0; cx < cells; cx++) {
                double u = (cx + 0.5) / cells, v = (cy + 0.5) / cells;
                Color c = jitter(mix(new Color(0x6B645A), OCHRE, rnd.nextDouble() * 0.25), rnd, 0.08);
                double hw = 0.46 / cells;
                wrapped(u, v, (x, y) -> {
                    java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
                    p.moveTo(x + sx(-hw, -hw), y + sy(-hw, -hw, 0));
                    p.lineTo(x + sx(hw, -hw), y + sy(hw, -hw, 0));
                    p.lineTo(x + sx(hw, hw), y + sy(hw, hw, 0));
                    p.lineTo(x + sx(-hw, hw), y + sy(-hw, hw, 0));
                    p.closePath();
                    g.setColor(c);
                    g.fill(p);
                    g.setColor(alpha(shade(c, 1.2), 0.5));
                    g.setStroke(new BasicStroke(1.5f));
                    g.draw(new Line2D.Double(x + sx(-hw, -hw), y + sy(-hw, -hw, 0), x + sx(hw, -hw), y + sy(hw, -hw, 0)));
                });
            }
        }
        g.dispose();
        return mask(img, 30 + variant);
    }

    /** Lavoura: sulcos de terra no eixo y com trigo ou verdura. */
    static BufferedImage crops(int variant) {
        Random rnd = new Random(6000 + variant);
        BufferedImage img = base((u, v) -> {
            double furrow = Math.abs(Math.sin(u * Math.PI * 4));
            Color soil = mix(new Color(0x3D2A1C), new Color(0x5A3F26), fbm(u, v, 4, 2, 50 + variant));
            return shade(soil, 0.75 + furrow * 0.35);
        });
        Graphics2D g = graphics(img);
        for (int row = 0; row < 4; row++) {
            for (int i = 0; i < 26; i++) {
                double u = (row + 0.5) / 4 + (rnd.nextDouble() - 0.5) * 0.04, v = rnd.nextDouble();
                double h = 6 + rnd.nextDouble() * 9;
                Color c = variant == 3 ? jitter(new Color(0x5A6438), rnd, 0.1)
                        : jitter(mix(STRAW, OCHRE, rnd.nextDouble() * 0.5), rnd, 0.08);
                wrapped(u, v, (x, y) -> {
                    g.setColor(alpha(c, 0.9));
                    g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Double(x, y, x + (rnd.nextDouble() - 0.5) * 3, y - h));
                    g.draw(new Line2D.Double(x, y, x + 3, y - h * 0.8));
                });
            }
        }
        g.dispose();
        return mask(img, 40 + variant);
    }

    // ---------------------------------------------------------------- base

    private interface Shader { Color at(double u, double v); }
    private interface Stamp { void at(double x, double y); }

    /** Pinta toda a imagem com uma função das coordenadas de mundo (u, v), periódica em 1. */
    private static BufferedImage base(Shader shader) {
        BufferedImage img = image(W, H);
        for (int py = 0; py < H; py++) {
            for (int px = 0; px < W; px++) {
                double dx = (px + 0.5 - W / 2.0) / HALF_W, dy = (py + 0.5 - H / 2.0) / HALF_H;
                // inverso da projeção, com (0.5, 0.5) no centro do tile
                double u = (dx + dy) / 2 + 0.5, v = (dy - dx) / 2 + 0.5;
                img.setRGB(px, py, shader.at(u - Math.floor(u), v - Math.floor(v)).getRGB());
            }
        }
        return img;
    }

    /** Desenha um detalhe em (u, v) e nas cópias vizinhas, para emendar na borda do tile. */
    private static void wrapped(double u, double v, Stamp stamp) {
        for (int du = -1; du <= 1; du++) {
            for (int dv = -1; dv <= 1; dv++) {
                double x = W / 2.0 + sx(u + du - 0.5, v + dv - 0.5);
                double y = H / 2.0 + sy(u + du - 0.5, v + dv - 0.5, 0);
                if (x > -12 && x < W + 12 && y > -12 && y < H + 12) stamp.at(x, y);
            }
        }
    }

    /** Opaco dentro do losango; some aos poucos fora dele, com borda irregular. */
    private static BufferedImage mask(BufferedImage img, int seed) {
        for (int py = 0; py < H; py++) {
            for (int px = 0; px < W; px++) {
                double dx = Math.abs(px + 0.5 - W / 2.0) / HALF_W, dy = Math.abs(py + 0.5 - H / 2.0) / HALF_H;
                double d = dx + dy;
                double wobble = noise(px * 0.09, py * 0.09, 64, seed) * 0.42; // só para fora: o losango fica sempre coberto
                double a = 1 - smooth(clamp((d - 1.02 - wobble) / 0.46));
                int rgb = img.getRGB(px, py) & 0xFFFFFF;
                img.setRGB(px, py, (ch(a * 255) << 24) | rgb);
            }
        }
        return img;
    }
}
