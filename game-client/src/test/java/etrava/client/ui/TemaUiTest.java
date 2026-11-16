package etrava.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import etrava.client.Headless;
import etrava.client.assets.Assets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tema com placeholder, {@code Ui.esconder} e navegação por teclado (V7, base de interface). */
class TemaUiTest {
    private static BitmapFont font;
    private static Assets assets;

    @BeforeAll
    static void load() {
        Headless.start();
        Headless.screen(1280, 720);
        Path a = Path.of(System.getProperty("etrava.assets"));
        assets = new Assets(a);
        font = new Fonts(a, assets.manifest()).text(1f);
    }

    @Test
    void temaSemArquivosDevolvePlaceholder(@TempDir Path vazio) {
        Tema tema = new Tema(vazio, null, font);
        for (String id : List.of("ui.painel_madeira", "ui.botao", "ui.botao_sobre", "ui.rolagem_pegador", "ui.foco",
                "ui.sol", "ui.id_que_nao_existe")) {
            Drawable d = tema.peca(id);
            assertNotNull(d, id);
            assertSame(d, tema.peca(id), "a peça é guardada");
        }
        assertTrue(tema.pad("ui.painel_madeira")[0] > 0, "o painel de madeira tem recuo de placeholder");
        assertNotNull(tema.botao.over);
        assertNotNull(tema.botao.disabled);
        assertNotNull(tema.rolagem.vScrollKnob);
        assertNull(tema.cursor("apontar", 2), "sem imagem, o cursor do sistema fica");
        assertEquals("241C18", tema.cor("tinta").toString().toUpperCase().substring(0, 6));
        tema.dispose();
    }

    @Test
    void temaComManifestoRealEPastaVaziaNaoLancaExcecao(@TempDir Path vazio) {
        Tema tema = new Tema(vazio, assets.manifest(), font);
        assertNotNull(tema.peca("ui.painel_madeira"));
        tema.dispose();
    }

    private record Painel(UiStage stage, Table raiz, ScrollPane lista, Foco foco, List<String> log) {}

    private static Painel painel(Tema tema, Ui ui) {
        Headless.screen(1280, 720);
        UiStage stage = new UiStage(new ScreenViewport(), Headless.batch());
        Escala.aplicar(stage.getViewport(), 1280, 720);
        Table raiz = ui.painel("Teste");
        raiz.setBounds(100, 100, 400, 300);
        Table itens = new Table();
        itens.top().left();
        List<String> log = new ArrayList<>();
        Foco foco = ui.foco().aoFechar(() -> log.add("fechou"));
        for (int i = 0; i < 40; i++) {
            Label item = new Label("Item " + i, tema.tinta);
            final int n = i;
            foco.adicionar(item, () -> log.add("ativou " + n));
            itens.add(item).left().row();
        }
        ScrollPane lista = ui.rolagem(itens);
        raiz.add(lista).grow();
        raiz.addActor(foco);
        stage.addActor(raiz);
        raiz.validate();
        return new Painel(stage, raiz, lista, foco, log);
    }

    @Test
    void esconderTiraOFocoDeRolagemEDeTeclado(@TempDir Path vazio) {
        Tema tema = new Tema(vazio, null, font);
        Ui ui = new Ui(tema);
        Painel p = painel(tema, ui);
        Actor alvo = p.foco().focado() != null ? p.foco().focado() : p.lista();
        p.stage().setScrollFocus(p.lista());
        p.stage().setKeyboardFocus(p.lista());
        Ui.esconder(p.raiz());
        assertFalse(p.raiz().isVisible());
        assertNull(p.stage().getScrollFocus());
        assertNull(p.stage().getKeyboardFocus());
        assertNotNull(alvo);
        tema.dispose();
    }

    @Test
    void foco_andaAtivaEFecha(@TempDir Path vazio) {
        Tema tema = new Tema(vazio, null, font);
        Ui ui = new Ui(tema);
        Painel p = painel(tema, ui);
        assertNull(p.foco().focado(), "sem tecla, sem anel");
        assertTrue(p.stage().keyDown(Input.Keys.DOWN));
        assertEquals(0, indice(p));
        assertTrue(p.stage().keyDown(Input.Keys.TAB));
        assertTrue(p.stage().keyDown(Input.Keys.DOWN));
        assertEquals(2, indice(p));
        assertTrue(p.foco().tecla(Input.Keys.TAB, true), "Shift+Tab volta");
        assertEquals(1, indice(p));
        assertTrue(p.stage().keyDown(Input.Keys.ENTER));
        assertEquals(List.of("ativou 1"), p.log());
        assertTrue(p.stage().keyDown(Input.Keys.ESCAPE));
        assertEquals("fechou", p.log().get(1));

        // a lista rola até o item focado
        p.foco().focar(39);
        assertTrue(p.lista().getScrollY() > 0, "rolou até o último item");
        // dá a volta
        assertTrue(p.stage().keyDown(Input.Keys.DOWN));
        assertEquals(0, indice(p));

        // painel escondido não recebe teclas
        Ui.esconder(p.raiz());
        assertFalse(p.stage().keyDown(Input.Keys.ENTER) && p.log().size() > 2);
        tema.dispose();
    }

    private static int indice(Painel p) {
        Actor f = p.foco().focado();
        assertNotNull(f);
        return Integer.parseInt(((Label) f).getText().toString().substring(5));
    }

    @Test
    void medidasPorProporcaoComMaximo(@TempDir Path vazio) {
        Tema tema = new Tema(vazio, null, font);
        Ui ui = new Ui(tema);
        UiStage stage = new UiStage(new ScreenViewport(), Headless.batch());
        Escala.aplicar(stage.getViewport(), 2560, 1080); // 21:9: tela virtual 1280x540
        float w = ui.largura(stage, 0.9f, 30);
        assertTrue(w <= stage.getWidth() * 0.9f);
        assertTrue(w <= 30 * font.getLineHeight() + 0.01f, "o máximo vale em telas largas");
        tema.dispose();
    }
}
