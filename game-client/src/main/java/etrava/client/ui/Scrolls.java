package etrava.client.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;

/**
 * Lista rolável padrão de todas as telas (V0, VT-08): barra vertical sempre visível
 * quando o conteúdo não cabe, sem rolagem horizontal, sem "esticar" além do fim e
 * sem arrastar o conteúdo com o mouse (que roubava cliques nos itens). A roda segue o
 * cursor ({@link UiStage}); o teclado rola pelo {@link UiStage} também.
 * O visual da barra é o do tema ({@link Tema#rolagem}).
 */
public final class Scrolls {
    private Scrolls() {}

    /** A rolagem padrão com o visual do tema ({@link Tema#rolagem}); mesmas regras de {@link #of}. */
    public static ScrollPane of(Actor content, ScrollPane.ScrollPaneStyle style) {
        ScrollPane pane = new ScrollPane(content, style);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        pane.setOverscroll(false, false);
        pane.setFlickScroll(false);
        pane.setScrollbarsOnTop(false);
        pane.setVariableSizeKnobs(true);
        return pane;
    }
}
