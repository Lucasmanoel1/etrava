package etrava.tools.art;

import static etrava.tools.art.ArtKit.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Tela de desenho com uma origem de mundo: o ponto (0, 0, 0) cai em
 * ({@link #ox}, {@link #oy}) da imagem — é o ponto de apoio do sprite no chão.
 */
final class Canvas {
    final BufferedImage image;
    final Graphics2D g;
    final double ox, oy;

    Canvas(int w, int h, double ox, double oy) {
        this.image = image(w, h);
        this.g = graphics(image);
        this.ox = ox;
        this.oy = oy;
    }

    double px(double x, double y) { return ox + sx(x, y); }
    double py(double x, double y, double z) { return oy + sy(x, y, z); }

    /** Polígono preenchido a partir de pontos de mundo (x, y, z em sequência). */
    Path2D poly(double... xyz) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < xyz.length; i += 3) {
            double x = px(xyz[i], xyz[i + 1]), y = py(xyz[i], xyz[i + 1], xyz[i + 2]);
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.closePath();
        return p;
    }

    void fill(Color c, double... xyz) {
        g.setColor(c);
        g.fill(poly(xyz));
    }

    /** Sombra suave no chão, em elipse, centrada num ponto de mundo. */
    void groundShadow(double x, double y, double radiusMeters, double strength) {
        double cx = px(x, y), cy = py(x, y, 0);
        double rx = radiusMeters * HALF_W * 1.41;
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        g.scale(1, 0.5);
        g.setPaint(new RadialGradientPaint(0, 0, (float) rx, new float[] {0f, 0.6f, 1f},
                new Color[] {alpha(Color.BLACK, strength), alpha(Color.BLACK, strength * 0.6), alpha(Color.BLACK, 0)}));
        g.fill(new Ellipse2D.Double(-rx, -rx, rx * 2, rx * 2));
        g.setTransform(old);
    }

    /** Entra no sistema de coordenadas de uma face plana (u, v em metros). Devolve a transformação anterior. */
    AffineTransform enterFace(double x, double y, double z, double ux, double uy, double uz,
            double vx, double vy, double vz) {
        AffineTransform old = g.getTransform();
        g.transform(face(x, y, z, ux, uy, uz, vx, vy, vz, ox, oy));
        return old;
    }

    void leave(AffineTransform old) {
        g.setTransform(old);
        g.setClip(null);
    }

    // ------------------------------------------------ texturas em coordenadas de face (metros)

    /** Fiadas de pedra irregular no retângulo [0, len] × [v0, v1]. */
    void stoneCourses(double len, double v0, double v1, double light, Random rnd) {
        g.setColor(shade(mix(OUTLINE, WARM_GREY, 0.3), light));
        g.fill(new Rectangle2D.Double(0, v0, len, v1 - v0));
        double rowH = 0.3;
        for (double v = v0; v < v1 - 0.01; v += rowH) {
            double u = -rnd.nextDouble() * 0.3;
            while (u < len) {
                double w = 0.34 + rnd.nextDouble() * 0.36;
                double a = Math.max(0, u) + 0.015, b = Math.min(len, u + w) - 0.015;
                if (b > a) {
                    Color c = shade(jitter(mix(WARM_GREY, OCHRE, rnd.nextDouble() * 0.4), rnd, 0.12), light);
                    double top = Math.min(v1, v + rowH) - 0.015;
                    g.setColor(c);
                    g.fill(new Rectangle2D.Double(a, v + 0.015, b - a, top - v - 0.015));
                    g.setColor(alpha(shade(c, 1.22), 0.6));
                    g.fill(new Rectangle2D.Double(a, top - 0.05, b - a, 0.05));
                }
                u += w;
            }
        }
    }

    /** Reboco manchado no retângulo [0, len] × [v0, v1]. */
    void plaster(double len, double v0, double v1, double light, Random rnd) {
        g.setColor(shade(PLASTER, light));
        g.fill(new Rectangle2D.Double(0, v0, len, v1 - v0));
        java.awt.Shape clip = g.getClip();
        g.clip(new Rectangle2D.Double(0, v0, len, v1 - v0));
        for (int i = 0; i < len * 14; i++) {
            double u = rnd.nextDouble() * len, v = v0 + Math.pow(rnd.nextDouble(), 1.6) * (v1 - v0);
            double r = 0.08 + rnd.nextDouble() * 0.28;
            Color c = rnd.nextDouble() < 0.7 ? shade(mix(PLASTER, OCHRE, 0.6), light * 0.8) : shade(PLASTER, light * 1.1);
            g.setColor(alpha(c, 0.10 + rnd.nextDouble() * 0.14));
            g.fill(new Ellipse2D.Double(u - r, v - r * 0.6, r * 2, r * 1.2));
        }
        g.setClip(clip);
    }

    void beam(double u0, double v0, double u1, double v1, double thickness, double light) {
        g.setColor(shade(DARK_WOOD, light));
        g.setStroke(new BasicStroke((float) thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(new java.awt.geom.Line2D.Double(u0, v0, u1, v1));
        g.setColor(alpha(shade(DARK_WOOD, light * 1.5), 0.5));
        g.setStroke(new BasicStroke((float) (thickness * 0.25)));
        g.draw(new java.awt.geom.Line2D.Double(u0, v0, u1, v1));
    }

    /** Tábuas verticais no retângulo dado. */
    void planks(double u0, double v0, double w, double h, Color wood, double light, Random rnd) {
        int n = Math.max(2, (int) Math.round(w / 0.2));
        for (int i = 0; i < n; i++) {
            g.setColor(shade(jitter(wood, rnd, 0.1), light));
            g.fill(new Rectangle2D.Double(u0 + w * i / n, v0, w / n - 0.012, h));
        }
    }

    /** Colmo: faixas horizontais com fios no sentido da queda. v = 0 é o beiral. */
    void thatch(double len, double slope, double light, Random rnd) {
        g.setColor(shade(mix(STRAW, OCHRE, 0.45), light * 0.8));
        g.fill(new Rectangle2D.Double(0, 0, len, slope));
        double course = 0.42;
        for (double v = slope; v > -course; v -= course) {
            for (int i = 0; i < len * 42; i++) {
                double u = rnd.nextDouble() * len;
                double top = v - rnd.nextDouble() * 0.08;
                double bottom = v - course - rnd.nextDouble() * 0.12;
                if (top <= 0) continue;
                double t = rnd.nextDouble();
                Color c = t < 0.2 ? mix(STRAW, Color.WHITE, 0.12) : t < 0.75 ? mix(STRAW, OCHRE, rnd.nextDouble() * 0.6)
                        : mix(OCHRE, DARK_WOOD, 0.4);
                g.setColor(alpha(shade(c, light), 0.85));
                g.setStroke(new BasicStroke(0.022f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new java.awt.geom.Line2D.Double(u, Math.min(slope, top), u + (rnd.nextDouble() - 0.5) * 0.05,
                        Math.max(-0.07, bottom)));
            }
            // sombra sob cada camada
            g.setColor(alpha(shade(DARK_WOOD, light), 0.28));
            g.fill(new Rectangle2D.Double(0, Math.max(0, v - course - 0.04), len, 0.06));
        }
        g.setColor(shade(mix(DARK_WOOD, OCHRE, 0.3), light)); // cumeeira
        g.fill(new Rectangle2D.Double(0, slope - 0.22, len, 0.22));
    }
}
