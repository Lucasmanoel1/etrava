package etrava.tools.art;

import static etrava.tools.art.ArtKit.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Objetos do cenário: árvore, barril, caixote, poço, lampião, monte de feno. */
final class PropArt {
    /** Raio na tela (px) de um círculo de 1 m deitado no chão: largura e altura. */
    private static final double RX = HALF_W * Math.sqrt(2), RY = HALF_H * Math.sqrt(2);

    private PropArt() {}

    static BuildingArt.Art tree(int variant, boolean dead) {
        Random rnd = new Random(500 + variant);
        Canvas c = new Canvas(300, 360, 150, 320);
        c.groundShadow(0.5, -0.1, dead ? 0.8 : 1.5, 0.38);
        double lean = (rnd.nextDouble() - 0.5) * 14;
        double topY = c.oy - 2.6 * Z_PX;
        // tronco
        Path2D trunk = new Path2D.Double();
        trunk.moveTo(c.ox - 13, c.oy + 3);
        trunk.quadTo(c.ox - 7 + lean * 0.4, c.oy - 80, c.ox - 5 + lean, topY);
        trunk.lineTo(c.ox + 5 + lean, topY);
        trunk.quadTo(c.ox + 8 + lean * 0.4, c.oy - 80, c.ox + 15, c.oy + 3);
        trunk.closePath();
        c.g.setPaint(new GradientPaint((float) c.ox - 12, 0, shade(DARK_WOOD, 1.35), (float) c.ox + 12, 0,
                shade(DARK_WOOD, 0.7)));
        c.g.fill(trunk);
        c.g.setStroke(new BasicStroke(1.2f));
        c.g.setColor(alpha(OUTLINE, 0.6));
        c.g.draw(trunk);
        // galhos
        for (int i = 0; i < (dead ? 9 : 4); i++) {
            double t = 0.45 + rnd.nextDouble() * 0.55, side = rnd.nextBoolean() ? 1 : -1;
            double bx = c.ox + lean * t, by = c.oy + (topY - c.oy) * t;
            double len = (dead ? 40 : 30) + rnd.nextDouble() * 45;
            double ex = bx + side * len, ey = by - len * (0.5 + rnd.nextDouble() * 0.6);
            c.g.setColor(shade(DARK_WOOD, side < 0 ? 1.2 : 0.8));
            c.g.setStroke(new BasicStroke((float) (5.5 - t * 3), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            c.g.draw(new Line2D.Double(bx, by, ex, ey));
            if (dead) {
                c.g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                c.g.draw(new Line2D.Double(ex, ey, ex + side * 18, ey - 16 - rnd.nextDouble() * 14));
                c.g.draw(new Line2D.Double((bx + ex) / 2, (by + ey) / 2, (bx + ex) / 2 + side * 6, (by + ey) / 2 - 26));
            }
        }
        if (!dead) foliage(c, rnd, lean);
        c.g.dispose();
        return new BuildingArt.Art(c.image, c.ox, c.oy, null);
    }

    private static void foliage(Canvas c, Random rnd, double lean) {
        record Blob(double x, double y, double z, double r, double depth) {}
        List<Blob> blobs = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * 1.45;
            double z = 3.5 + (rnd.nextDouble() - 0.5) * 2.2;
            double shrink = 1 - Math.abs(z - 3.4) / 1.9;
            double x = Math.cos(a) * rr * Math.max(0.35, shrink), y = Math.sin(a) * rr * Math.max(0.35, shrink);
            blobs.add(new Blob(x, y, z, 13 + rnd.nextDouble() * 13, x + y + z * 0.05));
        }
        blobs.sort(Comparator.comparingDouble(Blob::depth));
        for (Blob b : blobs) {
            double x = c.px(b.x(), b.y()) + lean, y = c.py(b.x(), b.y(), b.z());
            // mais claro no alto e à esquerda (de onde vem a luz)
            double lit = clamp(0.5 + (b.z() - 3.4) * 0.28 - (b.x() - b.y()) * 0.14);
            Color leaf = rnd.nextDouble() < 0.10 ? mix(OLIVE, mix(RUST, OCHRE, rnd.nextDouble()), 0.7) : OLIVE;
            Color base = shade(leaf, 0.55 + lit * 0.75);
            c.g.setColor(alpha(shade(base, 0.6), 0.9));
            c.g.fill(new Ellipse2D.Double(x - b.r() + 3, y - b.r() * 0.85 + 5, b.r() * 2, b.r() * 1.7));
            c.g.setColor(base);
            c.g.fill(new Ellipse2D.Double(x - b.r(), y - b.r() * 0.85, b.r() * 2, b.r() * 1.7));
            for (int i = 0; i < 5; i++) { // pinceladas de folha
                double dx = (rnd.nextDouble() - 0.62) * b.r() * 1.5, dy = (rnd.nextDouble() - 0.66) * b.r() * 1.3;
                c.g.setColor(alpha(shade(base, 1.18 + rnd.nextDouble() * 0.2), 0.55));
                c.g.fill(new Ellipse2D.Double(x + dx - 4, y + dy - 2.5, 8, 5));
            }
        }
    }

    static BuildingArt.Art barrel() {
        Random rnd = new Random(71);
        Canvas c = new Canvas(110, 130, 55, 100);
        c.groundShadow(0.25, -0.05, 0.55, 0.4);
        double rx = 0.4 * RX, ry = 0.4 * RY, h = 1.0 * Z_PX;
        Path2D body = new Path2D.Double();
        body.moveTo(c.ox - rx, c.oy - h);
        body.quadTo(c.ox - rx * 1.16, c.oy - h / 2, c.ox - rx, c.oy);
        body.append(new Arc2D.Double(c.ox - rx, c.oy - ry, rx * 2, ry * 2, 180, 180, Arc2D.OPEN), true);
        body.quadTo(c.ox + rx * 1.16, c.oy - h / 2, c.ox + rx, c.oy - h);
        body.closePath();
        Color wood = mix(DARK_WOOD, OCHRE, 0.45);
        c.g.setPaint(new GradientPaint((float) (c.ox - rx), 0, shade(wood, 1.25), (float) (c.ox + rx), 0, shade(wood, 0.6)));
        c.g.fill(body);
        c.g.setClip(body);
        c.g.setStroke(new BasicStroke(1f));
        for (int i = -4; i <= 4; i++) { // aduelas
            c.g.setColor(alpha(OUTLINE, 0.35));
            double x = c.ox + i * rx / 4.4;
            c.g.draw(new Line2D.Double(x, c.oy - h, x, c.oy + ry));
        }
        c.g.setColor(new Color(0x3A3634)); // aros de ferro
        c.g.setStroke(new BasicStroke(4.5f));
        for (double t : new double[] {0.24, 0.78}) {
            c.g.draw(new Arc2D.Double(c.ox - rx * 1.1, c.oy - h * t - ry, rx * 2.2, ry * 2, 180, 180, Arc2D.OPEN));
        }
        c.g.setClip(null);
        c.g.setColor(shade(wood, 0.95)); // tampa
        c.g.fill(new Ellipse2D.Double(c.ox - rx, c.oy - h - ry, rx * 2, ry * 2));
        c.g.setColor(shade(wood, 1.3));
        c.g.fill(new Ellipse2D.Double(c.ox - rx * 0.85, c.oy - h - ry * 0.85, rx * 1.7, ry * 1.7));
        c.g.setColor(alpha(OUTLINE, 0.55));
        c.g.setStroke(new BasicStroke(1.2f));
        c.g.draw(new Ellipse2D.Double(c.ox - rx, c.oy - h - ry, rx * 2, ry * 2));
        c.g.draw(body);
        if (rnd.nextBoolean()) c.g.draw(new Line2D.Double(c.ox - rx * 0.6, c.oy - h, c.ox + rx * 0.6, c.oy - h));
        c.g.dispose();
        return new BuildingArt.Art(c.image, c.ox, c.oy, null);
    }

    static BuildingArt.Art crate() {
        Random rnd = new Random(72);
        Canvas c = new Canvas(170, 150, 85, 100);
        double s = 0.45, h = 0.85;
        c.groundShadow(0.3, -0.05, 0.75, 0.38);
        Color wood = mix(DARK_WOOD, OCHRE, 0.55);
        box(c, rnd, -s, -s, s, s, 0, h, wood);
        c.g.dispose();
        return new BuildingArt.Art(c.image, c.ox, c.oy, null);
    }

    /** Caixa de tábuas com as três faces visíveis. */
    private static void box(Canvas c, Random rnd, double x0, double y0, double x1, double y1, double z0, double z1,
            Color wood) {
        AffineTransform old = c.enterFace(x0, y1, z0, 1, 0, 0, 0, 0, 1);
        c.planks(0, 0, x1 - x0, z1 - z0, wood, 1.0, rnd);
        frame(c, x1 - x0, z1 - z0, 1.0);
        c.leave(old);
        old = c.enterFace(x1, y1, z0, 0, -1, 0, 0, 0, 1);
        c.planks(0, 0, y1 - y0, z1 - z0, wood, 0.66, rnd);
        frame(c, y1 - y0, z1 - z0, 0.66);
        c.leave(old);
        old = c.enterFace(x0, y1, z1, 1, 0, 0, 0, -1, 0);
        c.planks(0, 0, x1 - x0, y1 - y0, wood, 1.18, rnd);
        frame(c, x1 - x0, y1 - y0, 1.18);
        c.leave(old);
    }

    private static void frame(Canvas c, double w, double h, double light) {
        c.g.setColor(shade(DARK_WOOD, light * 1.1));
        c.g.setStroke(new BasicStroke(0.09f));
        c.g.draw(new Rectangle2D.Double(0.045, 0.045, w - 0.09, h - 0.09));
        c.g.setStroke(new BasicStroke(0.07f));
        c.g.draw(new Line2D.Double(0.06, 0.06, w - 0.06, h - 0.06));
    }

    static BuildingArt.Art well() {
        Random rnd = new Random(73);
        Canvas c = new Canvas(260, 300, 130, 230);
        c.groundShadow(0.5, -0.1, 1.35, 0.4);
        double r = 0.9, h = 0.8;
        double rx = r * RX, ry = r * RY, hp = h * Z_PX;
        // mureta: cilindro de pedra
        Path2D ring = new Path2D.Double();
        ring.moveTo(c.ox - rx, c.oy - hp);
        ring.lineTo(c.ox - rx, c.oy);
        ring.append(new Arc2D.Double(c.ox - rx, c.oy - ry, rx * 2, ry * 2, 180, 180, Arc2D.OPEN), true);
        ring.lineTo(c.ox + rx, c.oy - hp);
        ring.closePath();
        c.g.setColor(mix(OUTLINE, WARM_GREY, 0.3));
        c.g.fill(ring);
        c.g.setClip(ring);
        for (int row = 0; row < 4; row++) {
            double y = c.oy - hp + row * hp / 3.2;
            for (int i = 0; i < 9; i++) {
                double a = Math.PI * (i + (row % 2) * 0.5) / 8.5;
                double x = c.ox - Math.cos(a) * rx, yy = y + Math.sin(a) * ry;
                Color col = shade(jitter(mix(WARM_GREY, OCHRE, rnd.nextDouble() * 0.4), rnd, 0.1), 1.2 - a / Math.PI * 0.6);
                c.g.setColor(col);
                c.g.fill(new java.awt.geom.RoundRectangle2D.Double(x - 14, yy + 1, 27, hp / 3.2 - 3, 6, 6));
            }
        }
        c.g.setClip(null);
        c.g.setColor(shade(WARM_GREY, 1.15)); // borda de cima
        c.g.fill(new Ellipse2D.Double(c.ox - rx, c.oy - hp - ry, rx * 2, ry * 2));
        c.g.setColor(new Color(0x15181C)); // o escuro lá dentro
        c.g.fill(new Ellipse2D.Double(c.ox - rx * 0.74, c.oy - hp - ry * 0.74, rx * 1.48, ry * 1.48));
        // armação com sarilho, corda e balde
        double postTop = c.oy - 2.3 * Z_PX;
        c.g.setColor(shade(DARK_WOOD, 0.8));
        c.g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        c.g.draw(new Line2D.Double(c.ox + rx * 0.82, c.oy - hp - 4, c.ox + rx * 0.82, postTop));
        c.g.setColor(shade(DARK_WOOD, 1.2));
        c.g.draw(new Line2D.Double(c.ox - rx * 0.82, c.oy - hp - 4, c.ox - rx * 0.82, postTop));
        c.g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        c.g.draw(new Line2D.Double(c.ox - rx * 0.95, postTop + 8, c.ox + rx * 0.95, postTop + 8));
        c.g.setColor(STRAW);
        c.g.setStroke(new BasicStroke(1.6f));
        c.g.draw(new Line2D.Double(c.ox + 6, postTop + 10, c.ox + 6, c.oy - hp - 22));
        c.g.setColor(mix(DARK_WOOD, OCHRE, 0.4));
        c.g.fill(new Rectangle2D.Double(c.ox - 4, c.oy - hp - 24, 20, 18));
        c.g.setColor(alpha(OUTLINE, 0.6));
        c.g.setStroke(new BasicStroke(1.2f));
        c.g.draw(new Rectangle2D.Double(c.ox - 4, c.oy - hp - 24, 20, 18));
        c.g.dispose();
        return new BuildingArt.Art(c.image, c.ox, c.oy, null);
    }

    /** Poste com lamparina; a camada de brilho acende à noite. */
    static BuildingArt.Art lampPost() {
        Canvas c = new Canvas(120, 220, 60, 195);
        Canvas glow = new Canvas(120, 220, 60, 195);
        c.groundShadow(0.15, -0.05, 0.3, 0.4);
        double top = c.oy - 2.5 * Z_PX;
        c.g.setColor(shade(DARK_WOOD, 0.9));
        c.g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        c.g.draw(new Line2D.Double(c.ox, c.oy, c.ox, top));
        c.g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        c.g.draw(new Line2D.Double(c.ox, top + 6, c.ox + 22, top + 6));
        c.g.draw(new Line2D.Double(c.ox, top + 26, c.ox + 14, top + 8));
        double lx = c.ox + 22, ly = top + 10;
        c.g.setColor(new Color(0x2B2725));
        c.g.setStroke(new BasicStroke(1.5f));
        c.g.draw(new Line2D.Double(lx, ly - 4, lx, ly + 4));
        c.g.fill(new Rectangle2D.Double(lx - 8, ly + 4, 16, 4));
        c.g.fill(new Rectangle2D.Double(lx - 7, ly + 26, 14, 4));
        c.g.setColor(new Color(0x6B5A3A));
        c.g.fill(new Rectangle2D.Double(lx - 6, ly + 8, 12, 18));
        c.g.setColor(new Color(0x2B2725));
        c.g.draw(new Rectangle2D.Double(lx - 6, ly + 8, 12, 18));
        glow.g.setPaint(new RadialGradientPaint((float) lx, (float) ly + 17, 30f, new float[] {0f, 0.25f, 1f},
                new Color[] {new Color(255, 236, 170, 255), new Color(255, 180, 80, 200), new Color(255, 150, 60, 0)}));
        glow.g.fill(new Ellipse2D.Double(lx - 30, ly - 13, 60, 60));
        c.g.dispose();
        glow.g.dispose();
        return new BuildingArt.Art(c.image, c.ox, c.oy, glow.image);
    }

    static BuildingArt.Art hay() {
        Random rnd = new Random(75);
        Canvas c = new Canvas(220, 190, 110, 140);
        c.groundShadow(0.4, -0.1, 1.05, 0.38);
        double rx = 0.8 * RX, ry = 0.8 * RY, h = 1.25 * Z_PX;
        Path2D mound = new Path2D.Double();
        mound.moveTo(c.ox - rx, c.oy);
        mound.curveTo(c.ox - rx, c.oy - h * 0.9, c.ox - rx * 0.4, c.oy - h * 1.15, c.ox, c.oy - h * 1.15);
        mound.curveTo(c.ox + rx * 0.4, c.oy - h * 1.15, c.ox + rx, c.oy - h * 0.9, c.ox + rx, c.oy);
        mound.append(new Arc2D.Double(c.ox - rx, c.oy - ry, rx * 2, ry * 2, 0, -180, Arc2D.OPEN), true);
        mound.closePath();
        c.g.setPaint(new GradientPaint((float) (c.ox - rx), 0, shade(STRAW, 1.1), (float) (c.ox + rx), 0, shade(OCHRE, 0.7)));
        c.g.fill(mound);
        c.g.setClip(mound);
        for (int i = 0; i < 700; i++) {
            double x = c.ox + (rnd.nextDouble() * 2 - 1) * rx, y = c.oy + ry - rnd.nextDouble() * (h * 1.15 + ry);
            double lit = 1.15 - (x - c.ox + rx) / (2 * rx) * 0.5;
            c.g.setColor(alpha(shade(mix(STRAW, OCHRE, rnd.nextDouble()), lit), 0.8));
            c.g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double dx = (x - c.ox) * 0.12 + (rnd.nextDouble() - 0.5) * 5;
            c.g.draw(new Line2D.Double(x, y, x + dx, y + 7 + rnd.nextDouble() * 8));
        }
        c.g.setClip(null);
        c.g.dispose();
        return new BuildingArt.Art(c.image, c.ox, c.oy, null);
    }
}
