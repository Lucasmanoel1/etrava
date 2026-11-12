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
    /** O lobo de lado é mais comprido que um quadro de gente: a folha dele tem quadros mais largos. */
    static final int QUAD_FRAME_W = 176, QUAD_ANCHOR_X = 88;
    static final int IDLE_FRAMES = 4, WALK_FRAMES = 8, ATTACK_FRAMES = 4;
    static final int DIRECTIONS = 8, COLUMNS = IDLE_FRAMES + WALK_FRAMES + ATTACK_FRAMES;
    /** Golpe em 4 quadros: recua, ergue, desce, termina. Ângulo do braço que bate e avanço do tronco. */
    private static final double[] STRIKE_ARM = {-0.7, 2.7, 1.35, 0.75}, STRIKE_LEAN = {-0.04, -0.02, 0.13, 0.07};

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
                int attack = col - IDLE_FRAMES - WALK_FRAMES;
                boolean walking = col >= IDLE_FRAMES && attack < 0;
                double phase = walking
                        ? (col - IDLE_FRAMES) * Math.PI * 2 / WALK_FRAMES
                        : attack >= 0 ? 0 : col * Math.PI * 2 / IDLE_FRAMES;
                drawFrame(g, col * FRAME_W + ANCHOR_X, dir * FRAME_H + ANCHOR_Y, look, yaw, walking, phase, attack);
            }
        }
        g.dispose();
        return img;
    }

    private static void drawFrame(Graphics2D g, double ox, double oy, Look look, double yaw, boolean walking,
            double phase, int attack) {
        List<Part> parts = new ArrayList<>();
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        double swing = walking ? Math.sin(phase) * 0.40 : 0;
        double bob = walking ? Math.abs(Math.cos(phase)) * 0.025 : Math.sin(phase) * 0.008;
        double lean = attack >= 0 ? STRIKE_LEAN[attack] : 0; // o tronco vai junto com o golpe
        double hipZ = 0.90 + bob - Math.abs(lean) * 0.25, shoulderZ = hipZ + 0.50, headZ = hipZ + 0.70;

        // pernas: coxa e canela; a perna que vai para trás dobra o joelho
        for (int side = -1; side <= 1; side += 2) {
            double a = attack >= 0 ? lean * 2.6 * side : swing * side; // no golpe, um pé à frente
            double bend = walking ? Math.max(0, -Math.cos(phase) * side) * 0.55 + 0.08 : 0.03;
            double kx = Math.sin(a) * 0.45, kz = hipZ - Math.cos(a) * 0.45;
            double fx = kx + Math.sin(a - bend) * 0.45, fz = Math.max(0.04, kz - Math.cos(a - bend) * 0.45);
            add(parts, cos, sin, 0, side * 0.09, hipZ, kx, side * 0.09, kz, 11, look.pants());
            add(parts, cos, sin, kx, side * 0.09, kz, fx, side * 0.09, fz, 9.5, look.pants());
            add(parts, cos, sin, fx - 0.02, side * 0.09, fz, fx + 0.13, side * 0.09, Math.max(0.03, fz - 0.03), 8,
                    look.boots());
        }
        // tronco e barra da túnica
        add(parts, cos, sin, 0, 0, hipZ + 0.04, lean, 0, shoulderZ - 0.04, 25, look.tunic());
        add(parts, cos, sin, 0, 0, hipZ - look.skirt(), 0, 0, hipZ + 0.06, 24, shade(look.tunic(), 0.88));
        // braços: balançam ao contrário das pernas
        for (int side = -1; side <= 1; side += 2) {
            boolean striking = attack >= 0 && side == 1; // bate com o braço direito; o outro equilibra
            double a = striking ? STRIKE_ARM[attack] : attack >= 0 ? -lean * 3 : -swing * side * 0.8;
            double fore = striking ? (attack == 1 ? -0.5 : 0.15) : 0.35; // cotovelo: dobrado no alto, esticado no golpe
            double ex = lean + Math.sin(a) * 0.28, ez = shoulderZ - Math.cos(a) * 0.28;
            double hx = ex + Math.sin(a + fore) * 0.26, hz = ez - Math.cos(a + fore) * 0.26;
            add(parts, cos, sin, lean, side * 0.2, shoulderZ - 0.02, ex, side * 0.21, ez, 8.5, look.tunic());
            add(parts, cos, sin, ex, side * 0.21, ez, hx, side * 0.21, hz, 7.5, shade(look.tunic(), 0.92));
            add(parts, cos, sin, hx, side * 0.21, hz, hx + 0.01, side * 0.21, hz - 0.02, 7, look.skin());
        }
        // pescoço, cabeça e cabelo (ou capuz), que fica atrás do rosto
        double hl = lean * 1.2;
        add(parts, cos, sin, lean, 0, shoulderZ, hl, 0, headZ - 0.06, 8, look.skin());
        add(parts, cos, sin, hl + 0.015, 0, headZ, hl + 0.016, 0, headZ + 0.01, 17, look.skin());
        Color cover = look.hood() ? shade(look.tunic(), 0.8) : look.hair();
        add(parts, cos, sin, hl - 0.035, 0, headZ + 0.03, hl - 0.034, 0, headZ + 0.045, look.hood() ? 20 : 18, cover);
        if (look.hood()) add(parts, cos, sin, lean - 0.02, 0, shoulderZ - 0.02, hl - 0.05, 0, headZ - 0.03, 22, cover);

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
        BufferedImage img = image(QUAD_FRAME_W * COLUMNS, FRAME_H * DIRECTIONS);
        Graphics2D g = graphics(img);
        for (int dir = 0; dir < DIRECTIONS; dir++) {
            double yaw = dir * Math.PI / 4;
            for (int col = 0; col < COLUMNS; col++) {
                int attack = col - IDLE_FRAMES - WALK_FRAMES;
                boolean walking = col >= IDLE_FRAMES && attack < 0;
                double phase = walking ? (col - IDLE_FRAMES) * Math.PI * 2 / WALK_FRAMES
                        : attack >= 0 ? 0 : col * Math.PI * 2 / IDLE_FRAMES;
                drawQuadruped(g, col * QUAD_FRAME_W + QUAD_ANCHOR_X, dir * FRAME_H + ANCHOR_Y, fur, belly, yaw, walking, phase,
                        attack);
            }
        }
        g.dispose();
        return img;
    }

    /** Bote do lobo em 4 quadros: encolhe, encolhe mais, salta, cai. Avanço e altura do corpo. */
    private static final double[] LUNGE_X = {-0.06, -0.10, 0.20, 0.10}, LUNGE_Z = {-0.07, -0.11, 0.10, 0.0};

    private static void drawQuadruped(Graphics2D g, double ox, double oy, Color fur, Color belly, double yaw,
            boolean walking, double phase, int attack) {
        List<Part> parts = new ArrayList<>();
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        double bob = walking ? Math.abs(Math.sin(phase)) * 0.03 : Math.sin(phase) * 0.006;
        double lx = attack >= 0 ? LUNGE_X[attack] : 0, lz = attack >= 0 ? LUNGE_Z[attack] : 0;
        double backZ = 0.62 + bob + lz;
        boolean leap = attack == 2;
        // patas: diagonais opostas juntas (trote); no bote, as da frente se esticam e as de trás empurram
        double[][] legs = {{0.30, 0.13, 1}, {0.30, -0.13, -1}, {-0.30, 0.10, -1}, {-0.30, -0.10, 1}};
        for (double[] l : legs) {
            boolean front = l[0] > 0;
            double a = walking ? Math.sin(phase) * 0.45 * l[2] : leap ? (front ? 0.9 : -0.7) : 0;
            double len = attack >= 0 && !leap ? 0.22 + lz * 0.4 : 0.28; // encolhido: pernas dobradas
            double kx = lx + l[0] + Math.sin(a) * len, kz = backZ - 0.12 - Math.cos(a) * len;
            double fx = kx + Math.sin(a * 0.4) * 0.26, fz = leap ? Math.max(0.03, kz - 0.20) : 0.03;
            add(parts, cos, sin, lx + l[0], l[1], backZ - 0.1, kx, l[1], kz, 8, shade(fur, 0.85));
            add(parts, cos, sin, kx, l[1], kz, fx, l[1], fz, 6, shade(fur, 0.72));
        }
        // corpo em dois volumes (garupa mais escura e estreita, peito largo): de frente e de costas não vira uma bola
        add(parts, cos, sin, lx - 0.38, 0, backZ - 0.02, lx - 0.05, 0, backZ + 0.01, 19, shade(fur, 0.86));
        add(parts, cos, sin, lx - 0.05, 0, backZ + 0.01, lx + 0.32, 0, backZ + 0.04, 23, fur);
        add(parts, cos, sin, lx - 0.25, 0, backZ - 0.09, lx + 0.24, 0, backZ - 0.07, 13, belly);
        // pescoço para a frente e cabeça baixa, de quem espreita; no bote, a cabeça sobe
        double headZ = backZ + (leap ? 0.14 : -0.03), hx = lx + 0.56;
        add(parts, cos, sin, lx + 0.32, 0, backZ + 0.05, hx - 0.06, 0, headZ + 0.02, 13, shade(fur, 0.95));
        add(parts, cos, sin, hx - 0.03, 0, headZ, hx + 0.04, 0, headZ - 0.01, 15, shade(fur, 1.08));
        add(parts, cos, sin, hx + 0.06, 0, headZ - 0.04, hx + 0.20, 0, headZ - 0.08, 7.5, belly); // focinho claro
        add(parts, cos, sin, hx + 0.20, 0, headZ - 0.07, hx + 0.21, 0, headZ - 0.075, 4, OUTLINE); // nariz
        for (int side = -1; side <= 1; side += 2) {
            add(parts, cos, sin, hx - 0.05, side * 0.065, headZ + 0.07, hx - 0.08, side * 0.085, headZ + 0.17, 5,
                    shade(fur, 0.62)); // orelhas
            add(parts, cos, sin, hx + 0.085, side * 0.05, headZ + 0.02, hx + 0.09, side * 0.05, headZ + 0.021, 2.6,
                    new Color(0xF09A3C)); // olhos: o único brilho do bicho
        }
        double tail = walking ? Math.sin(phase * 2) * 0.05 : 0;
        add(parts, cos, sin, lx - 0.38, 0, backZ, lx - 0.52, tail, backZ + 0.02, 8, shade(fur, 0.8));
        add(parts, cos, sin, lx - 0.52, tail, backZ + 0.02, lx - 0.70, tail * 2, backZ - 0.16, 7, shade(fur, 0.7));
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
