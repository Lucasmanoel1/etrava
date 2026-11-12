package etrava.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import etrava.client.Headless;
import etrava.client.assets.Assets;
import etrava.sim.api.DialogueView;
import etrava.sim.api.TradeView;
import etrava.sim.api.WorldView;
import etrava.sim.content.ContentDb;
import etrava.sim.world.KnowledgeEntry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * VT-08 sobre as telas de verdade (conteúdo e assets do jogo): com 80 itens no comércio,
 * 12 opções na conversa e 60 fatos no caderno, a tela fica dentro da janela, a lista rola
 * pela roda (mouse em cima) e pelo teclado, e o último item fica visível e clicável.
 */
class GameUiScrollTest {
    private static ContentDb content;
    private static Assets assets;
    private static Texts texts;
    private static BitmapFont font;

    @BeforeAll
    static void load() {
        Headless.start();
        Path c = Path.of(System.getProperty("etrava.content")), a = Path.of(System.getProperty("etrava.assets"));
        content = ContentDb.load(c);
        assets = new Assets(a);
        texts = new Texts(c);
        font = new Fonts(a, assets.manifest()).text(1f); // a fonte do jogo, no tamanho normal
    }

    private record Screen(GameUi ui, UiStage stage, int w, int h) {
        void mouseAt(float stageX, float stageY) {
            stage.mousePosition(() -> new GridPoint2(Math.round(stageX), Math.round(h - stageY)));
        }
    }

    private static Screen screen(int w, int h) {
        Headless.screen(w, h);
        UiStage stage = new UiStage(new ScreenViewport(), Headless.batch());
        GameUi ui = new GameUi(font, assets, content, texts, stage);
        ui.resize(w, h);
        return new Screen(ui, stage, w, h);
    }

    private static WorldView world(Map<String, Object> answers) {
        Map<String, Object> all = new HashMap<>(Map.of("money", 40, "areaId", "vila"));
        all.putAll(answers);
        return Headless.fake(WorldView.class, all);
    }

    /** Monta e desenha (sem GL) algumas vezes, para os textos quebrados acertarem a altura. */
    private static void show(Screen s, WorldView v, TradeView t) {
        for (int i = 0; i < 3; i++) s.ui().render(v, t, null, 0);
    }

    private static Rectangle rect(Actor a) {
        Vector2 lo = a.localToStageCoordinates(new Vector2(0, 0));
        Vector2 hi = a.localToStageCoordinates(new Vector2(a.getWidth(), a.getHeight()));
        return new Rectangle(lo.x, lo.y, hi.x - lo.x, hi.y - lo.y);
    }

    private static List<ScrollPane> visiblePanes(Actor a, List<ScrollPane> out) {
        if (!a.isVisible()) return out;
        if (a instanceof ScrollPane p) out.add(p);
        if (a instanceof Group g) for (Actor c : g.getChildren()) visiblePanes(c, out);
        return out;
    }

    /** A lista com o conteúdo mais alto na tela. */
    private static ScrollPane biggestPane(Screen s) {
        List<ScrollPane> panes = visiblePanes(s.stage().getRoot(), new ArrayList<>());
        assertFalse(panes.isEmpty(), "nenhuma lista visível");
        return panes.stream().max((x, y) -> Float.compare(x.getActor().getHeight(), y.getActor().getHeight())).get();
    }

    private static void assertOnScreen(Screen s, Actor a, String what) {
        Rectangle r = rect(a);
        assertTrue(r.x >= -1 && r.y >= -1 && r.x + r.width <= s.w() + 1 && r.y + r.height <= s.h() + 1,
                what + " sai da tela " + s.w() + "x" + s.h() + ": " + r);
    }

    /** A lista rola pela roda e pelo teclado até o fim, e o último item fica visível e é o que o clique atinge. */
    private static void assertScrollsToLast(Screen s, ScrollPane pane, String what) {
        assertTrue(pane.isScrollY(), what + ": a lista deveria ter o que rolar");
        assertOnScreen(s, pane, what + " (lista)");
        Rectangle p = rect(pane);
        s.mouseAt(p.x + p.width / 2, p.y + p.height / 2);
        assertTrue(s.stage().scrolled(0, 2), what + ": a roda com o mouse em cima tem de rolar a lista");
        assertTrue(pane.getScrollY() > 0, what + ": a roda não moveu a lista");
        assertTrue(s.stage().keyDown(com.badlogic.gdx.Input.Keys.END), what + ": End deveria ir ao fim");
        pane.updateVisualScroll();
        s.stage().draw(); // posiciona o conteúdo conforme a rolagem
        assertEquals(pane.getMaxY(), pane.getScrollY(), 0.5f, what + ": End não chegou ao fim");

        Group list = (Group) pane.getActor();
        Actor last = list.getChildren().peek();
        Rectangle r = rect(last);
        assertTrue(r.y >= p.y - 1 && r.y + r.height <= p.y + p.height + 1,
                what + ": último item fora da área visível " + r + " em " + p);
        Actor hit = s.stage().hit(r.x + Math.min(20, r.width / 2), r.y + r.height / 2, true);
        boolean inside = false;
        for (Actor a = hit; a != null; a = a.getParent()) inside |= a == last;
        assertTrue(inside, what + ": o clique no último item não chega nele (atingiu " + hit + ")");
    }

    @ParameterizedTest(name = "comércio com 80 itens em {0}x{1}")
    @CsvSource({"1280,720", "1366,768", "1920,1080"})
    void comercio(int w, int h) {
        Screen s = screen(w, h);
        List<TradeView.Offer> offers = IntStream.range(0, 80)
                .mapToObj(i -> new TradeView.Offer("pao", "Pão de centeio " + (i + 1), 1, 3, true)).toList();
        List<TradeView.Offer> wants = IntStream.range(0, 30)
                .mapToObj(i -> new TradeView.Offer("faca", "Faca de ofício " + (i + 1), 1, 5, true)).toList();
        show(s, world(Map.of()), new TradeView("taberneira", "Taberneira", false, 100, 0, offers, wants, null));
        List<ScrollPane> panes = visiblePanes(s.stage().getRoot(), new ArrayList<>());
        assertEquals(2, panes.size(), "o comércio tem duas colunas roláveis");
        for (ScrollPane pane : panes) assertScrollsToLast(s, pane, "comércio");
    }

    @ParameterizedTest(name = "conversa com 12 opções em {0}x{1}")
    @CsvSource({"1280,720", "1366,768", "1920,1080"})
    void conversa(int w, int h) {
        Screen s = screen(w, h);
        List<DialogueView.Choice> choices = IntStream.range(0, 12).mapToObj(i -> new DialogueView.Choice(
                "Uma pergunta comprida o bastante para quebrar em duas linhas na caixa de conversa, número " + (i + 1) + ".",
                i % 4 == 0 ? "Lábia" : null)).toList();
        DialogueView d = Headless.fake(DialogueView.class, Map.of("npcId", "avel", "npcName", "Avel", "portrait", "retrato.avel",
                "speakerName", "Avel", "text", "Fala que você quer saber. Mas pergunta uma coisa de cada vez.",
                "choices", choices, "history", List.of(new DialogueView.Line("Avel", "Bom dia."),
                        new DialogueView.Line("Você", "Bom dia, tio."), new DialogueView.Line("Avel", "Hum."))));
        show(s, world(Map.of("dialogue", d)), null);
        ScrollPane pane = biggestPane(s);
        for (Actor a = pane; a != null && a != s.stage().getRoot(); a = a.getParent()) assertOnScreen(s, a, "conversa");
        assertScrollsToLast(s, pane, "conversa");
    }

    @ParameterizedTest(name = "caderno com 60 fatos em {0}x{1}")
    @CsvSource({"1280,720", "1366,768", "1920,1080"})
    void caderno(int w, int h) {
        Screen s = screen(w, h);
        List<KnowledgeEntry> known = content.facts().keySet().stream().limit(60).map(id -> new KnowledgeEntry(id,
                content.facts().get(id).defaultVersion(), "rumor", "taberneira", 3, "vila")).toList();
        assertEquals(60, known.size());
        s.ui().toggleNotebook();
        show(s, world(Map.of("knowledge", known)), null);
        assertScrollsToLast(s, biggestPane(s), "caderno");
    }
}
