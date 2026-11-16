package etrava.client.ui;

import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/**
 * O ponto único da escala da interface (SPEC-visual §8.2): {@code k = max(1, ⌊altura/540⌋)}.
 * A interface é desenhada numa tela virtual de 540 a 1079 de altura e ampliada pelo inteiro
 * {@code k}, o que mantém nítidos os 9-patches de pixel art. Os Stages usam um
 * {@link ScreenViewport} com {@code unitsPerPixel = 1/k}; a fonte é gerada em {@code k} vezes o
 * tamanho lógico e entra no Stage com escala {@code 1/k} ({@link Fonts}).
 */
public final class Escala {
    /** Altura lógica mínima da tela virtual da interface. */
    public static final int ALTURA_BASE = 540;

    /**
     * Só para depuração e capturas: {@code ETRAVA_ESCALA=N} fixa o k (uma janela de captura é menor que a
     * tela pedida e o sistema a encolhe, então a altura real nunca chega a 1080 ou 2160).
     */
    private static final int FORCADO = forcado();

    private static int forcado() {
        try {
            String v = System.getenv("ETRAVA_ESCALA");
            return v == null ? 0 : Math.max(0, Integer.parseInt(v.trim()));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private Escala() {}

    /** O inteiro de ampliação para uma janela com esta altura, em pixels. */
    public static int k(int altura) {
        if (FORCADO > 0) return FORCADO;
        return Math.max(1, altura / ALTURA_BASE);
    }

    /** Aplica a escala ao viewport do Stage e o atualiza para a janela; devolve o {@code k} usado. */
    public static int aplicar(Viewport viewport, int largura, int altura) {
        int k = k(altura);
        if (viewport instanceof ScreenViewport sv) sv.setUnitsPerPixel(1f / k);
        viewport.update(largura, altura, true);
        return k;
    }
}
