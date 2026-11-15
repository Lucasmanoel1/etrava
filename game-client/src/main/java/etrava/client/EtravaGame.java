package etrava.client;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
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
import etrava.client.ui.MenuUi;
import etrava.client.ui.Texts;
import etrava.sim.api.ActorView;
import etrava.sim.api.DialogueView;
import etrava.sim.api.Command;
import etrava.sim.api.SimEvent;
import etrava.sim.api.Simulation;
import etrava.sim.api.WorldView;
import etrava.sim.combat.Combat;
import etrava.sim.world.EnemyState;
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
    private static final String DEFAULT_AREA = null; // null = onde a origem começa (a oficina de Avel)
    private static final float MAX_FRAME_TIME = 0.25f;

    private static volatile String crashContext = "jogo ainda não iniciado";

    private final LaunchOptions options;
    private ContentDb content;
    private Simulation sim;
    private Assets assets;
    /** Roupa e chapéu do jogador → folha e camada (V2, assets/config/jogador.yaml). */
    private etrava.client.render.Vestimenta vestimenta;
    /** V3: o golpe pela arma (assets/config/animacoes.yaml). */
    private etrava.client.render.Animacoes animacoesCfg;
    private SceneRenderer scene;
    private DayNight dayNight;
    private CameraRig camera;
    private etrava.client.debug.DebugMode debug;
    /** Roteiro de capturas em andamento ({@code --roteiro}), ou null. */
    private etrava.client.debug.CaptureScript capture;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private etrava.client.ui.Fonts fonts;
    /** Texto das telas de papel; {@code hudFont} é o mesmo tamanho, com sombra, para o que fica solto sobre o cenário. */
    private BitmapFont font, hudFont, titleFont, consoleFont;
    private float fontScale;
    /** As opções pediram outro tamanho de texto: as telas são refeitas no começo do próximo quadro. */
    private boolean uiDirty;
    /** Tempo real de jogo desta partida, em segundos (vai no cabeçalho do save). */
    private double playedSeconds;
    /** Quem está no meio de um golpe → segundos que faltam da animação. */
    private final Map<String, Float> strikes = new HashMap<>();
    private static final float STRIKE_TIME = 0.32f;
    private Bot bot;
    private Bench bench;
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
    private MenuUi menu;
    private final InputMultiplexer gameInput = new InputMultiplexer(), menuInput = new InputMultiplexer();
    private final Map<String, Texture> thumbs = new HashMap<>();
    private Pixmap thumbPending;
    private Settings settings;
    private etrava.client.audio.AudioPlayer audio;
    /** Metros andados desde o último passo (som de passos por superfície, RF-402). */
    private double stepMeters;
    private double lastPlayerX = Double.NaN, lastPlayerY = Double.NaN;
    private Texts texts;
    private Texture whitePx;
    private final GlyphLayout layout = new GlyphLayout();
    private final float[] px = new float[2];
    /** Legendas sobre quem fala: {NPC, texto, segundos restantes}. */
    private final List<Object[]> balloons = new ArrayList<>();
    private String hoverNpc;
    /** Contêiner sob o cursor (quando não há NPC), ou null. */
    private String hoverContainer;
    /** Porta ou passagem sob o cursor (saída de área, D-064). */
    private etrava.sim.content.ContentDb.Exit hoverExit;
    /** Inimigo sob o cursor, ou null. */
    private String hoverEnemy;
    /** O botão está apertado sobre um inimigo: continua atacando até soltar (RF-220). */
    private boolean attacking;
    /** Números de dano subindo da cabeça de quem apanhou: {id, texto, segundos, cor (0 = jogador)}. */
    private final List<Object[]> floaters = new ArrayList<>();
    /** Personagem → segundos de lampejo depois de um golpe. */
    private final Map<String, Float> flashes = new HashMap<>();
    private String notice = "";
    private float noticeTime;
    private int autosaveCount;
    private final List<ActorDraw> draws = new ArrayList<>();
    /** V3: o último golpe de cada um foi o pesado (escolhe a animação do golpe). */
    private final Map<String, Boolean> pesados = new HashMap<>();
    /** V3: o último desenho de cada inimigo, para deixar o corpo no chão quando ele é abatido. */
    private final Map<String, ActorDraw> ultimoInimigo = new HashMap<>();
    /** V3: corpos no chão (só apresentação): o desenho, o tempo desde a queda e quanto dura. */
    private final List<Object[]> corpos = new ArrayList<>();
    private static final float CORPO_DURA = 25f, QUEDA = 0.6f;
    /** V3: inimigos que estão armando o golpe (o brilho do aviso sai uma vez, no começo). */
    private final java.util.Set<String> armandoAntes = new java.util.HashSet<>();
    /** V3: o jogador acabou de perder e vai se levantar quando a página da derrota fechar. */
    private boolean levantarPendente;
    private float levantarT = -1f;
    /** Por personagem: última posição desenhada, metros andados e velocidade suavizada. */
    private final java.util.Map<String, double[]> motion = new java.util.HashMap<>();
    /** Plaquinhas de texto já desenhadas neste quadro: {x, y, largura, altura}. */
    private final List<float[]> labelRects = new ArrayList<>();

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
        assets = new Assets(options.assetsDir());
        vestimenta = etrava.client.render.Vestimenta.carregar(options.assetsDir());
        animacoesCfg = etrava.client.render.Animacoes.carregar(options.assetsDir());
        assets.preloadSprites();
        settings = Settings.load();
        audio = new etrava.client.audio.AudioPlayer(options.assetsDir(), assets.manifest(), settings);
        dayNight = DayNight.load(options.assetsDir());
        camera = new IsoCameraRig();
        camera.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        fonts = new etrava.client.ui.Fonts(options.assetsDir(), assets.manifest());
        titleFont = fonts.title();
        consoleFont = fonts.overlay(1f);
        Pixmap white = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        white.setColor(1, 1, 1, 1);
        white.fill();
        whitePx = new Texture(white);
        white.dispose();
        texts = new Texts(options.contentDir());
        debug = new etrava.client.debug.DebugMode(options.dev());
        if (debug.enabled()) console = new DebugConsole(consoleFont, this::runConsoleCommand);
        buildUi();
        if (options.bench() > 0) bench = new Bench();
        applySettings();
        timeScale = options.speed();
        if (options.bot() > 0) bot = new Bot();
        if (options.script() != null) {
            capture = etrava.client.debug.CaptureScript.load(options.script(), options.scriptOut(), new CaptureHost());
        }

        // linha de comando de desenvolvimento (área, semente, comandos, captura): direto ao jogo
        boolean direct = !options.menu() && (options.area() != null || options.seed() != null || !options.commands().isEmpty()
                || !options.timedCommands().isEmpty() || options.screenshot() != null);
        if (direct) {
            long seed = options.seed() != null ? options.seed() : System.nanoTime();
            String areaId = options.area() != null ? options.area() : DEFAULT_AREA;
            if (areaId != null && content.area(areaId) == null && !content.areas().isEmpty()) {
                String fallback = content.areas().keySet().iterator().next();
                LOG.warn("Área '{}' não existe; abrindo '{}'", areaId, fallback);
                areaId = fallback;
            }
            startGame(Simulation.newGame(content, seed, areaId));
            LOG.info("Novo jogo: semente={}, área={}", seed, areaId);
            if (console != null) for (String cmd : options.commands()) console.run(cmd);
        } else {
            menu.showMain(!saveSlots(false).isEmpty());
            Gdx.input.setInputProcessor(menuInput);
        }
    }

    /**
     * Cria (ou recria) as fontes e as telas no tamanho de texto das opções (RNF-040). A fonte é
     * gerada no tamanho exato, e as telas guardam a fonte com que nasceram: por isso tudo é refeito.
     * Se um menu estava aberto (é de lá que o tamanho muda), ele volta aberto nas opções.
     */
    private void buildUi() {
        boolean reopen = menu != null && menu.isOpen(), inGame = menu != null && menu.inGame();
        if (ui != null) ui.dispose();
        if (menu != null) menu.dispose();
        if (font != null) font.dispose();
        if (hudFont != null) hudFont.dispose();
        fontScale = settings.tamanhoTexto;
        font = fonts.text(fontScale);
        hudFont = fonts.overlay(fontScale);

        ui = new GameUi(font, assets, content, texts);
        ui.onChoice(this::chooseDialogue);
        ui.onToggleMark(f -> sim.submit(new Command.MarkFact(f, !sim.view().markedFacts().contains(f))));
        ui.onCommand(c -> sim.submit(c));
        ui.onLoad(name -> {
            String result = loadGame(name);
            showNotice(result);
            if (!sim.view().dead()) ui.closeDeath();
        });
        menu = new MenuUi(font, hudFont, titleFont, texts, settings, new MenuActions());

        menuInput.clear();
        menuInput.addProcessor(new MenuKeys());
        menuInput.addProcessor(menu.stage());
        gameInput.clear();
        gameInput.addProcessor(new GlobalKeys());
        gameInput.addProcessor(menu.stage());
        if (console != null) gameInput.addProcessor(console.stage());
        gameInput.addProcessor(ui.stage());
        gameInput.addProcessor(new GameInput());
        ui.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        menu.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        uiDirty = false;

        if (reopen && inGame) {
            menu.showPause();
            menu.showOptions(menu::showPause);
        } else if (reopen) {
            boolean canContinue = !saveSlots(false).isEmpty();
            menu.showMain(canContinue);
            menu.showOptions(() -> menu.showMain(canContinue));
        }
    }

    /** Põe uma simulação em jogo (novo jogo ou carregado) e fecha os menus. */
    private void startGame(Simulation s) {
        sim = s;
        playedSeconds = 0;
        strikes.clear();
        rebuildScene();
        lastDirX = lastDirY = 0;
        paused = false;
        attacking = false;
        balloons.clear();
        floaters.clear();
        ui.closeWindows();
        ui.closeNotebook();
        ui.closeDeath();
        menu.close();
        Gdx.input.setInputProcessor(gameInput);
    }

    /** Vale as opções na hora (RF-383). */
    private void applySettings() {
        Gdx.graphics.setVSync(settings.vsync);
        Gdx.graphics.setForegroundFPS(settings.limiteFps);
        if (settings.telaCheia && !Gdx.graphics.isFullscreen()) Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        else if (!settings.telaCheia && Gdx.graphics.isFullscreen()) Gdx.graphics.setWindowedMode(1280, 720);
        if (settings.tamanhoTexto != fontScale) uiDirty = true; // refeito fora do clique que pediu a mudança
        if (bench != null) { // medindo: sem nada que segure os quadros
            Gdx.graphics.setVSync(false);
            Gdx.graphics.setForegroundFPS(0);
        }
        if (scene != null) {
            scene.postEnabled = settings.posProcessamento;
            scene.lightingEnabled = settings.luzDinamica;
        }
    }

    // ---------------------------------------------------------------- menus

    /** Lista de saves para o menu: os três manuais, o rápido e os automáticos. */
    private List<MenuUi.Slot> saveSlots(boolean saving) {
        List<MenuUi.Slot> out = new ArrayList<>();
        for (String name : MenuUi.slotNames(saving)) {
            SaveGame.Meta meta = SaveGame.readMeta(savePath(name));
            if (meta == null && !saving) continue;
            String title = texts.get("slot." + name.replaceAll("_\\d", ""), name.replaceAll("\\D", ""));
            String detail;
            if (meta == null) {
                detail = texts.get("slot.livre");
            } else {
                var area = content.areaEntry(meta.areaId());
                String when = meta.savedAt().length() >= 16 ? meta.savedAt().substring(0, 16).replace('T', ' ') : meta.savedAt();
                detail = texts.get("slot.detalhe", meta.day(), meta.time(), area != null ? area.displayName() : meta.areaId(), when);
                if (meta.playedSeconds() >= 60) detail += " · " + texts.get("slot.tempo",
                        meta.playedSeconds() / 3600, String.format("%02d", meta.playedSeconds() / 60 % 60));
            }
            out.add(new MenuUi.Slot(name, title, detail, thumbnail(name)));
        }
        return out;
    }

    private Texture thumbnail(String name) {
        Texture old = thumbs.remove(name);
        if (old != null) old.dispose();
        Path file = UserDirs.saves().resolve(name + ".png");
        if (!java.nio.file.Files.exists(file)) return null;
        try {
            Texture t = new Texture(new FileHandle(file.toFile()));
            thumbs.put(name, t);
            return t;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Guarda o quadro de agora, pequeno, para virar a miniatura do próximo save. */
    private void captureThumb() {
        if (thumbPending != null) thumbPending.dispose();
        int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
        Pixmap full = Pixmap.createFromFrameBuffer(0, 0, w, h);
        thumbPending = new Pixmap(192, 108, Pixmap.Format.RGBA8888);
        thumbPending.setFilter(Pixmap.Filter.BiLinear);
        thumbPending.drawPixmap(full, 0, 0, w, h, 0, 0, 192, 108);
        full.dispose();
    }

    private void writeThumb(String name) {
        if (thumbPending == null) return;
        try {
            ByteBuffer pixels = thumbPending.getPixels();
            for (int i = 3; i < 192 * 108 * 4; i += 4) pixels.put(i, (byte) 0xFF);
            PixmapIO.writePNG(new FileHandle(UserDirs.saves().resolve(name + ".png").toFile()), thumbPending,
                    Deflater.DEFAULT_COMPRESSION, true);
        } catch (RuntimeException e) {
            LOG.warn("Miniatura do save não gravada: {}", e.getMessage());
        }
    }

    private void openPause() {
        captureThumb();
        menu.showPause();
    }

    private final class MenuActions implements MenuUi.Actions {
        @Override public void newGame(String name, boolean contemplative) {
            Simulation s = Simulation.newGame(content, System.nanoTime(), null,
                    contemplative ? Simulation.PACE_CONTEMPLATIVE : Simulation.PACE_NORMAL);
            s.stateWorld().playerName = name == null ? "" : name;
            startGame(s);
            LOG.info("Novo jogo: semente={}, nome='{}', ritmo={}", s.view().seed(), name, contemplative ? "contemplativo" : "normal");
        }

        @Override public void load(String slot) {
            String name = slot;
            if (name == null) { // "continuar": o save mais recente
                long best = -1;
                for (String n : MenuUi.slotNames(false)) {
                    java.io.File f = savePath(n).toFile();
                    if (f.exists() && f.lastModified() > best) {
                        best = f.lastModified();
                        name = n;
                    }
                }
                if (name == null) return;
            }
            String result = loadGame(name);
            showNotice(result);
        }

        @Override public void save(String slot) {
            String result = saveGame(slot);
            writeThumb(slot);
            showNotice(result);
            menu.close();
        }

        @Override public void resume() { menu.close(); }

        @Override public void toMainMenu() {
            menu.showMain(!saveSlots(false).isEmpty());
            Gdx.input.setInputProcessor(menuInput);
        }

        @Override public void quit() { Gdx.app.exit(); }
        @Override public void settingsChanged() { applySettings(); }
        @Override public List<MenuUi.Slot> slots(boolean forSaving) { return saveSlots(forSaving); }
        @Override public String originName() { return content.origin().name(); }
        @Override public String originDescription() {
            return content.origin().description() != null ? content.origin().description() : "";
        }
        @Override public boolean canSave() {
            return sim != null && sim.view().dialogue() == null && sim.view().trade() == null && !sim.view().dead()
                    && !sim.combat().inCombat();
        }
    }

    /** Teclas na tela do menu principal: Esc volta um nível. */
    private final class MenuKeys extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.ESCAPE) {
                menu.escape();
                return true;
            }
            return false;
        }
    }

    /** Fundo do menu principal: a ilustração da vila ao entardecer, cobrindo a tela. */
    private void renderMenuScreen(float dt) {
        ScreenUtils.clear(0.05f, 0.04f, 0.04f, 1f);
        Texture bg = assets.texture("ilustracao.menu");
        int sw = Gdx.graphics.getWidth(), sh = Gdx.graphics.getHeight();
        batch.getProjectionMatrix().setToOrtho2D(0, 0, sw, sh);
        batch.begin();
        if (bg != null) {
            float scale = Math.max(sw / (float) bg.getWidth(), sh / (float) bg.getHeight());
            float w = bg.getWidth() * scale, h = bg.getHeight() * scale;
            batch.draw(bg, (sw - w) / 2, (sh - h) / 2, w, h);
        }
        batch.end();
        audio.music("musica.menu");
        audio.ambience(null, false);
        audio.update(dt);
        menu.render(dt);
        frame++;
        if (options.screenshot() != null && frame == options.screenshotFrames()) {
            saveScreenshot(options.screenshot());
            Gdx.app.exit();
        }
        if (capture != null) capture.afterFrame();
    }

    @Override
    public void resize(int width, int height) {
        if (width == 0 || height == 0) return;
        camera.resize(width, height);
        if (scene != null) scene.resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        menu.resize(width, height);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        if (console != null) console.resize(width, height);
        ui.resize(width, height);
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), MAX_FRAME_TIME);
        if (uiDirty) buildUi();
        if (bot != null) bot.update(dt);
        if (sim == null || (menu.isOpen() && !menu.inGame())) {
            renderMenuScreen(dt);
            return;
        }
        WorldView v = sim.view();
        if (!menu.isOpen()) playedSeconds += dt;

        if (paused || menu.isOpen() || ui.epilogueOpen() || ui.inventoryOpen() || ui.characterOpen() || ui.mapOpen()) {
            // mundo parado (pausa, epílogo, mochila, ficha): só os comandos de mochila valem, sem gastar tempo
            List<SimEvent> events = sim.stepPaused();
            if (!events.isEmpty()) handleEvents(events);
        } else {
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
        scene.darkArea = sim.combat().darkArea();
        var areaEntry = content.areaEntry(v.areaId());
        scene.interior = areaEntry != null && areaEntry.interior();
        scene.playerLight = sim.combat().lightRadius();
        scene.playerLamp = sim.combat().equipped("luz") > 0;
        scene.render(camera, draws, hour, animTime, v.hasDestination(), v.destinationX(), v.destinationY());

        if (showCollision) drawCollisionOverlay();
        updateAudio(v, hour, dt);
        updateHover(v);
        drawBalloons(v, dt);
        drawCombatOverlay(v, dt);
        if (showHud) drawHud(v);
        ui.render(v, sim.tradeView(), sim.economy(), dt);
        menu.render(dt);
        if (console != null) console.render(dt);
        if (bench != null) bench.frame();

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
        if (capture != null) capture.afterFrame();
    }

    /** O que o roteiro de capturas pode fazer no jogo (V0). */
    private final class CaptureHost implements etrava.client.debug.CaptureScript.Host {
        @Override public void newGame(long seed, String area) {
            startGame(Simulation.newGame(content, seed, area));
        }

        @Override public String console(String command) {
            String out = runConsoleCommand(command);
            if (out != null && !out.isBlank()) LOG.info("roteiro> {} → {}", command, out.replace('\n', ' '));
            return out;
        }

        @Override public void windowSize(int w, int h) {
            if (Gdx.graphics.getWidth() != w || Gdx.graphics.getHeight() != h) Gdx.graphics.setWindowedMode(w, h);
        }

        @Override public void textScale(float scale) {
            if (settings.tamanhoTexto != scale) {
                settings.tamanhoTexto = scale;
                uiDirty = true;
            }
        }

        @Override public void requestPlayerMask() {
            if (scene != null) scene.capturePlayerMask = true;
        }

        @Override public Pixmap playerMask() {
            if (scene == null) return null;
            Pixmap m = scene.playerMask;
            scene.playerMask = null;
            return m;
        }

        @Override public Pixmap grabScreen() {
            return Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        }

        @Override public int pixelScale() { return camera.pixelScale(); }
        @Override public String clock() { return sim == null ? "menu" : sim.view().clock().format(); }
        @Override public void exit() { Gdx.app.exit(); }
    }

    /** Monta o cenário da área atual (novo jogo, troca de área, jogo carregado). */
    private void rebuildScene() {
        if (scene != null) scene.dispose();
        var here = content.areaEntry(sim.view().areaId());
        scene = new SceneRenderer(sim.area(), content.props(), assets, dayNight,
                here == null || !here.interior() && !here.dark());
        scene.propState = key -> sim.view().propState(key);
        scene.postEnabled = settings.posProcessamento;
        scene.lightingEnabled = settings.luzDinamica;
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
                    corpos.clear();
                    ultimoInimigo.clear();
                    audio.sfx("sfx.porta", 0.7f, 1f);
                    autosave();
                }
                case SimEvent.PlaceDiscovered p -> showNotice(texts.get("lugar.novo", p.place()));
                case SimEvent.PropChanged p -> {
                    if ("arvore_nalia".equals(p.key()) && "viva".equals(p.state())) audio.sfx("sfx.fenomeno.arvore");
                }
                case SimEvent.ItemUsed u -> {
                    var item = content.items().get(u.itemId());
                    if (item != null && item.document()) {
                        audio.ui("sfx.ui.pagina");
                        ui.showDocument(item.displayName(), item.text() != null ? item.text() : item.description());
                    } else if (item != null && item.food() > 0) {
                        audio.sfx("sfx.comer");
                    } else {
                        audio.sfx("sfx.item");
                    }
                }
                case SimEvent.TimeSkipped t -> {
                    previous.clear(); // todo mundo mudou de lugar: nada de deslizar pela tela
                    capturePrevious(sim.view());
                    if (t.interrupted()) showNotice("Algo interrompeu " + (t.slept() ? "o sono." : "a espera."));
                    if (t.slept()) autosave();
                }
                case SimEvent.AmbientLine l -> {
                    balloons.removeIf(b -> b[0].equals(l.npcId()));
                    balloons.add(new Object[] {l.npcId(), l.text(), (2.2f + l.text().length() * 0.045f) * settings.velocidadeTexto});
                }
                case SimEvent.FactLearned f -> {
                    showNotice(texts.get("caderno.atualizado"));
                    audio.ui("sfx.ui.anotado");
                }
                case SimEvent.JobFinished j -> {
                    var job = content.jobs().get(j.jobId());
                    String hours = String.format(Locale.ROOT, "%.1f", j.hours()).replace('.', ',');
                    showNotice(texts.get(j.interrupted() ? "trabalho.interrompido" : "trabalho.feito",
                            job != null ? job.displayName() : j.jobId(), hours, ui.coins(j.pay())));
                    if (j.pay() > 0) audio.sfx("sfx.moedas");
                    if (j.heard() != null) {
                        var giver = content.npcs().get(j.giver());
                        balloons.add(new Object[] {"jogador", texts.get("trabalho.ouviu",
                                giver != null ? giver.displayName() : j.giver(), j.heard()), 7f});
                    }
                    previous.clear();
                    capturePrevious(sim.view());
                }
                case SimEvent.JobRefused r -> showNotice(texts.get("trabalho.recusado"));
                case SimEvent.DeedSeen d -> showNotice(texts.get("ato.visto"));
                case SimEvent.Hit h -> {
                    String who = h.target();
                    floaters.add(new Object[] {who, h.damage() <= 0 ? texts.get("combate.errou")
                            : String.valueOf(Math.max(1, Math.round(h.damage()))), 1.1f, Combat.PLAYER.equals(who) ? 0 : 1});
                    if (h.damage() > 0) flashes.put(who, 0.25f);
                    strikes.put(h.attacker(), STRIKE_TIME);
                    pesados.put(h.attacker(), h.heavy());
                    if (h.damage() > 0 && scene != null) {
                        double[] pos = posicao(who);
                        if (pos != null) {
                            String tipo = tipoInimigo(who);
                            scene.particulas.impacto(pos[0], pos[1], !"vulto".equals(tipo), "vulto".equals(tipo), h.heavy());
                        }
                    }
                    if (h.damage() <= 0) audio.sfx("sfx.combate.golpe");
                    else audio.sfx(Combat.PLAYER.equals(who) ? "sfx.combate.dano" : "sfx.combate.acerto",
                            h.heavy() ? 1f : 0.8f, h.heavy() ? 0.8f : 1f);
                }
                case SimEvent.EnemiesAppeared a -> {
                    showNotice(texts.get("combate.apareceram"));
                    if (a.encounterId().startsWith("lobos")) audio.sfx("sfx.combate.uivo");
                    if (a.encounterId().startsWith("vulto")) audio.sfx("sfx.fenomeno.eco");
                }
                case SimEvent.EnemyChanged c -> {
                    if ("morreu".equals(c.what()) || "executado".equals(c.what())) deixarCorpo(c.enemyId());
                    if (c.line() != null) {
                        balloons.removeIf(b -> b[0].equals(c.enemyId()));
                        balloons.add(new Object[] {c.enemyId(), c.line(), 2.2f + c.line().length() * 0.045f});
                    }
                    if ("desistiu".equals(c.what()) || "fugiu".equals(c.what())) showNotice(texts.get("combate." + c.what()));
                    if ("rendeu".equals(c.what())) showNotice(texts.get("combate.rendeu"));
                }
                case SimEvent.Looted l -> {
                    showNotice(texts.get("combate.pegou", itemNames(l.items()), ui.coins(l.money())));
                    audio.sfx("sfx.moedas");
                }
                case SimEvent.Refused r -> showNotice(texts.get(r.reason()));
                case SimEvent.PlayerSurrendered s -> showNotice(texts.get("combate.rendicao", ui.coins(s.moneyLost())));
                case SimEvent.PlayerDefeated d -> {
                    attacking = false;
                    levantarPendente = true;
                    audio.sfx("sfx.derrota");
                    previous.clear();
                    capturePrevious(sim.view());
                    List<String> lines = new ArrayList<>();
                    long hours = Math.round((d.toTick() - d.fromTick()) / (double) sim.view().clock().ticksPerHour());
                    lines.add(texts.get("derrota.acorda", hours));
                    if (d.moneyLost() > 0) lines.add(texts.get("derrota.dinheiro", ui.coins(d.moneyLost())));
                    if (!d.itemsLost().isEmpty()) lines.add(texts.get("derrota.itens", itemNames(d.itemsLost())));
                    if (d.finder() != null) {
                        var finder = content.npcs().get(d.finder());
                        lines.add(texts.get("derrota.achou", finder != null ? finder.displayName() : d.finder()));
                    }
                    ui.showWake(lines);
                }
                case SimEvent.PlayerDied d -> {
                    attacking = false;
                    Map<String, String> saves = new java.util.LinkedHashMap<>();
                    for (String name : List.of("rapido", "auto_1", "auto_2", "auto_3")) {
                        SaveGame.Meta meta = SaveGame.readMeta(savePath(name));
                        if (meta != null) saves.put(name, texts.get("morte.save." + (name.startsWith("auto") ? "auto" : name),
                                meta.day(), meta.time()));
                    }
                    List<String> lines = new ArrayList<>();
                    lines.add(texts.get("morte.texto"));
                    lines.addAll(d.summary());
                    ui.showDeath(lines, saves);
                }
                case SimEvent.ChapterEnded c -> {
                    ui.showEpilogue(c.epilogue());
                    autosave();
                }
                default -> { /* nada a fazer na apresentação */ }
            }
        }
    }

    /**
     * Som de fundo e passos (RF-400–402). Música: combate &gt; festa &gt; a da área (à noite, lá fora,
     * a da noite). Ambiente: o da área, na versão de dia ou de noite.
     */
    private void updateAudio(WorldView v, double hour, float dt) {
        var entry = content.areaEntry(v.areaId());
        boolean night = hour >= 20 || hour < 5;
        String music;
        if (v.dead()) music = null;
        else if (sim.combat().inCombat()) music = "musica.combate";
        else if (v.flag("festa") && "vila".equals(v.areaId())) music = "musica.taverna";
        else if (entry != null && entry.music() != null)
            music = !entry.interior() && night && "musica.vila".equals(entry.music()) ? "musica.noite" : entry.music();
        else music = "musica.vila";
        audio.music(music);
        audio.ambience(entry == null ? null : entry.ambience(), night);
        audio.update(dt);
        // passos: um a cada pouco mais de um metro, pelo chão em que se pisa
        double x = v.playerX(), y = v.playerY();
        if (!Double.isNaN(lastPlayerX) && v.playerMoving()) {
            double d = Math.hypot(x - lastPlayerX, y - lastPlayerY);
            if (d < 2) stepMeters += d; // um salto (troca de área, teleporte) não conta
            if (stepMeters > 1.15) {
                stepMeters = 0;
                String t = sim.area().terrainAt((int) Math.floor(x), (int) Math.floor(y));
                String id = switch (t) {
                    case "pedra", "lajota" -> "sfx.passo.pedra";
                    case "tabuas" -> "sfx.passo.madeira";
                    default -> "sfx.passo.terra";
                };
                audio.sfx(id, 0.35f, 1f);
            }
        }
        lastPlayerX = x;
        lastPlayerY = y;
    }

    private void capturePrevious(WorldView v) {
        store(v.player());
        for (ActorView a : v.actors()) store(a);
        for (ActorView a : v.enemies()) store(a);
    }

    private void store(ActorView a) {
        double[] slot = previous.computeIfAbsent(a.id(), id -> new double[2]);
        slot[0] = a.x();
        slot[1] = a.y();
    }

    private void buildDraws(WorldView v, float alpha) {
        int n = 1 + v.actors().size() + v.enemies().size();
        while (draws.size() < n) draws.add(new ActorDraw());
        while (draws.size() > n) draws.remove(draws.size() - 1);
        fill(draws.get(0), v.player(), alpha);
        ActorDraw jogador = draws.get(0);
        jogador.sprite = vestimenta.corpo(v.equipment(), jogador.sprite, assets::temEntrada);
        jogador.camadas = vestimenta.camadas(v.equipment(), assets::temEntrada);
        // V3: o golpe pela arma na mão principal; o pesado quando foi o golpe forte
        jogador.golpe = jogador.attack >= 0 ? animacoesCfg.golpe(v.equipment() == null ? null
                : v.equipment().get("mainHand"), pesados.getOrDefault(Combat.PLAYER, false)) : null;
        // V3: depois da derrota, quando a página fecha, ele se levanta do chão
        if (levantarPendente && !ui.pageOpen()) {
            levantarPendente = false;
            levantarT = 0f;
        }
        jogador.unica = null;
        if (levantarT >= 0) {
            levantarT += Gdx.graphics.getDeltaTime();
            if (levantarT > 0.9f || jogador.moving) levantarT = -1f;
            else {
                jogador.unica = "levantar";
                jogador.unicaT = levantarT / 0.8f;
                jogador.fim = null;
            }
        }
        for (int i = 0; i < v.actors().size(); i++) fill(draws.get(i + 1), v.actors().get(i), alpha);
        int base = 1 + v.actors().size();
        for (int i = 0; i < v.enemies().size(); i++) {
            ActorDraw d = draws.get(base + i);
            fill(d, v.enemies().get(i), alpha);
            d.sprite = animacoesCfg.variante(d.sprite, d.id, assets::temEntrada);
            ActorDraw copia = ultimoInimigo.computeIfAbsent(d.id, k -> new ActorDraw());
            copia.id = d.id;
            copia.sprite = d.sprite;
            copia.x = d.x;
            copia.y = d.y;
            copia.facing = d.facing;
        }
        // os corpos no chão vão no fim da lista, ordenados com o resto pela profundidade
        float dt = Gdx.graphics.getDeltaTime();
        corpos.removeIf(c -> (float) c[1] > (float) c[2]);
        for (Object[] c : corpos) {
            c[1] = (float) c[1] + dt;
            ActorDraw d = (ActorDraw) c[0];
            d.unicaT = (float) c[1] / QUEDA;
            draws.add(d);
        }
    }

    /** Onde está alguém agora (jogador, NPC ou inimigo), pelo desenho do quadro anterior. */
    private double[] posicao(String id) {
        for (ActorDraw d : draws) if (id.equals(d.id)) return new double[] {d.x, d.y};
        return null;
    }

    /** O tipo do inimigo pelo id (lobo, saqueador, vulto), ou null se não for inimigo. */
    private String tipoInimigo(String id) {
        if (sim == null) return null;
        for (var e : sim.view().enemies()) if (e.id().equals(id) && e instanceof EnemyState s) return s.type;
        return null;
    }

    /** O inimigo abatido fica no chão (V3): cai, morre ou se desfaz, conforme a folha dele. */
    private void deixarCorpo(String id) {
        ActorDraw u = ultimoInimigo.remove(id);
        if (u == null || u.sprite == null) return;
        var sheet = assets.sheet(u.sprite);
        ActorDraw d = new ActorDraw();
        d.id = id + "#corpo";
        d.sprite = u.sprite;
        d.x = u.x;
        d.y = u.y;
        d.facing = u.facing;
        float dura = CORPO_DURA;
        if (sheet != null && sheet.has("morrer")) {
            d.unica = "morrer";
            d.fim = "morto";
        } else if (sheet != null && sheet.has("cair")) {
            d.unica = "cair";
            d.fim = "caido";
        } else if (sheet != null && sheet.has("desfazer")) {
            d.unica = "desfazer";                 // o vulto não deixa corpo: some ao se desfazer
            dura = QUEDA * 1.05f;
        } else return;
        corpos.add(new Object[] {d, 0f, dura});
        if (scene != null) scene.particulas.queda(d.x, d.y);
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
        d.activity = a.activity() == null ? "" : a.activity();
        // distância andada e velocidade (arte da Fase V: o quadro do andar sai da distância)
        double[] m = motion.computeIfAbsent(a.id(), id -> new double[] {d.x, d.y, 0, 0});
        double step = Math.hypot(d.x - m[0], d.y - m[1]);
        float dt = Math.max(1e-4f, Gdx.graphics.getDeltaTime());
        if (step > 2.0) step = 0; // teleporte, troca de área
        m[2] += step;
        m[3] += (step / dt - m[3]) * Math.min(1.0, dt * 6.0);
        m[0] = d.x;
        m[1] = d.y;
        // poeira da corrida: um punhado a cada ~0,9 m (V3)
        if (d.moving && m[3] > 2.4 && scene != null && Math.floor(m[2] / 0.9) != Math.floor((m[2] - step) / 0.9))
            scene.particulas.passo(d.x, d.y, d.facing);
        d.travel = m[2];
        d.speed = d.moving ? m[3] : 0;
        d.flash = Math.min(1f, flashes.getOrDefault(a.id(), 0f) * 4f);
        d.dano = Math.min(1f, flashes.getOrDefault(a.id(), 0f) / 0.25f);   // o recuo dura o lampejo
        d.unica = null;
        d.golpe = null;
        // golpe (D-078): o inimigo ergue o braço enquanto anuncia (primeira metade) e desce quando o golpe sai;
        // o do jogador é imediato, então a animação inteira passa depois do golpe
        Float strike = strikes.get(a.id());
        d.attack = -1f;
        if (a instanceof EnemyState e) {
            boolean armando = EnemyState.WINDUP.equals(e.state);
            if (armando && !armandoAntes.contains(e.id) && scene != null) scene.particulas.aviso(d.x, d.y, 1.9);
            if (armando) armandoAntes.add(e.id);
            else armandoAntes.remove(e.id);
            if (EnemyState.WINDUP.equals(e.state)) {
                var def = content.enemies().get(e.type);
                double total = Math.max(1, (def != null ? def.windupOr() : 0.45) / Simulation.DT);
                double left = e.hitTick - sim.view().clock().tick() - alpha;
                d.attack = (float) Math.max(0, Math.min(0.499, 0.5 * (1 - left / total)));
            } else if (strike != null) {
                d.attack = 0.5f + 0.499f * (1f - strike / STRIKE_TIME);
            }
        } else if (strike != null) {
            d.attack = 0.25f + 0.749f * (1f - strike / STRIKE_TIME);
        }
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
            long t0 = System.nanoTime();
            sim.save(savePath(name), LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    Math.round(playedSeconds));
            LOG.info("Save '{}' gravado em {} ms", name, (System.nanoTime() - t0) / 1_000_000); // RNF-022
            return "Jogo salvo: " + name;
        } catch (SaveGame.SaveException e) {
            LOG.warn("Falha ao salvar '{}'", name, e);
            return e.getMessage();
        }
    }

    /** Carrega um save. Se der errado, o jogo atual continua como estava (RF-365). */
    private String loadGame(String name) {
        try {
            SaveGame.Meta meta = SaveGame.readMeta(savePath(name));
            long t0 = System.nanoTime();
            startGame(Simulation.load(content, savePath(name)));
            LOG.info("Save '{}' carregado em {} ms", name, (System.nanoTime() - t0) / 1_000_000); // RNF-021
            playedSeconds = meta != null ? meta.playedSeconds() : 0;
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
        float top = Gdx.graphics.getHeight() - 10, line = hudFont.getLineHeight();
        hudFont.setColor(0.93f, 0.89f, 0.78f, 1f);
        String life = texts.get("hud.dinheiro", ui.coins(v.money()))
                + (v.hunger() >= etrava.sim.economy.Economy.HUNGRY ? "   " + texts.get(v.hunger()
                        >= etrava.sim.economy.Economy.STARVING ? "fome.faminto" : "fome.com_fome") : "")
                + (sim.economy().overloaded() ? "   " + texts.get("hud.carga") : "");
        hudFont.draw(batch, v.clock().format() + "   " + life + (paused ? "   [PAUSADO]" : ""), 14, top);
        if (noticeTime > 0) {
            noticeTime -= Gdx.graphics.getDeltaTime();
            hudFont.draw(batch, notice, 14, top - (debug.panelVisible() ? 2 : 1) * line);
        }
        if (debug.panelVisible()) { // só no modo de depuração, e só com F3 (VT-02)
            hudFont.setColor(0.7f, 0.7f, 0.7f, 1f);
            hudFont.draw(batch, String.format(Locale.ROOT,
                    "FPS %d   pos (%.1f, %.1f)   semente %d   vel x%.1f   zoom %d   F1 console   F3 esconde",
                    Gdx.graphics.getFramesPerSecond(), v.playerX(), v.playerY(), v.seed(), timeScale, camera.pixelScale()),
                    14, top - line);
        }
        batch.end();
    }

    /** Nomes dos itens, separados por vírgula ("nada" se vazio). */
    private String itemNames(List<String> ids) {
        if (ids.isEmpty()) return texts.get("encaixe.vazio");
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            var def = content.items().get(id);
            names.add(def != null ? def.displayName() : id);
        }
        return String.join(", ", names);
    }

    /**
     * Combate na tela: vida e fôlego do jogador, recargas das habilidades, barrinha de
     * vida sobre os inimigos à vista (que acende quando o golpe deles vem) e os números de dano.
     */
    private void drawCombatOverlay(WorldView v, float dt) {
        flashes.replaceAll((k, t) -> t - dt);
        flashes.values().removeIf(t -> t <= 0);
        strikes.replaceAll((k, t) -> t - dt);
        strikes.values().removeIf(t -> t <= 0);
        batch.begin();
        // barras do jogador, no canto de baixo
        if (showHud && v.dialogue() == null && v.trade() == null) { // a conversa e o comércio ocupam o pé da tela
            float x = 16, y = 18, w = 180, h = 9;
            float gap = Math.max(22, hudFont.getLineHeight() - 2); // com texto maior, as barras se afastam
            bar(x, y + gap, w, h, (float) (v.health() / sim.combat().maxHealth()), 0.62f, 0.16f, 0.12f);
            bar(x, y, w, h, (float) (v.stamina() / sim.combat().maxStamina()), 0.72f, 0.62f, 0.30f);
            hudFont.setColor(0.93f, 0.89f, 0.78f, 1f);
            float up = (hudFont.getCapHeight() - h) / 2;
            hudFont.draw(batch, texts.get("hud.vida", Math.round(v.health()), Math.round(sim.combat().maxHealth())),
                    x + w + 8, y + gap + h + up);
            hudFont.draw(batch, texts.get("hud.folego"), x + w + 8, y + h + up);
            if (!v.enemies().isEmpty()) { // as teclas de luta só aparecem quando há com quem lutar
                String q = cooldownText(Combat.HEAVY), e = cooldownText(Combat.PUSH);
                hudFont.setColor(0.75f, 0.72f, 0.64f, 1f);
                hudFont.draw(batch, texts.get("hud.habilidades", q, e), x, y + gap + 18 + hudFont.getLineHeight());
            }
        }
        // inimigos: vida, e o aviso do golpe
        double light = sim.combat().lightRadius();
        ActorDraw me = draws.get(0);
        for (EnemyState e : v.enemies()) {
            ActorDraw d = drawOf(e.id);
            var def = content.enemies().get(e.type);
            if (d == null || def == null || Math.hypot(d.x - me.x, d.y - me.y) > light) continue;
            camera.worldPxToScreen(IsoProjection.sx(d.x, d.y), IsoProjection.sy(d.x, d.y, 0) + 108, px);
            float frac = (float) Math.max(0, e.health / def.health());
            boolean windup = EnemyState.WINDUP.equals(e.state);
            boolean meek = !e.hostile();
            bar(px[0] - 22, px[1], 44, 5, frac, windup ? 0.95f : meek ? 0.5f : 0.62f,
                    windup ? 0.78f : meek ? 0.5f : 0.16f, windup ? 0.3f : meek ? 0.45f : 0.12f);
            if (e.id.equals(hoverEnemy)) {
                String label = def.displayName() + (EnemyState.SURRENDERED.equals(e.state) ? " " + texts.get("combate.rendido_rotulo") : "");
                layout.setText(hudFont, label);
                hudFont.setColor(0.95f, 0.91f, 0.80f, 1f);
                hudFont.draw(batch, label, px[0] - layout.width / 2, px[1] + 22);
            }
        }
        // números de dano
        for (int i = floaters.size() - 1; i >= 0; i--) {
            Object[] f = floaters.get(i);
            f[2] = (float) f[2] - dt;
            if ((float) f[2] <= 0) {
                floaters.remove(i);
                continue;
            }
            ActorDraw d = drawOf((String) f[0]);
            if (d == null) continue;
            float rise = (1.1f - (float) f[2]) * 30;
            camera.worldPxToScreen(IsoProjection.sx(d.x, d.y), IsoProjection.sy(d.x, d.y, 0) + 92, px);
            if ((int) f[3] == 0) hudFont.setColor(0.95f, 0.45f, 0.38f, Math.min(1f, (float) f[2] * 2));
            else hudFont.setColor(0.98f, 0.92f, 0.75f, Math.min(1f, (float) f[2] * 2));
            layout.setText(hudFont, (String) f[1]);
            hudFont.draw(batch, (String) f[1], px[0] - layout.width / 2, px[1] + rise);
        }
        hudFont.setColor(1f, 1f, 1f, 1f);
        batch.end();
    }

    private String cooldownText(String ability) {
        long ticks = sim.combat().cooldownLeft(ability);
        return ticks <= 0 ? texts.get("hud.pronto") : String.valueOf((ticks + 29) / 30);
    }

    /** Barra com fundo escuro; {@code frac} de 0 a 1. */
    private void bar(float x, float y, float w, float h, float frac, float r, float g, float b) {
        batch.setColor(0.08f, 0.06f, 0.07f, 0.8f);
        batch.draw(whitePx, x - 1, y - 1, w + 2, h + 2);
        batch.setColor(r, g, b, 1f);
        batch.draw(whitePx, x, y, w * Math.max(0, Math.min(1, frac)), h);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    // ---------------------------------------------------------------- NPCs: apontar, falar, legendas

    /** Qual NPC está sob o cursor (RF-004). Compara em pixels de mundo com o retângulo do sprite. */
    private void updateHover(WorldView v) {
        hoverNpc = null;
        hoverContainer = null;
        hoverEnemy = null;
        hoverExit = null;
        if (v.dead() || ui.epilogueOpen()) return;
        if (v.dialogue() != null || v.trade() != null || ui.notebookOpen() || ui.inventoryOpen() || ui.characterOpen()
                || (console != null && console.isOpen())) return;
        camera.screenToWorldPx(Gdx.input.getX(), Gdx.input.getY(), px);
        double best = Double.MAX_VALUE;
        double light = sim.combat().lightRadius();
        for (ActorDraw d : draws) {
            if (d == draws.get(0)) continue; // o próprio jogador
            boolean enemy = sim.combat().enemy(d.id) != null;
            // no escuro, só se mira em quem está no raio de luz (RF-008)
            if (enemy && Math.hypot(d.x - draws.get(0).x, d.y - draws.get(0).y) > light) continue;
            float fx = IsoProjection.sx(d.x, d.y), fy = IsoProjection.sy(d.x, d.y, 0);
            if (px[0] < fx - 40 || px[0] > fx + 40 || px[1] < fy - 8 || px[1] > fy + 116) continue;
            if (enemy) { // inimigo tem prioridade sobre NPC: no meio da luta, o clique é golpe
                if (hoverEnemy == null || d.x + d.y > best) {
                    best = d.x + d.y;
                    hoverEnemy = d.id;
                }
            } else if (hoverEnemy == null && (hoverNpc == null || d.x + d.y > best)) { // se houver dois, o da frente
                best = d.x + d.y;
                hoverNpc = d.id;
            }
        }
        if (hoverEnemy != null) hoverNpc = null;
        if (hoverNpc != null || hoverEnemy != null) return;
        // sem NPC sob o cursor: algum contêiner perto do ponto do chão apontado?
        camera.screenToWorld(Gdx.input.getX(), Gdx.input.getY(), worldPoint);
        for (var c : content.containers().values()) {
            double[] p = sim.economy().containerPoint(c);
            if (p != null && Math.hypot(p[0] - worldPoint[0], p[1] - worldPoint[1]) <= 0.9) hoverContainer = c.id();
        }
        if (hoverContainer != null) return;
        // portas: o ponto fica no chão, diante da porta; vale apontar para a porta desenhada, logo acima
        for (var exit : sim.openExits()) {
            var p = sim.area().spots().get(exit.spot());
            float fx = IsoProjection.sx(p.x(), p.y()), fy = IsoProjection.sy(p.x(), p.y(), 0);
            if (px[0] > fx - 34 && px[0] < fx + 34 && px[1] > fy - 14 && px[1] < fy + 120) hoverExit = exit;
        }
    }

    private void drawBalloons(WorldView v, float dt) {
        labelRects.clear();
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
        if (hoverExit != null) {
            var p = sim.area().spots().get(hoverExit.spot());
            var to = content.areaEntry(hoverExit.to());
            var entry = content.areaEntry(sim.view().areaId());
            ActorDraw spot = new ActorDraw();
            spot.x = p.x();
            spot.y = p.y();
            String where = to != null ? to.displayName() : hoverExit.to();
            boolean leaving = entry != null && entry.interior() || to != null && !to.interior() && !"estrutura".equals(hoverExit.to());
            drawLabel(spot, texts.get(leaving ? "porta.ir" : "porta.entrar", where), 0.55f);
        }
        if (hoverContainer != null) {
            var c = content.containers().get(hoverContainer);
            double[] p = sim.economy().containerPoint(c);
            if (p != null) {
                ActorDraw spot = new ActorDraw();
                spot.x = p[0];
                spot.y = p[1];
                drawLabel(spot, c.displayName(), 0.55f);
            }
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
        layout.setText(hudFont, text, new com.badlogic.gdx.graphics.Color(0.95f, 0.91f, 0.80f, 1f), 280,
                com.badlogic.gdx.utils.Align.center, true);
        float w = Math.min(280, layout.width) + 14, h = layout.height + 12;
        float x = Math.round(px[0] - w / 2), y = Math.round(px[1]);
        // duas plaquinhas não ficam uma em cima da outra: a de quem veio depois sobe
        for (int i = 0; i < labelRects.size(); i++) {
            float[] r = labelRects.get(i);
            if (x < r[0] + r[2] && x + w > r[0] && y < r[1] + r[3] && y + h > r[1]) {
                y = r[1] + r[3] + 3;
                i = -1;
            }
        }
        labelRects.add(new float[] {x, y, w, h});
        batch.setColor(0.08f, 0.06f, 0.07f, backgroundAlpha);
        batch.draw(whitePx, x, y, w, h);
        batch.setColor(1, 1, 1, 1);
        hudFont.draw(batch, layout, px[0] - 140, y + h - 6);
    }

    /** Opção do diálogo: primeiro as escolhas do NPC, depois "perguntar sobre" os fatos marcados. */
    private void chooseDialogue(int index) {
        audio.ui("sfx.ui.clique");
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
        boolean busy = sim.view().dialogue() != null || sim.view().trade() != null || ui.notebookOpen()
                || (console != null && console.isOpen());
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
                    if (console == null) return false; // fora do modo de depuração, F1 não faz nada
                    console.toggle();
                    return true;
                }
                case Input.Keys.F3 -> {
                    return debug.keyDown(keycode);
                }
                case Input.Keys.ESCAPE -> {
                    if (menu.isOpen()) menu.escape();
                    else if (console != null && console.isOpen()) console.toggle();
                    else if (ui.deathOpen()) return true; // só sai carregando um jogo
                    else if (ui.epilogueOpen()) ui.closeEpilogue();
                    else if (sim.view().dialogue() != null) sim.submit(new Command.LeaveDialogue());
                    else if (sim.view().trade() != null) sim.submit(new Command.CloseTrade());
                    else if (ui.closeWindows()) return true;
                    else if (ui.notebookOpen()) ui.closeNotebook();
                    else openPause();
                    return true;
                }
                case Input.Keys.F5 -> {
                    captureThumb();
                    showNotice(saveGame("rapido"));
                    writeThumb("rapido");
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
            if (sim.view().dead()) return true;
            if (sim.view().dialogue() != null) { // na conversa, o teclado só escolhe falas
                if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) chooseDialogue(keycode - Input.Keys.NUM_1);
                else if (keycode == Input.Keys.SPACE || keycode == Input.Keys.ENTER) chooseDialogue(0);
                return true;
            }
            if (sim.view().trade() != null) return true; // no comércio, só o mouse e o Esc
            if (keycode == Input.Keys.J || keycode == Input.Keys.N) {
                ui.closeWindows();
                ui.toggleNotebook();
                return true;
            }
            if (keycode == Input.Keys.I) {
                ui.closeNotebook();
                ui.toggleInventory();
                return true;
            }
            if (keycode == Input.Keys.C) {
                ui.closeNotebook();
                ui.toggleCharacter();
                return true;
            }
            if (keycode == Input.Keys.M) {
                ui.closeNotebook();
                ui.toggleMap();
                return true;
            }
            if (ui.notebookOpen() && (keycode == Input.Keys.LEFT || keycode == Input.Keys.RIGHT)) {
                ui.notebookTab(keycode == Input.Keys.LEFT ? -1 : 1);
                return true;
            }
            if (ui.notebookOpen() || ui.inventoryOpen() || ui.characterOpen() || ui.mapOpen()) return true;
            if (keycode == Input.Keys.SPACE) {
                paused = !paused;
                return true;
            }
            if (keycode == Input.Keys.T && !paused) { // esperar uma hora
                sim.submit(new Command.Wait(1));
                return true;
            }
            if (paused) return false;
            switch (keycode) { // combate (RF-221, RF-222)
                case Input.Keys.Q -> sim.submit(new Command.Ability(Combat.HEAVY));
                case Input.Keys.E -> sim.submit(new Command.Ability(Combat.PUSH));
                case Input.Keys.SHIFT_LEFT, Input.Keys.SHIFT_RIGHT -> sim.submit(new Command.Dodge(lastDirX, lastDirY));
                case Input.Keys.R -> sim.submit(new Command.Surrender());
                default -> {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT || paused) return false;
            if (sim.view().dialogue() != null || sim.view().trade() != null || ui.notebookOpen() || ui.epilogueOpen()
                    || ui.inventoryOpen() || ui.characterOpen()) return true;
            if (hoverEnemy != null) {
                EnemyState e = sim.combat().enemy(hoverEnemy);
                if (e != null && EnemyState.SURRENDERED.equals(e.state)) {
                    var def = content.enemies().get(e.type);
                    ui.showResolve(e.id, def != null ? def.displayName() : e.type, def != null ? def.line("rende") : null);
                } else {
                    sim.submit(new Command.Attack(hoverEnemy, true));
                    attacking = true;
                }
            } else if (hoverNpc != null) {
                boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
                sim.submit(ctrl ? new Command.Strike(hoverNpc) : new Command.Talk(hoverNpc));
            } else if (hoverContainer != null) sim.submit(new Command.OpenContainer(hoverContainer));
            else if (hoverExit != null) { // anda até a porta; chegando, entra
                var p = sim.area().spots().get(hoverExit.spot());
                sim.submit(new Command.MoveTo(p.x(), p.y()));
            } else moveToScreenPoint(screenX, screenY);
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT || !attacking) return false;
            attacking = false;
            sim.submit(new Command.ReleaseAttack()); // soltou: termina o golpe em curso e para
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (paused || !Gdx.input.isButtonPressed(Input.Buttons.LEFT)) return false;
            if (sim.view().dialogue() != null || sim.view().trade() != null || ui.notebookOpen() || ui.inventoryOpen()
                    || ui.characterOpen() || hoverNpc != null || hoverContainer != null || attacking) return true;
            long tick = sim.view().clock().tick();
            if (tick - lastDragTick >= 6) { // segurar o botão: recalcula o caminho 5 vezes por segundo
                lastDragTick = tick;
                moveToScreenPoint(screenX, screenY);
            }
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            // touchpad manda pedacinhos de passo: acumula até um passo inteiro (D-088)
            wheel += amountY;
            float step = Math.abs(amountY) >= 1f ? amountY : wheel; // roda comum: um entalhe, um passo
            if (Math.abs(step) >= 1f) {
                camera.zoomBy(Math.signum(step));
                wheel = 0;
            }
            return true;
        }

        private float wheel;
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
                        + "trabalhar ID | comercio ID | abrir CONTEINER | mochila | ficha | dar ITEM [N] | dinheiro N\n"
                        + "fome N | vida | precos\n"
                        + "encontro ID | saude N | inimigos | equipar ITEM\n"
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
                if (p.length > 1) ui.notebookTab((int) num(p, 1));
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
            case "mapa": {
                ui.toggleMap();
                return "";
            }
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
            case "trabalhar":
                if (p.length < 2) return "trabalhos: " + String.join(", ", content.jobs().keySet());
                sim.submit(new Command.Work(p[1]));
                return sim.economy().canWork(content.jobs().get(p[1])) ? "trabalhando" : "agora não dá (hora, quem dá, ou já feito hoje)";
            case "mochila":
                ui.toggleInventory();
                return "";
            case "ficha":
                ui.toggleCharacter();
                return "";
            case "abrir":
                if (p.length < 2) return "contêineres: " + String.join(", ", content.containers().keySet());
                sim.submit(new Command.OpenContainer(p[1]));
                return "indo até " + p[1];
            case "comercio":
                if (p.length < 2) return "comércios: " + String.join(", ", content.merchants().keySet());
                sim.submit(new Command.OpenTrade(p[1]));
                return "abrindo " + p[1];
            case "dar":
                if (p.length < 2 || !content.items().containsKey(p[1]))
                    return "itens: " + String.join(", ", content.items().keySet());
                sim.applyNow(List.of(new Effect.Give(p[1], p.length > 2 ? (int) num(p, 2) : 1, 0)));
                return "recebeu " + p[1];
            case "dinheiro":
                sim.applyNow(List.of(new Effect.Give(null, 0, (int) num(p, 1))));
                return v.money() + " moedas";
            case "encontro": // força um encontro de encounters.yaml, se o jogador estiver na área dele
                sim.applyNow(List.of(new Effect.Encounter(p[1])));
                return v.enemies().size() + " inimigo(s) na área";
            case "equipar":
                sim.submit(new Command.Equip(p[1]));
                sim.stepPaused();
                return "corpo: " + v.equipment();
            case "saude":
                sim.stateWorld().health = Math.max(1, Math.min(sim.combat().maxHealth(), num(p, 1)));
                return "vida " + Math.round(v.health());
            case "inimigos": {
                StringBuilder sb = new StringBuilder();
                for (var e : v.enemies())
                    sb.append(String.format(Locale.ROOT, "%s %s vida %.0f (%.1f, %.1f)%n", e.id, e.state, e.health, e.x, e.y));
                return sb.length() == 0 ? "ninguém" : sb.toString().trim();
            }
            case "puxar": { // depuração (V3, capturas): traz os inimigos da área para 4 m do jogador
                var w = sim.stateWorld();
                int i = 0;
                for (var e : w.enemies) {
                    double a = 2 * Math.PI * i++ / Math.max(1, w.enemies.size()) + 0.6;
                    e.x = w.player.x + 4 * Math.cos(a);
                    e.y = w.player.y + 4 * Math.sin(a);
                    e.homeX = e.x;
                    e.homeY = e.y;
                    e.path.clear();
                }
                previous.clear();
                return w.enemies.size() + " inimigo(s) a 4 m";
            }
            case "fome":
                sim.stateWorld().hunger = Math.max(0, Math.min(100, num(p, 1)));
                return "fome " + v.hunger();
            case "vida": {
                StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%d moedas · fome %.0f · carga %.1f/%.1f kg%n",
                        v.money(), v.hunger(), sim.economy().weight(), sim.economy().capacity()));
                v.skills().forEach((k, val) -> sb.append(String.format(Locale.ROOT, "%s %.1f  ", k, val)));
                sb.append("\nmochila ").append(v.inventory()).append("\ncorpo ").append(v.equipment());
                content.factionsInUse().forEach(f -> sb.append("\nreputação em ").append(f).append(": ")
                        .append(sim.economy().reputation(f)));
                return sb.toString();
            }
            case "precos": {
                StringBuilder sb = new StringBuilder("modificadores: " + sim.stateWorld().priceMods + "\n");
                for (var m : content.merchants().values()) {
                    sb.append(m.id()).append(": ");
                    for (var e : m.stockList()) {
                        var item = content.items().get(e.item());
                        sb.append(e.item()).append(" ").append(sim.economy().buyPrice(m, item)).append("  ");
                    }
                    sb.append("\n");
                }
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
                return "zoom: cada pixel de arte ocupa " + (int) num(p, 1) + " px da tela (0 = vista de longe, 1 a 4)";
            case "semente":
                return "semente " + v.seed();
            case "luz":
                scene.lightingEnabled = onOff(p);
                return "luz " + (scene.lightingEnabled ? "ligada" : "desligada");
            case "presenca":
                if (scene != null) scene.presenceEnabled = p.length < 2 || !p[1].equals("off");
                return "luz de presença " + (scene != null && scene.presenceEnabled ? "ligada" : "desligada");
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

    // ---------------------------------------------------------------- medição e teste de resistência

    /**
     * Medição de desempenho (AT-17, {@code --bench=SEGUNDOS}): sem V-Sync nem limite, descarta os
     * três primeiros segundos, mede os seguintes e grava no log a média, o 1% pior e a memória.
     */
    private final class Bench {
        private static final float WARMUP = 3f;
        private final float[] times = new float[1 << 20];
        private int count, calls0;
        private float elapsed;

        void frame() {
            float raw = Gdx.graphics.getDeltaTime();
            elapsed += raw;
            if (elapsed < WARMUP) return;
            if (count == 0) calls0 = scene.totalRenderCalls();
            if (count < times.length) times[count++] = raw;
            if (elapsed < WARMUP + options.bench()) return;
            float[] sorted = java.util.Arrays.copyOf(times, count);
            java.util.Arrays.sort(sorted);
            double sum = 0;
            for (float t : sorted) sum += t;
            int worst = Math.max(1, count / 100);
            double low = 0;
            for (int i = count - worst; i < count; i++) low += sorted[i];
            Runtime rt = Runtime.getRuntime();
            String result = String.format(Locale.ROOT,
                    "BENCH área=%s %s NPCs=%d inimigos=%d %dx%d luz=%s pós=%s | média %.0f FPS | 1%% pior %.0f FPS | "
                            + "pior quadro %.1f ms | %d chamadas/quadro | heap %d MB | %s",
                    sim.view().areaId(), sim.view().clock().format(), sim.view().actors().size(),
                    sim.view().enemies().size(), Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(),
                    scene.lightingEnabled, scene.postEnabled, count / sum, worst / low, sorted[count - 1] * 1000,
                    (scene.totalRenderCalls() - calls0) / Math.max(1, count),
                    (rt.totalMemory() - rt.freeMemory()) >> 20, Gdx.gl.glGetString(GL20.GL_RENDERER));
            LOG.info(result);
            System.out.println(result);
            Gdx.app.exit();
        }
    }

    /**
     * Jogador automático ({@code --bot=SEGUNDOS}): a cada instante faz alguma coisa ao acaso — anda,
     * conversa e escolhe falas, abre telas, compra, trabalha, dorme, luta, troca de área, salva e
     * carrega. Não testa se o jogo é bom; testa se ele aguenta horas de uso sem cair nem travar.
     */
    private final class Bot {
        private final java.util.Random rnd = new java.util.Random(options.seed() != null ? options.seed() : 7);
        private final MenuActions actions = new MenuActions();
        private float wait = 1f, elapsed;
        private int count;
        /** VT-01: quantas vezes a roda foi conferida depois de fechar um painel, e quantas não deu zoom. */
        private int zoomChecks, zoomFails;
        private boolean closedSomething;

        void update(float dt) {
            elapsed += dt;
            if (elapsed >= options.bot()) {
                Runtime rt = Runtime.getRuntime();
                String result = String.format(Locale.ROOT, "BOT terminou: %d ações em %.0f s, %s, heap %d MB", count, elapsed,
                        sim != null ? crashContext : "no menu", (rt.totalMemory() - rt.freeMemory()) >> 20)
                        + (options.panels() ? String.format(Locale.ROOT, "; VT-01 zoom: %d verificações, %d falhas", zoomChecks,
                                zoomFails) : "");
                LOG.info(result);
                System.out.println(result);
                Gdx.app.exit();
                return;
            }
            wait -= dt;
            if (wait > 0) return;
            wait = options.panels() ? 0.06f : 0.25f + rnd.nextFloat() * 1.4f;
            count++;
            if (count % 400 == 0) LOG.info("Bot: {} ações, {} s, {}", count, Math.round(elapsed), crashContext);
            act();
        }

        /**
         * Modo painéis (VT-01): abre um painel, rola com ele aberto, fecha com Esc — tudo pelo
         * processador de entrada de verdade, o mesmo do teclado e do mouse — e, com a tela
         * livre, confere que a roda muda o zoom.
         */
        private void panels() {
            WorldView v = sim.view();
            InputProcessor input = Gdx.input.getInputProcessor();
            boolean open = menu.isOpen() || (console != null && console.isOpen()) || ui.epilogueOpen() || v.dialogue() != null
                    || v.trade() != null || ui.notebookOpen() || ui.inventoryOpen() || ui.characterOpen() || ui.mapOpen();
            if (open) {
                if (rnd.nextInt(3) > 0) input.scrolled(0, rnd.nextBoolean() ? 1 : -1); // rola com o painel aberto
                else {
                    input.keyDown(Input.Keys.ESCAPE);
                    input.keyUp(Input.Keys.ESCAPE);
                    closedSomething = true;
                }
                return;
            }
            if (closedSomething) {
                closedSomething = false;
                int before = camera.pixelScale();
                float dir = before >= IsoCameraRig.MAX_PIXEL_SCALE ? 1 : before <= IsoCameraRig.MIN_PIXEL_SCALE ? -1
                        : rnd.nextBoolean() ? 1 : -1;
                input.scrolled(0, dir);
                zoomChecks++;
                if (camera.pixelScale() == before) {
                    zoomFails++;
                    LOG.warn("VT-01: a roda não mudou o zoom (escala {}, {})", before, crashContext);
                }
                return;
            }
            switch (rnd.nextInt(console != null ? 9 : 8)) {
                case 0 -> ui.toggleNotebook();
                case 1 -> ui.toggleInventory();
                case 2 -> ui.toggleCharacter();
                case 3 -> ui.toggleMap();
                case 4 -> openPause();
                case 5 -> {
                    if (!v.actors().isEmpty()) sim.submit(new Command.Talk(v.actors().get(rnd.nextInt(v.actors().size())).id()));
                }
                case 6 -> {
                    List<String> near = new ArrayList<>();
                    for (var c : content.containers().values()) if (sim.economy().containerPoint(c) != null) near.add(c.id());
                    if (!near.isEmpty()) sim.submit(new Command.OpenContainer(near.get(rnd.nextInt(near.size()))));
                    else sim.submit(new Command.MoveTo(v.playerX() + rnd.nextInt(21) - 10, v.playerY() + rnd.nextInt(21) - 10));
                }
                case 7 -> sim.submit(new Command.MoveTo(v.playerX() + rnd.nextInt(21) - 10, v.playerY() + rnd.nextInt(21) - 10));
                default -> console.toggle();
            }
        }

        private void act() {
            if (sim == null || (menu.isOpen() && !menu.inGame())) {
                actions.newGame("Bot", rnd.nextBoolean());
                return;
            }
            if (options.panels() && !ui.deathOpen()) {
                panels();
                return;
            }
            WorldView v = sim.view();
            if (menu.isOpen()) {
                if (rnd.nextInt(4) == 0 && actions.canSave()) actions.save("jogo_3");
                else menu.escape();
                return;
            }
            if (ui.deathOpen()) { // morreu: volta ao último save; sem save, começa outra vida
                String latest = null;
                long best = -1;
                for (String n : MenuUi.slotNames(false)) {
                    java.io.File f = savePath(n).toFile();
                    if (f.exists() && f.lastModified() > best) {
                        best = f.lastModified();
                        latest = n;
                    }
                }
                if (latest != null) loadGame(latest);
                if (sim.view().dead()) actions.newGame("Bot", false);
                ui.closeDeath();
                return;
            }
            if (ui.epilogueOpen()) {
                ui.closeEpilogue();
                return;
            }
            if (v.dialogue() != null) {
                DialogueView d = v.dialogue();
                if (rnd.nextInt(12) == 0) sim.submit(new Command.LeaveDialogue());
                else chooseDialogue(d.choices().isEmpty() ? 0 : rnd.nextInt(d.choices().size() + ui.askFacts().size()));
                return;
            }
            if (v.trade() != null) {
                var t = sim.tradeView();
                int r = rnd.nextInt(10);
                if (t == null || r < 3) sim.submit(new Command.CloseTrade());
                else if (r < 7 && !t.offers().isEmpty()) {
                    String item = t.offers().get(rnd.nextInt(t.offers().size())).item();
                    sim.submit(t.container() ? new Command.TakeItem(item) : new Command.Buy(item));
                } else if (!t.container() && !t.wants().isEmpty()) {
                    sim.submit(new Command.Sell(t.wants().get(rnd.nextInt(t.wants().size())).item()));
                }
                return;
            }
            if (ui.notebookOpen() || ui.inventoryOpen() || ui.characterOpen() || ui.mapOpen()) {
                int r = rnd.nextInt(10);
                if (ui.notebookOpen() && r < 5) ui.notebookTab(rnd.nextBoolean() ? 1 : -1);
                else if (ui.inventoryOpen() && r < 5 && !v.inventory().isEmpty()) {
                    List<String> items = new ArrayList<>(v.inventory().keySet());
                    String item = items.get(rnd.nextInt(items.size()));
                    sim.submit(rnd.nextBoolean() ? new Command.UseItem(item) : new Command.Equip(item));
                } else {
                    ui.closeWindows();
                    ui.closeNotebook();
                }
                return;
            }
            paused = false;
            int r = rnd.nextInt(100);
            if (!v.enemies().isEmpty() && r < 45) { // há inimigos: metade do tempo, luta
                EnemyState e = v.enemies().get(rnd.nextInt(v.enemies().size()));
                switch (rnd.nextInt(6)) {
                    case 0 -> sim.submit(new Command.Ability(Combat.HEAVY));
                    case 1 -> sim.submit(new Command.Ability(Combat.PUSH));
                    case 2 -> sim.submit(new Command.Dodge(rnd.nextInt(3) - 1, rnd.nextInt(3) - 1));
                    case 3 -> sim.submit(new Command.Surrender());
                    case 4 -> sim.submit(new Command.ReleaseAttack());
                    default -> sim.submit(new Command.Attack(e.id, true));
                }
            } else if (r < 32) {
                sim.submit(new Command.MoveTo(v.playerX() + rnd.nextInt(31) - 15, v.playerY() + rnd.nextInt(31) - 15));
            } else if (r < 47 && !v.actors().isEmpty()) {
                String npc = v.actors().get(rnd.nextInt(v.actors().size())).id();
                sim.submit(rnd.nextInt(40) == 0 ? new Command.Strike(npc) : new Command.Talk(npc));
            } else if (r < 57 && !sim.openExits().isEmpty()) {
                var exit = sim.openExits().get(rnd.nextInt(sim.openExits().size()));
                var spot = sim.area().spots().get(exit.spot());
                sim.submit(new Command.MoveTo(spot.x(), spot.y()));
            } else if (r < 62) {
                List<String> near = new ArrayList<>();
                for (var c : content.containers().values()) if (sim.economy().containerPoint(c) != null) near.add(c.id());
                if (!near.isEmpty()) sim.submit(new Command.OpenContainer(near.get(rnd.nextInt(near.size()))));
            } else if (r < 68) {
                switch (rnd.nextInt(4)) {
                    case 0 -> ui.toggleNotebook();
                    case 1 -> ui.toggleInventory();
                    case 2 -> ui.toggleCharacter();
                    default -> ui.toggleMap();
                }
            } else if (r < 72) {
                sim.submit(new Command.Wait(1 + rnd.nextInt(3)));
            } else if (r < 74) {
                sim.submit(new Command.Sleep());
            } else if (r < 79 && !content.jobs().isEmpty()) {
                List<String> jobs = new ArrayList<>(content.jobs().keySet());
                sim.submit(new Command.Work(jobs.get(rnd.nextInt(jobs.size()))));
            } else if (r < 83 && !content.merchants().isEmpty()) {
                List<String> shops = new ArrayList<>(content.merchants().keySet());
                sim.submit(new Command.OpenTrade(shops.get(rnd.nextInt(shops.size()))));
            } else if (r < 85) {
                captureThumb();
                saveGame("rapido");
                writeThumb("rapido");
            } else if (r < 86) {
                loadGame("rapido");
            } else if (r < 88) {
                openPause();
            } else if (r < 91) { // atalho de desenvolvimento: exercita a troca de área e o carregamento de cenário
                List<String> areas = new ArrayList<>(content.areas().keySet());
                sim.submit(new Command.EnterArea(areas.get(rnd.nextInt(areas.size())), null));
            } else if (r < 93) {
                camera.zoomBy(rnd.nextBoolean() ? 1 : -1);
            }
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
        if (scene != null) scene.dispose();
        ui.dispose();
        whitePx.dispose();
        assets.dispose();
        shapes.dispose();
        batch.dispose();
        font.dispose();
        hudFont.dispose();
        titleFont.dispose();
        consoleFont.dispose();
        if (audio != null) audio.dispose();
        if (menu != null) menu.dispose();
        for (Texture t : thumbs.values()) t.dispose();
        if (thumbPending != null) thumbPending.dispose();
        if (console != null) console.dispose();
    }
}
