package etrava.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import etrava.client.assets.Assets;
import etrava.client.ui.telas.Contexto;
import etrava.client.ui.telas.TelaComercio;
import etrava.client.ui.telas.TelaConversa;
import etrava.client.ui.telas.TelaMochila;
import etrava.sim.api.Command;
import etrava.sim.api.DialogueView;
import etrava.sim.api.TradeView;
import etrava.sim.api.WorldView;
import etrava.sim.content.ContentDb;
import etrava.sim.content.FactDef;
import etrava.sim.content.ItemDef;
import etrava.sim.content.NpcDef;
import etrava.sim.content.SkillsDef;
import etrava.sim.economy.Economy;
import etrava.sim.world.KnowledgeEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Telas do jogo sobre o cenário: diálogo, caderno, mochila, ficha do personagem,
 * comércio e contêiner. Aparência de papel e tinta
 * (PRD 10.1), sem marcadores de objetivo. O caderno mostra o que o personagem
 * ouviu e viu, com a fonte — nunca o que fazer (RF-121).
 */
public final class GameUi implements Disposable {
    private static final Color PAPER = new Color(0xD8CCB0FF), PAPER_DARK = new Color(0xB9A888FF);
    private static final Color INK = new Color(0x241C18FF), INK_SOFT = new Color(0x5A4636FF);
    private static final Color RUST = new Color(0x6E2C22FF), EDGE = new Color(0x2E2018FF);
    /** Quantos fatos marcados viram opção de "perguntar sobre" numa conversa. */
    public static final int MAX_ASK = TelaConversa.MAX_PERGUNTAS;

    private final Stage stage;
    private final Texture white;
    private final BitmapFont font;
    private final Assets assets;
    private final ContentDb content;
    private final Texts texts;
    private final Drawable paper, paperDark, edge;
    private final Label.LabelStyle ink, inkSoft, rust;
    /** O tema novo (V7) e os utilitários de tela; as telas já refeitas usam estes, as outras ainda o papel antigo. */
    private final Tema tema;
    private final Ui ui;
    private final Contexto contexto;

    private final Table notebookRoot = new Table();
    private final Table epilogueRoot = new Table();
    private final Table characterRoot = new Table();
    /** Conversa, comércio (e contêiner) e mochila: uma classe por tela, em {@code ui/telas/}. */
    private final TelaConversa conversa;
    private final TelaComercio comercio;
    private final TelaMochila mochila;
    private final Table mapRoot = new Table();
    private boolean mapOpen;
    private String mapShown = "";
    private boolean inventoryOpen, characterOpen;
    /** O que estava na tela da última vez, para só remontar quando algo mudar. */
    private String inventoryShown = "", characterShown = "";
    private Consumer<Command> onCommand = c -> { };
    private boolean epilogueOpen, deathOpen;
    private Consumer<String> onLoad = name -> { };
    private Consumer<String> onToggleMark = f -> { };
    private boolean notebookOpen;
    /** Aba do caderno: 0 ouvi dizer, 1 vi e li, 2 pessoas, 3 lugares, 4 documentos (PRD 10.2). */
    private int notebookTab;
    private int notebookEntries = -1, notebookMarks = -1;

    public GameUi(BitmapFont font, Assets assets, ContentDb content, Texts texts) {
        this(font, assets, content, texts, new UiStage(new ScreenViewport()));
    }

    /** O teste passa um Stage sem SpriteBatch (não há GL de verdade sem janela). */
    GameUi(BitmapFont font, Assets assets, ContentDb content, Texts texts, Stage stage) {
        this.stage = stage;
        this.font = font;
        this.assets = assets;
        this.content = content;
        this.texts = texts;
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
        tema = new Tema(assets.root(), assets.manifest(), font);
        ui = new Ui(tema);
        Contexto ctx = new Contexto(stage, tema, ui, assets, content, texts);
        conversa = new TelaConversa(ctx);
        comercio = new TelaComercio(ctx);
        mochila = new TelaMochila(ctx, assets);
        mochila.aoFechar(() -> inventoryOpen = false);
        contexto = ctx;

        notebookRoot.setFillParent(true);
        notebookRoot.center();
        Ui.esconder(notebookRoot);
        epilogueRoot.setFillParent(true);
        epilogueRoot.center();
        Ui.esconder(epilogueRoot);
        characterRoot.setFillParent(true);
        characterRoot.center();
        Ui.esconder(characterRoot);
        stage.addActor(conversa.raiz());
        stage.addActor(notebookRoot);
        stage.addActor(mochila.raiz());
        stage.addActor(characterRoot);
        stage.addActor(comercio.raiz());
        mapRoot.setFillParent(true);
        mapRoot.center();
        Ui.esconder(mapRoot);
        stage.addActor(mapRoot);
        stage.addActor(epilogueRoot);
    }

    public Stage stage() { return stage; }
    public void onChoice(IntConsumer handler) { conversa.aoEscolher(handler); }
    public void onToggleMark(Consumer<String> handler) { this.onToggleMark = handler; }
    public boolean notebookOpen() { return notebookOpen; }
    /** Comandos que as telas da mochila e do comércio enviam (comer, vestir, comprar…). */
    public void onCommand(Consumer<Command> handler) {
        this.onCommand = handler;
        contexto.comando = handler;
    }
    public boolean inventoryOpen() { return inventoryOpen; }
    public boolean characterOpen() { return characterOpen; }
    public boolean mapOpen() { return mapOpen; }

    /** Mapa ilustrado (M, RF-385): os lugares por onde já se passou e onde se está. Nenhum objetivo. */
    public void toggleMap() {
        mapOpen = !mapOpen;
        inventoryOpen = characterOpen = false;
        mapShown = "";
    }

    /** Mochila (I) e ficha (C) param o relógio enquanto abertas, como o caderno não para. */
    public void toggleInventory() {
        inventoryOpen = !inventoryOpen;
        characterOpen = false;
        inventoryShown = "";
    }

    public void toggleCharacter() {
        characterOpen = !characterOpen;
        inventoryOpen = false;
        characterShown = "";
    }

    /** Fecha mochila e ficha; devolve true se alguma estava aberta. */
    public boolean closeWindows() {
        boolean was = inventoryOpen || characterOpen || mapOpen;
        inventoryOpen = characterOpen = mapOpen = false;
        return was;
    }
    public List<String> askFacts() { return conversa.perguntas(); }

    public void toggleNotebook() {
        notebookOpen = !notebookOpen;
        notebookEntries = -1;
    }

    public void closeNotebook() { notebookOpen = false; }

    /**
     * Uma folha por cima de tudo, com o jogo parado: epílogo, "você acorda", rendido, morte.
     * (O nome ficou do M4, quando a única folha era o epílogo.)
     */
    public boolean epilogueOpen() { return epilogueOpen; }

    /** A tela de morte está aberta: só sai carregando um jogo (RF-341). */
    public boolean deathOpen() { return deathOpen; }
    /** Alguma página cheia (epílogo, derrota, morte) está na tela. */
    public boolean pageOpen() { return epilogueRoot.isVisible(); }

    /** Quem trata o pedido de carregar um save da tela de morte (recebe o nome do save). */
    public void onLoad(Consumer<String> handler) { this.onLoad = handler; }

    public void closeEpilogue() {
        if (deathOpen) return;
        epilogueOpen = false;
        Ui.esconder(epilogueRoot);
    }

    /** Fecha a tela de morte depois que um jogo foi carregado. */
    public void closeDeath() {
        deathOpen = false;
        closeEpilogue();
    }

    /** Um documento aberto para ler (RF-124): carta, registro, bilhete. */
    public void showDocument(String title, String text) {
        showPage(title, List.of(text), texts.get("documento.dica"), null, 600, 380);
    }

    /** Tela do fim do capítulo (RF-163): os trechos do epílogo, um por parágrafo. O jogo continua depois. */
    public void showEpilogue(List<String> lines) {
        // painéis ilustrados entre os parágrafos (PRD 10.2): a vila no começo, a árvore no meio
        showPage(texts.get("epilogo.titulo"), lines, texts.get("epilogo.dica"), null, 760, 600,
                List.of("ilustracao.epilogo_vila", "ilustracao.epilogo_arvore"));
    }

    /** Derrota não letal (RF-340): o que se perdeu e onde se acordou. */
    public void showWake(List<String> lines) {
        showPage(texts.get("derrota.titulo"), lines, texts.get("derrota.dica"), null, 620, 340);
    }

    /**
     * Morte (RF-341): o que aconteceu com o mundo, e os saves que dá para carregar.
     *
     * @param saves nome do save → como ele aparece ("Salvamento rápido — dia 3, 14:20")
     */
    public void showDeath(List<String> lines, Map<String, String> saves) {
        Table options = new Table();
        options.defaults().left().growX();
        if (saves.isEmpty()) options.add(new Label(texts.get("morte.sem_save"), inkSoft)).row();
        for (Map.Entry<String, String> e : saves.entrySet()) {
            Label option = new Label("> " + e.getValue(), ink);
            option.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onLoad.accept(e.getKey());
                }

                @Override
                public void enter(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor from) {
                    option.setColor(RUST);
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor to) {
                    option.setColor(INK);
                }
            });
            options.add(option).padBottom(6).row();
        }
        showPage(texts.get("morte.titulo"), lines, texts.get("morte.dica"), options, 760, 560);
        deathOpen = true;
    }

    /** Um inimigo rendido (RF-224): poupar, tomar o que ele leva ou acabar com ele. */
    public void showResolve(String enemyId, String name, String line) {
        Table options = new Table();
        options.defaults().left().growX();
        for (String action : List.of("poupar", "saquear", "executar")) {
            Label option = clickable(texts.get("rendido." + action), new Command.Resolve(enemyId, action));
            option.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    closeEpilogue();
                }
            });
            options.add(option).padBottom(6).row();
        }
        List<String> lines = line == null ? List.of(texts.get("rendido.texto", name))
                : List.of(texts.get("rendido.texto", name), "\"" + line + "\"");
        showPage(texts.get("rendido.titulo"), lines, texts.get("rendido.dica"), options, 560, 320);
    }

    private void showPage(String title, List<String> lines, String hint, Table extra, float width, float height) {
        showPage(title, lines, hint, extra, width, height, List.of());
    }

    private void showPage(String title, List<String> lines, String hint, Table extra, float width, float height,
            List<String> art) {
        epilogueRoot.clearChildren();
        Table page = new Table();
        page.setBackground(paper);
        page.pad(24).top();
        page.add(new Label(title, rust)).left().padBottom(14).row();
        Table body = new Table();
        body.top().left();
        body.defaults().left().growX();
        float artW = Math.min(stage.getWidth() - 60, width) - 70;
        for (int i = 0; i < lines.size(); i++) {
            int panel = art.isEmpty() ? -1 : i == 0 ? 0 : i == lines.size() / 2 ? 1 : -1;
            if (panel >= 0 && panel < art.size()) {
                Texture tex = assets.texture(art.get(panel));
                if (tex != null) body.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(tex)).size(artW, artW * 9f / 16f)
                        .padBottom(14).row();
            }
            Label paragraph = new Label(lines.get(i), ink);
            paragraph.setWrap(true);
            body.add(paragraph).padBottom(12).row();
        }
        ScrollPane scroll = Scrolls.of(body, white, PAPER_DARK, EDGE);
        page.add(scroll).grow().row();
        if (extra != null) page.add(extra).growX().padTop(8).row();
        page.add(new Label(hint, inkSoft)).right().padTop(10);
        Table border = new Table();
        border.setBackground(edge);
        border.pad(3);
        border.add(page).grow();
        epilogueRoot.add(border).size(Math.min(stage.getWidth() - 60, width), Math.min(stage.getHeight() - 60, height));
        epilogueRoot.setVisible(true);
        epilogueOpen = true;
    }

    public void resize(int width, int height) {
        ui.escala(Escala.aplicar(stage.getViewport(), width, height));
        conversa.invalidar();
        comercio.invalidar();
        notebookEntries = -1;
        inventoryShown = characterShown = "";
    }

    /** Atualiza as telas conforme o estado e desenha. */
    public void render(WorldView view, float dt) {
        render(view, null, null, dt);
    }

    /**
     * @param trade comércio ou contêiner aberto (com preços), ou null
     * @param eco   consultas de peso e capacidade; null = sem mochila nem ficha
     */
    public void render(WorldView view, TradeView trade, Economy eco, float dt) {
        update(view, trade, eco);
        stage.act(dt);
        stage.draw();
    }

    /** Monta ou esconde cada tela conforme o estado; não desenha. */
    void update(WorldView view, TradeView trade, Economy eco) {
        comercio.atualizar(view, trade, eco);
        boolean showInventory = inventoryOpen && eco != null && trade == null && !epilogueOpen;
        if (showInventory) {
            String sig = view.money() + "|" + view.inventory() + "|" + view.equipment() + "|" + Math.round(view.hunger());
            if (!sig.equals(inventoryShown)) {
                mochila.montar(view, eco);
                inventoryShown = sig;
            }
        }
        mochila.mostrar(showInventory);
        boolean showCharacter = characterOpen && trade == null && !epilogueOpen;
        if (showCharacter) {
            String sig = view.skills().toString() + Math.round(view.hunger());
            if (!sig.equals(characterShown)) {
                buildCharacter(view);
                characterShown = sig;
            }
        }
        Ui.mostrar(characterRoot, showCharacter);
        boolean showMap = mapOpen && trade == null && !epilogueOpen;
        if (showMap) {
            String sig = view.areaId() + "|" + stage.getWidth() + "x" + stage.getHeight();
            if (!sig.equals(mapShown)) {
                buildMap(view);
                mapShown = sig;
            }
        }
        Ui.mostrar(mapRoot, showMap);
        DialogueView d = view.dialogue();
        conversa.atualizar(view, d);
        boolean showNotebook = notebookOpen && d == null && !epilogueOpen;
        if (showNotebook && (view.knowledge().size() != notebookEntries || view.markedFacts().size() != notebookMarks)) {
            buildNotebook(view);
            notebookEntries = view.knowledge().size();
            notebookMarks = view.markedFacts().size();
        }
        Ui.mostrar(notebookRoot, showNotebook);
    }

    private Label clickable(String text, Command command) {
        Label label = new Label(text, ink);
        label.setWrap(true);
        if (command == null) {
            label.setStyle(inkSoft);
            return label;
        }
        label.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onCommand.accept(command);
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor from) {
                label.setColor(RUST);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor to) {
                label.setColor(INK);
            }
        });
        return label;
    }

    private String hungerText(double hunger) {
        if (hunger >= Economy.STARVING) return texts.get("fome.faminto");
        if (hunger >= Economy.HUNGRY) return texts.get("fome.com_fome");
        return texts.get(hunger >= 35 ? "fome.pouca" : "fome.satisfeito");
    }

    /** "1 moeda", "12 moedas". */
    public String coins(int n) {
        return contexto.moedas(n);
    }

    /**
     * Ficha do personagem (PRD 10.2): habilidades com valor e descrição. Sem nível, sem pontos (RF-181).
     * Primeira tela sobre a base do V7: painel de madeira, lista com a rolagem do tema, dica flutuante
     * em cada habilidade, Esc fecha, setas/Tab andam entre as habilidades.
     */
    private void buildCharacter(WorldView view) {
        characterRoot.clearChildren();
        Table painel = ui.painel(texts.get("ficha.titulo"));
        Foco foco = ui.foco().aoFechar(() -> characterOpen = false);
        Table list = new Table();
        list.top().left();
        list.defaults().left().growX();
        for (SkillsDef.Skill skill : content.skills().list()) {
            double exact = view.skills().getOrDefault(skill.id(), 0.0);
            Table item = new Table();
            item.pad(3, 4, 3, 4);
            item.add(new Label(skill.displayName(), tema.tinta)).left().growX();
            item.add(new Label(String.valueOf((int) Math.floor(exact)), tema.ferrugem)).right().row();
            Label desc = new Label(skill.description() == null ? "" : skill.description(), tema.tintaSuave);
            desc.setWrap(true);
            item.add(desc).colspan(2).growX().padLeft(10).row();
            ui.dica(item, texts.get("ficha.habilidade_dica", skill.displayName(),
                    String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f", exact)));
            foco.adicionar(item, null);
            list.add(item).padBottom(4).row();
        }
        painel.add(ui.rolagem(list)).grow().padRight(2).row();
        painel.add(new Label(hungerText(view.hunger()), view.hunger() >= Economy.HUNGRY ? tema.ferrugem : tema.tintaSuave))
                .left().padTop(6).row();
        painel.add(new Label(texts.get("ficha.dica"), tema.tintaSuave)).right().padTop(4);
        painel.addActor(foco);
        characterRoot.add(painel).width(ui.largura(stage, 0.92f, 30)).height(ui.altura(stage, 0.92f, 24));
    }

    // ---------------------------------------------------------------- mapa

    private void buildMap(WorldView view) {
        mapRoot.clearChildren();
        Table page = new Table();
        page.setBackground(paper);
        page.pad(14).top();
        page.add(new Label(texts.get("mapa.titulo"), rust)).left().padBottom(8).row();
        float w = Math.min(stage.getWidth() - 80, (stage.getHeight() - 130) * 16f / 9f), h = w * 9f / 16f;
        com.badlogic.gdx.scenes.scene2d.Group map = new com.badlogic.gdx.scenes.scene2d.Group();
        map.setSize(w, h);
        Texture art = assets.texture("ilustracao.mapa");
        if (art != null) {
            com.badlogic.gdx.scenes.scene2d.ui.Image img = new com.badlogic.gdx.scenes.scene2d.ui.Image(art);
            img.setSize(w, h);
            map.addActor(img);
        }
        for (var e : content.areaEntries().values()) {
            if (e.mapPos() == null || e.mapPos().size() < 2 || !view.flag("visitou." + e.id())) continue;
            float x = (float) (e.mapPos().get(0) * w), y = (float) ((1 - e.mapPos().get(1)) * h);
            boolean here = e.id().equals(view.areaId());
            com.badlogic.gdx.scenes.scene2d.ui.Image dot = new com.badlogic.gdx.scenes.scene2d.ui.Image(here ? edge : base(RUST));
            float size = here ? 12 : 8;
            dot.setBounds(x - size / 2, y - size / 2, size, size);
            map.addActor(dot);
            Label.LabelStyle st = new Label.LabelStyle(font, here ? INK : RUST);
            st.background = paperDark;
            Label name = new Label(" " + (here ? texts.get("mapa.voce", e.displayName()) : e.displayName()) + " ", st);
            name.pack();
            name.setPosition(Math.min(w - name.getWidth(), Math.max(0, x - name.getWidth() / 2)), y + 8);
            map.addActor(name);
        }
        Table frame = new Table();
        frame.setBackground(edge);
        frame.pad(2);
        frame.add(map).size(w, h);
        page.add(frame).row();
        page.add(new Label(texts.get("mapa.dica"), inkSoft)).right().padTop(6);
        mapRoot.add(page);
    }

    private Drawable base(Color c) {
        return new TextureRegionDrawable(white).tint(c);
    }

    // ---------------------------------------------------------------- caderno

    private void buildNotebook(WorldView view) {
        notebookRoot.clearChildren();
        Table page = new Table();
        page.setBackground(paper);
        page.pad(18).top();
        page.add(new Label(texts.get("caderno.titulo"), rust)).left().padBottom(8).row();

        Table tabs = new Table();
        String[] names = {"ouvi", "vi", "pessoas", "lugares", "documentos"};
        for (int i = 0; i < names.length; i++)
            tabs.add(tab(texts.get("caderno.aba." + names[i]), notebookTab == i, i)).padRight(8);
        page.add(tabs).left().padBottom(10).row();

        Table list = new Table();
        list.top().left();
        list.defaults().left().growX();
        int shown = notebookTab >= 2 ? notebookExtra(view, list) : 0;
        for (KnowledgeEntry k : notebookTab >= 2 ? List.<KnowledgeEntry>of() : view.knowledge()) {
            boolean seen = !k.certainty().equals("rumor") && !k.certainty().equals("claim");
            if (seen != (notebookTab == 1)) continue;
            FactDef fact = content.facts().get(k.fact());
            if (fact == null) continue;
            boolean marked = view.markedFacts().contains(k.fact());
            Label head = new Label((marked ? "* " : "") + texts.get("caderno.dia", k.day()) + " · " + source(k), inkSoft);
            Label body = new Label(fact.text(k.version()), marked ? rust : ink);
            body.setWrap(true);
            Table entry = new Table();
            entry.defaults().left().growX();
            entry.add(head).row();
            entry.add(body).padBottom(10).row();
            entry.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
            entry.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onToggleMark.accept(k.fact());
                }
            });
            list.add(entry).row();
            shown++;
        }
        if (shown == 0) list.add(new Label(texts.get("caderno.vazio"), inkSoft)).row();
        ScrollPane scroll = Scrolls.of(list, white, PAPER_DARK, EDGE);
        page.add(scroll).grow().row();
        page.add(new Label(texts.get("caderno.dica"), inkSoft)).right().padTop(8);

        Table border = new Table();
        border.setBackground(edge);
        border.pad(3);
        border.add(page).grow();
        notebookRoot.add(border).size(Math.min(stage.getWidth() - 60, 720), Math.min(stage.getHeight() - 60, 500));
    }

    private Label tab(String text, boolean active, int index) {
        Label.LabelStyle style = new Label.LabelStyle(font, active ? INK : INK_SOFT);
        style.background = active ? paperDark : null;
        Label label = new Label(" " + text + " ", style);
        label.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                notebookTab = index;
                notebookEntries = -1;
            }
        });
        return label;
    }

    /** Vai direto a uma aba (0 a 4); para o console de depuração e as capturas. */
    public void notebookTabTo(int tab) {
        notebookTab = Math.floorMod(tab, 5);
        notebookEntries = -1;
    }

    /** Muda de aba com as setas (para quem joga pelo teclado). */
    public void notebookTab(int delta) {
        notebookTab = Math.floorMod(notebookTab + delta, 5);
        notebookEntries = -1;
    }

    /**
     * Abas Pessoas, Lugares e Documentos: o que o personagem sabe de quem conheceu, por onde
     * passou e o que tem para ler. Nada de objetivo: só o que é (RF-121).
     */
    private int notebookExtra(WorldView view, Table list) {
        int shown = 0;
        switch (notebookTab) {
            case 2 -> {
                for (NpcDef npc : content.npcs().values()) {
                    if (!view.flag("conheceu." + npc.id())) continue;
                    String status = switch (String.valueOf(view.npcStatus(npc.id()))) {
                        case "departed" -> texts.get("pessoas.partiu");
                        case "dead" -> texts.get("pessoas.morreu");
                        default -> "";
                    };
                    int op = view.opinion(npc.id());
                    String mood = texts.get(op >= 40 ? "pessoas.gosta" : op >= 10 ? "pessoas.simpatia"
                            : op > -10 ? "pessoas.neutro" : op > -40 ? "pessoas.desconfia" : "pessoas.detesta");
                    Label head = new Label(npc.displayName() + (status.isEmpty() ? "" : " · " + status), rust);
                    Label body = new Label((npc.about() != null ? npc.about() + " " : "") + mood, ink);
                    body.setWrap(true);
                    list.add(head).row();
                    list.add(body).padBottom(10).row();
                    shown++;
                }
            }
            case 3 -> {
                Map<String, List<String>> byPlace = new java.util.LinkedHashMap<>();
                for (var e : content.areaEntries().values()) {
                    if (!view.flag("visitou." + e.id())) continue;
                    String place = e.place() != null ? e.place() : e.displayName();
                    byPlace.computeIfAbsent(place, k -> new ArrayList<>()).add(e.displayName());
                }
                for (Map.Entry<String, List<String>> e : byPlace.entrySet()) {
                    list.add(new Label(e.getKey(), rust)).row();
                    Label body = new Label(String.join(" · ", e.getValue()), ink);
                    body.setWrap(true);
                    list.add(body).padBottom(10).row();
                    shown++;
                }
            }
            default -> {
                for (Map.Entry<String, Integer> e : view.inventory().entrySet()) {
                    var item = content.items().get(e.getKey());
                    if (item == null || !item.document()) continue;
                    Label head = new Label(item.displayName(), rust);
                    Label body = new Label(item.description() + "  [" + texts.get("documento.ler") + "]", ink);
                    body.setWrap(true);
                    Table entry = new Table();
                    entry.defaults().left().growX();
                    entry.add(head).row();
                    entry.add(body).padBottom(10).row();
                    entry.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
                    entry.addListener(new ClickListener() {
                        @Override
                        public void clicked(InputEvent event, float x, float y) {
                            onCommand.accept(new Command.UseItem(item.id()));
                        }
                    });
                    list.add(entry).row();
                    shown++;
                }
            }
        }
        return shown;
    }

    /** "Fulano disse:", "Ouvi de passagem:"… — de onde veio a anotação (RF-104). */
    private String source(KnowledgeEntry k) {
        String src = k.source();
        if (src.startsWith("npc:")) {
            NpcDef npc = content.npcs().get(src.substring(4));
            String name = npc != null ? npc.displayName() : src.substring(4);
            return texts.get(k.certainty().equals("rumor") ? "fonte.rumor" : "fonte.claim", name);
        }
        if (src.startsWith("conversa:")) return texts.get("fonte.conversa");
        return switch (k.certainty()) {
            case "witnessed" -> texts.get("fonte.witnessed");
            case "read" -> texts.get("fonte.read");
            case "verified" -> texts.get("fonte.verified");
            default -> texts.get("fonte.mundo");
        };
    }

    @Override
    public void dispose() {
        stage.dispose();
        tema.dispose();
        white.dispose();
    }
}
