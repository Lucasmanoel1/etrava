package etrava.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/**
 * Lista rolável padrão de todas as telas (V0, VT-08): barra vertical sempre visível
 * quando o conteúdo não cabe, sem rolagem horizontal, sem "esticar" além do fim e
 * sem arrastar o conteúdo com o mouse (que roubava cliques nos itens). A roda segue o
 * cursor ({@link UiStage}); o teclado rola pelo {@link UiStage} também.
 * O visual definitivo da barra vem com o tema novo (V7).
 */
public final class Scrolls {
    /** Largura da barra, em pixels da interface. */
    public static final float BAR = 9;

    private Scrolls() {}

    public static ScrollPane of(Actor content, Texture white, Color track, Color knob) {
        ScrollPane.ScrollPaneStyle style = new ScrollPane.ScrollPaneStyle();
        style.vScroll = sized(new TextureRegionDrawable(white).tint(track), BAR, 1);
        style.vScrollKnob = sized(new TextureRegionDrawable(white).tint(knob), BAR, 24);
        return of(content, style);
    }

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

    private static Drawable sized(Drawable d, float w, float h) {
        if (d instanceof BaseDrawable b) {
            b.setMinWidth(w);
            b.setMinHeight(h);
        }
        return d;
    }
}
