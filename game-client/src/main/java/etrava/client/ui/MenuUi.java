package etrava.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import etrava.client.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Menus do jogo (PRD 10.2): principal, novo jogo, carregar, salvar, opções e pausa. Papel e tinta,
 * como o resto da interface. Quem usa diz o que fazer em cada botão por {@link Actions}.
 */
public final class MenuUi implements Disposable {
    private static final Color PAPER = new Color(0xD8CCB0FF), PAPER_DARK = new Color(0xB9A888FF);
    private static final Color INK = new Color(0x241C18FF), INK_SOFT = new Color(0x5A4636FF);
    private static final Color RUST = new Color(0x6E2C22FF), EDGE = new Color(0x2E2018FF);

    /** Um save para listar: nome do arquivo, como aparece e a miniatura (pode faltar). */
    public record Slot(String name, String title, String detail, Texture thumb) {}

    /** O que o jogo faz quando o jogador escolhe algo. */
    public interface Actions {
        void newGame(String name, boolean contemplative);
        void load(String slot);
        void save(String slot);
        void resume();
        void toMainMenu();
        void quit();
        void settingsChanged();
        List<Slot> slots(boolean forSaving);
        String originName();
        String originDescription();
        boolean canSave();
    }

    private final Stage stage = new Stage(new ScreenViewport());
    private final Texture white;
    private final BitmapFont font;
    private final Texts texts;
    private final Settings settings;
    private final Actions actions;
    private final Drawable paper, paperDark, edge;
    private final Label.LabelStyle ink, inkSoft, rust, title;
    private final Table root = new Table();
    private boolean open, inGame;
    private Runnable back = () -> { };

    public MenuUi(BitmapFont font, Texts texts, Settings settings, Actions actions) {
        this.font = font;
        this.texts = texts;
        this.settings = settings;
        this.actions = actions;
        Pixmap px = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        px.setColor(Color.WHITE);
        px.fill();
        white = new Texture(px);
        px.dispose();
        TextureRegionDrawable base = new TextureRegionDrawable(white);
        paper = base.tint(PAPER);
        paperDark = base.tint(PAPER_DARK);
        edge = base.tint(EDGE);
        ink = new Label.LabelStyle(font, INK);
        inkSoft = new Label.LabelStyle(font, INK_SOFT);
        rust = new Label.LabelStyle(font, RUST);
        title = new Label.LabelStyle(font, new Color(0xEEE6D2FF));
        root.setFillParent(true);
        stage.addActor(root);
    }

    public Stage stage() { return stage; }
    public boolean isOpen() { return open; }
    public boolean inGame() { return inGame; }

    public void resize(int w, int h) { stage.getViewport().update(w, h, true); }

    public void render(float dt) {
        if (!open) return;
        stage.act(dt);
        stage.draw();
    }

    public void close() {
        open = false;
        root.clearChildren();
    }

    /** Esc dentro de um menu: volta um nível (ou fecha a pausa). */
    public void escape() {
        back.run();
    }

    // ---------------------------------------------------------------- telas

    public void showMain(boolean canContinue) {
        inGame = false;
        Table col = column();
        Label name = new Label("ETRAVA", title);
        name.setFontScale(3f);
        root.clearChildren();
        root.left().bottom().pad(60);
        Table box = new Table();
        box.add(name).left().padBottom(4).row();
        box.add(new Label(texts.get("menu.subtitulo"), title)).left().padBottom(28).row();
        if (canContinue) col.add(button(texts.get("menu.continuar"), () -> actions.load(null))).row();
        col.add(button(texts.get("menu.novo"), this::showNewGame)).row();
        col.add(button(texts.get("menu.carregar"), () -> showSlots(false, () -> showMain(canContinue)))).row();
        col.add(button(texts.get("menu.opcoes"), () -> showOptions(() -> showMain(canContinue)))).row();
        col.add(button(texts.get("menu.sair"), actions::quit)).row();
        box.add(sheet(col, 280)).left();
        root.add(box).left().bottom();
        back = () -> { };
        open = true;
    }

    public void showNewGame() {
        Table col = column();
        col.add(new Label(texts.get("novo.titulo"), rust)).left().padBottom(12).row();
        col.add(new Label(texts.get("novo.origem"), inkSoft)).left().row();
        col.add(new Label(actions.originName(), ink)).left().padBottom(4).row();
        Label desc = new Label(actions.originDescription(), inkSoft);
        desc.setWrap(true);
        col.add(desc).width(460).left().padBottom(6).row();
        col.add(new Label(texts.get("novo.outras"), inkSoft)).left().padBottom(14).row();
        col.add(new Label(texts.get("novo.nome"), inkSoft)).left().row();
        TextField.TextFieldStyle tfs = new TextField.TextFieldStyle(font, INK, edge, paperDark, paperDark);
        TextField nameField = new TextField(settings.nome, tfs);
        nameField.setMessageText(texts.get("novo.nome_padrao"));
        nameField.setMaxLength(24);
        col.add(nameField).width(300).left().padBottom(14).row();
        col.add(new Label(texts.get("novo.ritmo"), inkSoft)).left().row();
        boolean[] slow = {settings.contemplativo};
        Label pace = new Label("", ink);
        Runnable refresh = () -> pace.setText("< " + texts.get(slow[0] ? "novo.ritmo.contemplativo" : "novo.ritmo.normal") + " >");
        refresh.run();
        pace.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                slow[0] = !slow[0];
                refresh.run();
            }
        });
        col.add(pace).left().row();
        Label paceHint = new Label(texts.get("novo.ritmo.dica"), inkSoft);
        paceHint.setWrap(true);
        col.add(paceHint).width(460).left().padBottom(18).row();
        Table row = new Table();
        row.add(button(texts.get("novo.comecar"), () -> {
            settings.nome = nameField.getText().trim();
            settings.contemplativo = slow[0];
            settings.save();
            actions.newGame(settings.nome, slow[0]);
        })).padRight(24);
        row.add(button(texts.get("menu.voltar"), () -> showMain(!actions.slots(false).isEmpty())));
        col.add(row).left();
        center(sheet(col, 520));
        stage.setKeyboardFocus(nameField);
        back = () -> showMain(!actions.slots(false).isEmpty());
    }

    /** Lista de saves para carregar ou para gravar (RF-360). */
    public void showSlots(boolean saving, Runnable onBack) {
        Table col = column();
        col.add(new Label(texts.get(saving ? "slots.salvar" : "slots.carregar"), rust)).left().padBottom(12).row();
        Table list = new Table();
        list.top().left();
        List<Slot> slots = actions.slots(saving);
        if (slots.isEmpty()) list.add(new Label(texts.get("slots.vazio"), inkSoft)).left().row();
        for (Slot s : slots) {
            Table line = new Table();
            line.left();
            if (s.thumb() != null) line.add(new Image(s.thumb())).size(160, 90).padRight(12);
            else line.add(new Label("", inkSoft)).size(160, 90).padRight(12);
            Table text = new Table();
            text.left();
            text.add(new Label(s.title(), ink)).left().row();
            text.add(new Label(s.detail(), inkSoft)).left().row();
            line.add(text).left().growX();
            line.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
            line.setBackground(paper);
            line.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (saving) actions.save(s.name());
                    else actions.load(s.name());
                }

                @Override
                public void enter(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor from) {
                    line.setBackground(paperDark);
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor to) {
                    line.setBackground(paper);
                }
            });
            list.add(line).growX().padBottom(6).row();
        }
        ScrollPane scroll = new ScrollPane(list, new ScrollPane.ScrollPaneStyle());
        scroll.setFadeScrollBars(false);
        col.add(scroll).width(520).height(Math.min(420, stage.getHeight() - 200)).row();
        col.add(button(texts.get("menu.voltar"), onBack)).left().padTop(10);
        center(sheet(col, 560));
        stage.setScrollFocus(scroll);
        back = onBack;
    }

    /** Opções (RF-383): volume por canal, vídeo e texto. Vale na hora e fica gravado. */
    public void showOptions(Runnable onBack) {
        Table col = column();
        col.add(new Label(texts.get("opcoes.titulo"), rust)).left().colspan(2).padBottom(12).row();
        volume(col, "opcoes.geral", () -> settings.volumeGeral, v -> settings.volumeGeral = v);
        volume(col, "opcoes.musica", () -> settings.volumeMusica, v -> settings.volumeMusica = v);
        volume(col, "opcoes.ambiente", () -> settings.volumeAmbiente, v -> settings.volumeAmbiente = v);
        volume(col, "opcoes.efeitos", () -> settings.volumeEfeitos, v -> settings.volumeEfeitos = v);
        volume(col, "opcoes.interface", () -> settings.volumeInterface, v -> settings.volumeInterface = v);
        toggle(col, "opcoes.tela_cheia", () -> settings.telaCheia, v -> settings.telaCheia = v);
        toggle(col, "opcoes.vsync", () -> settings.vsync, v -> settings.vsync = v);
        cycle(col, "opcoes.fps", new String[] {"30", "60", "120", texts.get("opcoes.sem_limite")},
                () -> switch (settings.limiteFps) { case 30 -> 0; case 120 -> 2; case 0 -> 3; default -> 1; },
                i -> settings.limiteFps = new int[] {30, 60, 120, 0}[i]);
        toggle(col, "opcoes.pos", () -> settings.posProcessamento, v -> settings.posProcessamento = v);
        toggle(col, "opcoes.luz", () -> settings.luzDinamica, v -> settings.luzDinamica = v);
        cycle(col, "opcoes.texto", new String[] {texts.get("opcoes.texto.normal"), texts.get("opcoes.texto.grande"),
                texts.get("opcoes.texto.maior")},
                () -> settings.tamanhoTexto >= 1.5f ? 2 : settings.tamanhoTexto >= 1.25f ? 1 : 0,
                i -> settings.tamanhoTexto = new float[] {1f, 1.25f, 1.5f}[i]);
        cycle(col, "opcoes.velocidade", new String[] {texts.get("opcoes.vel.lenta"), texts.get("opcoes.vel.normal"),
                texts.get("opcoes.vel.rapida")},
                () -> settings.velocidadeTexto >= 1.5f ? 0 : settings.velocidadeTexto <= 0.75f ? 2 : 1,
                i -> settings.velocidadeTexto = new float[] {1.6f, 1f, 0.7f}[i]);
        col.add(new Label(texts.get("opcoes.ritmo"), inkSoft)).left().colspan(2).padTop(8).row();
        col.add(button(texts.get("menu.voltar"), () -> {
            settings.save();
            onBack.run();
        })).left().colspan(2).padTop(14);
        center(sheet(col, 520));
        back = () -> {
            settings.save();
            onBack.run();
        };
    }

    /** Esc no meio do jogo (o mundo para enquanto o menu está aberto, RF-324). */
    public void showPause() {
        inGame = true;
        Table col = column();
        col.add(new Label(texts.get("pausa.titulo"), rust)).left().padBottom(12).row();
        col.add(button(texts.get("pausa.continuar"), actions::resume)).left().row();
        if (actions.canSave()) col.add(button(texts.get("pausa.salvar"), () -> showSlots(true, this::showPause))).left().row();
        else col.add(new Label(texts.get("pausa.nao_salva"), inkSoft)).left().row();
        col.add(button(texts.get("menu.carregar"), () -> showSlots(false, this::showPause))).left().row();
        col.add(button(texts.get("menu.opcoes"), () -> showOptions(this::showPause))).left().row();
        col.add(button(texts.get("pausa.menu"), actions::toMainMenu)).left().row();
        col.add(button(texts.get("menu.sair"), actions::quit)).left().row();
        center(sheet(col, 300));
        back = actions::resume;
        open = true;
    }

    // ---------------------------------------------------------------- peças

    private Table column() {
        Table t = new Table();
        t.top().left();
        t.defaults().left().padBottom(6);
        return t;
    }

    private Table sheet(Table content, float width) {
        Table page = new Table();
        page.setBackground(paper);
        page.pad(22);
        page.add(content).width(width).left();
        Table border = new Table();
        border.setBackground(edge);
        border.pad(3);
        border.add(page);
        return border;
    }

    private void center(Table sheet) {
        root.clearChildren();
        root.center().pad(0);
        root.add(sheet);
        open = true;
    }

    private Label button(String text, Runnable action) {
        Label l = new Label("> " + text, ink);
        l.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                action.run();
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor from) {
                l.setColor(RUST);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor to) {
                l.setColor(INK);
            }
        });
        return l;
    }

    private interface FloatGet { float get(); }
    private interface FloatSet { void set(float v); }
    private interface BoolGet { boolean get(); }
    private interface BoolSet { void set(boolean v); }
    private interface IntGet { int get(); }

    private void volume(Table col, String key, FloatGet get, FloatSet set) {
        Label value = new Label("", ink);
        Runnable refresh = () -> {
            int n = Math.round(get.get() * 10);
            value.setText("<  " + "#".repeat(n) + ".".repeat(10 - n) + "  " + n * 10 + "%  >");
        };
        refresh.run();
        value.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                float step = x < value.getWidth() / 2 ? -0.1f : 0.1f;
                set.set(Math.max(0, Math.min(1, Math.round((get.get() + step) * 10) / 10f)));
                refresh.run();
                actions.settingsChanged();
            }
        });
        col.add(new Label(texts.get(key), inkSoft)).left().width(200);
        col.add(value).left().row();
    }

    private void toggle(Table col, String key, BoolGet get, BoolSet set) {
        Label value = new Label("", ink);
        Runnable refresh = () -> value.setText("< " + texts.get(get.get() ? "opcoes.sim" : "opcoes.nao") + " >");
        refresh.run();
        value.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                set.set(!get.get());
                refresh.run();
                actions.settingsChanged();
            }
        });
        col.add(new Label(texts.get(key), inkSoft)).left().width(200);
        col.add(value).left().row();
    }

    private void cycle(Table col, String key, String[] options, IntGet get, Consumer<Integer> set) {
        Label value = new Label("", ink);
        Runnable refresh = () -> value.setText("< " + options[get.get()] + " >");
        refresh.run();
        value.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                int step = x < value.getWidth() / 2 ? -1 : 1;
                set.accept(Math.floorMod(get.get() + step, options.length));
                refresh.run();
                actions.settingsChanged();
            }
        });
        col.add(new Label(texts.get(key), inkSoft)).left().width(200);
        col.add(value).left().row();
    }

    /** Monta a lista de slots a partir de nomes (útil para quem chama). */
    public static List<String> slotNames(boolean saving) {
        List<String> names = new ArrayList<>(List.of("jogo_1", "jogo_2", "jogo_3"));
        if (!saving) names.addAll(List.of("rapido", "auto_1", "auto_2", "auto_3"));
        return names;
    }

    @Override
    public void dispose() {
        stage.dispose();
        white.dispose();
    }
}
