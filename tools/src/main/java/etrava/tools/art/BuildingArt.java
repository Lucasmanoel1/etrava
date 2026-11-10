package etrava.tools.art;

import static etrava.tools.art.ArtKit.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Casas de pedra, enxaimel e colmo, vistas de fora. A base fica centrada na
 * origem: x em [-w/2, w/2], y em [-d/2, d/2]. As faces visíveis são a sudoeste
 * (plano y = +d/2, iluminada) e a sudeste (plano x = +w/2, na sombra).
 */
final class BuildingArt {
    private static final double OVERHANG = 0.35, STONE = 0.9;
    private static final double LIT = 1.0, SHADED = 0.68;

    /** Abertura numa face: 'S' = sudoeste, 'E' = sudeste; pos = metros ao longo da face. */
    record Opening(char face, double pos, boolean door) {}

    record Spec(double w, double d, double wallH, double roofH, boolean ridgeAlongX, boolean chimney, int seed,
            Opening... openings) {}

    /** Imagem, ponto de apoio e a camada que brilha à noite (janelas). */
    record Art(BufferedImage image, double anchorX, double anchorY, BufferedImage emissive) {}

    private BuildingArt() {}

    static Art house(Spec s) {
        double hw = s.w() / 2, hd = s.d() / 2;
        double pad = 16, shadowExtra = 150;
        double halfSpan = (s.w() + s.d()) / 2 * HALF_W + OVERHANG * 2 * HALF_W;
        int width = (int) Math.ceil(halfSpan * 2 + pad * 2 + shadowExtra);
        double top = (s.w() + s.d()) / 2 * HALF_H + (s.wallH() + s.roofH() + 1.0) * Z_PX + pad;
        double bottom = (s.w() + s.d()) / 2 * HALF_H + OVERHANG * 2 * HALF_H + pad + 14;
        int height = (int) Math.ceil(top + bottom);
        Canvas c = new Canvas(width, height, pad + halfSpan, top);
        Canvas glow = new Canvas(width, height, pad + halfSpan, top);
        Random rnd = new Random(s.seed());

        // sombra no chão, caindo para leste
        for (int i = 0; i < 6; i++) {
            double grow = i * 0.07;
            c.fill(alpha(Color.BLACK, 0.05),
                    -hw, hd + grow, 0, hw + 1.5 + grow, hd + grow, 0,
                    hw + 1.9 + grow, -hd - grow, 0, hw, -hd - grow, 0);
        }

        // parede sudoeste (iluminada) e sudeste (sombra)
        wall(c, glow, s, rnd, 'S', -hw, hd, 1, 0, s.w(), LIT);
        wall(c, glow, s, rnd, 'E', hw, hd, 0, -1, s.d(), SHADED);

        double ridgeZ = s.wallH() + s.roofH();
        if (s.ridgeAlongX()) {
            gable(c, rnd, hw, hd, 0, -1, s.d(), s.wallH(), s.roofH(), SHADED);
            double run = hd + OVERHANG, eaveZ = s.wallH() - OVERHANG * s.roofH() / hd;
            roof(c, rnd, -hw - OVERHANG, hd + OVERHANG, eaveZ, 1, 0, s.w() + 2 * OVERHANG, 0, -run, ridgeZ - eaveZ, LIT);
        } else {
            gable(c, rnd, -hw, hd, 1, 0, s.w(), s.wallH(), s.roofH(), LIT);
            double run = hw + OVERHANG, eaveZ = s.wallH() - OVERHANG * s.roofH() / hw;
            roof(c, rnd, hw + OVERHANG, hd + OVERHANG, eaveZ, 0, -1, s.d() + 2 * OVERHANG, -run, 0, ridgeZ - eaveZ, 0.8);
        }
        if (s.chimney()) chimney(c, rnd, s, ridgeZ);

        c.g.dispose();
        glow.g.dispose();
        return new Art(c.image, c.ox, c.oy, glow.image);
    }

    /** Uma parede: pedra embaixo, reboco e madeira em cima, portas e janelas. */
    private static void wall(Canvas c, Canvas glow, Spec s, Random rnd, char face, double x, double y,
            double ux, double uy, double len, double light) {
        AffineTransform old = c.enterFace(x, y, 0, ux, uy, 0, 0, 0, 1);
        double h = s.wallH();
        c.stoneCourses(len, 0, STONE, light, rnd);
        c.plaster(len, STONE, h, light, rnd);
        // enxaimel: postos, travessas e escoras
        c.beam(0, STONE + 0.07, len, STONE + 0.07, 0.14, light);
        c.beam(0, h - 0.08, len, h - 0.08, 0.16, light);
        int posts = Math.max(2, (int) Math.round(len / 1.7));
        for (int i = 0; i <= posts; i++) {
            double u = Math.min(len - 0.08, Math.max(0.08, len * i / posts));
            c.beam(u, STONE, u, h, 0.15, light);
        }
        c.beam(0.1, STONE + 0.1, len / posts - 0.05, h - 0.12, 0.11, light);
        c.beam(len - 0.1, STONE + 0.1, len - len / posts + 0.05, h - 0.12, 0.11, light);

        AffineTransform oldGlow = glow.enterFace(x, y, 0, ux, uy, 0, 0, 0, 1);
        for (Opening o : s.openings()) {
            if (o.face() != face) continue;
            if (o.door()) door(c, rnd, o.pos(), light);
            else window(c, glow, rnd, o.pos(), light);
        }
        glow.leave(oldGlow);
        // escurece o pé da parede (sujeira, umidade)
        c.g.setPaint(new GradientPaint(0, 0, alpha(OUTLINE, 0.45), 0, 0.7f, alpha(OUTLINE, 0)));
        c.g.fill(new Rectangle2D.Double(0, 0, len, 0.7));
        c.leave(old);
    }

    private static void door(Canvas c, Random rnd, double u, double light) {
        double w = 1.0, h = 1.95;
        c.g.setColor(shade(DARK_WOOD, light * 0.7));
        c.g.fill(new Rectangle2D.Double(u - 0.1, 0, w + 0.2, h + 0.12));
        c.planks(u, 0.04, w, h - 0.04, mix(DARK_WOOD, RUST, 0.35), light, rnd);
        c.g.setColor(shade(new Color(0x2B2725), light)); // ferragens
        c.g.fill(new Rectangle2D.Double(u, 0.45, w, 0.07));
        c.g.fill(new Rectangle2D.Double(u, 1.45, w, 0.07));
        c.g.fill(new Ellipse2D.Double(u + w - 0.2, 0.92, 0.09, 0.09));
        c.g.setColor(shade(WARM_GREY, light * 1.1)); // soleira
        c.g.fill(new Rectangle2D.Double(u - 0.15, -0.02, w + 0.3, 0.1));
    }

    private static void window(Canvas c, Canvas glow, Random rnd, double u, double light) {
        double w = 0.72, v0 = 1.2, h = 0.8;
        c.g.setColor(shade(DARK_WOOD, light * 0.75));
        c.g.fill(new Rectangle2D.Double(u - 0.08, v0 - 0.08, w + 0.16, h + 0.16));
        c.g.setColor(new Color(0x1E1B22));
        c.g.fill(new Rectangle2D.Double(u, v0, w, h));
        c.beam(u + w / 2, v0, u + w / 2, v0 + h, 0.05, light);
        c.beam(u, v0 + h / 2, u + w, v0 + h / 2, 0.05, light);
        c.planks(u - 0.42, v0 - 0.04, 0.34, h + 0.08, mix(DARK_WOOD, OLIVE, 0.45), light, rnd); // folhas
        c.planks(u + w + 0.08, v0 - 0.04, 0.34, h + 0.08, mix(DARK_WOOD, OLIVE, 0.45), light, rnd);
        // à noite: vidraça acesa
        glow.g.setPaint(new GradientPaint(0, (float) v0, new Color(255, 170, 70, 235), 0, (float) (v0 + h),
                new Color(255, 214, 130, 235)));
        glow.g.fill(new Rectangle2D.Double(u, v0, w / 2 - 0.025, h / 2 - 0.025));
        glow.g.fill(new Rectangle2D.Double(u + w / 2 + 0.025, v0, w / 2 - 0.025, h / 2 - 0.025));
        glow.g.fill(new Rectangle2D.Double(u, v0 + h / 2 + 0.025, w / 2 - 0.025, h / 2 - 0.025));
        glow.g.fill(new Rectangle2D.Double(u + w / 2 + 0.025, v0 + h / 2 + 0.025, w / 2 - 0.025, h / 2 - 0.025));
    }

    /** Empena triangular acima de uma parede. */
    private static void gable(Canvas c, Random rnd, double x, double y, double ux, double uy, double len,
            double wallH, double roofH, double light) {
        AffineTransform old = c.enterFace(x, y, wallH, ux, uy, 0, 0, 0, 1);
        Path2D tri = new Path2D.Double();
        tri.moveTo(0, 0);
        tri.lineTo(len, 0);
        tri.lineTo(len / 2, roofH);
        tri.closePath();
        c.g.clip(tri);
        c.plaster(len, 0, roofH, light, rnd);
        c.g.clip(tri);
        c.beam(len / 2, 0, len / 2, roofH, 0.14, light);
        c.beam(len * 0.25, 0, len * 0.25, roofH, 0.11, light);
        c.beam(len * 0.75, 0, len * 0.75, roofH, 0.11, light);
        c.beam(0, roofH * 0.45, len, roofH * 0.45, 0.11, light);
        c.leave(old);
    }

    /** Água visível do telhado, em colmo. u corre ao longo do beiral; v sobe até a cumeeira. */
    private static void roof(Canvas c, Random rnd, double x, double y, double z, double ux, double uy, double len,
            double runX, double runY, double rise, double light) {
        double slope = Math.sqrt(runX * runX + runY * runY + rise * rise);
        // espessura do beiral
        c.fill(shade(DARK_WOOD, light * 0.8),
                x, y, z, x + ux * len, y + uy * len, z, x + ux * len, y + uy * len, z - 0.16, x, y, z - 0.16);
        AffineTransform old = c.enterFace(x, y, z, ux, uy, 0, runX / slope, runY / slope, rise / slope);
        c.g.clip(new Rectangle2D.Double(0, -0.08, len, slope + 0.08));
        c.thatch(len, slope, light, rnd);
        // tábuas de borda nas duas pontas
        c.g.setColor(shade(DARK_WOOD, light));
        c.g.fill(new Rectangle2D.Double(0, 0, 0.12, slope));
        c.g.fill(new Rectangle2D.Double(len - 0.12, 0, 0.12, slope));
        c.leave(old);
    }

    private static void chimney(Canvas c, Random rnd, Spec s, double ridgeZ) {
        double cx = s.ridgeAlongX() ? -s.w() * 0.22 : 0, cy = s.ridgeAlongX() ? 0 : -s.d() * 0.22;
        double r = 0.32, z0 = ridgeZ - 0.9, z1 = ridgeZ + 0.55;
        AffineTransform old = c.enterFace(cx - r, cy + r, z0, 1, 0, 0, 0, 0, 1);
        c.stoneCourses(r * 2, 0, z1 - z0, LIT, rnd);
        c.leave(old);
        old = c.enterFace(cx + r, cy + r, z0, 0, -1, 0, 0, 0, 1);
        c.stoneCourses(r * 2, 0, z1 - z0, SHADED, rnd);
        c.leave(old);
        c.fill(shade(WARM_GREY, 0.55), cx - r, cy - r, z1, cx + r, cy - r, z1, cx + r, cy + r, z1, cx - r, cy + r, z1);
        c.g.setStroke(new BasicStroke(1f));
    }
}
