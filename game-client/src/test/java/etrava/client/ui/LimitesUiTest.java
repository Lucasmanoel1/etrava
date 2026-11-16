package etrava.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import etrava.client.Headless;
import etrava.client.assets.Assets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A checagem de limites do VT-09: casos que passam e casos que têm de falhar. */
class LimitesUiTest {
    private static BitmapFont font;
    private static Label.LabelStyle estilo;

    @BeforeAll
    static void load() {
        Headless.start();
        Path a = Path.of(System.getProperty("etrava.assets"));
        font = new Fonts(a, new Assets(a).manifest()).text(1f);
        estilo = new Label.LabelStyle(font, Color.WHITE);
    }

    /** Stage de 1280x720 (k = 1: tela virtual de 1280x720). */
    private static UiStage stage() {
        Headless.screen(1280, 720);
        UiStage s = new UiStage(new ScreenViewport(), Headless.batch());
        Escala.aplicar(s.getViewport(), 1280, 720);
        return s;
    }

    private static Label label(String texto, float x, float y, float w, float h) {
        Label l = new Label(texto, estilo);
        l.setBounds(x, y, w, h);
        return l;
    }

    private static List<LimitesUi.Problema> checar(UiStage s) {
        return LimitesUi.verificar("teste", s);
    }

    private static long tipo(List<LimitesUi.Problema> ps, String tipo) {
        return ps.stream().filter(p -> p.tipo().equals(tipo)).count();
    }

    @Test
    void telaLimpaNaoTemProblemas() {
        UiStage s = stage();
        s.addActor(label("Pão de centeio", 20, 20, 300, 30));
        Table t = new Table();
        t.setFillParent(true);
        t.add(new Label("Título", estilo)).row();
        t.add(new Label("uma frase que quebra em varias linhas se precisar", estilo)).width(400).left();
        s.addActor(t);
        s.draw();
        assertEquals(List.of(), checar(s).stream().map(LimitesUi.Problema::legivel).toList());
    }

    @Test
    void atorForaDaTelaEhAchado() {
        UiStage s = stage();
        Actor fora = new Actor();
        fora.setBounds(1200, 100, 200, 50); // passa de 1280
        s.addActor(fora);
        Actor dentro = new Actor();
        dentro.setBounds(100, 100, 50, 50);
        s.addActor(dentro);
        List<LimitesUi.Problema> ps = checar(s);
        assertEquals(1, tipo(ps, "fora-da-tela"), ps.toString());
        assertTrue(ps.get(0).medidas().contains("1400"), ps.get(0).medidas());
    }

    @Test
    void invisivelOuIgnoradoOuVazioNaoContam() {
        UiStage s = stage();
        Actor escondido = new Actor();
        escondido.setBounds(-500, 0, 100, 100);
        escondido.setVisible(false);
        Actor vazio = new Actor(); // sem tamanho
        vazio.setPosition(5000, 5000);
        Actor sangra = new Actor();
        sangra.setBounds(-100, -100, 2000, 2000);
        sangra.setName(LimitesUi.IGNORAR + ".fundo");
        Table paiEscondido = new Table();
        paiEscondido.setVisible(false);
        Actor filho = new Actor();
        filho.setBounds(-50, 0, 10, 10);
        paiEscondido.addActor(filho);
        for (Actor a : List.of(escondido, vazio, sangra, paiEscondido)) s.addActor(a);
        assertTrue(checar(s).isEmpty(), checar(s).toString());
    }

    @Test
    void dentroDeRolagemNaoEhForaDaTela() {
        UiStage s = stage();
        Table lista = new Table();
        for (int i = 0; i < 100; i++) lista.add(new Label("linha " + i, estilo)).left().row();
        ScrollPane p = new ScrollPane(lista);
        p.setBounds(100, 100, 300, 200);
        s.addActor(p);
        p.validate();
        List<LimitesUi.Problema> ps = checar(s);
        assertEquals(0, tipo(ps, "fora-da-tela"), ps.toString());
        assertEquals(0, tipo(ps, "rolagem-desligada"), ps.toString());
    }

    @Test
    void textoSemQuebraMaisLargoQueOLabelEhCortado() {
        UiStage s = stage();
        s.addActor(label("Um nome de item bem comprido que não cabe", 20, 20, 60, 30));
        List<LimitesUi.Problema> ps = checar(s);
        assertEquals(1, tipo(ps, "texto-cortado"), ps.toString());
        assertTrue(ps.get(0).texto().startsWith("Um nome"));
    }

    @Test
    void textoComQuebraMaisAltoQueOLabelEhCortado() {
        UiStage s = stage();
        Label l = label("Uma pergunta comprida o bastante para quebrar em várias linhas nesta caixa estreita.", 20, 20, 120, 20);
        l.setWrap(true);
        s.addActor(l);
        List<LimitesUi.Problema> ps = checar(s);
        assertEquals(1, tipo(ps, "texto-cortado"), ps.toString());
        assertTrue(ps.get(0).medidas().contains("altura"), ps.get(0).medidas());

        UiStage ok = stage();
        Label folgado = label("Uma pergunta comprida o bastante para quebrar em várias linhas nesta caixa estreita.", 20, 20, 120, 400);
        folgado.setWrap(true);
        ok.addActor(folgado);
        assertTrue(checar(ok).isEmpty(), checar(ok).toString());
    }

    @Test
    void reticenciasContamComoCortado() {
        UiStage s = stage();
        Label l = label("Um nome de item bem comprido que não cabe", 20, 20, 60, 30);
        l.setEllipsis(true);
        s.addActor(l);
        List<LimitesUi.Problema> ps = checar(s);
        assertEquals(1, tipo(ps, "texto-cortado"), ps.toString());
        assertTrue(ps.get(0).medidas().contains("reticências"), ps.get(0).medidas());
    }

    @Test
    void rolagemDesligadaComConteudoMaiorEhAchada() {
        UiStage s = stage();
        Table lista = new Table();
        for (int i = 0; i < 100; i++) lista.add(new Label("linha " + i, estilo)).left().row();
        ScrollPane p = new ScrollPane(lista);
        p.setScrollingDisabled(true, true);
        p.setBounds(100, 100, 300, 200);
        s.addActor(p);
        List<LimitesUi.Problema> ps = checar(s);
        assertEquals(1, tipo(ps, "rolagem-desligada"), ps.toString());

        UiStage curto = stage();
        Table pouco = new Table();
        pouco.add(new Label("uma linha", estilo));
        ScrollPane q = new ScrollPane(pouco);
        q.setScrollingDisabled(true, true);
        q.setBounds(100, 100, 300, 200);
        curto.addActor(q);
        assertEquals(0, tipo(checar(curto), "rolagem-desligada"));
    }
}
