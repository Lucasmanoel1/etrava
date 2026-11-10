package etrava.client.camera;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import etrava.client.render.IsoProjection;

/** Câmera isométrica 2D: segue o jogador com suavização e tem zoom em faixa limitada (RF-020/021). */
public final class IsoCameraRig implements CameraRig {
    public static final float MIN_ZOOM = 0.6f;
    public static final float MAX_ZOOM = 2.2f;
    private static final float FOLLOW_SPEED = 8f;
    /** Mira um pouco acima dos pés, para o personagem ficar no centro da tela. */
    private static final float TARGET_HEIGHT = 0.9f;

    private final OrthographicCamera camera = new OrthographicCamera();
    private final Vector3 tmp = new Vector3();
    private final float[] bounds = new float[4];

    public IsoCameraRig() {
        camera.zoom = 1.5f;
    }

    @Override
    public void update(float dt, double targetX, double targetY) {
        float tx = IsoProjection.sx(targetX, targetY);
        float ty = IsoProjection.sy(targetX, targetY, TARGET_HEIGHT);
        float k = 1f - (float) Math.exp(-FOLLOW_SPEED * dt);
        camera.position.x += (tx - camera.position.x) * k;
        camera.position.y += (ty - camera.position.y) * k;
        camera.update();
    }

    @Override
    public void snapTo(double targetX, double targetY) {
        camera.position.set(IsoProjection.sx(targetX, targetY),
                IsoProjection.sy(targetX, targetY, TARGET_HEIGHT), 0);
        camera.update();
    }

    @Override
    public void resize(int width, int height) {
        float x = camera.position.x, y = camera.position.y;
        camera.setToOrtho(false, width, height);
        camera.position.set(x, y, 0);
        camera.update();
    }

    @Override
    public void zoomBy(float amount) {
        camera.zoom = MathUtils.clamp(camera.zoom + amount, MIN_ZOOM, MAX_ZOOM);
        camera.update();
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
