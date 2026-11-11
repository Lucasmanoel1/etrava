package etrava.tools.art;

import static etrava.tools.art.ArtKit.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RadialGradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.Random;

/**
 * Interiores e miudezas do M7: paredes de fundo dos cômodos, móveis, lareira, mó,
 * forja, e o que enche o vilarejo e a estrada (cercas, lápides, estacas, tenda,
 * carroça, banca, marco). Mesmo esquema dos outros geradores: desenha grande, com
 * três tons por material e luz da esquerda, e o {@link PixelArt} reduz para a grade.
 */
final class FurnitureArt {
    static final double WALL_H = 2.6, WALL_T = 0.25;
    private static final double TOP = 1.18, LEFT = 1.0, RIGHT = 0.64;

    private FurnitureArt() {}

    // ---------------------------------------------------------------- primitivas

    /** Caixa sólida com as três faces visíveis: frente (sudoeste), lado (sudeste) e tampo. */
    static void block(Canvas c, double x0, double y0, double x1, double y1, double z0, double z1, Color col) {
        c.fill(shade(col, LEFT), x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        c.fill(shade(col, RIGHT), x1, y1, z0, x1, y0, z0, x1, y0, z1, x1, y1, z1);
        c.fill(shade(col, TOP), x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        c.g.setColor(alpha(OUTLINE, 0.75));
        c.g.setStroke(new BasicStroke(1.3f));
        c.g.draw(c.poly(x0, y1, z0, x1, y1, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, x0, y1, z1));
        c.g.setColor(alpha(shade(col, 1.4), 0.6));
        c.g.draw(new Line2D.Double(c.px(x0, y1), c.py(x0, y1, z1), c.px(x1, y1), c.py(x1, y1, z1)));
    }

    /** Caixa de tábuas (frente e lado com veios). */
    static void woodBlock(Canvas c, Random rnd, double x0, double y0, double x1, double y1, double z0, double z1,
            Color wood) {
        block(c, x0, y0, x1, y1, z0, z1, wood);
        AffineTransform old = c.enterFace(x0, y1, z0, 1, 0, 0, 0, 0, 1);
        grain(c, rnd, x1 - x0, z1 - z0, wood, LEFT);
        c.leave(old);
        old = c.enterFace(x1, y1, z0, 0, -1, 0, 0, 0, 1);
        grain(c, rnd, y1 - y0, z1 - z0, wood, RIGHT);
        c.leave(old);
    }

    private static void grain(Canvas c, Random rnd, double w, double h, Color wood, double light) {
        c.g.setStroke(new BasicStroke(0.025f));
        for (double v = 0.12; v < h; v += 0.16 + rnd.nextDouble() * 0.1) {
            c.g.setColor(alpha(shade(wood, light * 0.7), 0.6));
            c.g.draw(new Line2D.Double(0.04, v, w - 0.04, v + (rnd.nextDouble() - 0.5) * 0.03));
        }
    }

    private static Canvas canvas(int w, int h, double ox, double oy) {
        return new Canvas(w, h, ox, oy);
    }

    private static BuildingArt.Art done(Canvas c) {
        c.g.dispose();
        return new BuildingArt.Art(c.image, c.ox, c.oy, null);
    }

    private static BuildingArt.Art done(Canvas c, Canvas glow) {
        c.g.dispose();
        glow.g.dispose();
        return new BuildingArt.Art(c.image, c.ox, c.oy, glow.image);
    }

    // ---------------------------------------------------------------- paredes de fundo

    /**
     * Um metro de parede de fundo de um cômodo. {@code alongX}: corre no eixo x (parede
     * norte, face clara para o sul); senão corre no eixo y (parede oeste, face na sombra
     * para o leste). {@code kind}: "lisa", "janela", "porta" ou "prateleira".
     */
    static BuildingArt.Art wall(boolean alongX, String kind, int seed) {
        Random rnd = new Random(900 + seed);
        Canvas c = canvas(200, 280, 100, 210);
        Canvas glow = canvas(200, 280, 100, 210);
        double t = WALL_T / 2, light = alongX ? LEFT : 0.82;
        // face visível do metro de parede: x em [-0.5, 0.5] (ou y), no plano da frente da espessura
        double fx = alongX ? -0.5 : t, fy = alongX ? t : 0.5, ux = alongX ? 1 : 0, uy = alongX ? 0 : -1;
        AffineTransform old = c.enterFace(fx, fy, 0, ux, uy, 0, 0, 0, 1);
        c.stoneCourses(1.0, 0, 0.55, light * 0.9, rnd);
        c.plaster(1.0, 0.55, WALL_H, light, rnd);
        c.beam(0, 0.6, 1.0, 0.6, 0.12, light);
        c.beam(0, WALL_H - 0.1, 1.0, WALL_H - 0.1, 0.16, light);
        c.beam(0.05, 0.55, 0.05, WALL_H, 0.12, light);
        switch (kind) {
            case "janela" -> {
                double w = 0.56, v0 = 1.25, h = 0.7, u = 0.22;
                c.g.setColor(shade(DARK_WOOD, light * 0.75));
                c.g.fill(new Rectangle2D.Double(u - 0.07, v0 - 0.07, w + 0.14, h + 0.14));
                c.g.setColor(new Color(0x2A3350));
                c.g.fill(new Rectangle2D.Double(u, v0, w, h));
                c.beam(u + w / 2, v0, u + w / 2, v0 + h, 0.05, light);
                c.beam(u, v0 + h / 2, u + w, v0 + h / 2, 0.05, light);
            }
            case "porta" -> {
                double u = 0.1, w = 0.8, h = 1.95;
                c.g.setColor(shade(DARK_WOOD, light * 0.6));
                c.g.fill(new Rectangle2D.Double(u - 0.06, 0, w + 0.12, h + 0.08));
                c.planks(u, 0.02, w, h - 0.02, mix(DARK_WOOD, RUST, 0.35), light, rnd);
                c.g.setColor(shade(new Color(0x2B2725), light));
                c.g.fill(new Rectangle2D.Double(u, 0.45, w, 0.06));
                c.g.fill(new Rectangle2D.Double(u, 1.45, w, 0.06));
                c.g.fill(new Ellipse2D.Double(u + w - 0.18, 0.92, 0.08, 0.08));
                // luz do dia passando pela fresta
                AffineTransform og = glow.enterFace(fx, fy, 0, ux, uy, 0, 0, 0, 1);
                glow.g.setColor(new Color(255, 214, 140, 160));
                glow.g.fill(new Rectangle2D.Double(u + w - 0.04, 0.05, 0.03, h - 0.1));
                glow.leave(og);
            }
            case "prateleira" -> {
                for (double v : new double[] {1.05, 1.6}) {
                    c.g.setColor(shade(DARK_WOOD, light * 1.1));
                    c.g.fill(new Rectangle2D.Double(0.12, v, 0.8, 0.06));
                    for (int i = 0; i < 4; i++) {
                        double u = 0.16 + i * 0.19 + rnd.nextDouble() * 0.04, hh = 0.14 + rnd.nextDouble() * 0.16;
                        Color jar = rnd.nextBoolean() ? mix(RUST, OCHRE, rnd.nextDouble()) : mix(WARM_GREY, OLIVE, rnd.nextDouble());
                        c.g.setColor(shade(jar, light));
                        c.g.fill(new Rectangle2D.Double(u, v + 0.06, 0.12, hh));
                        c.g.setColor(alpha(OUTLINE, 0.6));
                        c.g.setStroke(new BasicStroke(0.02f));
                        c.g.draw(new Rectangle2D.Double(u, v + 0.06, 0.12, hh));
                    }
                }
            }
            default -> {
                if (rnd.nextDouble() < 0.4) { // um prego com alguma coisa pendurada
                    c.g.setColor(shade(mix(OLIVE, STRAW, 0.4), light));
                    c.g.fill(new Rectangle2D.Double(0.45, 1.25, 0.1, 0.4));
                }
            }
        }
        c.g.setPaint(new java.awt.GradientPaint(0, 0, alpha(OUTLINE, 0.5), 0, 0.5f, alpha(OUTLINE, 0)));
        c.g.fill(new Rectangle2D.Double(0, 0, 1, 0.5));
        c.leave(old);
        // topo da parede (espessura), escuro: é o corte
        if (alongX) c.fill(new Color(0x2A2428), -0.5, -t, WALL_H, 0.5, -t, WALL_H, 0.5, t, WALL_H, -0.5, t, WALL_H);
        else c.fill(new Color(0x2A2428), -t, -0.5, WALL_H, t, -0.5, WALL_H, t, 0.5, WALL_H, -t, 0.5, WALL_H);
        return done(c, glow);
    }

    /** Pilar do canto onde as duas paredes de fundo se encontram. */
    static BuildingArt.Art wallCorner() {
        Random rnd = new Random(950);
        Canvas c = canvas(80, 260, 40, 210);
        double t = WALL_T / 2;
        AffineTransform old = c.enterFace(-t, t, 0, 1, 0, 0, 0, 0, 1);
        c.stoneCourses(WALL_T, 0, WALL_H, LEFT, rnd);
        c.leave(old);
        old = c.enterFace(t, t, 0, 0, -1, 0, 0, 0, 1);
        c.stoneCourses(WALL_T, 0, WALL_H, RIGHT, rnd);
        c.leave(old);
        c.fill(new Color(0x2A2428), -t, -t, WALL_H, t, -t, WALL_H, t, t, WALL_H, -t, t, WALL_H);
        return done(c);
    }

    // ---------------------------------------------------------------- móveis

    static BuildingArt.Art table(boolean longTable) {
        Random rnd = new Random(longTable ? 911 : 910);
        Canvas c = canvas(260, 180, 130, 120);
        double hx = longTable ? 1.0 : 0.55, hy = 0.45, z = 0.78;
        Color wood = mix(DARK_WOOD, OCHRE, 0.45);
        c.groundShadow(0.2, 0, hx + 0.2, 0.3);
        for (double[] l : new double[][] {{-hx + 0.1, hy - 0.1}, {hx - 0.1, hy - 0.1}, {hx - 0.1, -hy + 0.1}})
            block(c, l[0] - 0.05, l[1] - 0.05, l[0] + 0.05, l[1] + 0.05, 0, z - 0.06, shade(wood, 0.8));
        woodBlock(c, rnd, -hx, -hy, hx, hy, z - 0.07, z, wood);
        // caneca e prato
        mug(c, -hx * 0.4, 0.05, z);
        c.g.setColor(shade(WARM_GREY, 1.25));
        c.g.fill(new Ellipse2D.Double(c.px(hx * 0.35, -0.05) - 9, c.py(hx * 0.35, -0.05, z) - 4, 18, 8));
        return done(c);
    }

    private static void mug(Canvas c, double x, double y, double z) {
        double px = c.px(x, y), py = c.py(x, y, z);
        c.g.setColor(shade(mix(OCHRE, DARK_WOOD, 0.3), 1.1));
        c.g.fill(new Rectangle2D.Double(px - 4, py - 11, 8, 11));
        c.g.setColor(alpha(OUTLINE, 0.8));
        c.g.setStroke(new BasicStroke(1.2f));
        c.g.draw(new Rectangle2D.Double(px - 4, py - 11, 8, 11));
    }

    static BuildingArt.Art bench() {
        Random rnd = new Random(912);
        Canvas c = canvas(240, 140, 120, 100);
        Color wood = mix(DARK_WOOD, OCHRE, 0.35);
        c.groundShadow(0.1, 0, 1.0, 0.25);
        block(c, -0.85, -0.12, -0.75, 0.12, 0, 0.4, shade(wood, 0.8));
        block(c, 0.75, -0.12, 0.85, 0.12, 0, 0.4, shade(wood, 0.8));
        woodBlock(c, rnd, -0.95, -0.18, 0.95, 0.18, 0.4, 0.47, wood);
        return done(c);
    }

    static BuildingArt.Art bed() {
        Random rnd = new Random(913);
        Canvas c = canvas(260, 200, 130, 130);
        Color wood = mix(DARK_WOOD, RUST, 0.2);
        c.groundShadow(0.2, 0, 1.2, 0.3);
        woodBlock(c, rnd, -1.0, -0.5, 1.0, 0.5, 0, 0.35, wood);
        block(c, -0.95, -0.45, 0.95, 0.45, 0.35, 0.5, new Color(0x8C7E66)); // colchão de palha
        block(c, -0.2, -0.47, 0.97, 0.47, 0.5, 0.56, mix(new Color(0x5A6890), RUST, 0.45)); // manta
        block(c, -0.9, -0.3, -0.55, 0.3, 0.5, 0.64, new Color(0xD8CCB0)); // travesseiro
        woodBlock(c, rnd, -1.08, -0.5, -0.98, 0.5, 0, 0.95, wood); // cabeceira
        return done(c);
    }

    static BuildingArt.Art counter() {
        Random rnd = new Random(914);
        Canvas c = canvas(330, 220, 165, 150);
        Color wood = mix(DARK_WOOD, OCHRE, 0.3);
        c.groundShadow(0.2, 0, 1.5, 0.3);
        woodBlock(c, rnd, -1.4, -0.35, 1.4, 0.35, 0, 1.0, wood);
        block(c, -1.45, -0.42, 1.45, 0.42, 1.0, 1.07, shade(wood, 1.15));
        mug(c, -0.8, 0.1, 1.07);
        mug(c, -0.55, -0.05, 1.07);
        // barrilete deitado
        double px = c.px(0.7, 0), py = c.py(0.7, 0, 1.07);
        c.g.setColor(mix(DARK_WOOD, OCHRE, 0.5));
        c.g.fill(new Ellipse2D.Double(px - 16, py - 22, 32, 22));
        c.g.setColor(new Color(0x3A3634));
        c.g.setStroke(new BasicStroke(2.5f));
        c.g.draw(new Line2D.Double(px - 7, py - 22, px - 7, py));
        c.g.draw(new Line2D.Double(px + 7, py - 22, px + 7, py));
        return done(c);
    }

    static BuildingArt.Art hearth() {
        Random rnd = new Random(915);
        Canvas c = canvas(260, 330, 130, 240);
        Canvas glow = canvas(260, 330, 130, 240);
        double hx = 0.8, hy = 0.45;
        AffineTransform old = c.enterFace(-hx, hy, 0, 1, 0, 0, 0, 0, 1);
        c.stoneCourses(hx * 2, 0, 1.3, LEFT, rnd);
        c.g.setColor(new Color(0x14101A));
        c.g.fill(new Rectangle2D.Double(0.35, 0.05, hx * 2 - 0.7, 0.75));
        c.leave(old);
        old = c.enterFace(hx, hy, 0, 0, -1, 0, 0, 0, 1);
        c.stoneCourses(hy * 2, 0, 1.3, RIGHT, rnd);
        c.leave(old);
        block(c, -hx - 0.05, -hy - 0.05, hx + 0.05, hy + 0.05, 1.3, 1.42, WARM_GREY); // consolo
        old = c.enterFace(-0.35, 0.2, 1.42, 1, 0, 0, 0, 0, 1);
        c.stoneCourses(0.7, 0, 1.2, LEFT, rnd); // chaminé
        c.leave(old);
        // brasas e chamas (na camada que brilha)
        double fx = c.px(0, hy), fy = c.py(0, hy, 0.12);
        c.g.setColor(new Color(0x4A1E1A));
        c.g.fill(new Ellipse2D.Double(fx - 26, fy - 6, 52, 12));
        glow.g.setPaint(new RadialGradientPaint((float) fx, (float) fy - 14, 34f, new float[] {0f, 0.35f, 1f},
                new Color[] {new Color(255, 236, 170, 255), new Color(240, 154, 60, 220), new Color(200, 100, 40, 0)}));
        glow.g.fill(new Ellipse2D.Double(fx - 34, fy - 48, 68, 62));
        return done(c, glow);
    }

    static BuildingArt.Art workbench() {
        Random rnd = new Random(916);
        Canvas c = canvas(300, 220, 150, 150);
        Color wood = mix(DARK_WOOD, OCHRE, 0.3);
        c.groundShadow(0.2, 0, 1.3, 0.3);
        block(c, -1.1, 0.25, -1.0, 0.35, 0, 0.82, shade(wood, 0.8));
        block(c, 1.0, 0.25, 1.1, 0.35, 0, 0.82, shade(wood, 0.8));
        block(c, 1.0, -0.35, 1.1, -0.25, 0, 0.82, shade(wood, 0.8));
        woodBlock(c, rnd, -1.2, -0.42, 1.2, 0.42, 0.82, 0.95, wood);
        // torno de bancada, engrenagens e uma ferramenta
        block(c, -1.0, -0.15, -0.75, 0.15, 0.95, 1.12, new Color(0x565C66));
        for (int i = 0; i < 2; i++) {
            double px = c.px(0.1 + i * 0.4, 0.05 - i * 0.1), py = c.py(0.1 + i * 0.4, 0, 0.95);
            double r = 9 - i * 3;
            c.g.setColor(new Color(0x7A808A));
            c.g.fill(new Ellipse2D.Double(px - r, py - r * 0.55, r * 2, r * 1.1));
            c.g.setColor(new Color(0x3B4048));
            c.g.fill(new Ellipse2D.Double(px - r * 0.35, py - r * 0.2, r * 0.7, r * 0.4));
        }
        c.g.setColor(new Color(0x3B4048));
        c.g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        c.g.draw(new Line2D.Double(c.px(0.6, 0.25), c.py(0.6, 0.25, 0.96), c.px(1.0, 0.1), c.py(1.0, 0.1, 0.96)));
        return done(c);
    }

    static BuildingArt.Art chest() {
        Random rnd = new Random(917);
        Canvas c = canvas(170, 140, 85, 100);
        Color wood = mix(DARK_WOOD, RUST, 0.3);
        c.groundShadow(0.1, 0, 0.6, 0.3);
        woodBlock(c, rnd, -0.5, -0.32, 0.5, 0.32, 0, 0.5, wood);
        block(c, -0.52, -0.34, 0.52, 0.34, 0.5, 0.6, shade(wood, 1.1));
        c.g.setColor(new Color(0x3A3634));
        c.g.setStroke(new BasicStroke(3f));
        for (double x : new double[] {-0.3, 0.3})
            c.g.draw(new Line2D.Double(c.px(x, 0.34), c.py(x, 0.34, 0), c.px(x, 0.34), c.py(x, 0.34, 0.6)));
        c.g.setColor(new Color(0x9C7A45));
        c.g.fill(new Rectangle2D.Double(c.px(0, 0.34) - 3, c.py(0, 0.34, 0.45), 6, 7));
        return done(c);
    }

    static BuildingArt.Art sacks() {
        Random rnd = new Random(918);
        Canvas c = canvas(200, 150, 100, 110);
        c.groundShadow(0.1, 0, 0.8, 0.3);
        double[][] pos = {{-0.3, 0.1, 0}, {0.25, 0.15, 0}, {0, -0.2, 0}, {-0.05, 0.05, 0.42}};
        for (double[] p : pos) {
            double px = c.px(p[0], p[1]), py = c.py(p[0], p[1], p[2]);
            Color sack = jitter(new Color(0xB9A888), rnd, 0.06);
            c.g.setColor(sack);
            c.g.fill(new Ellipse2D.Double(px - 22, py - 30, 44, 32));
            c.g.setColor(shade(sack, 0.7));
            c.g.fill(new Ellipse2D.Double(px - 6, py - 22, 28, 22));
            c.g.setColor(shade(sack, 1.15));
            c.g.fill(new Ellipse2D.Double(px - 18, py - 28, 16, 10));
            c.g.setColor(alpha(OUTLINE, 0.7));
            c.g.setStroke(new BasicStroke(1.2f));
            c.g.draw(new Ellipse2D.Double(px - 22, py - 30, 44, 32));
        }
        return done(c);
    }

    static BuildingArt.Art millstone() {
        Canvas c = canvas(260, 220, 130, 150);
        c.groundShadow(0.1, 0, 1.2, 0.35);
        Random rnd = new Random(919);
        block(c, -0.9, -0.9, 0.9, 0.9, 0, 0.35, mix(DARK_WOOD, OCHRE, 0.3));
        double rx = 0.75 * HALF_W * 1.41, ry = 0.75 * HALF_H * 1.41;
        for (int layer = 0; layer < 2; layer++) {
            double z = 0.35 + layer * 0.28, px = c.px(0, 0), py = c.py(0, 0, z);
            c.g.setColor(shade(WARM_GREY, 0.7));
            c.g.fill(new Rectangle2D.Double(px - rx, py - 0.28 * Z_PX, rx * 2, 0.28 * Z_PX));
            c.g.fill(new Ellipse2D.Double(px - rx, py - ry, rx * 2, ry * 2));
            c.g.setColor(shade(jitter(WARM_GREY, rnd, 0.05), 1.15));
            c.g.fill(new Ellipse2D.Double(px - rx, py - 0.28 * Z_PX - ry, rx * 2, ry * 2));
            c.g.setColor(alpha(OUTLINE, 0.7));
            c.g.setStroke(new BasicStroke(1.3f));
            c.g.draw(new Ellipse2D.Double(px - rx, py - 0.28 * Z_PX - ry, rx * 2, ry * 2));
        }
        double px = c.px(0, 0), py = c.py(0, 0, 0.91);
        c.g.setColor(new Color(0x2A2428));
        c.g.fill(new Ellipse2D.Double(px - 7, py - 4, 14, 8));
        c.g.setColor(shade(DARK_WOOD, 1.0));
        c.g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        c.g.draw(new Line2D.Double(px, py, px + 50, py - 18)); // alavanca
        c.g.setColor(alpha(new Color(0xEEE6D2), 0.6)); // farinha
        c.g.fill(new Ellipse2D.Double(c.px(0.6, 0.6) - 20, c.py(0.6, 0.6, 0.35) - 6, 40, 12));
        return done(c);
    }

    static BuildingArt.Art herbRack() {
        Random rnd = new Random(920);
        Canvas c = canvas(200, 260, 100, 200);
        Color wood = mix(DARK_WOOD, OCHRE, 0.3);
        c.groundShadow(0.1, 0, 0.6, 0.3);
        block(c, -0.6, -0.05, -0.5, 0.05, 0, 1.9, shade(wood, 0.9));
        block(c, 0.5, -0.05, 0.6, 0.05, 0, 1.9, shade(wood, 0.9));
        block(c, -0.6, -0.05, 0.6, 0.05, 1.8, 1.9, wood);
        for (int i = 0; i < 6; i++) {
            double x = -0.45 + i * 0.18, px = c.px(x, 0), py = c.py(x, 0, 1.8);
            Color herb = mix(OLIVE, i % 3 == 0 ? OCHRE : new Color(0x5A6438), rnd.nextDouble() * 0.6);
            c.g.setColor(shade(herb, 1.0));
            c.g.fill(new Ellipse2D.Double(px - 6, py, 12, 28 + rnd.nextDouble() * 10));
            c.g.setColor(shade(herb, 1.3));
            c.g.fill(new Ellipse2D.Double(px - 4, py + 2, 5, 16));
        }
        woodBlock(c, rnd, -0.55, -0.25, 0.55, 0.25, 0, 0.7, wood); // arca de potes embaixo
        return done(c);
    }

    static BuildingArt.Art anvil() {
        Canvas c = canvas(170, 140, 85, 100);
        c.groundShadow(0.1, 0, 0.6, 0.35);
        block(c, -0.2, -0.2, 0.2, 0.2, 0, 0.45, mix(DARK_WOOD, OCHRE, 0.3)); // cepo
        Color iron = new Color(0x3B4048);
        block(c, -0.12, -0.1, 0.12, 0.1, 0.45, 0.62, iron);
        block(c, -0.4, -0.13, 0.32, 0.13, 0.62, 0.78, shade(iron, 1.2));
        c.fill(shade(iron, 1.3), 0.32, -0.08, 0.78, 0.55, 0, 0.74, 0.32, 0.08, 0.78);
        return done(c);
    }

    /** Forja ao ar livre: um telheiro sobre a fornalha, com brasas que brilham. */
    static BuildingArt.Art forge() {
        Random rnd = new Random(921);
        Canvas c = canvas(420, 420, 210, 300);
        Canvas glow = canvas(420, 420, 210, 300);
        c.groundShadow(0.4, -0.2, 2.0, 0.35);
        // fornalha de pedra
        AffineTransform old = c.enterFace(-0.7, 0.6, 0, 1, 0, 0, 0, 0, 1);
        c.stoneCourses(1.4, 0, 0.9, LEFT, rnd);
        c.leave(old);
        old = c.enterFace(0.7, 0.6, 0, 0, -1, 0, 0, 0, 1);
        c.stoneCourses(1.2, 0, 0.9, RIGHT, rnd);
        c.leave(old);
        c.fill(new Color(0x2A2428), -0.7, -0.6, 0.9, 0.7, -0.6, 0.9, 0.7, 0.6, 0.9, -0.7, 0.6, 0.9);
        double fx = c.px(0, 0), fy = c.py(0, 0, 0.9);
        c.g.setColor(new Color(0x4A1E1A));
        c.g.fill(new Ellipse2D.Double(fx - 28, fy - 9, 56, 18));
        glow.g.setPaint(new RadialGradientPaint((float) fx, (float) fy, 40f, new float[] {0f, 0.3f, 1f},
                new Color[] {new Color(255, 214, 120, 255), new Color(220, 100, 40, 210), new Color(160, 60, 30, 0)}));
        glow.g.fill(new Ellipse2D.Double(fx - 40, fy - 30, 80, 50));
        // postes e telheiro de tábuas
        Color wood = mix(DARK_WOOD, OCHRE, 0.25);
        for (double[] p : new double[][] {{-1.4, 1.2}, {1.4, 1.2}, {1.4, -1.2}})
            block(c, p[0] - 0.08, p[1] - 0.08, p[0] + 0.08, p[1] + 0.08, 0, 2.5, shade(wood, 0.9));
        c.fill(shade(wood, 1.05), -1.6, 1.4, 2.5, 1.6, 1.4, 2.5, 1.6, -1.4, 2.9, -1.6, -1.4, 2.9);
        c.g.setColor(alpha(OUTLINE, 0.8));
        c.g.setStroke(new BasicStroke(1.3f));
        c.g.draw(c.poly(-1.6, 1.4, 2.5, 1.6, 1.4, 2.5, 1.6, -1.4, 2.9, -1.6, -1.4, 2.9));
        for (int i = 1; i < 8; i++) {
            double x = -1.6 + i * 0.4;
            c.g.setColor(alpha(shade(wood, 0.6), 0.8));
            c.g.draw(new Line2D.Double(c.px(x, 1.4), c.py(x, 1.4, 2.5), c.px(x, -1.4), c.py(x, -1.4, 2.9)));
        }
        return done(c, glow);
    }

    // ---------------------------------------------------------------- vilarejo e estrada

    /** Um metro de cerca de varas, no eixo x ou no eixo y. */
    static BuildingArt.Art fence(boolean alongX) {
        Canvas c = canvas(150, 120, 75, 80);
        Color wood = mix(DARK_WOOD, OCHRE, 0.35);
        double ax = alongX ? -0.5 : 0, ay = alongX ? 0 : 0.5, bx = alongX ? 0.5 : 0, by = alongX ? 0 : -0.5;
        for (double[] p : new double[][] {{ax, ay}, {bx, by}})
            block(c, p[0] - 0.05, p[1] - 0.05, p[0] + 0.05, p[1] + 0.05, 0, 0.95, shade(wood, 0.9));
        c.g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double z : new double[] {0.4, 0.78}) {
            c.g.setColor(alpha(OUTLINE, 0.8));
            c.g.draw(new Line2D.Double(c.px(ax, ay), c.py(ax, ay, z) + 1, c.px(bx, by), c.py(bx, by, z) + 1));
            c.g.setColor(shade(wood, alongX ? 1.1 : 0.8));
            c.g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            c.g.draw(new Line2D.Double(c.px(ax, ay), c.py(ax, ay, z), c.px(bx, by), c.py(bx, by, z)));
            c.g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }
        return done(c);
    }

    static BuildingArt.Art gravestone(int variant) {
        Random rnd = new Random(930 + variant);
        Canvas c = canvas(120, 150, 60, 110);
        c.groundShadow(0.1, -0.05, 0.4, 0.3);
        // montinho de terra
        c.g.setColor(new Color(0x5A3F26));
        c.g.fill(new Ellipse2D.Double(c.px(0, 0.45) - 28, c.py(0, 0.45, 0) - 10, 56, 20));
        double h = 0.7 + rnd.nextDouble() * 0.3, w = 0.22 + rnd.nextDouble() * 0.1;
        Color stone = jitter(new Color(0x857D70), rnd, 0.08);
        block(c, -w, -0.07, w, 0.07, 0, h, stone);
        if (variant % 2 == 0) { // topo arredondado
            double px = c.px(0, 0.07), py = c.py(0, 0.07, h);
            c.g.setColor(shade(stone, LEFT));
            c.g.fill(new Ellipse2D.Double(px - w * HALF_W * 0.9, py - 8, w * HALF_W * 1.8, 16));
        }
        c.g.setColor(alpha(shade(stone, 0.5), 0.8)); // marca riscada
        c.g.setStroke(new BasicStroke(1.4f));
        double px = c.px(0, 0.07), py = c.py(0, 0.07, h * 0.65);
        c.g.draw(new Line2D.Double(px - 5, py, px + 5, py));
        c.g.draw(new Line2D.Double(px, py - 6, px, py + 8));
        return done(c);
    }

    /** Estaca de agrimensor, com fita na ponta (a comitiva de Caldris, A2/H2). */
    static BuildingArt.Art stake() {
        Canvas c = canvas(80, 120, 40, 100);
        c.groundShadow(0, 0, 0.15, 0.3);
        block(c, -0.04, -0.04, 0.04, 0.04, 0, 1.15, new Color(0xB8975A));
        double px = c.px(0, 0), py = c.py(0, 0, 1.05);
        c.g.setColor(new Color(0x9C2A2A));
        c.g.fill(new Rectangle2D.Double(px - 1, py - 4, 14, 6));
        c.g.fill(new Rectangle2D.Double(px + 8, py, 7, 5));
        return done(c);
    }

    /** Marco de pedra da divisa (H2). */
    static BuildingArt.Art marker() {
        Canvas c = canvas(100, 110, 50, 80);
        c.groundShadow(0, 0, 0.3, 0.3);
        block(c, -0.15, -0.15, 0.15, 0.15, 0, 0.55, new Color(0x6B645A));
        return done(c);
    }

    /** Tenda de lona da comitiva. */
    static BuildingArt.Art tent() {
        Canvas c = canvas(360, 300, 180, 200);
        c.groundShadow(0.3, -0.2, 1.8, 0.35);
        Color canvasCol = new Color(0xB9A888), shadeCol = shade(canvasCol, 0.7);
        double hx = 1.3, hy = 1.0, z = 2.0;
        c.fill(shade(canvasCol, LEFT), -hx, hy, 0, hx, hy, 0, hx, 0, z, -hx, 0, z);
        c.fill(shadeCol, hx, hy, 0, hx, -hy, 0, hx, 0, z);
        c.fill(new Color(0x2A2428), -0.25, hy, 0, 0.25, hy, 0, 0, hy * 0.35, z * 0.65);
        c.g.setColor(alpha(OUTLINE, 0.8));
        c.g.setStroke(new BasicStroke(1.4f));
        c.g.draw(c.poly(-hx, hy, 0, hx, hy, 0, hx, -hy, 0, hx, 0, z, -hx, 0, z));
        // faixa de Caldris: cinza e ferrugem
        c.g.setColor(new Color(0x565C66));
        c.g.setStroke(new BasicStroke(5f));
        c.g.draw(new Line2D.Double(c.px(-hx, hy * 0.5), c.py(-hx, hy * 0.5, z * 0.5), c.px(hx, hy * 0.5), c.py(hx, hy * 0.5, z * 0.5)));
        block(c, -hx - 0.05, -0.05, -hx + 0.05, 0.05, 0, z + 0.4, DARK_WOOD);
        c.g.setColor(new Color(0x6E2C22));
        double fx = c.px(-hx, 0), fy = c.py(-hx, 0, z + 0.4);
        c.g.fill(new Rectangle2D.Double(fx, fy, 20, 10));
        return done(c);
    }

    static BuildingArt.Art cart() {
        Random rnd = new Random(931);
        Canvas c = canvas(320, 220, 160, 150);
        c.groundShadow(0.2, 0, 1.4, 0.3);
        Color wood = mix(DARK_WOOD, OCHRE, 0.4);
        wheel(c, -0.6, 0.6);
        woodBlock(c, rnd, -1.2, -0.6, 1.0, 0.6, 0.5, 0.65, wood);
        woodBlock(c, rnd, -1.2, 0.5, 1.0, 0.6, 0.65, 1.0, wood);
        woodBlock(c, rnd, 0.9, -0.6, 1.0, 0.6, 0.65, 1.0, shade(wood, 0.9));
        block(c, 1.0, -0.05, 2.0, 0.05, 0.5, 0.58, shade(wood, 0.8)); // varal
        c.g.setColor(new Color(0xB9A888));
        c.g.fill(new Ellipse2D.Double(c.px(0, 0) - 30, c.py(0, 0, 1.0) - 16, 50, 24)); // carga
        wheel(c, -0.6, -0.6);
        return done(c);
    }

    private static void wheel(Canvas c, double x, double y) {
        double px = c.px(x, y), py = c.py(x, y, 0.45);
        c.g.setColor(shade(DARK_WOOD, 0.9));
        c.g.setStroke(new BasicStroke(5f));
        c.g.draw(new Ellipse2D.Double(px - 16, py - 24, 32, 48));
        c.g.setStroke(new BasicStroke(2f));
        c.g.draw(new Line2D.Double(px, py - 24, px, py + 24));
        c.g.draw(new Line2D.Double(px - 16, py, px + 16, py));
    }

    /** Banca de troca do Posto: tábuas sobre cavaletes e um toldo. */
    static BuildingArt.Art stall() {
        Random rnd = new Random(932);
        Canvas c = canvas(340, 320, 170, 220);
        c.groundShadow(0.2, 0, 1.6, 0.3);
        Color wood = mix(DARK_WOOD, OCHRE, 0.35);
        for (double[] p : new double[][] {{-1.2, 0.6}, {1.2, 0.6}, {1.2, -0.6}})
            block(c, p[0] - 0.06, p[1] - 0.06, p[0] + 0.06, p[1] + 0.06, 0, 2.1, shade(wood, 0.9));
        woodBlock(c, rnd, -1.2, -0.5, 1.2, 0.5, 0.8, 0.92, wood);
        for (int i = 0; i < 5; i++) {
            double x = -1.0 + i * 0.45, px = c.px(x, 0), py = c.py(x, 0, 0.92);
            Color good = i % 2 == 0 ? new Color(0xEEE6D2) : mix(RUST, OCHRE, rnd.nextDouble());
            c.g.setColor(good);
            c.g.fill(new Ellipse2D.Double(px - 10, py - 12, 20, 13));
            c.g.setColor(alpha(OUTLINE, 0.7));
            c.g.draw(new Ellipse2D.Double(px - 10, py - 12, 20, 13));
        }
        Color cloth = new Color(0x6E2C22);
        c.fill(shade(cloth, LEFT), -1.35, 0.75, 2.0, 1.35, 0.75, 2.0, 1.35, -0.6, 2.35, -1.35, -0.6, 2.35);
        for (int i = 0; i < 6; i++) { // listras do toldo
            double x0 = -1.35 + i * 0.45 + 0.22;
            c.fill(shade(new Color(0xD8CCB0), LEFT), x0, 0.75, 2.0, x0 + 0.22, 0.75, 2.0, x0 + 0.22, -0.6, 2.35, x0, -0.6, 2.35);
        }
        return done(c);
    }

    static BuildingArt.Art scarecrow() {
        Canvas c = canvas(140, 230, 70, 200);
        c.groundShadow(0, 0, 0.3, 0.3);
        block(c, -0.04, -0.04, 0.04, 0.04, 0, 2.0, DARK_WOOD);
        block(c, -0.55, -0.03, 0.55, 0.03, 1.45, 1.52, DARK_WOOD);
        double px = c.px(0, 0), py = c.py(0, 0, 1.0);
        c.g.setColor(new Color(0x5A6438));
        c.g.fill(new Rectangle2D.Double(px - 16, py - 32, 32, 36));
        c.g.setColor(new Color(0xB89A5A));
        c.g.fill(new Ellipse2D.Double(px - 10, py - 54, 20, 22));
        c.g.setColor(new Color(0x7A5632));
        c.g.fill(new Rectangle2D.Double(px - 16, py - 58, 32, 6));
        c.g.fill(new Rectangle2D.Double(px - 9, py - 70, 18, 13));
        return done(c);
    }

    /** Árvore do pomar: copa mais baixa, com frutas. */
    static BuildingArt.Art fruitTree(int variant) {
        BuildingArt.Art base = PropArt.tree(10 + variant, false);
        Random rnd = new Random(940 + variant);
        java.awt.Graphics2D g = graphics(base.image());
        for (int i = 0; i < 26; i++) {
            double x = base.anchorX() + (rnd.nextDouble() - 0.5) * 150, y = base.anchorY() - 150 - rnd.nextDouble() * 110;
            if ((base.image().getRGB((int) x, (int) y) >>> 24) < 200) continue;
            g.setColor(new Color(0x9C2A2A));
            g.fill(new Ellipse2D.Double(x - 4, y - 4, 8, 8));
            g.setColor(new Color(0xC47A48));
            g.fill(new Ellipse2D.Double(x - 3, y - 3, 3, 3));
        }
        g.dispose();
        return base;
    }

    /** Terra remexida: o lugar onde o buraco foi tapado (A3). */
    static BuildingArt.Art filledPit() {
        Random rnd = new Random(933);
        Canvas c = canvas(260, 160, 130, 90);
        double rx = 0.9 * HALF_W * 1.41, ry = 0.9 * HALF_H * 1.41;
        c.g.setColor(new Color(0x5A3F26));
        c.g.fill(new Ellipse2D.Double(c.ox - rx, c.oy - ry, rx * 2, ry * 2));
        for (int i = 0; i < 90; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble());
            c.g.setColor(jitter(new Color(0x6B5136), rnd, 0.15));
            c.g.fill(new Ellipse2D.Double(c.ox + Math.cos(a) * rx * r - 3, c.oy + Math.sin(a) * ry * r - 2, 6, 4));
        }
        return done(c);
    }

    /** Imagem vazia, para variantes "nada aqui" (o campo antes de abrir o buraco). */
    static BuildingArt.Art empty() {
        Canvas c = canvas(8, 8, 4, 4);
        return done(c);
    }

    /** Poste com tabuleta (Posto da Encruzilhada, encruzilhada da estrada). */
    static BuildingArt.Art signpost() {
        Canvas c = canvas(160, 220, 80, 190);
        c.groundShadow(0, 0, 0.25, 0.3);
        block(c, -0.05, -0.05, 0.05, 0.05, 0, 2.2, DARK_WOOD);
        Color board = mix(DARK_WOOD, OCHRE, 0.55);
        c.fill(shade(board, LEFT), -0.1, 0.06, 1.75, 0.75, 0.06, 1.75, 0.85, 0.06, 1.9, 0.75, 0.06, 2.05, -0.1, 0.06, 2.05);
        c.fill(shade(board, RIGHT), 0.06, 0.1, 1.35, 0.06, -0.65, 1.35, 0.06, -0.75, 1.5, 0.06, -0.65, 1.65, 0.06, 0.1, 1.65);
        c.g.setColor(alpha(OUTLINE, 0.8));
        c.g.setStroke(new BasicStroke(1.3f));
        c.g.draw(c.poly(-0.1, 0.06, 1.75, 0.75, 0.06, 1.75, 0.85, 0.06, 1.9, 0.75, 0.06, 2.05, -0.1, 0.06, 2.05));
        return done(c);
    }

    /** Montinho de pedras / entulho de campo, para a beira da estrada. */
    static BuildingArt.Art rocks() {
        Random rnd = new Random(934);
        Canvas c = canvas(160, 110, 80, 70);
        c.groundShadow(0, 0, 0.6, 0.3);
        for (int i = 0; i < 5; i++) {
            double x = (rnd.nextDouble() - 0.5) * 0.7, y = (rnd.nextDouble() - 0.5) * 0.5, s = 0.12 + rnd.nextDouble() * 0.12;
            block(c, x - s, y - s, x + s, y + s, 0, s * 1.4, jitter(new Color(0x6B645A), rnd, 0.1));
        }
        return done(c);
    }
}
