package etrava.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.util.function.Supplier;

/**
 * Stage da interface com a regra da roda do mouse (D-088, SPEC-visual §8.1): a roda vai
 * para a lista ({@link ScrollPane}) que está <b>sob o cursor</b> e pode rolar; se não houver
 * nenhuma, este Stage não consome a roda e ela segue para a câmera (zoom).
 * <p>
 * Motivo: o {@code ScrollPane} do libGDX cancela (consome) todo evento de roda que recebe,
 * mesmo invisível e mesmo sem nada para rolar. O {@code Stage} comum entrega a roda ao
 * "foco de rolagem", e os painéis davam esse foco à sua lista ao abrir e só se escondiam
 * ao fechar: a lista invisível continuava engolindo a roda e o zoom parava, até o painel
 * ser reconstruído por outro motivo.
 */
public class UiStage extends Stage {
    private final Vector2 tmp = new Vector2();
    private Supplier<GridPoint2> mouse = () -> new GridPoint2(Gdx.input.getX(), Gdx.input.getY());

    public UiStage(Viewport viewport) {
        super(viewport); // o Stage cria e descarta o próprio SpriteBatch
    }

    public UiStage(Viewport viewport, Batch batch) {
        super(viewport, batch);
    }

    /** Posição do mouse em coordenadas de tela (y para baixo). Troca-se nos testes. */
    void mousePosition(Supplier<GridPoint2> mouse) {
        this.mouse = mouse;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        ScrollPane pane = scrollableUnderMouse();
        setScrollFocus(pane);
        if (pane == null) return false;
        return super.scrolled(amountX, amountY);
    }

    /**
     * Setas, Page Up/Down, Home e End rolam a lista sob o cursor ou, se não houver, a primeira
     * lista visível com o que rolar. Sem lista na tela, a tecla segue adiante (andar com as setas).
     */
    @Override
    public boolean keyDown(int keycode) {
        if (super.keyDown(keycode)) return true; // campo de texto com foco (console) tem prioridade
        float line = 40;
        float delta = switch (keycode) {
            case com.badlogic.gdx.Input.Keys.UP -> -line;
            case com.badlogic.gdx.Input.Keys.DOWN -> line;
            case com.badlogic.gdx.Input.Keys.PAGE_UP -> -Float.MAX_VALUE / 4;
            case com.badlogic.gdx.Input.Keys.PAGE_DOWN -> Float.MAX_VALUE / 4;
            case com.badlogic.gdx.Input.Keys.HOME -> Float.NEGATIVE_INFINITY;
            case com.badlogic.gdx.Input.Keys.END -> Float.POSITIVE_INFINITY;
            default -> 0;
        };
        if (delta == 0) return false;
        ScrollPane pane = scrollableUnderMouse();
        if (pane == null) pane = firstScrollable(getRoot());
        if (pane == null) return false;
        if (Float.isInfinite(delta)) pane.setScrollY(delta < 0 ? 0 : pane.getMaxY());
        else if (Math.abs(delta) > line) pane.setScrollY(pane.getScrollY() + Math.signum(delta) * pane.getScrollHeight() * 0.9f);
        else pane.setScrollY(pane.getScrollY() + delta);
        return true;
    }

    /** Primeira lista visível (com todos os pais visíveis) que tem o que rolar, em profundidade. */
    static ScrollPane firstScrollable(Actor actor) {
        if (!actor.isVisible()) return null;
        if (actor instanceof ScrollPane pane && pane.isScrollY()) return pane;
        if (actor instanceof com.badlogic.gdx.scenes.scene2d.Group g) {
            for (Actor child : g.getChildren()) {
                ScrollPane found = firstScrollable(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** A lista mais interna sob o cursor que tem o que rolar, ou null. Ator invisível nunca é atingido. */
    ScrollPane scrollableUnderMouse() {
        GridPoint2 m = mouse.get();
        screenToStageCoordinates(tmp.set(m.x, m.y));
        Actor hit = hit(tmp.x, tmp.y, false);
        for (Actor a = hit; a != null; a = a.getParent()) {
            if (a instanceof ScrollPane pane && (pane.isScrollY() || pane.isScrollX())) return pane;
        }
        return null;
    }
}
