package etrava.tools.art;

import static etrava.tools.art.ArtKit.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ícones dos itens (RF-200): desenhados num quadro de {@link #SIZE} px e reduzidos para a
 * paleta do jogo, como o resto da arte por código. Um desenho por item de {@code items.yaml};
 * item sem desenho aqui continua com o placeholder (e o aviso no log).
 */
final class ItemIcons {
    static final int SIZE = 80;

    private static final Color BREAD = new Color(0xB8975A), CRUST = new Color(0x7A5632), IRON = new Color(0x7A808A),
            IRON_DARK = new Color(0x3B4048), WOOD = new Color(0x7C5A40), LEATHER = new Color(0x8A4E2E),
            LINEN = new Color(0xD8CCB0), PAPER = new Color(0xEEE6D2), INK = new Color(0x3A3634),
            RED = new Color(0x9C2A2A), GREEN = new Color(0x5A6438), LIGHT_GREEN = new Color(0x868C52),
            CLAY = new Color(0xA85F38), AMBER = new Color(0xF09A3C), WOOL = new Color(0x565C66);

    private static final Map<String, Consumer<Graphics2D>> ICONS = new LinkedHashMap<>();

    static {
        ICONS.put("pao", g -> {
            oval(g, 12, 26, 56, 34, BREAD);
            arc(g, 14, 26, 52, 22, CRUST, 4);
            for (int i = 0; i < 3; i++) line(g, 28 + i * 12, 32, 22 + i * 12, 46, CRUST, 4);
        });
        ICONS.put("sopa", g -> {
            oval(g, 10, 30, 60, 36, WOOD);
            oval(g, 16, 26, 48, 20, CLAY);
            oval(g, 30, 30, 8, 5, LIGHT_GREEN);
            oval(g, 44, 34, 7, 5, BREAD);
        });
        ICONS.put("queijo", g -> {
            poly(g, new Color(0xD4B878), 10, 52, 62, 30, 70, 46, 70, 62, 10, 66);
            poly(g, new Color(0xE8D8A0), 10, 52, 62, 30, 70, 46);
            oval(g, 28, 54, 8, 7, BREAD);
            oval(g, 48, 52, 6, 6, BREAD);
        });
        ICONS.put("maca", g -> {
            oval(g, 16, 24, 48, 46, RED);
            oval(g, 24, 30, 12, 12, new Color(0xC47A48));
            line(g, 40, 26, 44, 14, CRUST, 4);
            oval(g, 44, 12, 16, 9, GREEN);
        });
        ICONS.put("biscoito", g -> {
            oval(g, 8, 34, 36, 30, CRUST);
            oval(g, 34, 20, 38, 32, BREAD);
            for (int[] p : new int[][] {{46, 30}, {58, 36}, {50, 42}, {20, 44}, {30, 52}}) oval(g, p[0], p[1], 5, 5, CRUST.darker());
        });
        ICONS.put("carne_salgada", g -> {
            poly(g, new Color(0x6E2C22), 14, 40, 30, 20, 60, 24, 68, 44, 50, 62, 22, 58);
            poly(g, new Color(0xA06C50), 26, 38, 36, 28, 54, 32, 56, 44, 44, 52, 30, 50);
            oval(g, 36, 36, 10, 9, LINEN);
        });
        ICONS.put("legumes", g -> {
            poly(g, new Color(0xC86428), 18, 34, 34, 28, 64, 64, 58, 68);
            line(g, 24, 30, 14, 14, GREEN, 5);
            line(g, 26, 30, 28, 10, LIGHT_GREEN, 5);
            oval(g, 40, 22, 26, 24, LINEN);
            line(g, 52, 22, 56, 10, GREEN, 5);
        });
        ICONS.put("cerveja", g -> {
            rect(g, 18, 26, 34, 42, WOOD, 6);
            arc(g, 46, 34, 22, 24, WOOD, 6);
            rect(g, 18, 34, 34, 5, IRON_DARK, 0);
            rect(g, 18, 56, 34, 5, IRON_DARK, 0);
            oval(g, 14, 14, 20, 16, PAPER);
            oval(g, 28, 12, 22, 18, PAPER);
        });
        ICONS.put("cha_ervas", g -> {
            rect(g, 18, 38, 36, 28, CLAY, 10);
            arc(g, 48, 42, 18, 18, CLAY, 5);
            oval(g, 20, 34, 32, 10, GREEN);
            curve(g, 30, 30, 24, 22, 32, 14, LINEN, 3);
            curve(g, 42, 30, 36, 20, 44, 10, LINEN, 3);
        });
        ICONS.put("unguento_sal", g -> {
            rect(g, 20, 30, 40, 38, CLAY, 10);
            rect(g, 18, 20, 44, 12, LINEN, 4);
            line(g, 20, 32, 60, 32, CRUST, 3);
            rect(g, 28, 44, 24, 12, PAPER, 2);
        });
        ICONS.put("sal_medicinal", g -> {
            sack(g, LINEN);
            oval(g, 26, 14, 28, 12, PAPER);
            for (int[] p : new int[][] {{30, 18}, {40, 16}, {46, 20}}) oval(g, p[0], p[1], 4, 4, new Color(0xC4BBA8));
        });
        ICONS.put("farinha", g -> {
            sack(g, new Color(0xB9A888));
            line(g, 30, 24, 50, 24, CRUST, 4);
        });
        ICONS.put("ferro", g -> {
            poly(g, IRON_DARK, 8, 46, 20, 60, 62, 60, 74, 46);
            poly(g, IRON, 8, 46, 20, 34, 62, 34, 74, 46);
            poly(g, new Color(0xA39A8A), 20, 34, 26, 30, 68, 30, 62, 34);
        });
        ICONS.put("pregos", g -> {
            for (int i = 0; i < 4; i++) {
                int x = 16 + i * 14;
                line(g, x, 22 + i * 3, x + 8, 64 - i * 2, IRON, 4);
                line(g, x - 5, 21 + i * 3, x + 6, 20 + i * 3, IRON_DARK, 5);
            }
        });
        ICONS.put("lenha", g -> {
            for (int[] p : new int[][] {{10, 44}, {38, 44}, {24, 24}}) {
                rect(g, p[0], p[1], 32, 20, WOOD, 8);
                oval(g, p[0] + 20, p[1], 14, 20, new Color(0xB8975A));
                oval(g, p[0] + 24, p[1] + 6, 6, 8, CRUST);
            }
        });
        ICONS.put("couro", g -> {
            poly(g, LEATHER, 16, 16, 30, 22, 50, 22, 64, 16, 58, 36, 66, 62, 50, 56, 40, 66, 30, 56, 14, 62, 22, 36);
            poly(g, new Color(0xA85F38), 28, 28, 52, 28, 50, 50, 30, 50);
        });
        ICONS.put("corda", g -> {
            for (int i = 0; i < 4; i++) ring(g, 14 + i * 2, 24 + i * 6, 52 - i * 4, 22, new Color(0x9C7A45), 6);
            line(g, 60, 40, 68, 64, new Color(0x9C7A45), 6);
        });
        ICONS.put("ervas", g -> {
            for (int i = -2; i <= 2; i++) {
                line(g, 40, 62, 40 + i * 9, 18 + Math.abs(i) * 4, GREEN, 4);
                oval(g, 34 + i * 9, 10 + Math.abs(i) * 4, 12, 16, LIGHT_GREEN);
            }
            rect(g, 32, 52, 16, 8, CRUST, 2);
        });
        ICONS.put("oleo", g -> {
            rect(g, 22, 30, 36, 40, new Color(0x44502E), 12);
            rect(g, 32, 14, 16, 20, new Color(0x44502E), 4);
            rect(g, 31, 8, 18, 9, WOOD, 3);
            rect(g, 28, 46, 24, 16, new Color(0xD4B878), 6);
        });
        ICONS.put("martelo", g -> {
            line(g, 22, 66, 50, 26, WOOD, 7);
            poly(g, IRON, 34, 14, 66, 30, 58, 42, 26, 26);
            poly(g, IRON_DARK, 26, 26, 34, 14, 40, 17, 32, 29);
        });
        ICONS.put("faca", g -> {
            poly(g, new Color(0xA39A8A), 30, 44, 66, 10, 70, 16, 40, 52);
            line(g, 14, 66, 32, 48, WOOD, 9);
            line(g, 26, 40, 42, 56, IRON_DARK, 4);
        });
        ICONS.put("machadinha", g -> {
            line(g, 18, 68, 52, 18, WOOD, 7);
            poly(g, IRON, 44, 10, 70, 18, 68, 44, 50, 34, 40, 22);
            poly(g, new Color(0xA39A8A), 62, 16, 70, 18, 68, 44, 60, 40);
        });
        ICONS.put("cajado", g -> {
            line(g, 14, 70, 58, 14, WOOD, 7);
            line(g, 58, 14, 66, 12, CRUST, 9);
            line(g, 30, 50, 34, 44, CRUST, 9);
        });
        ICONS.put("lanca_curta", g -> {
            line(g, 12, 70, 54, 22, WOOD, 6);
            poly(g, IRON, 48, 20, 70, 8, 60, 30);
            line(g, 46, 30, 54, 22, LEATHER, 8);
        });
        ICONS.put("lamparina", g -> {
            ring(g, 30, 6, 20, 16, IRON_DARK, 4);
            rect(g, 24, 18, 32, 8, IRON_DARK, 3);
            rect(g, 26, 26, 28, 34, new Color(0xFFC864), 4);
            oval(g, 34, 34, 12, 20, AMBER);
            rect(g, 22, 58, 36, 10, IRON_DARK, 3);
            line(g, 26, 26, 26, 60, IRON_DARK, 3);
            line(g, 54, 26, 54, 60, IRON_DARK, 3);
        });
        ICONS.put("camisa_linho", g -> shirt(g, LINEN, new Color(0xB9A888), false));
        ICONS.put("gibao_couro", g -> {
            shirt(g, LEATHER, new Color(0x5A3F26), true);
            for (int i = 0; i < 3; i++) oval(g, 37, 30 + i * 11, 6, 6, new Color(0xD4B878));
        });
        ICONS.put("avental_couro", g -> {
            poly(g, new Color(0x7A5632), 28, 14, 52, 14, 56, 30, 64, 68, 16, 68, 24, 30);
            line(g, 28, 14, 20, 6, CRUST, 4);
            line(g, 52, 14, 60, 6, CRUST, 4);
            rect(g, 30, 42, 20, 14, new Color(0x5A3F26), 3);
        });
        ICONS.put("chapeu_feltro", g -> {
            oval(g, 6, 40, 68, 24, new Color(0x4A3526));
            poly(g, new Color(0x624632), 22, 50, 26, 22, 54, 22, 58, 50);
            oval(g, 26, 16, 28, 14, new Color(0x624632));
            line(g, 23, 44, 57, 44, RED, 5);
        });
        ICONS.put("capuz_la", g -> {
            poly(g, WOOL, 40, 8, 62, 28, 66, 66, 14, 66, 18, 28);
            oval(g, 26, 26, 28, 34, new Color(0x1E1A22));
            line(g, 22, 62, 58, 62, new Color(0x3B4048), 5);
        });
        ICONS.put("bolsa_cinto", g -> {
            line(g, 8, 22, 72, 22, CRUST, 7);
            rect(g, 22, 22, 36, 44, LEATHER, 12);
            poly(g, new Color(0x5A3F26), 22, 24, 58, 24, 54, 44, 40, 50, 26, 44);
            oval(g, 36, 42, 8, 8, new Color(0xD4B878));
        });
        ICONS.put("pele_lobo", g -> {
            poly(g, new Color(0x6B645A), 20, 10, 32, 18, 48, 18, 60, 10, 56, 30, 70, 40, 56, 46, 60, 70, 46, 60, 40, 72,
                    34, 60, 20, 70, 24, 46, 10, 40, 24, 30);
            poly(g, new Color(0x857D70), 32, 24, 48, 24, 50, 52, 30, 52);
        });
        ICONS.put("lasca_fibra", g -> {
            poly(g, new Color(0x1E1A22), 18, 62, 30, 14, 52, 22, 64, 48, 44, 68);
            line(g, 32, 22, 26, 56, new Color(0x3B4048), 3);
            line(g, 42, 26, 40, 60, new Color(0x3B4048), 3);
            line(g, 50, 32, 52, 54, new Color(0x565C66), 3);
        });
        ICONS.put("placa_simbolos", g -> {
            rect(g, 12, 18, 56, 46, new Color(0x3B4048), 4);
            rect(g, 16, 22, 48, 38, new Color(0x565C66), 2);
            for (int r = 0; r < 3; r++)
                for (int c = 0; c < 3; c++) line(g, 24 + c * 13, 28 + r * 10, 24 + c * 13, 34 + r * 10, new Color(0x1E1A22), 3);
        });
        ICONS.put("papeis_avel", g -> {
            sheetOf(g, 20, 14, PAPER.darker());
            sheetOf(g, 14, 18, PAPER);
            writing(g, 20, 28, 5);
            rect(g, 20, 24, 18, 5, new Color(0x857D70), 0); // o nome raspado
        });
        ICONS.put("carta_iria", g -> {
            poly(g, PAPER, 10, 26, 70, 26, 70, 62, 10, 62);
            poly(g, new Color(0xC4BBA8), 10, 26, 40, 48, 70, 26);
            outlinePoly(g, 10, 26, 40, 48, 70, 26);
            oval(g, 34, 40, 12, 12, RED);
        });
        ICONS.put("esboco_simbolos", g -> {
            sheetOf(g, 16, 14, PAPER);
            for (int r = 0; r < 3; r++)
                for (int c = 0; c < 3; c++) line(g, 28 + c * 10, 26 + r * 12, 28 + c * 10, 32 + r * 12, INK, 3);
        });
        ICONS.put("bilhete_avel", g -> {
            poly(g, PAPER, 16, 28, 62, 22, 66, 54, 20, 60);
            line(g, 24, 36, 56, 32, INK, 3);
            line(g, 26, 46, 48, 43, INK, 3);
        });
        ICONS.put("edital_caldris", g -> {
            sheetOf(g, 16, 10, PAPER);
            rect(g, 24, 18, 32, 6, INK, 0);
            writing(g, 22, 30, 3);
            oval(g, 44, 52, 16, 16, RED);
            line(g, 16, 10, 64, 10, WOOD, 5);
        });
        ICONS.put("tracado_agrimensora", g -> {
            sheetOf(g, 12, 16, new Color(0xD8CCB0));
            line(g, 18, 56, 34, 30, RED, 3);
            line(g, 34, 30, 62, 24, RED, 3);
            line(g, 18, 34, 60, 58, INK, 2);
            for (int[] p : new int[][] {{18, 56}, {34, 30}, {62, 24}}) oval(g, p[0] - 3, p[1] - 3, 7, 7, INK);
        });
        ICONS.put("lista_oren", g -> {
            sheetOf(g, 22, 10, PAPER);
            writing(g, 28, 20, 6);
            line(g, 26, 36, 50, 36, RED, 3); // o nome riscado
        });
        ICONS.put("canto_carta", g -> {
            poly(g, PAPER, 18, 62, 18, 30, 40, 22, 62, 62);
            poly(g, new Color(0x2A2428), 18, 30, 26, 24, 34, 28, 40, 22, 46, 32, 40, 34, 30, 34, 22, 38);
            line(g, 24, 48, 46, 48, INK, 3);
            line(g, 24, 56, 52, 56, INK, 3);
        });
        ICONS.put("livro_enterros", g -> {
            rect(g, 16, 14, 48, 56, new Color(0x4A1E1A), 4);
            rect(g, 22, 14, 42, 52, PAPER, 2);
            rect(g, 16, 14, 44, 50, new Color(0x6E2C22), 4);
            line(g, 24, 16, 24, 62, new Color(0x4A1E1A), 4);
            line(g, 40, 28, 40, 48, new Color(0xD4B878), 3);
            line(g, 33, 34, 47, 34, new Color(0xD4B878), 3);
        });
    }

    private ItemIcons() {}

    static Map<String, Consumer<Graphics2D>> all() {
        return ICONS;
    }

    static BufferedImage draw(Consumer<Graphics2D> icon) {
        BufferedImage img = image(SIZE, SIZE);
        Graphics2D g = graphics(img);
        icon.accept(g);
        g.dispose();
        return img;
    }

    // ---------------------------------------------------------------- peças

    private static void oval(Graphics2D g, double x, double y, double w, double h, Color c) {
        g.setColor(c);
        g.fill(new Ellipse2D.Double(x, y, w, h));
    }

    private static void rect(Graphics2D g, double x, double y, double w, double h, Color c, double round) {
        g.setColor(c);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, round, round));
    }

    private static void line(Graphics2D g, double x0, double y0, double x1, double y1, Color c, double width) {
        g.setColor(c);
        g.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x0, y0, x1, y1));
    }

    private static void ring(Graphics2D g, double x, double y, double w, double h, Color c, double width) {
        g.setColor(c);
        g.setStroke(new BasicStroke((float) width));
        g.draw(new Ellipse2D.Double(x, y, w, h));
    }

    /** Meia-volta do lado direito (asa de caneca) ou de cima (risco no pão). */
    private static void arc(Graphics2D g, double x, double y, double w, double h, Color c, double width) {
        g.setColor(c);
        g.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(x, y, w, h, w > h * 1.5 ? 20 : -90, w > h * 1.5 ? 140 : 180, Arc2D.OPEN));
    }

    private static void curve(Graphics2D g, double x0, double y0, double cx, double cy, double x1, double y1, Color c,
            double width) {
        Path2D p = new Path2D.Double();
        p.moveTo(x0, y0);
        p.quadTo(cx, cy, x1, y1);
        g.setColor(c);
        g.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(p);
    }

    private static Path2D path(double... pts) {
        Path2D p = new Path2D.Double();
        p.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) p.lineTo(pts[i], pts[i + 1]);
        p.closePath();
        return p;
    }

    private static void poly(Graphics2D g, Color c, double... pts) {
        g.setColor(c);
        g.fill(path(pts));
    }

    private static void outlinePoly(Graphics2D g, double... pts) {
        g.setColor(new Color(0x857D70));
        g.setStroke(new BasicStroke(3f));
        g.draw(path(pts));
    }

    private static void sack(Graphics2D g, Color cloth) {
        poly(g, cloth, 22, 26, 58, 26, 66, 60, 56, 70, 24, 70, 14, 60);
        poly(g, shade(cloth, 0.85), 28, 12, 52, 12, 56, 26, 24, 26);
        poly(g, shade(cloth, 0.8), 50, 30, 58, 28, 64, 60, 56, 68);
    }

    private static void shirt(Graphics2D g, Color cloth, Color dark, boolean sleeveless) {
        if (!sleeveless) {
            poly(g, shade(cloth, 0.9), 22, 16, 6, 34, 16, 44, 26, 34);
            poly(g, shade(cloth, 0.9), 58, 16, 74, 34, 64, 44, 54, 34);
        }
        poly(g, cloth, 22, 14, 58, 14, 60, 68, 20, 68);
        poly(g, dark, 32, 14, 48, 14, 40, 26);
        line(g, 20, 66, 60, 66, dark, 4);
    }

    private static void sheetOf(Graphics2D g, double x, double y, Color paper) {
        rect(g, x, y, 80 - 2 * x, 80 - y - 10, paper, 2);
    }

    private static void writing(Graphics2D g, double x, double y, int lines) {
        for (int i = 0; i < lines; i++) line(g, x, y + i * 7, 80 - x - (i % 2 == 0 ? 0 : 8), y + i * 7, INK, 2.4);
    }
}
