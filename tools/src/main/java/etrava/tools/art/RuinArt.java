package etrava.tools.art;

import static etrava.tools.art.ArtKit.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.Random;

/**
 * Arte provisória da estrutura enterrada (M6): fibras petrificadas, entulho, a laje
 * riscada, a escada sob o buraco e a boca do buraco na superfície. Pedra fria e
 * fibra cor de osso velho; nada de brilho mágico (RF-242).
 */
final class RuinArt {
    private static final Color ROCK = new Color(0x4C4842);
    private static final Color FIBER = new Color(0x8A7F6C);
    private static final Color FIBER_DARK = new Color(0x5A5244);

    private RuinArt() {}

    /** Feixe de fibras saindo da rocha, torcidas como raiz, mas duras como pedra. */
    static BuildingArt.Art fiber(int seed) {
        Random rnd = new Random(40 + seed);
        Canvas c = new Canvas(160, 260, 80, 230);
        c.groundShadow(0, 0, 0.55, 0.35);
        double top = c.oy - 2.6 * Z_PX;
        for (int i = 0; i < 9; i++) {
            double x0 = c.ox + (rnd.nextDouble() - 0.5) * 34, x1 = c.ox + (rnd.nextDouble() - 0.5) * 60;
            double y1 = top + rnd.nextDouble() * 30;
            CubicCurve2D curve = new CubicCurve2D.Double(x0, c.oy + 2, x0 + (rnd.nextDouble() - 0.5) * 40,
                    c.oy - 60, x1 + (rnd.nextDouble() - 0.5) * 40, y1 + 60, x1, y1);
            float w = (float) (5 + rnd.nextDouble() * 7);
            c.g.setStroke(new BasicStroke(w + 2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            c.g.setColor(alpha(OUTLINE, 0.7));
            c.g.draw(curve);
            c.g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            c.g.setColor(mix(FIBER_DARK, FIBER, rnd.nextDouble()));
            c.g.draw(curve);
            c.g.setStroke(new BasicStroke(w * 0.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            c.g.setColor(alpha(shade(FIBER, 1.3), 0.45));
            c.g.draw(new CubicCurve2D.Double(curve.getX1() - 1.5, curve.getY1(), curve.getCtrlX1() - 1.5,
                    curve.getCtrlY1(), curve.getCtrlX2() - 1.5, curve.getCtrlY2(), curve.getX2() - 1.5, curve.getY2()));
        }
        // nós, como de nervo, e a base presa na rocha
        for (int i = 0; i < 5; i++) {
            double x = c.ox + (rnd.nextDouble() - 0.5) * 30, y = c.oy - rnd.nextDouble() * 120;
            c.g.setColor(shade(FIBER, 0.85));
            c.g.fill(new Ellipse2D.Double(x - 5, y - 4, 10, 8));
        }
        rocks(c, rnd, 0.45, 8);
        c.g.dispose();
        return new BuildingArt.Art(c.image, c.ox, c.oy, null);
    }

    static BuildingArt.Art rubble() {
        Random rnd = new Random(51);
        Canvas c = new Canvas(180, 120, 90, 90);
        c.groundShadow(0, 0, 0.7, 0.4);
        rocks(c, rnd, 0.6, 14);
        c.g.dispose();
        return new BuildingArt.Art(c.image, c.ox, c.oy, null);
    }

    /** Pedras soltas em volta de um ponto (raio em metros). */
    private static void rocks(Canvas c, Random rnd, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * radius;
            double x = Math.cos(a) * r, y = Math.sin(a) * r, s = 0.12 + rnd.nextDouble() * 0.18;
            double px = c.px(x, y), py = c.py(x, y, 0);
            double w = s * HALF_W * 1.6, h = s * Z_PX * 1.3;
            Path2D stone = new Path2D.Double();
            stone.moveTo(px - w, py);
            stone.lineTo(px - w * 0.6, py - h);
            stone.lineTo(px + w * 0.5, py - h * 1.1);
            stone.lineTo(px + w, py - h * 0.2);
            stone.lineTo(px + w * 0.3, py + h * 0.35);
            stone.closePath();
            c.g.setPaint(new GradientPaint((float) (px - w), (float) (py - h), shade(jitter(ROCK, rnd, 0.1), 1.25),
                    (float) (px + w), (float) py, shade(ROCK, 0.65)));
            c.g.fill(stone);
            c.g.setStroke(new BasicStroke(1.2f));
            c.g.setColor(alpha(OUTLINE, 0.6));
            c.g.draw(stone);
        }
    }

    /** A laje no meio da câmara: pedra lisa, riscada com marcas em grupos de nove. */
    static BuildingArt.Art slab() {
        Random rnd = new Random(62);
        Canvas c = new Canvas(300, 200, 150, 150);
        c.groundShadow(0, 0, 1.4, 0.45);
        double hw = 1.1, hd = 0.7, z = 0.9;
        Color stone = new Color(0x6A645A);
        c.fill(shade(stone, 0.6), hw, -hd, 0, hw, hd, 0, hw, hd, z, hw, -hd, z);       // face leste
        c.fill(shade(stone, 0.8), -hw, hd, 0, hw, hd, 0, hw, hd, z, -hw, hd, z);       // face sul
        c.fill(shade(stone, 1.15), -hw, -hd, z, hw, -hd, z, hw, hd, z, -hw, hd, z);    // tampo
        c.g.setColor(alpha(OUTLINE, 0.7));
        c.g.setStroke(new BasicStroke(1.4f));
        c.g.draw(c.poly(-hw, -hd, z, hw, -hd, z, hw, hd, z, -hw, hd, z));
        c.g.draw(c.poly(-hw, hd, 0, hw, hd, 0, hw, hd, z, -hw, hd, z));
        c.g.draw(c.poly(hw, -hd, 0, hw, hd, 0, hw, hd, z, hw, -hd, z));
        // três grupos de nove riscos no tampo
        c.g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int group = 0; group < 3; group++) {
            double gx = -0.75 + group * 0.75;
            for (int i = 0; i < 9; i++) {
                double a = i * Math.PI * 2 / 9 + rnd.nextDouble() * 0.1, r0 = 0.08, r1 = 0.22;
                double x0 = gx + Math.cos(a) * r0, y0 = Math.sin(a) * r0 * 0.9;
                double x1 = gx + Math.cos(a) * r1, y1 = Math.sin(a) * r1 * 0.9;
                c.g.setColor(alpha(shade(stone, 0.45), 0.9));
                c.g.draw(new Line2D.Double(c.px(x0, y0), c.py(x0, y0, z), c.px(x1, y1), c.py(x1, y1, z)));
            }
        }
        // uma fibra que atravessa a laje, como se a pedra tivesse crescido em volta dela
        c.g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        c.g.setColor(FIBER_DARK);
        c.g.draw(new CubicCurve2D.Double(c.px(hw, 0.2), c.py(hw, 0.2, 0.2), c.px(hw + 0.4, 0.3), c.py(hw + 0.4, 0.3, 0.1),
                c.px(hw + 0.6, 0.6), c.py(hw + 0.6, 0.6, 0), c.px(hw + 0.9, 0.9), c.py(hw + 0.9, 0.9, 0)));
        c.g.dispose();
        return new BuildingArt.Art(c.image, c.ox, c.oy, null);
    }

    /** A escada de mão que sobe para o buraco, na sala de entrada. */
    static BuildingArt.Art ladder() {
        Canvas c = new Canvas(120, 300, 60, 270);
        c.groundShadow(0, 0, 0.4, 0.3);
        double top = c.oy - 4.2 * Z_PX;
        c.g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        c.g.setColor(shade(DARK_WOOD, 0.95));
        c.g.draw(new Line2D.Double(c.ox - 16, c.oy, c.ox - 10, top));
        c.g.draw(new Line2D.Double(c.ox + 16, c.oy, c.ox + 22, top));
        c.g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 1; i < 11; i++) {
            double t = i / 11.0, y = c.oy + (top - c.oy) * t, dx = 6 * t;
            c.g.setColor(shade(DARK_WOOD, 1.1 - t * 0.3));
            c.g.draw(new Line2D.Double(c.ox - 16 + dx, y, c.ox + 16 + dx, y));
        }
        c.g.dispose();
        return new BuildingArt.Art(c.image, c.ox, c.oy, null);
    }

    /** A boca do buraco no chão da superfície, com a ponta da escada aparecendo. */
    static BuildingArt.Art pit() {
        Random rnd = new Random(77);
        Canvas c = new Canvas(260, 160, 130, 90);
        double rx = 0.8 * HALF_W * 1.41, ry = 0.8 * HALF_H * 1.41;
        c.g.setColor(new Color(0x6B5136));
        c.g.fill(new Ellipse2D.Double(c.ox - rx * 1.25, c.oy - ry * 1.25, rx * 2.5, ry * 2.5));
        for (int i = 0; i < 60; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 1.0 + rnd.nextDouble() * 0.3;
            c.g.setColor(jitter(new Color(0x5A4430), rnd, 0.12));
            c.g.fill(new Ellipse2D.Double(c.ox + Math.cos(a) * rx * r - 3, c.oy + Math.sin(a) * ry * r - 2, 6, 4));
        }
        c.g.setColor(new Color(0x0E0C0B));
        c.g.fill(new Ellipse2D.Double(c.ox - rx, c.oy - ry, rx * 2, ry * 2));
        c.g.setColor(alpha(Color.BLACK, 0.5));
        c.g.fill(new Ellipse2D.Double(c.ox - rx * 0.8, c.oy - ry * 0.6, rx * 1.6, ry * 1.4));
        c.g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        c.g.setColor(shade(DARK_WOOD, 0.9));
        c.g.draw(new Line2D.Double(c.ox - 12, c.oy + 4, c.ox - 16, c.oy - 34));
        c.g.draw(new Line2D.Double(c.ox + 12, c.oy + 4, c.ox + 8, c.oy - 34));
        c.g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            double y = c.oy - 6 - i * 12;
            c.g.draw(new Line2D.Double(c.ox - 13 - i, y, c.ox + 11 - i, y));
        }
        c.g.dispose();
        return new BuildingArt.Art(c.image, c.ox, c.oy, null);
    }
}
