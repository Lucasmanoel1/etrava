package etrava.client.debug;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;

/** VT-02: sem modo de depuração, nada técnico na tela; com ele, o painel começa escondido e F3 alterna. */
class DebugModeTest {
    @Test
    void jogoNormalNuncaMostraOPainel() {
        DebugMode d = new DebugMode(false);
        assertFalse(d.keyDown(Input.Keys.F3));
        assertFalse(d.keyDown(Input.Keys.F1));
        assertFalse(d.panelVisible());
    }

    @Test
    void depuracaoComecaEscondidaEF3Alterna() {
        DebugMode d = new DebugMode(true);
        assertFalse(d.panelVisible(), "nem no modo de depuração o painel aparece sozinho");
        assertTrue(d.keyDown(Input.Keys.F3));
        assertTrue(d.panelVisible());
        assertTrue(d.keyDown(Input.Keys.F3));
        assertFalse(d.panelVisible());
        assertFalse(d.keyDown(Input.Keys.A));
    }
}
