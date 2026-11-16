package etrava.tools.art.ui;

import static etrava.tools.art.ui.K.*;

import etrava.tools.art.ui.Pecas.Peca;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prancha de revisão do tema: uma montagem a 2× (painel de exemplo com as peças esticadas, para provar que
 * os 9-patches não mostram emenda) e, embaixo, o catálogo com cada peça a 1× e a 3× com o ID.
 */
final class Prancha {
    private final Map<String, Peca> por = new LinkedHashMap<>();

    Prancha(List<Peca> pecas) {
        for (Peca p : pecas) por.put(p.id(), p);
    }

    /** Cena em 1× lógico, pintada pixel a pixel (sem suavização). */
    private static final class Cena {
        final BufferedImage img;
        final Graphics2D g;

        Cena(int w, int h) {
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) img.setRGB(x, y, ((x / 8) + (y / 8)) % 2 == 0 ? G3 : Q2);   // grade para ver transparências
        }

        void pix(Pix p, int x, int y) {
            for (int j = 0; j < p.h; j++)
                for (int i = 0; i < p.w; i++) {
                    int c = p.p[j * p.w + i];
                    if (c != 0 && x + i >= 0 && y + j >= 0 && x + i < img.getWidth() && y + j < img.getHeight()) img.setRGB(x + i, y + j, c);
                }
        }

        void texto(String s, int x, int y, int cor, int tam, boolean negrito) {
            g.setFont(new Font(Font.SERIF, negrito ? Font.BOLD : Font.PLAIN, tam));
            g.setColor(new Color(cor, true));
            g.drawString(s, x, y);
        }

        void centro(String s, int cx, int y, int cor, int tam, boolean negrito) {
            g.setFont(new Font(Font.SERIF, negrito ? Font.BOLD : Font.PLAIN, tam));
            int w = g.getFontMetrics().stringWidth(s);
            texto(s, cx - w / 2, y, cor, tam, negrito);
        }

        BufferedImage ampliar(int k) {
            BufferedImage o = new BufferedImage(img.getWidth() * k, img.getHeight() * k, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < o.getHeight(); y++)
                for (int x = 0; x < o.getWidth(); x++) o.setRGB(x, y, img.getRGB(x / k, y / k));
            return o;
        }
    }

    private Pix nove(String id, int w, int h) {
        Peca p = por.get(id);
        return p.img().esticar(p.split(), w, h);
    }

    private Pix img(String id) { return por.get(id).img(); }

    /** Ladrilha a trama no retângulo, como o cliente fará por baixo do texto. */
    private static void ladrilhar(Cena c, Pix t, int x0, int y0, int w, int h) {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) c.img.setRGB(x0 + x, y0 + y, t.p[(y % t.h) * t.w + x % t.w]);
    }

    private static int fator(Peca p) { return p.img().w > 64 || p.img().h > 64 ? 1 : 3; }

    BufferedImage gerar() {
        BufferedImage m = montagem().ampliar(2);
        int largura = m.getWidth();
        int x = 10, y = 0, linha = 0;
        List<int[]> pos = new ArrayList<>();
        for (Peca p : por.values()) {
            int cw = Math.max(p.img().w * (fator(p) == 3 ? 4 : 1) + 24, 190);
            if (x + cw > largura) {
                x = 10;
                y += linha + 28;
                linha = 0;
            }
            pos.add(new int[] {x, y});
            linha = Math.max(linha, p.img().h * fator(p));
            x += cw;
        }
        int altCat = y + linha + 50;
        BufferedImage out = new BufferedImage(largura, m.getHeight() + altCat + 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(Q2, true));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(m, 0, 0, null);
        int base = m.getHeight() + 30;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int i = 0;
        for (Peca p : por.values()) {
            int[] q = pos.get(i++);
            g.setColor(new Color(G8, true));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g.drawString(p.id(), q[0], base + q[1] + 11);
            g.drawString(p.img().w + "x" + p.img().h + (p.split() != null ? " split " + Arrays.toString(p.split()) : ""), q[0], base + q[1] + 23);
            g.drawImage(p.img().imagem(), q[0], base + q[1] + 30, null);
            if (fator(p) == 3) g.drawImage(p.img().imagem(), q[0] + p.img().w + 8, base + q[1] + 30, p.img().w * 3, p.img().h * 3, null);
        }
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------ montagem

    private static final int TINTA = 0xFF241C18, CLARA = 0xFFEEE6D2;

    private Cena montagem() {
        Cena c = new Cena(790, 340);
        // painel principal
        c.pix(nove("ui.painel_madeira", 420, 280), 10, 10);
        ladrilhar(c, img("ui.papel_trama"), 10 + 14, 10 + 14, 420 - 28, 280 - 28);   // trama forrando o miolo
        c.centro("Mochila de Avel", 220, 38, TINTA, 14, true);
        c.pix(nove("ui.divisoria", 372, 5), 34, 44);
        // lista com rolagem
        c.pix(nove("ui.pergaminho_escuro", 130, 100), 34, 56);
        ladrilhar(c, img("ui.papel_trama_escuro"), 34 + 3, 56 + 3, 130 - 6, 100 - 6);
        String[] itens = {"Pão duro", "Faca de cozinha", "Corda curta", "Camisa de linho", "Odre de água", "Isqueiro"};
        for (int i = 0; i < 6; i++) c.texto(itens[i], 42, 72 + i * 15, i == 1 ? 0xFF6E2C22 : TINTA, 11, false);
        c.pix(nove("ui.foco", 118, 17), 40, 74);
        c.pix(nove("ui.rolagem_trilho", 10, 100), 170, 56);
        c.pix(nove("ui.rolagem_pegador", 10, 40), 170, 58);
        // botões nos quatro estados
        String[] estados = {"botao", "botao_sobre", "botao_press", "botao_desativado"};
        String[] rot = {"Vender", "Comprar", "Fechar"};
        for (int r = 0; r < 3; r++)
            for (int e = 0; e < 4; e++) {
                int bx = 196 + e * 56, by = 56 + r * 28;
                c.pix(nove("ui." + estados[e], 52, 24), bx, by);
                c.centro(rot[r], bx + 26, by + 16 + (e == 2 ? 1 : 0), e == 3 ? 0xFF857D70 : CLARA, 10, false);
            }
        // abas
        c.pix(nove("ui.aba", 90, 22), 196, 144);
        c.pix(nove("ui.aba_ativa", 90, 22), 292, 144);
        c.centro("pessoas", 196 + 38, 159, CLARA, 10, false);
        c.centro("lugares", 292 + 38, 159, CLARA, 10, false);
        // retrato, encaixes, tooltip
        Pix pf = new Pix(48, 56);
        pf.rect(0, 0, 47, 55, G5);
        pf.rect(14, 10, 33, 36, PELE3);
        pf.rect(6, 36, 41, 55, M3);
        c.pix(pf, 34 + 8, 170 + 8);
        c.pix(nove("ui.moldura_retrato", 64, 72), 34, 166);
        for (int i = 0; i < 5; i++) {
            int ex = 108 + i * 42;
            c.pix(img("ui.encaixe"), ex, 170);
            if (i == 1 || i == 2) {
                Pix ic = new Pix(32, 32);
                ic.rect(6, 6, 25, 25, i == 1 ? F3 : T5);
                c.pix(ic, ex + 4, 174);
                c.pix(img(i == 1 ? "ui.moldura_icone_sel" : "ui.moldura_icone"), ex, 170);
            }
        }
        c.pix(nove("ui.dica", 150, 30), 108, 218);
        c.texto("Faca de Avel · 3 moedas", 116, 237, CLARA, 10, false);
        // barras e opções
        c.pix(nove("ui.barra_fundo", 120, 12), 34, 246);
        c.pix(nove("ui.barra_vida", 76, 6), 37, 249);
        c.pix(nove("ui.barra_fundo", 120, 12), 34, 262);
        c.pix(nove("ui.barra_folego", 100, 6), 37, 265);
        c.pix(img("ui.caixa"), 290, 224);
        c.pix(img("ui.caixa_marcada"), 290, 244);
        c.texto("Sons", 308, 235, TINTA, 11, false);
        c.texto("Música", 308, 255, TINTA, 11, false);
        c.pix(nove("ui.deslizante_trilho", 90, 6), 290, 270);
        c.pix(img("ui.deslizante_pino"), 330, 266);
        // peças soltas, fora do painel
        int x0 = 450;
        c.pix(nove("ui.livro_esq", 140, 190), x0, 10);
        c.pix(nove("ui.livro_dir", 140, 190), x0 + 140, 10);
        c.centro("Caderno", x0 + 70, 34, TINTA, 11, true);
        c.pix(nove("ui.divisoria", 100, 5), x0 + 20, 40);
        c.texto("Avel, ferreiro.", x0 + 20, 56, TINTA, 11, false);
        c.texto("Fala pouco, bate muito.", x0 + 20, 70, TINTA, 10, false);
        c.pix(nove("ui.aba_ativa", 56, 22), x0 + 284, 30);
        c.pix(nove("ui.aba", 56, 22), x0 + 284, 56);
        c.pix(nove("ui.documento", 96, 110), x0 + 4, 206);
        c.texto("Recibo", x0 + 24, 232, TINTA, 11, true);
        c.pix(nove("ui.pergaminho", 100, 70), x0 + 110, 206);
        c.texto("Pergaminho", x0 + 124, 246, TINTA, 11, false);
        c.pix(nove("ui.couro", 70, 40), x0 + 220, 206);
        c.pix(img("ui.sol"), x0 + 226, 217);
        c.pix(img("ui.lua"), x0 + 244, 217);
        c.pix(img("ui.bolsa"), x0 + 262, 217);
        c.pix(nove("ui.bilhete", 86, 36), x0 + 220, 254);
        c.texto("Fome", x0 + 236, 276, TINTA, 10, false);
        c.pix(nove("ui.balao", 90, 34), x0 + 110, 290);
        c.texto("Boa tarde.", x0 + 120, 306, TINTA, 10, false);
        c.pix(nove("ui.divisoria_v", 5, 60), x0 + 316, 250);
        // cursores
        String[] cur = {"apontar", "falar", "entrar", "pegar", "atacar", "esperar"};
        for (int i = 0; i < 6; i++) c.pix(img("ui.cursor_" + cur[i]), 14 + i * 40, 300);
        return c;
    }
}
