package etrava.client;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * libGDX sem janela para os testes do cliente: arquivos e nativos de verdade, GL que não
 * faz nada e uma "tela" do tamanho que o teste pedir (a câmera usa a altura da janela para
 * converter o y do mouse).
 */
public final class Headless {
    private static boolean started;
    private static int width = 1280, height = 720;

    private Headless() {}

    public static synchronized void start() {
        if (started) return;
        started = true;
        HeadlessApplicationConfiguration cfg = new HeadlessApplicationConfiguration();
        cfg.updatesPerSecond = -1;
        new HeadlessApplication(new ApplicationAdapter() {}, cfg);
        Gdx.gl = Gdx.gl20 = nothing(GL20.class);
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[] {Graphics.class},
                (p, m, a) -> switch (m.getName()) {
                    case "getWidth", "getBackBufferWidth" -> width;
                    case "getHeight", "getBackBufferHeight" -> height;
                    default -> zero(m.getReturnType());
                });
    }

    public static void screen(int w, int h) {
        width = w;
        height = h;
    }

    /** Batch que não desenha, mas tem matrizes e cor (o recorte das listas usa as matrizes). */
    public static com.badlogic.gdx.graphics.g2d.Batch batch() {
        return fake(com.badlogic.gdx.graphics.g2d.Batch.class, Map.of(
                "getTransformMatrix", new com.badlogic.gdx.math.Matrix4(),
                "getProjectionMatrix", new com.badlogic.gdx.math.Matrix4(),
                "getColor", new com.badlogic.gdx.graphics.Color(1, 1, 1, 1)));
    }

    /** Implementação vazia de uma interface: devolve zero, falso, texto vazio, coleção vazia ou null. */
    @SuppressWarnings("unchecked")
    public static <T> T nothing(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, m, a) -> {
            if (m.getName().equals("toString")) return type.getSimpleName() + "(teste)";
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            return zero(m.getReturnType());
        });
    }

    /** Como {@link #nothing}, mas com respostas por nome de método. */
    @SuppressWarnings("unchecked")
    public static <T> T fake(Class<T> type, Map<String, Object> answers) {
        T empty = nothing(type);
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (p, m, a) -> answers.containsKey(m.getName()) ? answers.get(m.getName()) : m.invoke(empty, a));
    }

    public static Object zero(Class<?> t) {
        if (t == boolean.class) return Boolean.FALSE;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == float.class) return 0f;
        if (t == double.class) return 0d;
        if (t == String.class) return "";
        if (t == List.class) return List.of();
        if (t == Map.class) return Map.of();
        if (t == Set.class) return Set.of();
        return null;
    }
}
