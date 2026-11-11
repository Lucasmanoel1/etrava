package etrava.client.camera;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import etrava.client.render.IsoProjection;

/**
 * Câmera isométrica 2D: segue o jogador com suavização e tem zoom em passos
 * (RF-020/021). O jogo é pixel art, então o zoom só assume valores em que um
 * pixel de arte ocupa um número inteiro de pixels da tela (1× a 4×), e a
 * câmera para sempre em cima de um pixel da tela — é isso que evita tremedeira.
 */
public final class IsoCameraRig implements CameraRig {
    public static final int MIN_PIXEL_SCALE = 1;
    public static final int MAX_PIXEL_SCALE = 4;
    private static final float FOLLOW_SPEED = 8f;
    /** Mira um pouco acima dos pés, para o personagem ficar no centro da tela. */
    private static final float TARGET_HEIGHT = 0.9f;

    private final OrthographicCamera camera = new OrthographicCamera();
    private final Vector3 tmp = new Vector3();
    private final float[] bounds = new float[4];
    /** Posição "verdadeira" da câmera; a usada no desenho é esta, arredondada. */
    private float x, y;
    private int pixelScale = 2;
    private boolean scaleChosen;

    @Override
    public void update(float dt, double targetX, double targetY) {
        float tx = IsoProjection.sx(targetX, targetY);
        float ty = IsoProjection.sy(targetX, targetY, TARGET_HEIGHT);
        float k = 1f - (float) Math.exp(-FOLLOW_SPEED * dt);
        x += (tx - x) * k;
        y += (ty - y) * k;
        apply();
    }

    @Override
    public void snapTo(double targetX, double targetY) {
        x = IsoProjection.sx(targetX, targetY);
        y = IsoProjection.sy(targetX, targetY, TARGET_HEIGHT);
        apply();
    }

    private void apply() {
        camera.zoom = IsoProjection.ART_SCALE / (float) pixelScale;
        float step = camera.zoom; // um pixel de tela, em unidades de mundo
        camera.position.set(Math.round(x / step) * step, Math.round(y / step) * step, 0);
        camera.update();
    }

    @Override
    public void resize(int width, int height) {
        // dimensões pares, para o centro da tela cair entre dois pixels e não no meio de um
        camera.setToOrtho(false, width - width % 2, height - height % 2);
        if (!scaleChosen) { // primeira vez: escolhe o zoom pelo tamanho da janela
            pixelScale = height >= 1000 ? 3 : 2;
            scaleChosen = true;
        }
        apply();
    }

    /** Positivo afasta um passo, negativo aproxima um passo. */
    @Override
    public void zoomBy(float amount) {
        if (amount == 0) return;
        setPixelScale(pixelScale + (amount > 0 ? -1 : 1));
    }

    /** Quantos pixels da tela cada pixel de arte ocupa (1 = mais longe, 4 = mais perto). */
    public void setPixelScale(int scale) {
        pixelScale = MathUtils.clamp(scale, MIN_PIXEL_SCALE, MAX_PIXEL_SCALE);
        scaleChosen = true;
        apply();
    }

    @Override
    public Matrix4 combined() {
        return camera.combined;
    }

    @Override
    public void screenToWorld(int screenX, int screenY, double[] out) {
        camera.unproject(tmp.set(screenX, screenY, 0));
        out[0] = IsoProjection.worldX(tmp.x, tmp.y);
        out[1] = IsoProjection.worldY(tmp.x, tmp.y);
    }

    @Override
    public void screenToWorldPx(int screenX, int screenY, float[] out) {
        camera.unproject(tmp.set(screenX, screenY, 0));
        out[0] = tmp.x;
        out[1] = tmp.y;
    }

    @Override
    public void worldPxToScreen(float worldX, float worldY, float[] out) {
        camera.project(tmp.set(worldX, worldY, 0));
        out[0] = tmp.x;
        out[1] = tmp.y;
    }

    @Override
    public float[] visibleBounds() {
        float halfW = camera.viewportWidth * camera.zoom / 2f;
        float halfH = camera.viewportHeight * camera.zoom / 2f;
        bounds[0] = camera.position.x - halfW;
        bounds[1] = camera.position.y - halfH;
        bounds[2] = camera.position.x + halfW;
        bounds[3] = camera.position.y + halfH;
        return bounds;
    }
}
