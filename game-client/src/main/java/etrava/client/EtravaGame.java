package etrava.client;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import etrava.client.camera.CameraRig;
import etrava.client.camera.IsoCameraRig;
import etrava.client.debug.DebugConsole;
import etrava.client.render.AreaRenderer;
import etrava.sim.api.Command;
import etrava.sim.api.Simulation;
import etrava.sim.api.WorldView;
import etrava.sim.content.ContentDb;
import etrava.sim.validation.ContentValidator;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Laço principal do cliente. Roda a simulação em passo fixo, envia a entrada do
 * jogador como comandos e desenha o estado. Nunca altera o estado diretamente.
 */
public final class EtravaGame extends ApplicationAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(EtravaGame.class);
    private static final String START_AREA = "area_teste";
    private static final float MAX_FRAME_TIME = 0.25f;

    private static volatile String crashContext = "jogo ainda não iniciado";

    private final LaunchOptions options;
    private ContentDb content;
    private Simulation sim;
    private AreaRenderer areaRenderer;
    private CameraRig camera;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private DebugConsole console;

    private float accumulator;
    private float timeScale = 1f;
    private boolean paused;
    private double prevX, prevY;
    private double lastDirX, lastDirY;
    private long lastDragTick = -1;
    private int frame;
    private final double[] worldPoint = new double[2];

    public EtravaGame(LaunchOptions options) {
        this.options = options;
    }

    /** Semente e dia de jogo, para o relatório de crash (RNF-032). */
    public static String crashContext() {
        return crashContext;
    }

    @Override
    public void create() {
        content = ContentDb.load(options.contentDir());
        if (options.dev()) {
            for (String p : ContentValidator.validate(content)) LOG.warn("Validador: {}", p);
        }
        long seed = options.seed() != null ? options.seed() : System.nanoTime();
        sim = Simulation.newGame(content, seed, START_AREA);
        LOG.info("Novo jogo: semente={}, área={}", seed, START_AREA);

        areaRenderer = new AreaRenderer(sim.area(), content.props());
        camera = new IsoCameraRig();
        camera.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        WorldView v = sim.view();
        camera.snapTo(v.playerX(), v.playerY());
        prevX = v.playerX();
        prevY = v.playerY();

        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();

        InputMultiplexer input = new InputMultiplexer();
        input.addProcessor(new GlobalKeys());
        if (options.dev()) {
            console = new DebugConsole(font, this::runConsoleCommand);
            input.addProcessor(console.stage());
            for (String cmd : options.commands()) console.run(cmd);
        }
        input.addProcessor(new GameInput());
        Gdx.input.setInputProcessor(input);
    }

    @Override
    public void resize(int width, int height) {
        if (width == 0 || height == 0) return;
        camera.resize(width, height);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        if (console != null) console.resize(width, height);
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), MAX_FRAME_TIME);
        WorldView v = sim.view();

        if (!paused) {
            pollMovementKeys();
            accumulator += dt * timeScale;
            while (accumulator >= Simulation.DT) {
                prevX = v.playerX();
                prevY = v.playerY();
                sim.step();
                accumulator -= (float) Simulation.DT;
            }
        }
        float alpha = paused ? 1f : (float) (accumulator / Simulation.DT);
        double px = prevX + (v.playerX() - prevX) * alpha;
        double py = prevY + (v.playerY() - prevY) * alpha;
        crashContext = "semente=" + v.seed() + ", " + v.clock().format() + ", área=" + v.areaId();

        camera.update(dt, px, py);

        ScreenUtils.clear(0.05f, 0.05f, 0.06f, 1f);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined());
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        areaRenderer.render(shapes, camera.visibleBounds(), px, py, v.playerFacing(),
                v.hasDestination(), v.destinationX(), v.destinationY());
        shapes.end();

        drawHud(v);
        if (console != null) console.render(dt);

        frame++;
        if (options.screenshot() != null && frame == options.screenshotFrames()) {
            saveScreenshot(options.screenshot());
            Gdx.app.exit();
        }
    }

    private void drawHud(WorldView v) {
        batch.begin();
        float top = Gdx.graphics.getHeight() - 12;
        font.setColor(0.93f, 0.89f, 0.78f, 1f);
        font.draw(batch, v.clock().format() + (paused ? "   [PAUSADO]" : ""), 14, top);
        if (options.dev()) {
            font.setColor(0.7f, 0.7f, 0.7f, 1f);
            font.draw(batch, String.format(Locale.ROOT, "FPS %d   pos (%.1f, %.1f)   semente %d   vel x%.1f   F1 console",
                    Gdx.graphics.getFramesPerSecond(), v.playerX(), v.playerY(), v.seed(), timeScale), 14, top - 20);
        }
        batch.end();
    }

    // ---------------------------------------------------------------- entrada

    /** WASD relativo à câmera (RF-002): "cima" na tela é noroeste→(-1,-1) no mundo isométrico. */
    private void pollMovementKeys() {
        double dx = 0, dy = 0;
        if (console == null || !console.isOpen()) {
            if (Gdx.input.isKeyPressed(Input.Keys.W)) { dx -= 1; dy -= 1; }
            if (Gdx.input.isKeyPressed(Input.Keys.S)) { dx += 1; dy += 1; }
            if (Gdx.input.isKeyPressed(Input.Keys.A)) { dx -= 1; dy += 1; }
            if (Gdx.input.isKeyPressed(Input.Keys.D)) { dx += 1; dy -= 1; }
        }
        if (dx != lastDirX || dy != lastDirY) {
            sim.submit(new Command.MoveDir(dx, dy));
            lastDirX = dx;
            lastDirY = dy;
        }
    }

    private void moveToScreenPoint(int screenX, int screenY) {
        camera.screenToWorld(screenX, screenY, worldPoint);
        sim.submit(new Command.MoveTo(worldPoint[0], worldPoint[1]));
    }

    /** Teclas que valem mesmo com o console aberto. */
    private final class GlobalKeys extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            switch (keycode) {
                case Input.Keys.F1 -> {
                    if (console != null) console.toggle();
                    return true;
                }
                case Input.Keys.ESCAPE -> {
                    if (console != null && console.isOpen()) console.toggle();
                    else Gdx.app.exit();
                    return true;
                }
                case Input.Keys.F12 -> {
                    String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                    saveScreenshot(UserDirs.screenshots().resolve("etrava-" + stamp + ".png"));
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }
    }

    private final class GameInput extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.SPACE) {
                paused = !paused;
                return true;
            }
            return false;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT || paused) return false;
            moveToScreenPoint(screenX, screenY);
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (paused || !Gdx.input.isButtonPressed(Input.Buttons.LEFT)) return false;
            long tick = sim.view().clock().tick();
            if (tick - lastDragTick >= 6) { // segurar o botão: recalcula o caminho 5 vezes por segundo
                lastDragTick = tick;
                moveToScreenPoint(screenX, screenY);
            }
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            camera.zoomBy(amountY * 0.1f);
            return true;
        }
    }

    // ---------------------------------------------------------------- console

    private String runConsoleCommand(String line) {
        String[] p = line.trim().split("\\s+");
        WorldView v = sim.view();
        switch (p[0].toLowerCase(Locale.ROOT)) {
            case "ajuda":
                return "pos | tp X Y | ir X Y | hora | avancar HORAS | vel FATOR | semente | validar | sair";
            case "pos":
                return String.format(Locale.ROOT, "(%.2f, %.2f) em %s", v.playerX(), v.playerY(), v.areaId());
            case "tp":
                sim.submit(new Command.Teleport(num(p, 1), num(p, 2)));
                return "teletransporte pedido (ignorado se o ponto estiver bloqueado)";
            case "ir":
                sim.submit(new Command.MoveTo(num(p, 1), num(p, 2)));
                return "andando";
            case "hora":
                return v.clock().format() + " (tick " + v.clock().tick() + ")";
            case "avancar":
                sim.fastForward((long) (num(p, 1) * v.clock().ticksPerHour()));
                return "agora: " + v.clock().format();
            case "vel":
                timeScale = (float) Math.max(0, Math.min(20, num(p, 1)));
                return "velocidade x" + timeScale;
            case "semente":
                return "semente " + v.seed();
            case "validar":
                List<String> problems = ContentValidator.validate(ContentDb.load(options.contentDir()));
                return problems.isEmpty() ? "conteúdo válido" : String.join("\n", problems);
            case "console":
                console.toggle();
                return "";
            case "sair":
                Gdx.app.exit();
                return "";
            default:
                return "comando desconhecido: " + p[0] + " (digite 'ajuda')";
        }
    }

    private static double num(String[] parts, int index) {
        if (index >= parts.length) throw new IllegalArgumentException("faltou um número");
        try {
            return Double.parseDouble(parts[index].replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("número inválido: " + parts[index]);
        }
    }

    // ---------------------------------------------------------------- captura

    private void saveScreenshot(Path file) {
        int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h);
        ByteBuffer pixels = pixmap.getPixels();
        for (int i = 3; i < w * h * 4; i += 4) pixels.put(i, (byte) 0xFF); // imagem opaca
        try {
            PixmapIO.writePNG(new FileHandle(file.toFile()), pixmap, Deflater.DEFAULT_COMPRESSION, true);
            LOG.info("Captura salva em {}", file);
        } catch (RuntimeException e) {
            LOG.warn("Não foi possível salvar a captura em {}: {}", file, e.getMessage());
        } finally {
            pixmap.dispose();
        }
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
        if (console != null) console.dispose();
    }
}
