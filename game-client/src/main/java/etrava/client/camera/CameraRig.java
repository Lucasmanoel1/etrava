package etrava.client.camera;

import com.badlogic.gdx.math.Matrix4;

/**
 * Câmera como componente substituível (RF-024). O resto do jogo só conversa com
 * ela por posições de mundo; ninguém assume ângulo ou coordenada de tela.
 */
public interface CameraRig {
    /** Segue o ponto de mundo (x, y) com suavização. */
    void update(float deltaSeconds, double targetX, double targetY);

    /** Coloca a câmera direto no ponto, sem suavização. */
    void snapTo(double targetX, double targetY);

    void resize(int width, int height);

    /** Positivo afasta, negativo aproxima; respeita a faixa permitida. */
    void zoomBy(float amount);

    Matrix4 combined();

    /** Converte um ponto da tela no ponto do chão sob ele. {@code out} recebe (x, y) de mundo. */
    void screenToWorld(int screenX, int screenY, double[] out);

    /** Retângulo visível em pixels de mundo: {minX, minY, maxX, maxY}. */
    float[] visibleBounds();
}
