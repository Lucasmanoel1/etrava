package etrava.client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.fasterxml.jackson.databind.JsonNode;
import etrava.sim.content.PropPlacement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feixe de luz do sol pelas janelas dos interiores (V6, SPEC-visual §7.4): uma mancha no chão (somada
 * ao buffer de luz), a coluna de luz no ar (aditiva, discreta) e poucos grãos de poeira que descem
 * devagar dentro dela. A janela é um prop da parede ({@code parede_x_janela}, {@code parede_y_janela});
 * a luz entra pelo lado do centro do cômodo. A força vem da hora (nada à noite). Parâmetros em
 * {@code assets/config/luz_viva.yaml}, chave {@code feixe}. Sem estado: tudo é função da hora e do
 * tempo, então as capturas se repetem.
 */
final class FeixeJanela {
    /** Uma janela: centro na parede, eixo da parede ('x' ou 'y') e sentido da luz (+1 ou -1). */
    record Janela(double x, double y, char eixo, int sentido) {}

    /** Quanto a parede tem de espessura para dentro do centro do prop (m). */
    private static final double PAREDE = 0.13;

    private final Map<String, Character> props = new HashMap<>();
    private final double nascer, por, largura, base, topo, piso, ar, deslocamento;
    private final float[] cor = {1f, 0.88f, 0.58f};
    private final int poeira, poeiraTeto;
    private final List<double[]> pendentes = new ArrayList<>();
    private final List<Janela> janelas = new ArrayList<>();
    private final float[] v = new float[20];

    FeixeJanela(JsonNode c) {
        c.path("janelas").fields().forEachRemaining(f -> props.put(f.getKey(), f.getValue().asText("x").charAt(0)));
        nascer = c.path("nascer").asDouble(6.5);
        por = c.path("por").asDouble(18.5);
        largura = c.path("largura").asDouble(0.8);
        base = c.path("base").asDouble(0.9);
        topo = c.path("topo").asDouble(2.0);
        piso = c.path("piso").asDouble(0.34);
        ar = c.path("ar").asDouble(0.085);
        deslocamento = c.path("deslocamento").asDouble(0.9);
        poeira = c.path("poeira").asInt(6);
        poeiraTeto = c.path("poeira_teto").asInt(24);
        for (int i = 0; i < 3 && i < c.path("cor").size(); i++) cor[i] = (float) c.path("cor").get(i).asDouble(cor[i]);
    }

    /** Guarda a janela se o prop for uma; chamado para cada prop ao montar a cena. */
    void registrar(String propId, double x, double y) {
        Character e = props.get(propId);
        if (e != null) pendentes.add(new double[] {x, y, e});
    }

    /** Depois de todos os props: decide o lado de dentro de cada janela pelo centro do cômodo. */
    void fechar(List<PropPlacement> todos) {
        janelas.clear();
        if (pendentes.isEmpty() || todos.isEmpty()) return;
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
        for (PropPlacement p : todos) {
            x0 = Math.min(x0, p.x());
            x1 = Math.max(x1, p.x());
            y0 = Math.min(y0, p.y());
            y1 = Math.max(y1, p.y());
        }
        double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        for (double[] w : pendentes) {
            char eixo = (char) w[2];
            int s = eixo == 'x' ? (cy >= w[1] ? 1 : -1) : (cx >= w[0] ? 1 : -1);
            janelas.add(new Janela(w[0], w[1], eixo, s));
        }
    }

    int janelas() {
        return janelas.size();
    }

    /** Força do sol pela hora, de 0 a 1: sobe do nascer, pico ao meio-dia, zero depois do pôr do sol. */
    static double intensidade(double hora, double nascer, double por) {
        if (hora <= nascer || hora >= por) return 0;
        return Math.pow(Math.sin(Math.PI * (hora - nascer) / (por - nascer)), 0.6);
    }

    double intensidade(double hora) {
        return intensidade(hora, nascer, por);
    }

    /**
     * Mancha do sol no chão para a hora, em metros do mundo: {@code {x, y}} de perto-esquerda,
     * perto-direita, longe-direita e longe-esquerda. De manhã e à tarde o sol é baixo (a mancha
     * vai mais longe) e desliza ao longo da parede.
     */
    double[][] mancha(Janela j, double hora) {
        double meio = (nascer + por) / 2, metade = (por - nascer) / 2;
        double desvio = Math.max(-1, Math.min(1, (hora - meio) / metade));   // -1 manhã, +1 tarde
        double k = 0.9 + 1.4 * Math.abs(desvio);                              // corrida horizontal por metro de altura
        double d1 = base * k, d2 = topo * k, w = largura / 2;
        double[] ds = {d1, d1, d2, d2}, us = {-w, w, w, -w};
        double parede = (j.eixo() == 'x' ? j.y() : j.x()) + j.sentido() * PAREDE;
        double[][] c = new double[4][2];
        for (int i = 0; i < 4; i++) {
            double fundo = parede + j.sentido() * ds[i];
            double ao = (j.eixo() == 'x' ? j.x() : j.y()) + us[i] + desvio * deslocamento * ds[i] / d2;
            c[i][0] = j.eixo() == 'x' ? ao : fundo;
            c[i][1] = j.eixo() == 'x' ? fundo : ao;
        }
        return c;
    }

    /** Mancha no chão, somada ao buffer de luz (lote aberto, projeção da câmera, mistura aditiva). */
    void desenharPiso(SpriteBatch b, Texture branco, double hora, float[] lim) {
        double f = intensidade(hora);
        if (f <= 0.01) return;
        for (Janela j : janelas) {
            if (!naVista(j, lim)) continue;
            double[][] c = mancha(j, hora);
            // três faixas, da mais larga e fraca à mais estreita: borda macia sem textura
            for (int k = 0; k < 3; k++) {
                float a = (float) (piso * f * (0.30 + 0.2 * k));
                float cf = Color.toFloatBits(cor[0], cor[1], cor[2], a);
                double enc = 1 - 0.22 * k;
                double cx = (c[0][0] + c[2][0]) / 2, cy = (c[0][1] + c[2][1]) / 2;
                for (int i = 0; i < 4; i++) {
                    pv(i, cx + (c[i][0] - cx) * enc, cy + (c[i][1] - cy) * enc, 0, cf);
                }
                b.draw(branco, v, 0, 20);
            }
        }
    }

    /** Coluna de luz no ar e poeira, somadas à cena (lote aberto, projeção da câmera, mistura aditiva). */
    void desenharAr(SpriteBatch b, Texture branco, double hora, float tempo, boolean comPoeira, float[] lim) {
        double f = intensidade(hora);
        if (f <= 0.01) return;
        int visiveis = 0;
        for (Janela j : janelas) if (naVista(j, lim)) visiveis++;
        if (visiveis == 0) return;
        int graos = comPoeira ? Math.min(poeira, Math.max(2, poeiraTeto / visiveis)) : 0;
        for (Janela j : janelas) {
            if (!naVista(j, lim)) continue;
            double[][] c = mancha(j, hora);
            double[] te = ponto(j, -largura / 2, topo), td = ponto(j, largura / 2, topo);
            double cx = (c[0][0] + c[2][0]) / 2, cy = (c[0][1] + c[2][1]) / 2;
            // coluna: do topo da janela até a borda longe da mancha; some ao chegar no chão
            for (int k = 0; k < 3; k++) {
                float a = (float) (ar * f * (0.35 + 0.3 * k));
                float forte = Color.toFloatBits(cor[0], cor[1], cor[2], a);
                float fraco = Color.toFloatBits(cor[0], cor[1], cor[2], 0f);
                double enc = 1 - 0.25 * k;
                pv(0, lerp(cx, te[0], enc), lerp(cy, te[1], enc), te[2], forte);
                pv(1, lerp(cx, td[0], enc), lerp(cy, td[1], enc), td[2], forte);
                pv(2, lerp(cx, c[2][0], enc), lerp(cy, c[2][1], enc), 0, fraco);
                pv(3, lerp(cx, c[3][0], enc), lerp(cy, c[3][1], enc), 0, fraco);
                b.draw(branco, v, 0, 20);
            }
            for (int i = 0; i < graos; i++) grao(b, branco, j, c, te, td, i, tempo, (float) f);
        }
        b.setColor(Color.WHITE);
    }

    /** Um grão de poeira: 1 pixel de arte que desce devagar e pisca, dentro do volume do feixe. */
    private void grao(SpriteBatch b, Texture branco, Janela j, double[][] c, double[] te, double[] td, int i,
            float t, float f) {
        int s = LuzViva.semente(j.x() + i * 0.37, j.y() - i * 0.53, 7);
        double r1 = (s & 0xff) / 255.0, r2 = ((s >>> 8) & 0xff) / 255.0, r3 = ((s >>> 16) & 0xff) / 255.0;
        double u = (t / (16 + 10 * r3) + r1) % 1.0;                      // 0 = na janela, 1 = no chão (volta lenta)
        double lado = Math.max(0.05, Math.min(0.95, r2 + 0.05 * Math.sin(t * 0.6 + i * 2.1)));
        double ax = lerp(te[0], td[0], lado), ay = lerp(te[1], td[1], lado), az = te[2];
        double bx = lerp(c[3][0], c[2][0], lado), by = lerp(c[3][1], c[2][1], lado);
        double x = lerp(ax, bx, u), y = lerp(ay, by, u), z = lerp(az, 0.05, u) + 0.06 * Math.sin(t * 0.5 + i);
        float px = IsoProjection.snap(IsoProjection.sx(x, y)), py = IsoProjection.snap(IsoProjection.sy(x, y, z));
        float pisca = (float) (0.5 + 0.5 * Math.sin(t * (0.7 + r3) + i * 1.7));
        float a = Math.min(1f, f * (0.30f + 0.55f * pisca) * (float) Math.sin(Math.PI * u));
        b.setColor(cor[0], cor[1], cor[2], a);
        b.draw(branco, px, py, IsoProjection.ART_SCALE, IsoProjection.ART_SCALE);
    }

    private boolean naVista(Janela j, float[] lim) {
        float x = IsoProjection.sx(j.x(), j.y()), y = IsoProjection.sy(j.x(), j.y(), 0);
        return x > lim[0] - 500 && x < lim[2] + 500 && y > lim[1] - 500 && y < lim[3] + 500;
    }

    /** Ponto do topo da janela, u metros ao longo da parede a partir do centro, {x, y, z}. */
    private double[] ponto(Janela j, double u, double z) {
        double p = (j.eixo() == 'x' ? j.y() : j.x()) + j.sentido() * PAREDE;
        double ao = (j.eixo() == 'x' ? j.x() : j.y()) + u;
        return j.eixo() == 'x' ? new double[] {ao, p, z} : new double[] {p, ao, z};
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private void pv(int i, double x, double y, double z, float cor) {
        v[i * 5] = IsoProjection.sx(x, y);
        v[i * 5 + 1] = IsoProjection.sy(x, y, z);
        v[i * 5 + 2] = cor;
        v[i * 5 + 3] = 0.5f;
        v[i * 5 + 4] = 0.5f;
    }
}
