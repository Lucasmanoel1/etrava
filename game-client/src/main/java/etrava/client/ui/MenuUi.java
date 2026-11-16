package etrava.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import etrava.client.Settings;
import etrava.client.assets.Assets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Menus do jogo (PRD 10.2, V7): principal, novo jogo, carregar, salvar, opções e pausa, no tema novo
 * ({@link Tema}, {@link Ui}, {@link Foco}). Tudo navega por teclado (setas, Tab, Enter; Esc volta pelo
 * jogo); deslizantes e escolhas mudam com esquerda e direita. Larguras e alturas por fração da tela com
 * teto em linhas de fonte; o que não cabe rola. No menu principal as camadas vivas ({@link MenuFx})
 * passam sobre a ilustração. Quem usa diz o que fazer em cada botão por {@link Actions}.
 */
public final class MenuUi implements Disposable {
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

    private final Stage stage = new UiStage(new ScreenViewport());
    private final Texture white;
    private final BitmapFont font;
    private final Texts texts;
    private final Settings settings;
    private final Actions actions;
    private final Tema tema;
    private final boolean donoDoTema;
    private final Ui ui;
    private final MenuFx fx = new MenuFx(7);
    private final Label.LabelStyle title, subtitle;
    private final Table root = new Table();
    private boolean open, inGame;
    private Runnable back = () -> { };
    /** Refaz a tela aberta (a janela mudou de tamanho). */
    private Runnable rebuild;
    private float fixedStep;
    // o que o jogador já digitou no novo jogo, para sobreviver a um resize
    private String newName = "";
    private boolean newSlow;

    /**
     * @param overlayFont texto claro sobre a ilustração do menu (com sombra)
     * @param titleFont   o nome do jogo
     */
    public MenuUi(BitmapFont font, BitmapFont overlayFont, BitmapFont titleFont, Assets assets, Texts texts,
            Settings settings, Actions actions) {
        this(null, font, overlayFont, titleFont, assets, texts, settings, actions);
    }

    /** Sobre um tema compartilhado (quem o criou o descarta). */
    public MenuUi(Tema compartilhado, BitmapFont font, BitmapFont overlayFont, BitmapFont titleFont, Assets assets, Texts texts,
            Settings settings, Actions actions) {
        this.font = font;
        this.texts = texts;
        this.settings = settings;
        this.actions = actions;
        Pixmap px = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        px.setColor(Color.WHITE);
        px.fill();
        white = new Texture(px);
        px.dispose();
        donoDoTema = compartilhado == null;
        tema = compartilhado != null ? compartilhado : new Tema(assets.root(), assets.manifest(), font);
        ui = new Ui(tema);
        title = new Label.LabelStyle(titleFont, tema.cor("tinta_clara"));
        subtitle = new Label.LabelStyle(overlayFont, tema.cor("tinta_clara"));
        root.setFillParent(true);
        stage.addActor(root);
    }

    public Stage stage() { return stage; }
    public boolean isOpen() { return open; }
    public boolean inGame() { return inGame; }

    /** Só para capturas: o tempo das camadas do menu passa em passos fixos, não pelo relógio. */
    public void fixedStep(float seconds) { fixedStep = seconds; }

    public void resize(int w, int h) {
        ui.escala(Escala.aplicar(stage.getViewport(), w, h));
        if (open && rebuild != null) rebuild.run();
    }

    public void render(float dt) {
        if (!open) return;
        if (!inGame) {
            fx.render(fixedStep > 0 ? fixedStep : Math.min(dt, 0.1f),
                    com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight());
        }
        stage.act(dt);
        stage.draw();
    }

    public void close() {
        open = false;
        rebuild = null;
        root.clearChildren();
        stage.setKeyboardFocus(null);
    }

    /** Esc dentro de um menu: volta um nível (ou fecha a pausa). */
    public void escape() {
        back.run();
    }

    private void open(Runnable builder) {
        rebuild = builder;
        stage.setKeyboardFocus(null);
        builder.run();
        open = true;
    }

    // ---------------------------------------------------------------- telas

    public void showMain(boolean canContinue) {
        inGame = false;
        open(() -> buildMain(canContinue));
    }

    private void buildMain(boolean canContinue) {
        root.clearChildren();
        float side = Math.max(stage.getWidth() * 0.06f, (stage.getWidth() - stage.getHeight() * 16f / 9f) / 2 + stage.getHeight() * 0.08f);
        root.left().bottom().pad(0, side, stage.getHeight() * 0.09f, 0);
        Table box = new Table();
        Foco foco = ui.foco();
        box.add(new Label("ETRAVA", title)).left().padBottom(2).row();
        box.add(new Label(texts.get("menu.subtitulo"), subtitle)).left().padBottom(font.getLineHeight() * 1.4f).row();
        float w = ui.largura(stage, 0.32f, 12);
        if (canContinue) menuButton(box, foco, texts.get("menu.continuar"), () -> actions.load(null), w);
        menuButton(box, foco, texts.get("menu.novo"), this::showNewGame, w);
        menuButton(box, foco, texts.get("menu.carregar"), () -> showSlots(false, () -> showMain(canContinue)), w);
        menuButton(box, foco, texts.get("menu.opcoes"), () -> showOptions(() -> showMain(canContinue)), w);
        menuButton(box, foco, texts.get("menu.sair"), actions::quit, w);
        box.addActor(foco);
        root.add(box).left().bottom();
        back = () -> { };
    }

    private void menuButton(Table col, Foco foco, String text, Runnable action, float width) {
        TextButton b = ui.botao(text, action);
        foco.adicionar(b);
        col.add(b).width(width).minHeight(font.getLineHeight() * 1.9f).left().padBottom(font.getLineHeight() * 0.3f).row();
    }

    public void showNewGame() {
        inGame = false;
        newName = settings.nome == null ? "" : settings.nome;
        newSlow = settings.contemplativo;
        open(this::buildNewGame);
    }

    private void buildNewGame() {
        Runnable voltar = () -> showMain(!actions.slots(false).isEmpty());
        root.clearChildren();
        root.center().pad(0);
        Table painel = ui.painel(texts.get("novo.titulo"));
        Foco foco = ui.foco();
        Table list = new Table();
        list.top().left();
        list.defaults().left().growX();
        list.add(new Label(texts.get("novo.origem"), tema.tintaSuave)).row();
        list.add(new Label(actions.originName(), tema.tinta)).padBottom(2).row();
        String desc = actions.originDescription();
        if (desc != null && !desc.isBlank()) list.add(ui.paragrafo(desc)).padBottom(4).row();
        Label others = new Label(texts.get("novo.outras"), tema.tintaSuave);
        others.setWrap(true);
        list.add(others).padBottom(line(0.9f)).row();

        list.add(new Label(texts.get("novo.nome"), tema.tintaSuave)).row();
        TextField.TextFieldStyle tfs = new TextField.TextFieldStyle(font, tema.cor("tinta"),
                new TextureRegionDrawable(white).tint(tema.cor("tinta")),
                new TextureRegionDrawable(white).tint(new Color(0x5A423259)), tema.peca("ui.pergaminho_escuro"));
        TextField nameField = new TextField(newName, tfs);
        nameField.setMessageText(texts.get("novo.nome_padrao"));
        nameField.setMaxLength(24);
        nameField.setFocusTraversal(false);
        nameField.setTextFieldListener((tf, c) -> {
            newName = tf.getText();
            if (c == '\n' || c == '\r') { // Enter: sai do campo e volta a navegar pelas setas
                stage.setKeyboardFocus(null);
                foco.focar(1);
            }
        });
        list.add(nameField).width(ui.largura(stage, 0.8f, 16)).left().padBottom(line(0.9f)).row();
        foco.adicionar(nameField, () -> stage.setKeyboardFocus(nameField));

        list.add(new Label(texts.get("novo.ritmo"), tema.tintaSuave)).row();
        String[] pace = {texts.get("novo.ritmo.normal"), texts.get("novo.ritmo.contemplativo")};
        Table choice = choice(pace, () -> newSlow ? 1 : 0, i -> newSlow = i == 1);
        foco.adicionar(choice, () -> step(choice).accept(1), step(choice));
        list.add(choice).left().fill(false, false).expand(false, false).padBottom(2).row();
        Label hint = new Label(texts.get("novo.ritmo.dica"), tema.tintaSuave);
        hint.setWrap(true);
        list.add(hint).padBottom(2).row();

        painel.add(ui.rolagem(list)).grow().padRight(2).row();
        Table buttons = new Table();
        buttons.add(button(foco, texts.get("novo.comecar"), () -> {
            settings.nome = newName.trim();
            settings.contemplativo = newSlow;
            settings.save();
            actions.newGame(settings.nome, newSlow);
        })).minHeight(font.getLineHeight() * 1.9f).padRight(line(1f));
        buttons.add(button(foco, texts.get("menu.voltar"), voltar)).minHeight(font.getLineHeight() * 1.9f);
        painel.add(buttons).left().padTop(line(0.5f));
        painel.addActor(foco);
        root.add(painel).width(ui.largura(stage, 0.92f, 30)).maxHeight(ui.altura(stage, 0.92f, 32));
        // abre com o cursor no nome: quem começa digita (Enter sai do campo)
        if (nameField.getText().isEmpty()) stage.setKeyboardFocus(nameField);
        back = voltar;
    }

    /** Lista de saves para carregar ou para gravar (RF-360). */
    public void showSlots(boolean saving, Runnable onBack) {
        open(() -> buildSlots(saving, onBack));
    }

    private void buildSlots(boolean saving, Runnable onBack) {
        root.clearChildren();
        root.center().pad(0);
        Table painel = ui.painel(texts.get(saving ? "slots.salvar" : "slots.carregar"));
        Foco foco = ui.foco();
        Table list = new Table();
        list.top().left();
        list.defaults().left().growX();
        List<Slot> slots = actions.slots(saving);
        if (slots.isEmpty()) list.add(new Label(texts.get("slots.vazio"), tema.tintaSuave)).left().pad(line(0.5f)).row();
        float thumbW = font.getLineHeight() * 7f, thumbH = thumbW * 9f / 16f;
        for (Slot s : slots) {
            Table card = ui.fundo("ui.pergaminho_escuro");
            card.left();
            Actor pic = s.thumb() != null ? new Image(s.thumb()) : new Image(tema.peca("ui.couro"));
            Table frame = ui.fundo("ui.moldura_retrato");
            frame.add(pic).size(thumbW, thumbH);
            card.add(frame).padRight(line(0.8f));
            Table text = new Table();
            text.left().top();
            text.add(new Label(s.title(), tema.ferrugem)).left().padBottom(2).row();
            String[] parts = s.detail().split(" · ");
            for (int i = 0; i < parts.length; i++) {
                Label l = new Label(parts[i], i == 0 ? tema.tinta : tema.tintaSuave);
                l.setWrap(true);
                text.add(l).left().growX().row();
            }
            card.add(text).growX().top();
            Runnable pick = () -> {
                if (saving) actions.save(s.name());
                else actions.load(s.name());
            };
            card.setTouchable(Touchable.enabled);
            card.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    pick.run();
                }

                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Actor from) {
                    card.setBackground(tema.peca("ui.pergaminho"));
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Actor to) {
                    card.setBackground(tema.peca("ui.pergaminho_escuro"));
                }
            });
            foco.adicionar(card, pick);
            list.add(card).padBottom(line(0.4f)).row();
        }
        painel.add(ui.rolagem(list)).grow().padRight(2).row();
        painel.add(button(foco, texts.get("menu.voltar"), onBack)).left().minHeight(font.getLineHeight() * 1.9f)
                .padTop(line(0.5f));
        painel.addActor(foco);
        root.add(painel).width(ui.largura(stage, 0.92f, 34)).height(ui.altura(stage, 0.92f, 34));
        back = onBack;
    }

    /** Opções (RF-383): volume por canal, vídeo e texto. Vale na hora e fica gravado. */
    public void showOptions(Runnable onBack) {
        open(() -> buildOptions(onBack));
    }

    private void buildOptions(Runnable onBack) {
        Runnable voltar = () -> {
            settings.save();
            onBack.run();
        };
        root.clearChildren();
        root.center().pad(0);
        Table painel = ui.painel(texts.get("opcoes.titulo"));
        Foco foco = ui.foco();
        Table list = new Table();
        list.top().left();
        list.defaults().left().growX();
        volume(list, foco, "opcoes.geral", () -> settings.volumeGeral, v -> settings.volumeGeral = v);
        volume(list, foco, "opcoes.musica", () -> settings.volumeMusica, v -> settings.volumeMusica = v);
        volume(list, foco, "opcoes.ambiente", () -> settings.volumeAmbiente, v -> settings.volumeAmbiente = v);
        volume(list, foco, "opcoes.efeitos", () -> settings.volumeEfeitos, v -> settings.volumeEfeitos = v);
        volume(list, foco, "opcoes.interface", () -> settings.volumeInterface, v -> settings.volumeInterface = v);
        divider(list);
        toggle(list, foco, "opcoes.tela_cheia", () -> settings.telaCheia, v -> settings.telaCheia = v);
        toggle(list, foco, "opcoes.vsync", () -> settings.vsync, v -> settings.vsync = v);
        cycle(list, foco, "opcoes.fps", new String[] {"30", "60", "120", texts.get("opcoes.sem_limite")},
                () -> switch (settings.limiteFps) { case 30 -> 0; case 120 -> 2; case 0 -> 3; default -> 1; },
                i -> settings.limiteFps = new int[] {30, 60, 120, 0}[i]);
        toggle(list, foco, "opcoes.pos", () -> settings.posProcessamento, v -> settings.posProcessamento = v);
        toggle(list, foco, "opcoes.luz", () -> settings.luzDinamica, v -> settings.luzDinamica = v);
        cycle(list, foco, "opcoes.detalhes", new String[] {texts.get("opcoes.detalhes.alto"), texts.get("opcoes.detalhes.baixo")},
                () -> settings.detalhesAmbiente ? 0 : 1, i -> settings.detalhesAmbiente = i == 0);
        divider(list);
        cycle(list, foco, "opcoes.texto", new String[] {texts.get("opcoes.texto.normal"), texts.get("opcoes.texto.grande"),
                texts.get("opcoes.texto.maior")},
                () -> settings.tamanhoTexto >= 1.5f ? 2 : settings.tamanhoTexto >= 1.25f ? 1 : 0,
                i -> settings.tamanhoTexto = new float[] {1f, 1.25f, 1.5f}[i]);
        cycle(list, foco, "opcoes.velocidade", new String[] {texts.get("opcoes.vel.lenta"), texts.get("opcoes.vel.normal"),
                texts.get("opcoes.vel.rapida")},
                () -> settings.velocidadeTexto >= 1.5f ? 0 : settings.velocidadeTexto <= 0.75f ? 2 : 1,
                i -> settings.velocidadeTexto = new float[] {1.6f, 1f, 0.7f}[i]);
        Label note = new Label(texts.get("opcoes.ritmo"), tema.tintaSuave);
        note.setWrap(true);
        list.add(note).padTop(line(0.5f)).row();

        painel.add(ui.rolagem(list)).grow().padRight(2).row();
        painel.add(button(foco, texts.get("menu.voltar"), voltar)).left().minHeight(font.getLineHeight() * 1.9f)
                .padTop(line(0.5f));
        painel.addActor(foco);
        root.add(painel).width(ui.largura(stage, 0.92f, 30)).maxHeight(ui.altura(stage, 0.92f, 34));
        back = voltar;
    }

    /** Esc no meio do jogo (o mundo para enquanto o menu está aberto, RF-324). */
    public void showPause() {
        inGame = true;
        open(this::buildPause);
    }

    private void buildPause() {
        root.clearChildren();
        root.center().pad(0);
        Table painel = ui.painel(texts.get("pausa.titulo"));
        Foco foco = ui.foco();
        float w = ui.largura(stage, 0.6f, 14);
        pauseButton(painel, foco, texts.get("pausa.continuar"), actions::resume, w);
        if (actions.canSave()) pauseButton(painel, foco, texts.get("pausa.salvar"), () -> showSlots(true, this::showPause), w);
        else painel.add(new Label(texts.get("pausa.nao_salva"), tema.desativado)).center().padBottom(line(0.5f)).padTop(line(0.2f)).row();
        pauseButton(painel, foco, texts.get("menu.carregar"), () -> showSlots(false, this::showPause), w);
        pauseButton(painel, foco, texts.get("menu.opcoes"), () -> showOptions(this::showPause), w);
        pauseButton(painel, foco, texts.get("pausa.menu"), actions::toMainMenu, w);
        pauseButton(painel, foco, texts.get("menu.sair"), actions::quit, w);
        painel.addActor(foco);
        root.add(painel);
        back = actions::resume;
    }

    private void pauseButton(Table col, Foco foco, String text, Runnable action, float width) {
        TextButton b = ui.botao(text, action);
        foco.adicionar(b);
        col.add(b).width(width).minHeight(font.getLineHeight() * 1.9f).padBottom(font.getLineHeight() * 0.3f).row();
    }

    // ---------------------------------------------------------------- peças

    private float line(float n) { return font.getLineHeight() * n; }

    private TextButton button(Foco foco, String text, Runnable action) {
        TextButton b = ui.botao(text, action);
        foco.adicionar(b);
        return b;
    }

    private void divider(Table list) {
        list.add(ui.divisoria()).padTop(line(0.4f)).padBottom(line(0.4f)).row();
    }

    /** Uma linha de opção: o nome à esquerda, o controle à direita; o conjunto todo é um item do foco. */
    private Table row(String key, Actor control) {
        Table r = new Table();
        r.pad(line(0.15f), 4, line(0.15f), 4);
        Label name = new Label(texts.get(key), tema.tinta);
        name.setEllipsis(true);
        r.add(name).left().growX().minWidth(0).padRight(line(0.8f));
        r.add(control).right();
        return r;
    }

    private void volume(Table list, Foco foco, String key, FloatGet get, FloatSet set) {
        Slider slider = new Slider(0, 1, 0.1f, false, tema.deslizante);
        slider.setValue(get.get());
        Label value = new Label("", tema.tintaSuave);
        value.setAlignment(Align.right);
        Runnable refresh = () -> value.setText(Math.round(get.get() * 100) + "%");
        refresh.run();
        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                float v = Math.max(0, Math.min(1, Math.round(slider.getValue() * 10) / 10f));
                if (v == get.get()) return;
                set.set(v);
                refresh.run();
                actions.settingsChanged();
            }
        });
        Table control = new Table();
        control.add(slider).width(line(8)).padRight(line(0.6f));
        control.add(value).width(new GlyphLayout(font, "100%").width + 2).right();
        Table r = row(key, control);
        IntConsumer side = d -> slider.setValue(Math.max(0, Math.min(1, slider.getValue() + d * 0.1f)));
        foco.adicionar(r, null, side);
        list.add(r).row();
    }

    private void toggle(Table list, Foco foco, String key, BoolGet get, BoolSet set) {
        CheckBox box = new CheckBox(" " + texts.get(get.get() ? "opcoes.sim" : "opcoes.nao"), tema.caixa);
        box.setChecked(get.get());
        box.getLabelCell().padLeft(line(0.3f));
        box.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                set.set(box.isChecked());
                box.setText(" " + texts.get(box.isChecked() ? "opcoes.sim" : "opcoes.nao"));
                actions.settingsChanged();
            }
        });
        Table r = row(key, box);
        foco.adicionar(r, () -> box.setChecked(!box.isChecked()), d -> box.setChecked(d > 0));
        list.add(r).row();
    }

    private void cycle(Table list, Foco foco, String key, String[] options, IntGet get, IntConsumer set) {
        Table control = choice(options, get, set);
        Table r = row(key, control);
        foco.adicionar(r, () -> step(control).accept(1), step(control));
        list.add(r).row();
    }

    private static IntConsumer step(Table choice) { return (IntConsumer) choice.getUserObject(); }

    /** Escolha: [<] valor [>]; o passo (-1/+1) fica no userObject para o foco usar com as setas. */
    private Table choice(String[] options, IntGet get, IntConsumer set) {
        Label value = new Label(options[get.get()], tema.tinta);
        value.setAlignment(Align.center);
        float widest = 0;
        for (String o : options) widest = Math.max(widest, new GlyphLayout(font, o).width);
        IntConsumer step = d -> {
            set.accept(Math.floorMod(get.get() + d, options.length));
            value.setText(options[get.get()]);
            actions.settingsChanged();
        };
        TextButton less = ui.botao("<", () -> step.accept(-1));
        TextButton more = ui.botao(">", () -> step.accept(1));
        Table control = new Table();
        control.add(less).minWidth(line(1.8f));
        control.add(value).width(widest + line(1.2f));
        control.add(more).minWidth(line(1.8f));
        control.setUserObject(step);
        return control;
    }

    private interface FloatGet { float get(); }
    private interface FloatSet { void set(float v); }
    private interface BoolGet { boolean get(); }
    private interface BoolSet { void set(boolean v); }
    private interface IntGet { int get(); }

    /** Monta a lista de slots a partir de nomes (útil para quem chama). */
    public static List<String> slotNames(boolean saving) {
        List<String> names = new ArrayList<>(List.of("jogo_1", "jogo_2", "jogo_3"));
        if (!saving) names.addAll(List.of("rapido", "auto_1", "auto_2", "auto_3"));
        return names;
    }

    @Override
    public void dispose() {
        stage.dispose();
        fx.dispose();
        if (donoDoTema) tema.dispose();
        white.dispose();
    }
}
