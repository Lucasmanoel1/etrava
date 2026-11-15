package etrava.client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Vida ambiente (V4, SPEC-visual §7.3): folhas que caem das árvores com o vento, pássaros que
 * cruzam o céu de dia, borboletas perto das flores, vaga-lumes no mato e mariposas nos lampiões
 * à noite. Pixels de arte nas cores da paleta, só na parte visível do mapa e com teto global.
 * Pura apresentação: nada entra no save; semente fixa (as capturas se repetem).
 * "Detalhes do ambiente: baixo" desliga tudo isto.
 */
final class Ambiente {
    static final int TETO = 160;

    private static final Color[] FOLHAS = {Color.valueOf("6B7045"), Color.valueOf("868C52"), Color.valueOf("9C7A45"),
            Color.valueOf("A85F38")};
    private static final Color PASSARO = Color.valueOf("1E1A22");
    private static final Color[] ASAS = {Color.valueOf("D8CCB0"), Color.valueOf("D8BC70"), Color.valueOf("C89A9A"),
            Color.valueOf("7E8EAE")};
    private static final Color VAGALUME = Color.valueOf("D8BC70"), VAGALUME2 = Color.valueOf("FFE9A8");
    private static final Color MARIPOSA = Color.valueOf("C4BBA8");
    /** V5: fumaça de chaminé e de forja, em dois cinzas da paleta. */
    private static final Color[] FUMACA = {Color.valueOf("A39A8A"), Color.valueOf("C4BBA8")};

    private enum Tipo { FOLHA, PASSARO, BORBOLETA, VAGALUME, MARIPOSA, FUMACA }

    private static final class P {
        Tipo tipo;
        double x, y, z, vx, vy, vz, cx, cy, cz, fase;
        float vida, dura;
        Color cor;
    }

    /** Ponto de origem: árvore (x, y, altura da copa), flor ou lampião. */
    record Ponto(double x, double y, double z) {}

    private final List<Ponto> arvores, flores, lampioes, chamines;
    private final List<P> vivas = new ArrayList<>();
    private final Random rnd = new Random(11);
    private float acum;
    private boolean aquecido;

    Ambiente(List<Ponto> arvores, List<Ponto> flores, List<Ponto> lampioes, List<Ponto> chamines) {
        this.chamines = chamines;
        this.arvores = arvores;
        this.flores = flores;
        this.lampioes = lampioes;
    }

    int vivas() {
        return vivas.size();
    }

    /**
     * @param noite 0 de dia, 1 de noite
     * @param vento 0..1, força do vento agora (folhas caem mais nas rajadas)
     * @param b     limites visíveis (pixels de mundo: x0, y0, x1, y1)
     */
    void atualizar(float dt, double noite, double vento, float[] b) {
        if (!aquecido) {   // ao entrar na área o ar já tem fumaça, folhas e vaga-lumes (6 s simulados)
            aquecido = true;
            for (int i = 0; i < 60; i++) atualizar(0.1f, noite, vento, b);
        }
        dt = Math.min(dt, 0.1f);
        acum += dt;
        // nasce de 4 em 4 décimos de segundo; cada tipo pela sua chance e só no que se vê
        while (acum >= 0.1f) {
            acum -= 0.1f;
            // fumaça: cada chaminé à vista solta um floco de vez em quando (não disputa o teto com o resto)
            for (Ponto c : chamines) {
                if (rnd.nextDouble() >= 0.3) continue;   // mesmo fora da vista: ao chegar, a fumaça já está lá
                P p = nova(Tipo.FUMACA, FUMACA[rnd.nextInt(FUMACA.length)], 4.5f + rnd.nextFloat() * 2f);
                p.x = c.x() + (rnd.nextDouble() - 0.5) * 0.25;
                p.y = c.y() + (rnd.nextDouble() - 0.5) * 0.25;
                p.z = c.z();
                p.vz = 0.45 + rnd.nextDouble() * 0.25;
            }
            int outras = 0;   // a fumaça não disputa o teto com folhas e bichos
            for (P q : vivas) if (q.tipo != Tipo.FUMACA) outras++;
            if (outras >= TETO) break;
            double dia = 1 - noite;
            if (rnd.nextDouble() < 0.10 + 0.35 * vento) folha(b);
            if (dia > 0.6 && rnd.nextDouble() < 0.004) bando(b);
            if (dia > 0.7 && rnd.nextDouble() < 0.05) borboleta(b);
            if (noite > 0.6 && rnd.nextDouble() < 0.35) vagalume(b);
            if (noite > 0.5 && rnd.nextDouble() < 0.25) mariposa(b);
        }
        for (int i = vivas.size() - 1; i >= 0; i--) {
            P p = vivas.get(i);
            p.vida += dt;
            if (p.vida >= p.dura) {
                vivas.remove(i);
                continue;
            }
            double t = p.vida;
            switch (p.tipo) {
                case FOLHA -> {          // cai balançando de um lado a outro e para no chão
                    if (p.z > 0) {
                        double bal = Math.sin(t * 3.1 + p.fase);
                        p.x += (p.vx + bal * 0.35 + vento * 0.4) * dt;
                        p.y += (p.vy - bal * 0.35 + vento * 0.4) * dt;
                        p.z = Math.max(0, p.z - (0.55 + 0.25 * Math.abs(bal)) * dt);
                    }
                }
                case FUMACA -> {         // sobe, perde força e deita com o vento
                    double bal = Math.sin(t * 1.3 + p.fase) * 0.12;
                    p.x += (0.05 + vento * 0.35 + bal) * dt;
                    p.y += (-0.03 - vento * 0.2 + bal) * dt;
                    p.z += p.vz * dt * Math.max(0.35, 1 - t / 7);
                }
                case PASSARO -> {
                    p.x += p.vx * dt;
                    p.y += p.vy * dt;
                }
                case BORBOLETA -> {      // voo em zigue-zague em volta da flor
                    p.x = p.cx + 0.6 * Math.sin(t * 1.3 + p.fase) + 0.15 * Math.sin(t * 7.0);
                    p.y = p.cy + 0.6 * Math.cos(t * 1.1 + p.fase * 2) + 0.15 * Math.cos(t * 6.3);
                    p.z = 0.35 + 0.25 * Math.abs(Math.sin(t * 4.0 + p.fase));
                }
                case VAGALUME -> {       // deriva devagar, acende e apaga
                    p.x = p.cx + 0.5 * Math.sin(t * 0.5 + p.fase);
                    p.y = p.cy + 0.5 * Math.cos(t * 0.4 + p.fase * 1.7);
                    p.z = p.cz + 0.25 * Math.sin(t * 0.7 + p.fase);
                }
                case MARIPOSA -> {       // voltas rápidas em torno da chama
                    double a = t * 5.0 + p.fase;
                    p.x = p.cx + 0.22 * Math.cos(a) + 0.06 * Math.sin(t * 13);
                    p.y = p.cy + 0.22 * Math.sin(a);
                    p.z = p.cz + 0.12 * Math.sin(t * 3.3 + p.fase);
                }
            }
        }
    }

    private boolean visivel(double x, double y, double z, float[] b, float folga) {
        float sx = IsoProjection.sx(x, y), sy = IsoProjection.sy(x, y, z);
        return sx > b[0] - folga && sx < b[2] + folga && sy > b[1] - folga && sy < b[3] + folga;
    }

    private Ponto sorteio(List<Ponto> l, float[] b) {
        if (l.isEmpty()) return null;
        for (int k = 0; k < 6; k++) {
            Ponto p = l.get(rnd.nextInt(l.size()));
            if (visivel(p.x(), p.y(), 0, b, 64)) return p;
        }
        return null;
    }

    private P nova(Tipo t, Color c, float dura) {
        P p = new P();
        p.tipo = t;
        p.cor = c;
        p.dura = dura;
        p.fase = rnd.nextDouble() * 6.28;
        vivas.add(p);
        return p;
    }

    private void folha(float[] b) {
        Ponto a = sorteio(arvores, b);
        if (a == null) return;
        P p = nova(Tipo.FOLHA, FOLHAS[rnd.nextInt(FOLHAS.length)], 9f);
        double ang = rnd.nextDouble() * 6.28, r = rnd.nextDouble() * 1.4;
        p.x = a.x() + Math.cos(ang) * r;
        p.y = a.y() + Math.sin(ang) * r;
        p.z = a.z() * (0.5 + 0.4 * rnd.nextDouble());
        p.vx = (rnd.nextDouble() - 0.5) * 0.2;
        p.vy = (rnd.nextDouble() - 0.5) * 0.2;
    }

    /** Três a seis pássaros em fila solta, bem alto, atravessando a vista. */
    private void bando(float[] b) {
        double cx = IsoProjection.worldX(b[0], (b[1] + b[3]) / 2), cy = IsoProjection.worldY(b[0], (b[1] + b[3]) / 2);
        double dir = -0.3 + rnd.nextDouble() * 0.6; // para leste na tela, com desvio
        double vx = Math.cos(dir - Math.PI / 4) * 7, vy = Math.sin(dir - Math.PI / 4) * 7;
        int n = 3 + rnd.nextInt(4);
        double alt = 9 + rnd.nextDouble() * 5;
        for (int i = 0; i < n; i++) {
            P p = nova(Tipo.PASSARO, PASSARO, 14f);
            p.x = cx - vx * 0.3 - i * 0.6 + rnd.nextDouble() * 0.5;
            p.y = cy - vy * 0.3 + i * 0.4 + rnd.nextDouble() * 0.5;
            p.z = alt + rnd.nextDouble() * 0.6;
            p.vx = vx;
            p.vy = vy;
        }
    }

    private void borboleta(float[] b) {
        Ponto f = sorteio(flores, b);
        if (f == null) return;
        P p = nova(Tipo.BORBOLETA, ASAS[rnd.nextInt(ASAS.length)], 8f + rnd.nextFloat() * 6f);
        p.cx = f.x();
        p.cy = f.y();
    }

    private void vagalume(float[] b) {
        double x0 = IsoProjection.worldX((b[0] + b[2]) / 2, (b[1] + b[3]) / 2);
        double y0 = IsoProjection.worldY((b[0] + b[2]) / 2, (b[1] + b[3]) / 2);
        double w = (b[2] - b[0]) / IsoProjection.HALF_W / 2;
        double x = x0 + (rnd.nextDouble() - 0.5) * w * 1.4, y = y0 + (rnd.nextDouble() - 0.5) * w * 1.4;
        P p = nova(Tipo.VAGALUME, VAGALUME, 4f + rnd.nextFloat() * 4f);
        p.cx = x;
        p.cy = y;
        p.cz = 0.3 + rnd.nextDouble() * 0.8;
    }

    private void mariposa(float[] b) {
        Ponto l = sorteio(lampioes, b);
        if (l == null) return;
        int perto = 0;
        for (P q : vivas) if (q.tipo == Tipo.MARIPOSA && q.cx == l.x() && q.cy == l.y()) perto++;
        if (perto >= 3) return;
        P p = nova(Tipo.MARIPOSA, MARIPOSA, 6f + rnd.nextFloat() * 6f);
        p.cx = l.x();
        p.cy = l.y();
        p.cz = l.z();
    }

    void desenhar(SpriteBatch batch, Texture branco, float[] b, float tempo) {
        int px = IsoProjection.ART_SCALE;
        for (P p : vivas) {
            if (!visivel(p.x, p.y, p.z, b, 8)) continue;
            float sx = IsoProjection.snap(IsoProjection.sx(p.x, p.y)), sy = IsoProjection.snap(IsoProjection.sy(p.x, p.y, p.z));
            float fadein = Math.min(1f, p.vida / 0.6f), fadeout = Math.min(1f, (p.dura - p.vida) / 0.8f);
            float a = Math.min(fadein, fadeout);
            switch (p.tipo) {
                case PASSARO -> {        // um "v" que bate as asas
                    boolean cima = Math.sin(tempo * 9 + p.fase) > 0;
                    batch.setColor(p.cor.r, p.cor.g, p.cor.b, a);
                    batch.draw(branco, sx, sy, px, px);
                    batch.draw(branco, sx - px, sy + (cima ? px : 0), px, px);
                    batch.draw(branco, sx + px, sy + (cima ? px : 0), px, px);
                }
                case BORBOLETA -> {
                    boolean aberta = Math.sin(tempo * 14 + p.fase) > 0;
                    batch.setColor(p.cor.r, p.cor.g, p.cor.b, a);
                    batch.draw(branco, sx, sy, px, px);
                    if (aberta) batch.draw(branco, sx - px, sy, px * 3, px);
                }
                case FUMACA -> {         // floco redondo que cresce e rareia (3, 5, 7 px de arte)
                    float f = p.vida / p.dura;
                    int r = f < 0.15f ? 1 : f < 0.5f ? 2 : 3;
                    batch.setColor(p.cor.r, p.cor.g, p.cor.b, a * (0.8f - 0.55f * f));
                    for (int dy = -r; dy <= r; dy++) {
                        int larg = r - (Math.abs(dy) == r && r > 1 ? 1 : 0) - (Math.abs(dy) == r && r == 1 ? 1 : 0);
                        batch.draw(branco, sx - larg * px, sy + dy * px, (2 * larg + 1) * px, px);
                    }
                }
                case VAGALUME -> {
                    float pisca = (float) Math.max(0, Math.sin(p.vida * 2.2 + p.fase));
                    if (pisca < 0.2f) continue;
                    Color c = pisca > 0.8f ? VAGALUME2 : VAGALUME;
                    batch.setColor(c.r, c.g, c.b, a * pisca);
                    batch.draw(branco, sx, sy, px, px);
                }
                default -> {
                    batch.setColor(p.cor.r, p.cor.g, p.cor.b, a);
                    batch.draw(branco, sx, sy, px, px);
                }
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    /** Vaga-lumes acesos, para a passada de luz (cada um é um pontinho de luz própria). */
    void luzes(java.util.function.Consumer<double[]> saida) {
        for (P p : vivas) {
            if (p.tipo != Tipo.VAGALUME) continue;
            double pisca = Math.max(0, Math.sin(p.vida * 2.2 + p.fase));
            if (pisca > 0.4) saida.accept(new double[] {p.x, p.y, p.z, pisca});
        }
    }
}
