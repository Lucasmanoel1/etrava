package etrava.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A roda do mouse e o zoom (VT-01, D-088): uma lista escondida não pode mais engolir a roda.
 * Tela 1280×720; o painel ocupa o retângulo (100, 100)–(500, 400) em coordenadas do Stage.
 */
class UiStageTest {
    private static final int W = 1280, H = 720;

    @BeforeAll
    static void headless() {
        HeadlessApplicationConfiguration cfg = new HeadlessApplicationConfiguration();
        cfg.updatesPerSecond = -1; // sem laço: só precisamos do Gdx.* preenchido
        new HeadlessApplication(new ApplicationAdapter() {}, cfg);
        if (Gdx.gl == null) Gdx.gl = Gdx.gl20 = nothing(GL20.class); // o viewport chama glViewport
        // a câmera converte o y do mouse pela altura da janela: uma tela de 1280×720
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[] {Graphics.class},
                (p, m, a) -> switch (m.getName()) {
                    case "getWidth", "getBackBufferWidth" -> W;
                    case "getHeight", "getBackBufferHeight" -> H;
                    default -> zero(m.getReturnType());
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> T nothing(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (p, m, a) -> zero(m.getReturnType()));
    }

    private static Object zero(Class<?> t) {
        if (t == boolean.class) return Boolean.FALSE;
        if (t == float.class) return 0f;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        return null;
    }

    /** Batch que não desenha nada: o teste não chama draw(). */
    private static Batch noBatch() {
        return nothing(Batch.class);
    }

    private record Panel(Table root, ScrollPane pane) {}

    /** Painel com uma lista bem mais alta do que cabe: há o que rolar. */
    private static Panel panel(Stage stage) {
        Actor tall = new Actor();
        tall.setSize(380, 2000);
        ScrollPane pane = new ScrollPane(tall, new ScrollPane.ScrollPaneStyle());
        pane.setSmoothScrolling(false);
        Table root = new Table();
        root.setBounds(100, 100, 400, 300);
        root.add(pane).grow();
        stage.addActor(root);
        root.validate();
        assertTrue(pane.isScrollY(), "a lista do teste precisa ter o que rolar");
        return new Panel(root, pane);
    }

    private static UiStage stage(int mouseX, int mouseYFromTop) {
        UiStage s = new UiStage(new ScreenViewport(), noBatch());
        s.getViewport().update(W, H, true);
        s.mousePosition(() -> new GridPoint2(mouseX, mouseYFromTop));
        return s;
    }

    @Test
    void defeitoOriginal_stageComumEngoleARodaComPainelEscondido() {
        Stage s = new Stage(new ScreenViewport(), noBatch());
        s.getViewport().update(W, H, true);
        Panel p = panel(s);
        s.setScrollFocus(p.pane()); // o que os painéis faziam ao abrir
        p.root().setVisible(false); // ...e ao fechar só se escondiam
        assertTrue(s.scrolled(0, 1), "reprodução: o Stage comum consome a roda e a câmera não dá zoom");
    }

    @Test
    void painelEscondidoNaoEngoleARoda() {
        UiStage s = stage(300, H - 250); // mouse em cima de onde o painel estava
        Panel p = panel(s);
        s.setScrollFocus(p.pane());
        p.root().setVisible(false);
        for (int i = 0; i < 50; i++) assertFalse(s.scrolled(0, i % 2 == 0 ? 1 : -1), "a roda tem de ir para a câmera");
        assertNull(s.getScrollFocus());
    }

    @Test
    void listaSobOCursorRola() {
        UiStage s = stage(300, H - 250);
        Panel p = panel(s);
        assertTrue(s.scrolled(0, 3));
        assertTrue(p.pane().getScrollY() > 0, "a lista deveria ter descido");
        assertSame(p.pane(), s.getScrollFocus());
    }

    @Test
    void foraDaListaARodaVaiParaACamera() {
        UiStage s = stage(900, 100); // longe do painel
        Panel p = panel(s);
        s.setScrollFocus(p.pane()); // mesmo com foco antigo
        assertFalse(s.scrolled(0, 1));
        assertEquals(0, p.pane().getScrollY());
    }

    @Test
    void listaQueCabeInteiraNaoPrendeARoda() {
        UiStage s = stage(300, H - 250);
        Actor small = new Actor();
        small.setSize(100, 50);
        ScrollPane pane = new ScrollPane(small, new ScrollPane.ScrollPaneStyle());
        Table root = new Table();
        root.setBounds(100, 100, 400, 300);
        root.add(pane).grow();
        s.addActor(root);
        root.validate();
        assertFalse(s.scrolled(0, 1), "sem nada para rolar, a roda continua dando zoom");
    }
}
