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
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import etrava.client.assets.Assets;
import etrava.client.camera.CameraRig;
import etrava.client.camera.IsoCameraRig;
import etrava.client.debug.DebugConsole;
import etrava.client.render.DayNight;
import etrava.client.render.IsoProjection;
import etrava.client.render.SceneRenderer;
import etrava.client.render.SceneRenderer.ActorDraw;
import etrava.client.ui.GameUi;
import etrava.client.ui.Texts;
import etrava.sim.api.ActorView;
import etrava.sim.api.DialogueView;
import etrava.sim.api.Command;
import etrava.sim.api.SimEvent;
import etrava.sim.api.Simulation;
import etrava.sim.api.WorldView;
import etrava.sim.content.ContentDb;
import etrava.sim.nav.NavGrid;
import etrava.sim.rules.Effect;
import etrava.sim.save.SaveGame;
import etrava.sim.validation.ContentValidator;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Laço principal do cliente. Roda a simulação em passo fixo, envia a entrada do
 * jogador como comandos e desenha o estado. Nunca altera o estado diretamente.
 */
public final class EtravaGame extends ApplicationAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(EtravaGame.class);
    private static final String DEFAULT_AREA = "vila_ref";
    private static final float MAX_FRAME_TIME = 0.25f;

    private static volatile String crashContext = "jogo ainda não iniciado";

    private final LaunchOptions options;
    private ContentDb content;
    private Simulation sim;
    private Assets assets;
    private SceneRenderer scene;
    private DayNight dayNight;
    private CameraRig camera;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private DebugConsole console;

    private float accumulator;
    private float timeScale = 1f;
    private boolean paused;
    private boolean showCollision;
    private boolean showHud = true;
    private double lastDirX, lastDirY;
    private long lastDragTick = -1;
    private int frame;
    private final double[] worldPoint = new double[2];
    /** Posições do tick anterior, por personagem, para interpolar o desenho. */
    private final Map<String, double[]> previous = new HashMap<>();
    private GameUi ui;
    private Texts texts;
    private Texture whitePx;
    private final GlyphLayout layout = new GlyphLayout();
    private final float[] px = new float[2];
    /** Legendas sobre quem fala: {NPC, texto, segundos restantes}. */
    private final List<Object[]> balloons = new ArrayList<>();
    private String hoverNpc;
    private String notice = "";
    private float noticeTime;
    private int autosaveCount;
    private final List<ActorDraw> draws = new ArrayList<>();

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
            ContentValidator.Report report = ContentValidator.validate(options.contentDir(), options.assetsDir());
            for (String p : report.errors()) LOG.warn("Validador: {}", p);
        }
        long seed = options.seed() != null ? options.seed() : System.nanoTime();
        String areaId = options.area() != null ? options.area() : DEFAULT_AREA;
        if (content.area(areaId) == null && !content.areas().isEmpty()) {
            String fallback = content.areas().keySet().iterator().next();
            LOG.warn("Área '{}' não existe; abrindo '{}'", areaId, fallback);
            areaId = fallback;
        }
        sim = Simulation.newGame(content, seed, areaId);
        LOG.info("Novo jogo: semente={}, área={}", seed, areaId);

        assets = new Assets(options.assetsDir());
        dayNight = DayNight.load(options.assetsDir());
        camera = new IsoCameraRig();
        camera.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        rebuildScene();

        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        Pixmap white = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        white.setColor(1, 1, 1, 1);
        white.fill();
        whitePx = new Texture(white);
        white.dispose();
        texts = new Texts(options.contentDir());
        ui = new GameUi(font, assets, content, texts);
        ui.onChoice(this::chooseDialogue);
        ui.onToggleMark(f -> sim.submit(new Command.MarkFact(f, !sim.view().markedFacts().contains(f))));

        InputMultiplexer input = new InputMultiplexer();
        input.addProcessor(new GlobalKeys());
        if (options.dev()) {
            console = new DebugConsole(font, this::runConsoleCommand);
            input.addProcessor(console.stage());
            for (String cmd : options.commands()) console.run(cmd);
        }
        input.addProcessor(ui.stage());
        input.addProcessor(new GameInput());
        Gdx.input.setInputProcessor(input);
    }

    @Override
    public void resize(int width, int height) {
        if (width == 0 || height == 0) return;
        camera.resize(width, height);
        scene.resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        if (console != null) console.resize(width, height);
        ui.resize(width, height);
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), MAX_FRAME_TIME);
        WorldView v = sim.view();

        if (!paused && !ui.epilogueOpen()) { // o epílogo é lido com o mundo parado
            pollMovementKeys();
            accumulator += dt * timeScale;
            while (accumulator >= Simulation.DT) {
                capturePrevious(v);
                List<SimEvent> events = sim.step();
                accumulator -= (float) Simulation.DT;
                if (!events.isEmpty()) handleEvents(events);
                v = sim.view();
            }
        }
        v = sim.view();
        float alpha = paused ? 1f : (float) (accumulator / Simulation.DT);
        buildDraws(v, alpha);
        ActorDraw player = draws.get(0);
        crashContext = "semente=" + v.seed() + ", " + v.clock().format() + ", área=" + v.areaId();

        camera.update(dt, player.x, player.y);

        ScreenUtils.clear(0.05f, 0.05f, 0.06f, 1f);
        double hour = v.clock().minuteOfDay() / 60.0
                + (v.clock().tick() % 25) / 25.0 / 60.0; // fração do minuto, para a luz mudar sem degraus
        float animTime = (v.clock().tick() + alpha) / 30f;
        scene.render(camera, draws, hour, animTime, v.hasDestination(), v.destinationX(), v.destinationY());

        if (showCollision) drawCollisionOverlay();
        updateHover(v);
        drawBalloons(v, dt);
        if (showHud) drawHud(v);
        ui.render(v, dt);
        if (console != null) console.render(dt);

        frame++;
        if (console != null) {
            for (String timed : options.timedCommands()) {
                int colon = timed.indexOf(':');
                if (colon > 0 && Integer.parseInt(timed.substring(0, colon)) == frame) console.run(timed.substring(colon + 1));
            }
        }
        if (options.screenshot() != null && frame == options.screenshotFrames()) {
            saveScreenshot(options.screenshot());
            Gdx.app.exit();
        }
    }

    /** Monta o cenário da área atual (novo jogo, troca de área, jogo carregado). */
    private void rebuildScene() {
        if (scene != null) scene.dispose();
        scene = new SceneRenderer(sim.area(), content.props(), assets, dayNight);
        scene.propState = key -> sim.view().propState(key);
        scene.resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        WorldView v = sim.view();
        camera.snapTo(v.playerX(), v.playerY());
        previous.clear();
        capturePrevious(v);
        accumulator = 0;
    }

    private void handleEvents(List<SimEvent> events) {
        for (SimEvent e : events) {
            switch (e) {
                case SimEvent.AreaChanged a -> {
                    rebuildScene();
                    autosave();
                }
                case SimEvent.TimeSkipped t -> {
                    previous.clear(); // todo mundo mudou de lugar: nada de deslizar pela tela
                    capturePrevious(sim.view());
                    if (t.interrupted()) showNotice("Algo interrompeu " + (t.slept() ? "o sono." : "a espera."));
                    if (t.slept()) autosave();
                }
                case SimEvent.AmbientLine l -> {
                    balloons.removeIf(b -> b[0].equals(l.npcId()));
                    balloons.add(new Object[] {l.npcId(), l.text(), 2.2f + l.text().length() * 0.045f});
                }
                case SimEvent.FactLearned f -> showNotice(texts.get("caderno.atualizado"));
                case SimEvent.ChapterEnded c -> {
                    ui.showEpilogue(c.epilogue());
                    autosave();
                }
                default -> { /* nada a fazer na apresentação */ }
            }
        }
    }

    private void capturePrevious(WorldView v) {
        store(v.player());
        for (ActorView a : v.actors()) store(a);
    }

    private void store(ActorView a) {
        double[] slot = previous.computeIfAbsent(a.id(), id -> new double[2]);
        slot[0] = a.x();
        slot[1] = a.y();
    }

    private void buildDraws(WorldView v, float alpha) {
        int n = 1 + v.actors().size();
        while (draws.size() < n) draws.add(new ActorDraw());
        while (draws.size() > n) draws.remove(draws.size() - 1);
        fill(draws.get(0), v.player(), alpha);
        for (int i = 0; i < v.actors().size(); i++) fill(draws.get(i + 1), v.actors().get(i), alpha);
    }

    private void fill(ActorDraw d, ActorView a, float alpha) {
        double[] prev = previous.get(a.id());
        double px = prev != null ? prev[0] : a.x(), py = prev != null ? prev[1] : a.y();
        d.id = a.id();
        d.sprite = a.sprite();
        d.x = px + (a.x() - px) * alpha;
        d.y = py + (a.y() - py) * alpha;
        d.facing = a.facing();
        d.moving = a.moving();
    }

    // ---------------------------------------------------------------- salvar e carregar

    private void showNotice(String message) {
        notice = message;
        noticeTime = 4f;
        LOG.info(message);
    }

    private Path savePath(String name) {
        return UserDirs.saves().resolve(name + SaveGame.EXTENSION);
    }

    private String saveGame(String name) {
        try {
            sim.save(savePath(name), LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return "Jogo salvo: " + name;
        } catch (SaveGame.SaveException e) {
            LOG.warn("Falha ao salvar '{}'", name, e);
            return e.getMessage();
        }
    }

    /** Carrega um save. Se der errado, o jogo atual continua como estava (RF-365). */
    private String loadGame(String name) {
        try {
            sim = Simulation.load(content, savePath(name));
            rebuildScene();
            lastDirX = lastDirY = 0;
            return "Jogo carregado: " + name + " (" + sim.view().clock().format() + ")";
        } catch (SaveGame.SaveException e) {
            LOG.warn("Falha ao carregar '{}'", name, e);
            return e.getMessage();
        } catch (RuntimeException e) {
            LOG.warn("Falha ao carregar '{}'", name, e);
            return "Não foi possível carregar o jogo: " + e.getMessage();
        }
    }

    /** Salvamento automático rotativo em 3 arquivos, ao dormir e ao trocar de área (RF-361). */
    private void autosave() {
        String name = "auto_" + (autosaveCount++ % 3 + 1);
        String result = saveGame(name);
        LOG.info("Salvamento automático: {}", result);
    }

    private void drawHud(WorldView v) {
        batch.begin();
        float top = Gdx.graphics.getHeight() - 12;
        font.setColor(0.93f, 0.89f, 0.78f, 1f);
        font.draw(batch, v.clock().format() + (paused ? "   [PAUSADO]" : ""), 14, top);
        if (noticeTime > 0) {
            noticeTime -= Gdx.graphics.getDeltaTime();
            font.draw(batch, notice, 14, top - (options.dev() ? 40 : 20));
        }
        if (options.dev()) {
            font.setColor(0.7f, 0.7f, 0.7f, 1f);
            font.draw(batch, String.format(Locale.ROOT, "FPS %d   pos (%.1f, %.1f)   semente %d   vel x%.1f   F1 console",
                    Gdx.graphics.getFramesPerSecond(), v.playerX(), v.playerY(), v.seed(), timeScale), 14, top - 20);
        }
        batch.end();
    }

    // ---------------------------------------------------------------- NPCs: apontar, falar, legendas

    /** Qual NPC está sob o cursor (RF-004). Compara em pixels de mundo com o retângulo do sprite. */
    private void updateHover(WorldView v) {
        hoverNpc = null;
        if (v.dialogue() != null || ui.notebookOpen() || (console != null && console.isOpen())) return;
        camera.screenToWorldPx(Gdx.input.getX(), Gdx.input.getY(), px);
        double best = Double.MAX_VALUE;
        for (ActorDraw d : draws) {
            if (d == draws.get(0)) continue; // o próprio jogador
            float fx = IsoProjection.sx(d.x, d.y), fy = IsoProjection.sy(d.x, d.y, 0);
            if (px[0] < fx - 40 || px[0] > fx + 40 || px[1] < fy - 8 || px[1] > fy + 116) continue;
            if (d.x + d.y < best || hoverNpc == null) { // se houver dois, o da frente
                best = -(d.x + d.y);
                hoverNpc = d.id;
            }
        }
    }

    private void drawBalloons(WorldView v, float dt) {
        batch.begin();
        for (int i = balloons.size() - 1; i >= 0; i--) {
            Object[] b = balloons.get(i);
            b[2] = (float) b[2] - dt;
            if ((float) b[2] <= 0) {
                balloons.remove(i);
                continue;
            }
            ActorDraw who = drawOf((String) b[0]);
            if (who != null) drawLabel(who, (String) b[1], 0.80f);
        }
        if (hoverNpc != null && drawOf(hoverNpc) != null && balloons.stream().noneMatch(b -> b[0].equals(hoverNpc))) {
            var def = content.npcs().get(hoverNpc);
            drawLabel(drawOf(hoverNpc), def != null ? def.displayName() : hoverNpc, 0.55f);
        }
        batch.end();
    }

    private ActorDraw drawOf(String id) {
        for (ActorDraw d : draws) if (id.equals(d.id)) return d;
        return null;
    }

    /** Texto numa plaquinha escura acima da cabeça de um personagem. */
    private void drawLabel(ActorDraw who, String text, float backgroundAlpha) {
        camera.worldPxToScreen(IsoProjection.sx(who.x, who.y), IsoProjection.sy(who.x, who.y, 0) + 122, px);
        layout.setText(font, text, new com.badlogic.gdx.graphics.Color(0.95f, 0.91f, 0.80f, 1f), 280,
                com.badlogic.gdx.utils.Align.center, true);
        float w = Math.min(280, layout.width) + 14, h = layout.height + 12;
        float x = Math.round(px[0] - w / 2), y = Math.round(px[1]);
        batch.setColor(0.08f, 0.06f, 0.07f, backgroundAlpha);
        batch.draw(whitePx, x, y, w, h);
        batch.setColor(1, 1, 1, 1);
        font.draw(batch, layout, px[0] - 140, y + h - 6);
    }

    /** Opção do diálogo: primeiro as escolhas do NPC, depois "perguntar sobre" os fatos marcados. */
    private void chooseDialogue(int index) {
        DialogueView d = sim.view().dialogue();
        if (d == null || index < 0) return;
        if (d.choices().isEmpty()) {
            if (index == 0) sim.submit(new Command.Advance());
        } else if (index < d.choices().size()) {
            sim.submit(new Command.Choose(index));
        } else if (index - d.choices().size() < ui.askFacts().size()) {
            sim.submit(new Command.AskAbout(ui.askFacts().get(index - d.choices().size())));
        }
    }

    /** Depuração: pinta de vermelho as células onde não se anda. */
    private void drawCollisionOverlay() {
        NavGrid nav = sim.nav();
        float[] b = camera.visibleBounds();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined());
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.9f, 0.15f, 0.1f, 0.35f);
        double s = NavGrid.CELL_SIZE;
        for (int cy = 0; cy < nav.height(); cy++) {
            for (int cx = 0; cx < nav.width(); cx++) {
                if (!nav.isBlocked(cx, cy)) continue;
                double x = cx * s, y = cy * s;
                float px = IsoProjection.sx(x, y), py = IsoProjection.sy(x, y, 0);
                if (px < b[0] - 64 || px > b[2] + 64 || py < b[1] - 64 || py > b[3] + 64) continue;
                float x2 = IsoProjection.sx(x + s, y), y2 = IsoProjection.sy(x + s, y, 0);
                float x3 = IsoProjection.sx(x + s, y + s), y3 = IsoProjection.sy(x + s, y + s, 0);
                float x4 = IsoProjection.sx(x, y + s), y4 = IsoProjection.sy(x, y + s, 0);
                shapes.triangle(px, py, x2, y2, x3, y3);
                shapes.triangle(px, py, x3, y3, x4, y4);
            }
        }
        shapes.end();
    }

    // ---------------------------------------------------------------- entrada

    /** WASD relativo à câmera (RF-002): "cima" na tela é noroeste→(-1,-1) no mundo isométrico. */
    private void pollMovementKeys() {
        double dx = 0, dy = 0;
        boolean busy = sim.view().dialogue() != null || ui.notebookOpen() || (console != null && console.isOpen());
        if (!busy) {
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
                    else if (ui.epilogueOpen()) ui.closeEpilogue();
                    else if (sim.view().dialogue() != null) sim.submit(new Command.LeaveDialogue());
                    else if (ui.notebookOpen()) ui.closeNotebook();
                    else Gdx.app.exit();
                    return true;
                }
                case Input.Keys.F5 -> {
                    showNotice(saveGame("rapido"));
                    return true;
                }
                case Input.Keys.F9 -> {
                    showNotice(loadGame("rapido"));
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
            if (ui.epilogueOpen()) {
                if (keycode == Input.Keys.SPACE || keycode == Input.Keys.ENTER) ui.closeEpilogue();
                return true;
            }
            if (sim.view().dialogue() != null) { // na conversa, o teclado só escolhe falas
                if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) chooseDialogue(keycode - Input.Keys.NUM_1);
                else if (keycode == Input.Keys.SPACE || keycode == Input.Keys.ENTER) chooseDialogue(0);
                return true;
            }
            if (keycode == Input.Keys.J || keycode == Input.Keys.N) {
                ui.toggleNotebook();
                return true;
            }
            if (ui.notebookOpen()) return true;
            if (keycode == Input.Keys.SPACE) {
                paused = !paused;
                return true;
            }
            if (keycode == Input.Keys.T && !paused) { // esperar uma hora
                sim.submit(new Command.Wait(1));
                return true;
            }
            return false;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT || paused) return false;
            if (sim.view().dialogue() != null || ui.notebookOpen() || ui.epilogueOpen()) return true;
            if (hoverNpc != null) sim.submit(new Command.Talk(hoverNpc));
            else moveToScreenPoint(screenX, screenY);
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (paused || !Gdx.input.isButtonPressed(Input.Buttons.LEFT)) return false;
            if (sim.view().dialogue() != null || ui.notebookOpen() || hoverNpc != null) return true;
            long tick = sim.view().clock().tick();
            if (tick - lastDragTick >= 6) { // segurar o botão: recalcula o caminho 5 vezes por segundo
                lastDragTick = tick;
                moveToScreenPoint(screenX, screenY);
            }
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            camera.zoomBy(amountY);
            return true;
        }
    }

    // ---------------------------------------------------------------- console

    private String runConsoleCommand(String line) {
        String[] p = line.trim().split("\\s+");
        WorldView v = sim.view();
        switch (p[0].toLowerCase(Locale.ROOT)) {
            case "ajuda":
                return "pos | tp X Y | ir X Y | area ID | hora [H] | avancar HORAS | esperar HORAS | dormir\n"
                        + "salvar [NOME] | carregar [NOME] | saves | npcs | cronica | agendar HORAS TEXTO\n"
                        + "falar NPC | escolher N | caderno | sabe NPC\n"
                        + "arcos | arco ID ESTAGIO | prop CHAVE ESTADO | status NPC SITUACAO | ato ID | epilogo\n"
                        + "vel FATOR | zoom VALOR | semente | luz on/off | pos-fx on/off | colisao | hud | validar | sair";
            case "pos":
                return String.format(Locale.ROOT, "(%.2f, %.2f) em %s", v.playerX(), v.playerY(), v.areaId());
            case "tp":
                sim.submit(new Command.Teleport(num(p, 1), num(p, 2)));
                return "teletransporte pedido (ignorado se o ponto estiver bloqueado)";
            case "ir":
                sim.submit(new Command.MoveTo(num(p, 1), num(p, 2)));
                return "andando";
            case "hora":
                if (p.length > 1) { // avança até a próxima vez que o relógio marcar essa hora
                    double target = num(p, 1) % 24, now = v.clock().minuteOfDay() / 60.0;
                    double delta = (target - now + 24) % 24;
                    sim.fastForward(Math.round(delta * v.clock().ticksPerHour()));
                }
                return v.clock().format() + " (tick " + v.clock().tick() + ")";
            case "falar":
                if (p.length < 2) return "use: falar NPC";
                sim.submit(new Command.Talk(p[1]));
                return "indo falar com " + p[1];
            case "escolher":
                chooseDialogue((int) num(p, 1) - 1);
                return "";
            case "caderno":
                ui.toggleNotebook();
                return "";
            case "arcos": {
                StringBuilder sb = new StringBuilder();
                GameClockStamp stamp = new GameClockStamp(v);
                for (var def : content.arcs().values()) {
                    var state = v.arcs().get(def.id());
                    String outcome = sim.arcOutcome(def.id());
                    sb.append(def.id()).append(": ").append(state.stage()).append(outcome != null
                            ? " (desfecho " + outcome + ")" : " (prazo: " + stamp.format(state.deadline()) + ")").append("\n");
                }
                return sb.length() == 0 ? "nenhum arco" : sb.toString().trim();
            }
            case "arco":
                if (p.length < 3) return "use: arco ID ESTAGIO";
                if (!content.arcs().containsKey(p[1])) return "arco inexistente: " + p[1];
                if (content.arcs().get(p[1]).stage(p[2]) == null)
                    return "estágios de " + p[1] + ": " + String.join(", ", content.arcs().get(p[1]).stages().keySet());
                sim.applyNow(List.of(new Effect.Arc(p[1], p[2])));
                return p[1] + " agora em " + v.arcs().get(p[1]).stage();
            case "prop":
                if (p.length < 3) return "use: prop CHAVE ESTADO";
                if (content.keyedProp(p[1]) == null) return "nenhum prop com a chave " + p[1];
                if (!content.keyedProp(p[1]).stateMap().containsKey(p[2]))
                    return "estados de " + p[1] + ": " + String.join(", ", content.keyedProp(p[1]).stateMap().keySet());
                sim.applyNow(List.of(new Effect.SetProp(p[1], p[2])));
                return p[1] + ": " + p[2];
            case "status":
                if (p.length < 3 || !content.npcs().containsKey(p[1])) return "use: status NPC present|away|departed|dead";
                if (!etrava.sim.content.NpcDef.STATUSES.contains(p[2])) return "situação inválida: " + p[2];
                sim.applyNow(List.of(new Effect.NpcStatus(p[1], p[2])));
                return p[1] + ": " + v.npcStatus(p[1]);
            case "ato": // comete um ato onde o jogador está: só pesa para quem estiver vendo
                if (p.length < 2 || !content.deeds().containsKey(p[1]))
                    return "atos: " + String.join(", ", content.deeds().keySet());
                sim.applyNow(List.of(new Effect.Deed(p[1])));
                return v.chronicle().get(v.chronicle().size() - 1).text();
            case "epilogo": {
                List<String> lines = v.epilogue().isEmpty() ? sim.previewEpilogue() : v.epilogue();
                if (lines.isEmpty()) return "sem epílogo no conteúdo";
                ui.showEpilogue(lines);
                return v.epilogue().isEmpty() ? "prévia: o capítulo ainda não terminou" : "";
            }
            case "sabe": {
                if (p.length < 2) return "use: sabe NPC";
                StringBuilder sb = new StringBuilder("opinião " + v.opinion(p[1]) + " · " + v.npcStatus(p[1]) + "\n");
                v.memories(p[1]).forEach(m -> sb.append("lembra: ").append(m.type()).append(" (").append(m.subject())
                        .append(", dia ").append(m.day()).append(")\n"));
                v.npcKnowledge(p[1]).values().forEach(k -> sb.append(k.fact()).append(" (").append(k.version())
                        .append(", ").append(k.certainty()).append(", ").append(k.source()).append(")\n"));
                return sb.toString().trim();
            }
            case "esperar":
                sim.submit(new Command.Wait((int) num(p, 1)));
                return "esperando";
            case "dormir":
                sim.submit(new Command.Sleep());
                return "dormindo até de manhã";
            case "area":
                if (p.length < 2) return "áreas: " + String.join(", ", content.areas().keySet());
                if (content.area(p[1]) == null) return "área inexistente: " + p[1];
                sim.submit(new Command.EnterArea(p[1], null));
                return "indo para " + p[1];
            case "salvar":
                return saveGame(p.length > 1 ? p[1] : "manual");
            case "carregar":
                return loadGame(p.length > 1 ? p[1] : "manual");
            case "saves": {
                StringBuilder sb = new StringBuilder();
                java.io.File[] files = UserDirs.saves().toFile().listFiles((d, n) -> n.endsWith(SaveGame.EXTENSION));
                if (files != null) {
                    for (java.io.File f : files) {
                        SaveGame.Meta m = SaveGame.readMeta(f.toPath());
                        sb.append(f.getName().replace(SaveGame.EXTENSION, "")).append(": ")
                                .append(m == null ? "ilegível" : "Dia " + m.day() + ", " + m.time() + ", " + m.areaId())
                                .append("\n");
                    }
                }
                return sb.length() == 0 ? "nenhum save" : sb.toString().trim();
            }
            case "npcs": {
                StringBuilder sb = new StringBuilder();
                for (String id : content.npcs().keySet()) {
                    ActorView n = v.npc(id);
                    sb.append(String.format(Locale.ROOT, "%s: %s em %s (%.1f, %.1f)%n", id, n.activity(),
                            v.npcArea(id), n.x(), n.y()));
                }
                return sb.toString().trim();
            }
            case "cronica": {
                StringBuilder sb = new StringBuilder();
                List<etrava.sim.world.ChronicleEntry> all = v.chronicle();
                for (int i = Math.max(0, all.size() - 10); i < all.size(); i++) {
                    sb.append("tick ").append(all.get(i).tick()).append(" [").append(all.get(i).type()).append("] ")
                            .append(all.get(i).text()).append("\n");
                }
                return sb.length() == 0 ? "crônica vazia" : sb.toString().trim();
            }
            case "agendar": { // agendar HORAS TEXTO — cria um evento que interrompe sono e espera
                long at = v.clock().tick() + Math.round(num(p, 1) * v.clock().ticksPerHour());
                String text = p.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(p, 2, p.length)) : "evento";
                sim.schedule(at, true, List.of(new Effect.Chronicle("depuracao", "", text)));
                return "agendado para daqui a " + p[1] + " h";
            }
            case "avancar":
                sim.fastForward((long) (num(p, 1) * v.clock().ticksPerHour()));
                return "agora: " + v.clock().format();
            case "vel":
                timeScale = (float) Math.max(0, Math.min(20, num(p, 1)));
                return "velocidade x" + timeScale;
            case "zoom":
                ((IsoCameraRig) camera).setPixelScale((int) num(p, 1));
                return "zoom: cada pixel de arte ocupa " + (int) num(p, 1) + " px da tela (1 a 4)";
            case "semente":
                return "semente " + v.seed();
            case "luz":
                scene.lightingEnabled = onOff(p);
                return "luz " + (scene.lightingEnabled ? "ligada" : "desligada");
            case "pos-fx":
                scene.postEnabled = onOff(p);
                return "pós-processamento " + (scene.postEnabled ? "ligado" : "desligado");
            case "colisao":
                showCollision = !showCollision;
                return "";
            case "hud":
                showHud = !showHud;
                return "";
            case "validar":
                ContentValidator.Report report = ContentValidator.validate(options.contentDir(), options.assetsDir());
                List<String> out = new ArrayList<>(report.errors());
                for (String w : report.warnings()) out.add("aviso: " + w);
                return out.isEmpty() ? "conteúdo válido" : String.join("\n", out);
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

    /** Escreve um tick como "Dia N, HH:MM" (para o console). */
    private static final class GameClockStamp {
        private final etrava.sim.time.GameClock clock;

        GameClockStamp(WorldView v) {
            clock = new etrava.sim.time.GameClock(v.clock().ticksPerDay());
        }

        String format(long tick) {
            clock.setTick(tick);
            return clock.format();
        }
    }

    private static boolean onOff(String[] parts) {
        if (parts.length < 2) throw new IllegalArgumentException("use on ou off");
        return parts[1].equalsIgnoreCase("on");
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
        scene.dispose();
        ui.dispose();
        whitePx.dispose();
        assets.dispose();
        shapes.dispose();
        batch.dispose();
        font.dispose();
        if (console != null) console.dispose();
    }
}
