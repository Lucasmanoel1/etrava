package etrava.client.render;

/**
 * Projeção isométrica 2:1 (D-018). Mundo: x para leste, y para sul, z para cima,
 * em metros. Tela: pixels de mundo, com y para cima.
 * Um tile de 1 m ocupa 128×64 px.
 */
public final class IsoProjection {
    public static final float HALF_W = 64f;
    public static final float HALF_H = 32f;
    /** Pixels por metro de altura. */
    public static final float Z_PX = 56f;

    private IsoProjection() {}

    public static float sx(double x, double y) {
        return (float) ((x - y) * HALF_W);
    }

    public static float sy(double x, double y, double z) {
        return (float) (-(x + y) * HALF_H + z * Z_PX);
    }

    /** Inversa no plano do chão (z = 0). */
    public static double worldX(float sx, float sy) {
        return (sx / HALF_W - sy / HALF_H) / 2.0;
    }

    public static double worldY(float sx, float sy) {
        return (-sy / HALF_H - sx / HALF_W) / 2.0;
    }
}
