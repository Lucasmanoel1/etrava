package etrava.client.ui;

import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checagem automática de limites da interface (VT-09): sobre um {@link Stage} já montado e
 * desenhado, acha o que a tela esconde ou corta. Três regras:
 * <ol>
 *   <li><b>fora da tela</b> — ator visível (com todos os pais visíveis e tamanho não vazio) cujo retângulo
 *       sai da tela virtual do Stage; o que está dentro de um {@link ScrollPane} é recortado de propósito
 *       e fica de fora desta regra;</li>
 *   <li><b>texto cortado</b> — {@link Label} sem quebra de linha cuja largura é menor que a do texto; com
 *       quebra (ou com {@code \n}) cuja altura é menor que a do texto; ou com reticências aplicadas;</li>
 *   <li><b>rolagem desligada</b> — {@link ScrollPane} com conteúdo maior que a área e rolagem desligada
 *       naquele eixo.</li>
 * </ol>
 * Exceção declarada: um ator cujo nome começa com {@value #IGNORAR} (e tudo dentro dele) não é checado — para
 * ilustrações que sangram para fora da tela de propósito. Use com parcimônia.
 */
public final class LimitesUi {
    /** Prefixo de {@link Actor#setName} que tira o ator (e os filhos) da checagem. */
    public static final String IGNORAR = "limites:ignorar";
    /** Tolerância em unidades do Stage (arredondamento de layout). */
    private static final float TOL = 1f;

    /** Um problema achado, com o que for preciso para localizá-lo sem abrir o jogo. */
    public record Problema(String tela, String tipo, String ator, String texto, String medidas) {
        public String legivel() {
            return tela + " | " + tipo + " | " + ator + (texto.isEmpty() ? "" : " | \"" + texto + "\"") + " | " + medidas;
        }
    }

    private LimitesUi() {}

    /** Confere o Stage inteiro; {@code tela} só rotula os problemas. */
    public static List<Problema> verificar(String tela, Stage stage) {
        List<Problema> out = new ArrayList<>();
        float w = stage.getWidth(), h = stage.getHeight();
        for (Actor a : stage.getRoot().getChildren()) visitar(tela, a, a.getClass().getSimpleName() + "#0", w, h, false, out);
        return out;
    }

    private static void visitar(String tela, Actor a, String caminho, float w, float h, boolean emRolagem, List<Problema> out) {
        if (!a.isVisible() || a.getColor().a <= 0f) return;
        if (a.getName() != null && a.getName().startsWith(IGNORAR)) return;
        String nome = a.getName() != null ? caminho + "[" + a.getName() + "]" : caminho;

        if (!emRolagem && a.getWidth() > 0 && a.getHeight() > 0) {
            float[] r = retangulo(a);
            if (r[0] < -TOL || r[1] < -TOL || r[2] > w + TOL || r[3] > h + TOL) {
                out.add(new Problema(tela, "fora-da-tela", nome, texto(a), String.format(Locale.ROOT,
                        "x %.0f..%.0f y %.0f..%.0f em tela %.0fx%.0f", r[0], r[2], r[1], r[3], w, h)));
            }
        }
        if (a instanceof Label l) cortado(tela, l, nome, out);
        if (a instanceof ScrollPane p) {
            rolagem(tela, p, nome, out);
            emRolagem = true;
        }
        if (a instanceof Group g) {
            int i = 0;
            for (Actor c : g.getChildren()) visitar(tela, c, nome + ">" + c.getClass().getSimpleName() + "#" + i++, w, h, emRolagem, out);
        }
    }

    /** Menor retângulo (x0, y0, x1, y1), em coordenadas do Stage, que contém o ator. */
    private static float[] retangulo(Actor a) {
        Vector2 p = new Vector2();
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            p.set(i % 2 == 0 ? 0 : a.getWidth(), i < 2 ? 0 : a.getHeight());
            a.localToStageCoordinates(p);
            x0 = Math.min(x0, p.x);
            y0 = Math.min(y0, p.y);
            x1 = Math.max(x1, p.x);
            y1 = Math.max(y1, p.y);
        }
        return new float[] {x0, y0, x1, y1};
    }

    private static String texto(Actor a) {
        if (a instanceof Label l) {
            String t = l.getText().toString().replace('\n', ' ');
            return t.length() > 60 ? t.substring(0, 57) + "..." : t;
        }
        return "";
    }

    private static void cortado(String tela, Label l, String nome, List<Problema> out) {
        if (l.getText().size == 0) return;
        l.validate();
        GlyphLayout layout = l.getGlyphLayout();
        boolean quebra = l.getWrap();
        String ellipsis = reticencias(l);
        float lw = l.getWidth(), lh = l.getHeight();
        if (ellipsis != null && !quebra) {
            float natural = new GlyphLayout(l.getStyle().font, l.getText().toString()).width;
            if (natural > lw + TOL) {
                out.add(new Problema(tela, "texto-cortado", nome, texto(l), String.format(Locale.ROOT,
                        "reticências: texto pede %.0f, label tem %.0f", natural, lw)));
            }
        } else if (!quebra && layout.width > lw + TOL) {
            out.add(new Problema(tela, "texto-cortado", nome, texto(l), String.format(Locale.ROOT,
                    "sem quebra: texto pede %.0f de largura, label tem %.0f", layout.width, lw)));
        }
        if ((quebra || l.getText().indexOf('\n') >= 0) && layout.height > lh + TOL) {
            out.add(new Problema(tela, "texto-cortado", nome, texto(l), String.format(Locale.ROOT,
                    "com quebra: texto pede %.0f de altura, label tem %.0f (largura %.0f)", layout.height, lh, lw)));
        }
    }

    /** A API do libGDX não expõe as reticências do Label; lê o campo (null = não há). */
    private static String reticencias(Label l) {
        try {
            var f = Label.class.getDeclaredField("ellipsis");
            f.setAccessible(true);
            return (String) f.get(l);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static void rolagem(String tela, ScrollPane p, String nome, List<Problema> out) {
        Actor c = p.getActor();
        if (c == null) return;
        p.validate();
        float cw = c.getWidth(), ch = c.getHeight(); // sem rolagem o ScrollPane encolhe o conteúdo à área: vale o tamanho preferido
        if (c instanceof com.badlogic.gdx.scenes.scene2d.utils.Layout lay) {
            cw = Math.max(cw, lay.getPrefWidth());
            ch = Math.max(ch, lay.getPrefHeight());
        }
        float aw = p.getScrollWidth(), ah = p.getScrollHeight();
        if (ch > ah + TOL && p.isScrollingDisabledY()) {
            out.add(new Problema(tela, "rolagem-desligada", nome, "", String.format(Locale.ROOT,
                    "conteúdo %.0f de altura, área %.0f, rolagem vertical desligada", ch, ah)));
        }
        if (cw > aw + TOL && p.isScrollingDisabledX()) {
            out.add(new Problema(tela, "rolagem-desligada", nome, "", String.format(Locale.ROOT,
                    "conteúdo %.0f de largura, área %.0f, rolagem horizontal desligada", cw, aw)));
        }
    }
}
