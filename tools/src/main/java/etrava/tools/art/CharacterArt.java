package etrava.tools.art;

import static etrava.tools.art.ArtKit.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Folha de sprites de um personagem: um boneco articulado em 3D, desenhado nas
 * 8 direções e em cada quadro de animação. Como o mesmo "modelo" gera todos os
 * quadros, o personagem fica idêntico em qualquer direção (D-019).
 * Linhas = direção (0 = leste do mundo, de 45° em 45°); colunas = quadros.
 */
final class CharacterArt {
    static final int FRAME_W = 96, FRAME_H = 128, ANCHOR_X = 48, ANCHOR_Y = 112;
    static final int IDLE_FRAMES = 4, WALK_FRAMES = 8;
    static final int DIRECTIONS = 8, COLUMNS = IDLE_FRAMES + WALK_FRAMES;

    /** Aparência; {@code scale} &lt; 1 faz gente menor (criança). */
    record Look(Color skin, Color hair, Color tunic, Color pants, Color boots, boolean hood, double skirt, double scale) {
        Look(Color skin, Color hair, Color tunic, Color pants, Color boots, boolean hood, double skirt) {
            this(skin, hair, tunic, pants, boots, hood, skirt, 1.0);
        }
    }

    private record Part(double x0, double y0, double z0, double x1, double y1, double z1, double width, Color color,
            double depth) {}

    private CharacterArt() {}

    static BufferedImage sheet(Look look) {
        BufferedImage img = image(FRAME_W * COLUMNS, FRAME_H * DIRECTIONS);
        Graphics2D g = graphics(img);
        for (int dir = 0; dir < DIRECTIONS; dir++) {
            double yaw = dir * Math.PI / 4;
            for (int col = 0; col < COLUMNS; col++) {
                boolean walking = col >= IDLE_FRAMES;
                double phase = walking
                        ? (col - IDLE_FRAMES) * Math.PI * 2 / WALK_FRAMES
                        : col * Math.PI * 2 / IDLE_FRAMES;
                drawFrame(g, col * FRAME_W + ANCHOR_X, dir * FRAME_H + ANCHOR_Y, look, yaw, walking, phase);
            }
        }
        g.dispose();
        return img;
    }

    private static void drawFrame(Graphics2D g, double ox, double oy, Look look, double yaw, boolean walking,
            double phase) {
        List<Part> parts = new ArrayList<>();
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        double swing = walking ? Math.sin(phase) * 0.40 : 0;
        double bob = walking ? Math.abs(Math.cos(phase)) * 0.025 : Math.sin(phase) * 0.008;
        double hipZ = 0.90 + bob, shoulderZ = 1.40 + bob, headZ = 1.60 + bob;

        // pernas: coxa e canela; a perna que vai para trás dobra o joelho
        for (int side = -1; side <= 1; side += 2) {
            double a = swing * side;
            double bend = walking ? Math.max(0, -Math.cos(phase) * side) * 0.55 + 0.08 : 0.03;
            double kx = Math.sin(a) * 0.45, kz = hipZ - Math.cos(a) * 0.45;
            double fx = kx + Math.sin(a - bend) * 0.45, fz = Math.max(0.04, kz - Math.cos(a - bend) * 0.45);
            add(parts, cos, sin, 0, side * 0.09, hipZ, kx, side * 0.09, kz, 11, look.pants());
            add(parts, cos, sin, kx, side * 0.09, kz, fx, side * 0.09, fz, 9.5, look.pants());
            add(parts, cos, sin, fx - 0.02, side * 0.09, fz, fx + 0.13, side * 0.09, Math.max(0.03, fz - 0.03), 8,
                    look.boots());
        }
        // tronco e barra da túnica
        add(parts, cos, sin, 0, 0, hipZ + 0.04, 0, 0, shoulderZ - 0.04, 25, look.tunic());
        add(parts, cos, sin, 0, 0, hipZ - look.skirt(), 0, 0, hipZ + 0.06, 24, shade(look.tunic(), 0.88));
        // braços: balançam ao contrário das pernas
        for (int side = -1; side <= 1; side += 2) {
            double a = -swing * side * 0.8;
            double ex = Math.sin(a) * 0.28, ez = shoulderZ - Math.cos(a) * 0.28;
            double hx = ex + Math.sin(a + 0.35) * 0.26, hz = ez - Math.cos(a + 0.35) * 0.26;
            add(parts, cos, sin, 0, side * 0.2, shoulderZ - 0.02, ex, side * 0.21, ez, 8.5, look.tunic());
            add(parts, cos, sin, ex, side * 0.21, ez, hx, side * 0.21, hz, 7.5, shade(look.tunic(), 0.92));
            add(parts, cos, sin, hx, side * 0.21, hz, hx + 0.01, side * 0.21, hz - 0.02, 7, look.skin());
        }
        // pescoço, cabeça e cabelo (ou capuz), que fica atrás do rosto
        add(parts, cos, sin, 0, 0, shoulderZ, 0, 0, headZ - 0.06, 8, look.skin());
        add(parts, cos, sin, 0.015, 0, headZ, 0.016, 0, headZ + 0.01, 17, look.skin());
        Color cover = look.hood() ? shade(look.tunic(), 0.8) : look.hair();
        add(parts, cos, sin, -0.035, 0, headZ + 0.03, -0.034, 0, headZ + 0.045, look.hood() ? 20 : 18, cover);
        if (look.hood()) add(parts, cos, sin, -0.02, 0, shoulderZ - 0.02, -0.05, 0, headZ - 0.03, 22, cover);

        if (look.scale() != 1.0) { // criança: o mesmo boneco, menor (a cabeça encolhe menos)
            double k = look.scale();
            parts.replaceAll(p -> new Part(p.x0() * k, p.y0() * k, p.z0() * k, p.x1() * k, p.y1() * k, p.z1() * k,
                    p.width() * (p.width() > 15 ? Math.sqrt(k) : k), p.color(), p.depth()));
        }
        paint(g, ox, oy, parts);
    }

    /**
     * Folha de um quadrúpede (o lobo do M6): o mesmo esquema de segmentos girados, com
     * corpo na horizontal, quatro patas em trote, cabeça baixa e rabo.
     */
    static BufferedImage quadrupedSheet(Color fur, Color belly) {
        BufferedImage img = image(FRAME_W * COLUMNS, FRAME_H * DIRECTIONS);
        Graphics2D g = graphics(img);
        for (int dir = 0; dir < DIRECTIONS; dir++) {
            double yaw = dir * Math.PI / 4;
            for (int col = 0; col < COLUMNS; col++) {
                boolean walking = col >= IDLE_FRAMES;
                double phase = walking ? (col - IDLE_FRAMES) * Math.PI * 2 / WALK_FRAMES : col * Math.PI * 2 / IDLE_FRAMES;
                drawQuadruped(g, col * FRAME_W + ANCHOR_X, dir * FRAME_H + ANCHOR_Y, fur, belly, yaw, walking, phase);
            }
        }
        g.dispose();
        return img;
    }

    private static void drawQuadruped(Graphics2D g, double ox, double oy, Color fur, Color belly, double yaw,
            boolean walking, double phase) {
        List<Part> parts = new ArrayList<>();
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        double bob = walking ? Math.abs(Math.sin(phase)) * 0.03 : Math.sin(phase) * 0.006;
        double backZ = 0.62 + bob;
        // patas: diagonais opostas juntas (trote)
        double[][] legs = {{0.30, 0.11, 1}, {0.30, -0.11, -1}, {-0.30, 0.11, -1}, {-0.30, -0.11, 1}};
        for (double[] l : legs) {
            double a = walking ? Math.sin(phase) * 0.45 * l[2] : 0;
            double kx = l[0] + Math.sin(a) * 0.28, kz = backZ - 0.12 - Math.cos(a) * 0.28;
            double fx = kx + Math.sin(a * 0.4) * 0.26, fz = Math.max(0.03, kz - 0.26);
            add(parts, cos, sin, l[0], l[1], backZ - 0.1, kx, l[1], kz, 8, shade(fur, 0.85));
            add(parts, cos, sin, kx, l[1], kz, fx, l[1], fz, 6, shade(fur, 0.75));
        }
        add(parts, cos, sin, -0.38, 0, backZ, 0.34, 0, backZ + 0.03, 22, fur);           // corpo
        add(parts, cos, sin, -0.25, 0, backZ - 0.08, 0.22, 0, backZ - 0.06, 14, belly); // peito e barriga
        add(parts, cos, sin, 0.34, 0, backZ + 0.03, 0.52, 0, backZ + 0.12, 13, fur);     // pescoço
        add(parts, cos, sin, 0.52, 0, backZ + 0.12, 0.6, 0, backZ + 0.1, 14, fur);       // cabeça
        add(parts, cos, sin, 0.6, 0, backZ + 0.08, 0.76, 0, backZ + 0.03, 7, shade(fur, 0.9)); // focinho
        for (int side = -1; side <= 1; side += 2)
            add(parts, cos, sin, 0.52, side * 0.05, backZ + 0.2, 0.5, side * 0.06, backZ + 0.28, 4, shade(fur, 0.7));
        double tail = walking ? Math.sin(phase * 2) * 0.05 : 0;
        add(parts, cos, sin, -0.4, 0, backZ, -0.68, tail, backZ - 0.14, 7, shade(fur, 0.8));
        paint(g, ox, oy, parts);
    }

    /** Desenha os segmentos de trás para a frente, com contorno e um brilho do lado da luz. */
    private static void paint(Graphics2D g, double ox, double oy, List<Part> parts) {
        parts.sort(Comparator.comparingDouble(Part::depth));
        for (Part p : parts) {
            double x0 = ox + sx(p.x0(), p.y0()), y0 = oy + sy(p.x0(), p.y0(), p.z0());
            double x1 = ox + sx(p.x1(), p.y1()), y1 = oy + sy(p.x1(), p.y1(), p.z1());
            stroke(g, x0, y0, x1, y1, p.width() + 2.4, OUTLINE);
            stroke(g, x0, y0, x1, y1, p.width(), p.color());
            double off = p.width() * 0.2;
            stroke(g, x0 - off, y0 - off * 0.6, x1 - off, y1 - off * 0.6, p.width() * 0.38,
                    alpha(shade(p.color(), 1.3), 0.55));
        }
    }

    /** Acrescenta um segmento, girando-o pela direção do personagem (frente = +x local). */
    private static void add(List<Part> parts, double cos, double sin, double x0, double y0, double z0,
            double x1, double y1, double z1, double width, Color color) {
        double ax = x0 * cos - y0 * sin, ay = x0 * sin + y0 * cos;
        double bx = x1 * cos - y1 * sin, by = x1 * sin + y1 * cos;
        double depth = (ax + ay + bx + by) / 2 + (z0 + z1) * 0.02;
        parts.add(new Part(ax, ay, z0, bx, by, z1, width, color, depth));
    }

    private static void stroke(Graphics2D g, double x0, double y0, double x1, double y1, double width, Color c) {
        g.setColor(c);
        if (Math.hypot(x1 - x0, y1 - y0) < 1.2) {
            g.fill(new Ellipse2D.Double(x0 - width / 2, y0 - width / 2, width, width));
            return;
        }
        g.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x0, y0, x1, y1));
    }
}
