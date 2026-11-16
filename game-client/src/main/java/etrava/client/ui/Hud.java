package etrava.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import etrava.sim.content.AssetManifest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * O HUD sobre o cenário (V7): plaquinha de dia, hora e dinheiro, vida e fôlego, avisos em bilhetes,
 * balões de fala e rótulos. Desenhado no {@link Batch} do jogo, na mesma escala inteira k da
 * interface ({@link Escala}): as peças do {@link Tema} (filtro nearest) são ampliadas por k e a
 * fonte é a das telas (gerada em k vezes, reduzida a 1/k), então o texto fica no tamanho físico e nítido.
 * Todas as medidas aqui são em unidades da tela virtual (altura de 540 a 1079); cada passada é
 * {@link #inicio} … {@link #fim}. Nada aqui é objetivo, marcador ou imperativo (PRD 10.1).
 * <p>
 * Não é um {@code Stage}: os limites da tela são garantidos pelo próprio desenho (tudo é preso a
 * uma margem de {@value #M} unidades das bordas).
 */
public final class Hud implements Disposable {
    private static final int M = 8, ICONE_BASE = 18;
    private static final float ENTRA = 0.3f, SAI = 0.35f;
    /** Onde fica a ponta da cauda do balão, a partir da borda esquerda da peça (pixels de arte). */
    private static final float CAUDA_X = 11f;
    private static final int MAX_AVISOS = 4;

    private final Tema tema;
    private final Ui ui;
    /** Tinta (sem sombra, na escala das telas) e a mesma fonte com sombra, para texto solto sobre o cenário. */
    private final BitmapFont tinta, claro;
    private final Color cTinta, cClara;
    private final GlyphLayout lay = new GlyphLayout();
    private final Matrix4 proj = new Matrix4(), salvo = new Matrix4();
    private final Color tmp = new Color();
    private final List<float[]> ocupados = new ArrayList<>();
    private final List<Aviso> avisos = new ArrayList<>();

    private Batch b;
    private float largura, altura;
    private int k = 1;
    /** 0 = dia (sol), 1 = noite (lua); anda até o alvo para o ícone trocar sem salto. */
    private float fase = -1f;
    private float numeroT;
    private int ultimaVida = Integer.MIN_VALUE;

    private static final class Aviso {
        final String texto;
        float t, dur, y = Float.NaN;

        Aviso(String texto) {
            this.texto = texto;
            this.dur = Math.max(3.5f, 2f + texto.length() * 0.045f);
        }
    }

    /**
     * @param raiz     pasta {@code assets/}
     * @param manifest o manifesto de assets
     * @param tinta    a fonte das telas (sem sombra, escala 1/k)
     * @param claro    a mesma fonte com sombra (texto claro sobre o cenário)
     */
    public Hud(Path raiz, AssetManifest manifest, BitmapFont tinta, BitmapFont claro) {
        this.tema = new Tema(raiz, manifest, tinta);
        this.ui = new Ui(tema);
        this.tinta = tinta;
        this.claro = claro;
        this.cTinta = tema.cor("tinta");
        this.cClara = tema.cor("tinta_clara");
    }

    public Tema tema() { return tema; }

    /** O {@link Ui} do HUD: é dele o cursor do mouse (apontar, falar, entrar, pegar, atacar, esperar). */
    public Ui ui() { return ui; }

    // ---------------------------------------------------------------- passada

    /** Começa uma passada de desenho em unidades da tela virtual; {@code telaL} × {@code telaA} em pixels. */
    public void inicio(Batch batch, int telaL, int telaA, int k) {
        this.b = batch;
        this.k = Math.max(1, k);
        largura = telaL / (float) this.k;
        altura = telaA / (float) this.k;
        ui.escala(this.k);
        ocupados.clear();
        salvo.set(batch.getProjectionMatrix());
        proj.setToOrtho2D(0, 0, largura, altura);
        batch.setProjectionMatrix(proj);
        batch.begin();
    }

    public void fim() {
        b.end();
        b.setProjectionMatrix(salvo);
        b.setColor(1, 1, 1, 1);
    }

    // ---------------------------------------------------------------- canto de cima: dia, hora, dinheiro

    /**
     * Plaquinha de couro no canto de cima à esquerda: sol ou lua (conforme a hora), "Dia N, HH:MM" e a bolsa
     * com o dinheiro; sob ela, o estado (fome, carga, pausa) e a linha de depuração quando houver.
     */
    public void topo(String relogio, int minutoDoDia, String dinheiro, String status, String depuracao, float dt) {
        float alvo = minutoDoDia >= 6 * 60 && minutoDoDia < 19 * 60 ? 0f : 1f;
        if (fase < 0) fase = alvo;
        else fase += Math.signum(alvo - fase) * Math.min(Math.abs(alvo - fase), dt / 0.9f);

        int[] p = tema.pad("ui.couro");
        float lh = tinta.getLineHeight(), cap = tinta.getCapHeight();
        int ICONE = ICONE_BASE * escalaTexto();
        float linha = Math.max(ICONE, Math.round(lh));
        lay.setText(tinta, relogio);
        float wRel = lay.width;
        lay.setText(tinta, dinheiro);
        float wDin = lay.width;
        float w1 = ICONE + 6 + wRel + 14 + ICONE + 4 + wDin;
        boolean tem = status != null && !status.isEmpty();
        float w2 = 0;
        if (tem) {
            lay.setText(tinta, status);
            w2 = lay.width;
        }
        float pw = (float) Math.ceil(Math.max(w1, w2) + p[0] + p[1]);
        float ph = (float) Math.ceil(p[2] + p[3] + linha + (tem ? lh : 0));
        float x = M, topo = altura - M, y = topo - ph;
        peca("ui.couro", x, y, pw, ph, 1f);

        float cx = x + p[0], base = topo - p[2] - linha, centro = base + linha / 2f;
        float iy = Math.round(centro - ICONE / 2f);
        peca("ui.sol", cx, iy - Math.round(fase * 6), ICONE, ICONE, 1f - fase);
        peca("ui.lua", cx, iy - Math.round((1f - fase) * 6), ICONE, ICONE, fase);
        float tx = cx + ICONE + 6;
        texto(tinta, relogio, cClara, 1f, tx, centro + cap / 2f);
        float bx = tx + wRel + 14;
        peca("ui.bolsa", Math.round(bx), iy, ICONE, ICONE, 1f);
        texto(tinta, dinheiro, cClara, 1f, bx + ICONE + 4, centro + cap / 2f);
        if (tem) texto(tinta, status, cClara, 0.85f, cx, base - (lh - cap) / 2f);
        if (depuracao != null && !depuracao.isEmpty()) texto(claro, depuracao, cClara, 1f, x, y - 4);
    }

    // ---------------------------------------------------------------- canto de baixo: vida e fôlego

    /**
     * Vida e fôlego em barras numa moldura pequena de couro, no canto de baixo. Sem número permanente: o
     * número aparece com o mouse em cima e por um instante quando a vida muda. {@code habilidades}
     * (ou null) é a dica das teclas de luta, só com inimigo perto.
     */
    public void barras(float vida, float vidaMax, float folego, float folegoMax,
                       String textoVida, String textoFolego, String habilidades, float dt) {
        int[] p = tema.pad("ui.couro");
        float lh = tinta.getLineHeight();
        int s = escalaTexto();
        float bw = Math.min(220f * s, Math.max(120f, Math.round(lh * 7))), bh = 12 * s, gap = 4 * s;
        float pw = bw + p[0] + p[1], ph = p[2] + p[3] + 2 * bh + gap;
        float x = M, y = M;
        peca("ui.couro", x, y, pw, ph, 1f);
        float bx = x + p[0];
        barra(bx, y + p[3] + bh + gap, bw, bh, vidaMax <= 0 ? 0 : vida / vidaMax, "ui.barra_vida");
        barra(bx, y + p[3], bw, bh, folegoMax <= 0 ? 0 : folego / folegoMax, "ui.barra_folego");

        int v = Math.round(vida);
        if (ultimaVida != Integer.MIN_VALUE && v != ultimaVida) numeroT = 2.5f;
        ultimaVida = v;
        if (numeroT > 0) numeroT -= dt;
        float mx = Gdx.input.getX() / (float) k, my = (Gdx.graphics.getHeight() - Gdx.input.getY()) / (float) k;
        boolean sobre = mx >= x && mx <= x + pw && my >= y && my <= y + ph;
        if (sobre || numeroT > 0) {
            placa(textoVida + "   " + textoFolego, x + pw + 4, y + (ph - (lh + 12)) / 2f, largura * 0.5f, 1f);
        }
        if (habilidades != null) placa(habilidades, x, y + ph + 4, largura - 2 * M, 0.95f);
    }

    /** Texto grande pede peças maiores: ícones e barras ampliados por um inteiro (pixel de arte nítido). */
    private int escalaTexto() {
        return Math.max(1, Math.round(tinta.getLineHeight() / 28f));
    }

    private void barra(float x, float y, float w, float h, float frac, String preenchimento) {
        peca("ui.barra_fundo", x, y, w, h, 1f);
        frac = Math.max(0f, Math.min(1f, frac));
        float e = 3 * escalaTexto(), fw = (w - 2 * e) * frac;
        if (frac > 0f) peca(preenchimento, x + e, y + e, Math.max(4f, fw), h - 2 * e, 1f);
    }

    /** Plaquinha escura (dica) com texto claro, canto inferior esquerdo em (x, y), presa dentro da tela. */
    private void placa(String s, float x, float y, float larguraMax, float alfa) {
        int[] p = tema.pad("ui.dica");
        tmp.set(cClara.r, cClara.g, cClara.b, alfa);
        lay.setText(tinta, s, tmp, Math.max(40f, larguraMax - p[0] - p[1]), Align.left, true);
        float w = (float) Math.ceil(lay.width) + p[0] + p[1], h = (float) Math.ceil(lay.height) + p[2] + p[3] + 3;
        x = Math.round(Math.max(M, Math.min(x, largura - M - w)));
        y = Math.round(Math.max(M, Math.min(y, altura - M - h)));
        peca("ui.dica", x, y, w, h, alfa);
        tinta.draw(b, lay, x + p[0], y + h - p[2]);
    }

    // ---------------------------------------------------------------- avisos

    /** Um aviso entra como bilhete deslizando; o mesmo texto ainda na tela só reinicia o tempo. */
    public void aviso(String texto) {
        if (texto == null || texto.isBlank()) return;
        for (Aviso a : avisos) {
            if (a.texto.equals(texto) && a.t < a.dur - SAI) {
                a.t = Math.min(a.t, ENTRA);
                return;
            }
        }
        if (avisos.size() >= MAX_AVISOS) {
            Aviso velho = avisos.get(0);
            velho.t = Math.max(velho.t, velho.dur - SAI);
        }
        avisos.add(new Aviso(texto));
    }

    /** Bilhetes no canto de cima à direita: o mais novo no alto, os outros descem; ninguém se sobrepõe. */
    public void avisos(float dt) {
        for (int i = avisos.size() - 1; i >= 0; i--) {
            Aviso a = avisos.get(i);
            a.t += dt;
            if (a.t >= a.dur) avisos.remove(i);
        }
        if (avisos.isEmpty()) return;
        int[] p = tema.pad("ui.bilhete");
        float maxW = Math.min(largura * 0.4f, 24 * tinta.getLineHeight());
        float y = altura - M;
        for (int i = avisos.size() - 1; i >= 0; i--) {
            Aviso a = avisos.get(i);
            lay.setText(tinta, a.texto, cTinta, maxW - p[0] - p[1], Align.left, true);
            float w = (float) Math.ceil(lay.width) + p[0] + p[1];
            float h = Math.max((float) Math.ceil(lay.height) + p[2] + p[3] + 2, 28f);
            float alvo = y - h;
            a.y = Float.isNaN(a.y) ? alvo : a.y + (alvo - a.y) * Math.min(1f, dt * 14f);
            float desliza = 0f;
            if (a.t < ENTRA) {
                float u = 1f - a.t / ENTRA;
                desliza = u * u * u;
            } else if (a.t > a.dur - SAI) {
                float u = (a.t - (a.dur - SAI)) / SAI;
                desliza = u * u;
            }
            float x = Math.round(largura - M - w + desliza * (w + M));
            float ay = Math.round(a.y);
            peca("ui.bilhete", x, ay, w, h, 1f);
            tinta.draw(b, lay, x + p[0], ay + h - p[2]);
            y = alvo - 4;
        }
    }

    // ---------------------------------------------------------------- sobre o cenário: balões e rótulos

    /**
     * Balão de fala: a ponta da cauda (embutida embaixo à esquerda da peça) fica em ({@code sx}, {@code sy}),
     * em pixels de tela com y para cima (a cabeça de quem fala). Quebra de linha numa largura máxima; sobe
     * para não cobrir outro balão ou rótulo já desenhado nesta passada.
     */
    public void balao(float sx, float sy, String texto, float alfa) {
        flutuante("ui.balao", texto, cTinta, Math.min(largura * 0.3f, 16 * tinta.getLineHeight()), sx, sy, CAUDA_X, alfa);
    }

    /** Rótulo escuro com o nome de quem (ou do que) está sob o mouse, centrado sobre o ponto. */
    public void rotulo(float sx, float sy, String texto, float alfa) {
        flutuante("ui.dica", texto, cClara, Math.min(largura * 0.3f, 16 * tinta.getLineHeight()), sx, sy, -1f, alfa);
    }

    private void flutuante(String id, String s, Color cor, float larguraMax, float sx, float sy, float dx, float alfa) {
        int[] p = tema.pad(id);
        tmp.set(cor.r, cor.g, cor.b, alfa);
        lay.setText(tinta, s, tmp, larguraMax - p[0] - p[1], Align.left, true);
        float w = Math.max((float) Math.ceil(lay.width) + p[0] + p[1], 28f);
        float h = (float) Math.ceil(lay.height) + p[2] + p[3] + 3;
        float ax = sx / k, ay = sy / k;
        float x = Math.round(ax - (dx < 0 ? w / 2f : dx));
        float y = Math.round(ay);
        x = Math.max(M, Math.min(x, largura - M - w));
        // dois balões não ficam um em cima do outro: o que veio depois sobe
        for (int i = 0; i < ocupados.size(); i++) {
            float[] r = ocupados.get(i);
            if (x < r[0] + r[2] && x + w > r[0] && y < r[1] + r[3] && y + h > r[1]) {
                y = r[1] + r[3] + 3;
                i = -1;
            }
        }
        y = Math.min(y, altura - M - h);
        ocupados.add(new float[] {x, y, w, h});
        peca(id, x, y, w, h, alfa);
        tinta.draw(b, lay, x + p[0], y + h - p[2]);
    }

    // ---------------------------------------------------------------- baixo nível

    private void peca(String id, float x, float y, float w, float h, float alfa) {
        if (alfa <= 0f) return;
        b.setColor(1, 1, 1, alfa);
        tema.peca(id).draw(b, x, y, w, h);
        b.setColor(1, 1, 1, 1);
    }

    private void texto(BitmapFont f, String s, Color cor, float alfa, float x, float topo) {
        tmp.set(cor.r, cor.g, cor.b, cor.a * alfa);
        lay.setText(f, s, tmp, 0, Align.left, false);
        f.draw(b, lay, Math.round(x), Math.round(topo));
    }

    @Override
    public void dispose() {
        tema.dispose();
    }
}
