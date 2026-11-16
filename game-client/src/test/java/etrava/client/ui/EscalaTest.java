package etrava.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.viewport.ScreenViewport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Escala da interface (SPEC-visual §8.2) nas 8 resoluções do VT-09. */
class EscalaTest {
    @BeforeAll
    static void headless() {
        etrava.client.Headless.start();
    }

    @ParameterizedTest(name = "{0}x{1} → k={2}, tela virtual {3}x{4}")
    @CsvSource({
        "1280,720,1,1280,720",
        "1366,768,1,1366,768",
        "1600,900,1,1600,900",
        "1920,1080,2,960,540",
        "2560,1080,2,1280,540",
        "2560,1440,2,1280,720",
        "3840,2160,4,960,540",
        "1280,1024,1,1280,1024"
    })
    void escalaDasResolucoes(int w, int h, int k, int virtualW, int virtualH) {
        assertEquals(k, Escala.k(h));
        etrava.client.Headless.screen(w, h);
        ScreenViewport vp = new ScreenViewport();
        assertEquals(k, Escala.aplicar(vp, w, h));
        assertEquals(virtualW, vp.getWorldWidth(), 0.01f);
        assertEquals(virtualH, vp.getWorldHeight(), 0.01f);
    }

    @Test
    void nuncaMenorQueUm() {
        assertEquals(1, Escala.k(100));
        assertEquals(1, Escala.k(539));
        assertEquals(1, Escala.k(540));
        assertEquals(2, Escala.k(1079 + 1));
    }
}
