package etrava.client.debug;

import com.badlogic.gdx.Input;

/**
 * Modo de depuração (VT-02, SPEC-visual §7.6). Ligado com {@code --debug},
 * {@code ETRAVA_DEBUG=1} ou {@code -Detrava.dev=true} (o {@code gradlew run} liga).
 * Desligado, nada técnico aparece e F1/F3 não fazem nada. Ligado, o painel técnico
 * (FPS, posição, semente, velocidade, zoom) começa <b>escondido</b> e F3 o mostra;
 * F1 abre o console.
 */
public final class DebugMode {
    private final boolean enabled;
    private boolean panel;

    public DebugMode(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() { return enabled; }

    /** O painel técnico está na tela. */
    public boolean panelVisible() { return enabled && panel; }

    /** Trata F3; devolve true se a tecla foi usada. F1 (console) fica com quem tem o console. */
    public boolean keyDown(int keycode) {
        if (!enabled || keycode != Input.Keys.F3) return false;
        panel = !panel;
        return true;
    }
}
