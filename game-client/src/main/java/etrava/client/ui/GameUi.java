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
    public static final int MAX_ASK = 3;
    /** Lado do ícone de item na tela, em pixels. */
    private static final float ICON = 40;

    private final Stage stage = new Stage(new ScreenViewport());
    private final Texture white;
    private final BitmapFont font;
    private final Assets assets;
    private final ContentDb content;
    private final Texts texts;
    private final Drawable paper, paperDark, edge;
    private final Label.LabelStyle ink, inkSoft, rust;

    private final Table dialogueRoot = new Table();
    private final Table notebookRoot = new Table();
    private final Table epilogueRoot = new Table();
    private final Table inventoryRoot = new Table();
    private final Table characterRoot = new Table();
    private final Table tradeRoot = new Table();
    private final Table mapRoot = new Table();
    private boolean mapOpen;
    private String mapShown = "";
    private boolean inventoryOpen, characterOpen;
    /** O que estava na tela da última vez, para só remontar quando algo mudar. */
    private String inventoryShown = "", characterShown = "", tradeShown = "";
    private Consumer<Command> onCommand = c -> { };
    private boolean epilogueOpen, deathOpen;
    private Consumer<String> onLoad = name -> { };
    private IntConsumer onChoice = i -> { };
    private Consumer<String> onToggleMark = f -> { };
    private Object dialogueShown;
    private int dialogueLines = -1;
    private boolean notebookOpen;
    /** Aba do caderno: 0 ouvi dizer, 1 vi e li, 2 pessoas, 3 lugares, 4 documentos (PRD 10.2). */
    private int notebookTab;
    private int notebookEntries = -1, notebookMarks = -1;
    /** Fatos oferecidos como "perguntar sobre" na conversa aberta, na ordem das opções. */
    private final List<String> askFacts = new ArrayList<>();

    public GameUi(BitmapFont font, Assets assets, ContentDb content, Texts texts) {
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

        dialogueRoot.setFillParent(true);
        dialogueRoot.bottom().pad(16);
        dialogueRoot.setVisible(false);
        notebookRoot.setFillParent(true);
        notebookRoot.center();
        notebookRoot.setVisible(false);
        epilogueRoot.setFillParent(true);
        epilogueRoot.center();
        epilogueRoot.setVisible(false);
        for (Table t : List.of(inventoryRoot, characterRoot, tradeRoot)) {
            t.setFillParent(true);
            t.center();
            t.setVisible(false);
        }
        stage.addActor(dialogueRoot);
        stage.addActor(notebookRoot);
        stage.addActor(inventoryRoot);
        stage.addActor(characterRoot);
        stage.addActor(tradeRoot);
        mapRoot.setFillParent(true);
        mapRoot.center();
        mapRoot.setVisible(false);
        stage.addActor(mapRoot);
        stage.addActor(epilogueRoot);
    }

    public Stage stage() { return stage; }
    public void onChoice(IntConsumer handler) { this.onChoice = handler; }
    public void onToggleMark(Consumer<String> handler) { this.onToggleMark = handler; }
    public boolean notebookOpen() { return notebookOpen; }
    /** Comandos que as telas da mochila e do comércio enviam (comer, vestir, comprar…). */
    public void onCommand(Consumer<Command> handler) { this.onCommand = handler; }
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
    public List<String> askFacts() { return askFacts; }

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

    /** Quem trata o pedido de carregar um save da tela de morte (recebe o nome do save). */
    public void onLoad(Consumer<String> handler) { this.onLoad = handler; }

    public void closeEpilogue() {
        if (deathOpen) return;
        epilogueOpen = false;
        epilogueRoot.setVisible(false);
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
        ScrollPane scroll = new ScrollPane(body, new ScrollPane.ScrollPaneStyle());
        scroll.setFadeScrollBars(false);
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
        stage.setScrollFocus(scroll);
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        dialogueLines = -1;
        notebookEntries = -1;
        inventoryShown = characterShown = tradeShown = "";
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
        if (trade == null) {
            tradeRoot.setVisible(false);
            tradeShown = "";
        } else {
            String sig = trade.toString() + view.money();
            if (!sig.equals(tradeShown)) {
                buildTrade(view, trade);
                tradeShown = sig;
            }
            tradeRoot.setVisible(true);
        }
        boolean showInventory = inventoryOpen && eco != null && trade == null && !epilogueOpen;
        if (showInventory) {
            String sig = view.money() + "|" + view.inventory() + "|" + view.equipment() + "|" + Math.round(view.hunger());
            if (!sig.equals(inventoryShown)) {
                buildInventory(view, eco);
                inventoryShown = sig;
            }
        }
        inventoryRoot.setVisible(showInventory);
        boolean showCharacter = characterOpen && trade == null && !epilogueOpen;
        if (showCharacter) {
            String sig = view.skills().toString() + Math.round(view.hunger());
            if (!sig.equals(characterShown)) {
                buildCharacter(view);
                characterShown = sig;
            }
        }
        characterRoot.setVisible(showCharacter);
        boolean showMap = mapOpen && trade == null && !epilogueOpen;
        if (showMap) {
            String sig = view.areaId() + "|" + stage.getWidth() + "x" + stage.getHeight();
            if (!sig.equals(mapShown)) {
                buildMap(view);
                mapShown = sig;
            }
        }
        mapRoot.setVisible(showMap);
        DialogueView d = view.dialogue();
        if (d == null) {
            dialogueRoot.setVisible(false);
            dialogueShown = null;
            dialogueLines = -1;
        } else if (d != dialogueShown || d.history().size() != dialogueLines) {
            buildDialogue(view, d);
            dialogueShown = d;
            dialogueLines = d.history().size();
        }
        boolean showNotebook = notebookOpen && d == null && !epilogueOpen;
        if (showNotebook && (view.knowledge().size() != notebookEntries || view.markedFacts().size() != notebookMarks)) {
            buildNotebook(view);
            notebookEntries = view.knowledge().size();
            notebookMarks = view.markedFacts().size();
        }
        notebookRoot.setVisible(showNotebook);
        stage.act(dt);
        stage.draw();
    }

    // ---------------------------------------------------------------- diálogo

    private void buildDialogue(WorldView view, DialogueView d) {
        dialogueRoot.clearChildren();
        dialogueRoot.setVisible(true);
        float width = Math.min(stage.getWidth() - 32, 980);

        Table text = new Table();
        text.setBackground(paper);
        text.pad(14).top().left();
        text.defaults().left().growX();
        // as duas últimas falas anteriores, apagadas, para dar contexto
        List<DialogueView.Line> history = d.history();
        for (int i = Math.max(0, history.size() - 3); i < history.size() - 1; i++) {
            Label old = new Label(history.get(i).speaker() + ": " + history.get(i).text(), inkSoft);
            old.setWrap(true);
            text.add(old).padBottom(4).row();
        }
        text.add(new Label(d.speakerName(), rust)).padTop(4).row();
        Label line = new Label(d.text(), ink);
        line.setWrap(true);
        text.add(line).padBottom(10).row();

        askFacts.clear();
        int number = 1;
        for (DialogueView.Choice c : d.choices()) {
            String label = (c.tag() != null ? "[" + c.tag() + "] " : "") + c.text();
            text.add(choice(number, label, number - 1)).padBottom(2).row();
            number++;
        }
        if (d.choices().isEmpty()) {
            text.add(choice(1, texts.get("dialogo.continuar"), 0)).row();
        } else {
            for (String factId : view.markedFacts()) {
                FactDef fact = content.facts().get(factId);
                if (fact == null || askFacts.size() >= MAX_ASK) continue;
                String version = view.knowledge().stream().filter(k -> k.fact().equals(factId)).findFirst()
                        .map(KnowledgeEntry::version).orElse(fact.defaultVersion());
                askFacts.add(factId);
                text.add(choice(number, texts.get("dialogo.perguntar", fact.text(version)), number - 1)).padBottom(2).row();
                number++;
            }
        }
        Label hint = new Label(texts.get("dialogo.dica"), inkSoft);
        hint.setAlignment(Align.right);
        text.add(hint).padTop(8).row();

        Image portrait = new Image(new TextureRegionDrawable(new TextureRegion(assets.portrait(d.portrait()))));
        Table frame = new Table();
        frame.setBackground(edge);
        frame.pad(4);
        frame.add(portrait).size(192, 256);

        Table border = new Table();
        border.setBackground(edge);
        border.pad(3);
        border.add(text).grow();
        dialogueRoot.add(frame).bottom().padRight(10);
        dialogueRoot.add(border).width(width - 220).bottom().minHeight(190);
    }

    private Label choice(int number, String text, int index) {
        Label label = new Label(number + ". " + text, ink);
        label.setWrap(true);
        label.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onChoice.accept(index);
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

    // ---------------------------------------------------------------- mochila, ficha e comércio

    /** Folha de papel com borda escura e título, do tamanho pedido. */
    private Table sheet(Table root, String title, float width, float height) {
        root.clearChildren();
        Table page = new Table();
        page.setBackground(paper);
        page.pad(18).top();
        page.add(new Label(title, rust)).left().padBottom(8).row();
        Table border = new Table();
        border.setBackground(edge);
        border.pad(3);
        border.add(page).grow();
        root.add(border).size(Math.min(stage.getWidth() - 60, width), Math.min(stage.getHeight() - 60, height));
        return page;
    }

    /** Ícone do item (RF-200): 20 px de arte, ampliados sem borrar; item sem ícone mostra o placeholder. */
    private Image icon(String itemId) {
        ItemDef def = content.items().get(itemId);
        return new Image(assets.sprite(def != null ? def.iconId() : "icone." + itemId).region());
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

    private static String kg(double v) {
        return String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    private String hungerText(double hunger) {
        if (hunger >= Economy.STARVING) return texts.get("fome.faminto");
        if (hunger >= Economy.HUNGRY) return texts.get("fome.com_fome");
        return texts.get(hunger >= 35 ? "fome.pouca" : "fome.satisfeito");
    }

    /** "1 moeda", "12 moedas". */
    public String coins(int n) {
        return n == 1 ? texts.get("moeda.uma") : texts.get("moeda.varias", Integer.toString(n));
    }

    /** Mochila e equipamento (PRD 10.2): lista por categoria, peso, encaixes. Clique come, veste ou tira. */
    private void buildInventory(WorldView view, Economy eco) {
        Table page = sheet(inventoryRoot, texts.get("mochila.titulo"), 860, 540);
        Table cols = new Table();
        cols.top().left();

        Table list = new Table();
        list.top().left();
        list.defaults().left().growX();
        boolean any = false;
        for (String category : ItemDef.CATEGORIES) {
            boolean header = false;
            for (Map.Entry<String, Integer> e : view.inventory().entrySet()) {
                ItemDef item = content.items().get(e.getKey());
                if (item == null || !category.equals(item.category())) continue;
                if (!header) {
                    list.add(new Label(texts.get("categoria." + category), rust)).padTop(any ? 8 : 0).row();
                    header = true;
                }
                any = true;
                Command action = item.usable() ? new Command.UseItem(item.id())
                        : item.slot() != null ? new Command.Equip(item.id()) : null;
                String verb = item.usable() ? texts.get(item.food() > 0 ? "mochila.comer" : item.document() ? "mochila.ler" : "mochila.usar")
                        : item.slot() != null ? texts.get("mochila.vestir") : "";
                String line = item.displayName() + (e.getValue() > 1 ? " x" + e.getValue() : "") + "  ·  "
                        + kg(item.weight() * e.getValue()) + " kg" + (verb.isEmpty() ? "" : "   [" + verb + "]");
                Label desc = new Label(item.description() == null ? "" : item.description(), inkSoft);
                desc.setWrap(true);
                Table text = new Table();
                text.left();
                text.defaults().left().growX();
                text.add(clickable(line, action)).row();
                text.add(desc).row();
                Table row = new Table();
                row.left();
                row.add(icon(item.id())).size(ICON).padRight(10).top();
                row.add(text).growX();
                list.add(row).padBottom(6).row();
            }
        }
        if (!any) list.add(new Label(texts.get("mochila.vazia"), inkSoft)).row();
        ScrollPane scroll = new ScrollPane(list, new ScrollPane.ScrollPaneStyle());
        scroll.setFadeScrollBars(false);

        Table slots = new Table();
        slots.top().left();
        slots.defaults().left().growX();
        slots.add(new Label(texts.get("mochila.corpo"), rust)).padBottom(4).row();
        for (String slot : ItemDef.SLOTS) {
            String itemId = view.equipment().get(slot);
            ItemDef item = itemId == null ? null : content.items().get(itemId);
            String line = texts.get("encaixe." + slot) + ": " + (item == null ? texts.get("encaixe.vazio") : item.displayName());
            slots.add(clickable(line, item == null ? null : new Command.Unequip(slot))).padBottom(2).row();
        }
        slots.add(new Label(texts.get("mochila.peso", kg(eco.weight()), kg(eco.capacity())),
                eco.overloaded() ? rust : ink)).padTop(14).row();
        if (eco.overloaded()) slots.add(new Label(texts.get("mochila.pesado"), rust)).row();
        slots.add(new Label(texts.get("mochila.dinheiro", coins(view.money())), ink)).padTop(6).row();
        slots.add(new Label(hungerText(view.hunger()), view.hunger() >= Economy.HUNGRY ? rust : ink)).padTop(6).row();

        cols.add(scroll).grow().padRight(16);
        cols.add(slots).width(250).top();
        page.add(cols).grow().row();
        page.add(new Label(texts.get("mochila.dica"), inkSoft)).right().padTop(8);
        stage.setScrollFocus(scroll);
    }

    /** Ficha do personagem (PRD 10.2): habilidades com valor e descrição. Sem nível, sem pontos (RF-181). */
    private void buildCharacter(WorldView view) {
        Table page = sheet(characterRoot, texts.get("ficha.titulo"), 640, 480);
        Table list = new Table();
        list.top().left();
        list.defaults().left().growX();
        for (SkillsDef.Skill skill : content.skills().list()) {
            int value = (int) Math.floor(view.skills().getOrDefault(skill.id(), 0.0));
            list.add(new Label(skill.displayName() + "  " + value, ink)).row();
            Label desc = new Label(skill.description() == null ? "" : skill.description(), inkSoft);
            desc.setWrap(true);
            list.add(desc).padLeft(12).padBottom(8).row();
        }
        page.add(list).grow().row();
        page.add(new Label(hungerText(view.hunger()), view.hunger() >= Economy.HUNGRY ? rust : inkSoft)).left().row();
        page.add(new Label(texts.get("ficha.dica"), inkSoft)).right().padTop(8);
    }

    /** Comércio (PRD 10.2): duas colunas, preço, dinheiro dos dois e o efeito da opinião. Contêiner: só pegar. */
    private void buildTrade(WorldView view, TradeView t) {
        Table page = sheet(tradeRoot, t.name(), 860, 520);
        Table head = new Table();
        head.left();
        if (t.container()) {
            head.add(new Label(t.owner() == null ? texts.get("conteiner.sem_dono")
                    : texts.get("conteiner.dono", t.owner()), t.owner() == null ? inkSoft : rust)).left();
        } else {
            head.add(new Label(texts.get("comercio.caixa", coins(t.partnerCash())), inkSoft)).padRight(20);
            head.add(new Label(texts.get("comercio.seu_dinheiro", coins(view.money())), ink)).padRight(20);
            int op = t.opinionPercent();
            head.add(new Label(op == 0 ? texts.get("comercio.opiniao_neutra")
                    : texts.get(op < 0 ? "comercio.opiniao_desconto" : "comercio.opiniao_acrescimo", Math.abs(op)), inkSoft));
        }
        page.add(head).left().padBottom(10).row();

        Table cols = new Table();
        cols.top();
        cols.add(offerColumn(texts.get(t.container() ? "conteiner.tem" : "comercio.vende"), t.offers(),
                t.container() ? "conteiner.pegar" : "comercio.comprar", t.container())).grow().top().padRight(16);
        if (!t.container()) cols.add(offerColumn(texts.get("comercio.compra"), t.wants(), "comercio.vender", false)
                ).grow().top();
        page.add(cols).grow().row();
        page.add(new Label(texts.get(t.container() ? "conteiner.dica" : "comercio.dica"), inkSoft)).right().padTop(8);
    }

    private Table offerColumn(String title, List<TradeView.Offer> offers, String verbKey, boolean container) {
        Table col = new Table();
        col.top().left();
        col.defaults().left().growX();
        col.add(new Label(title, rust)).padBottom(4).row();
        Table list = new Table();
        list.top().left();
        list.defaults().left().growX();
        for (TradeView.Offer o : offers) {
            String price = container ? "" : "  ·  " + (o.price() > 0 ? coins(o.price()) : texts.get("comercio.sem_valor"));
            Command cmd = !o.possible() ? null : switch (verbKey) {
                case "comercio.comprar" -> new Command.Buy(o.item());
                case "comercio.vender" -> new Command.Sell(o.item());
                default -> new Command.TakeItem(o.item());
            };
            String line = o.name() + " x" + o.qty() + price + (cmd == null ? "" : "   [" + texts.get(verbKey) + "]");
            Table row = new Table();
            row.left();
            row.add(icon(o.item())).size(ICON * 0.8f).padRight(8);
            row.add(clickable(line, cmd)).growX();
            list.add(row).padBottom(3).row();
        }
        if (offers.isEmpty()) list.add(new Label(texts.get("comercio.nada"), inkSoft)).row();
        ScrollPane scroll = new ScrollPane(list, new ScrollPane.ScrollPaneStyle());
        scroll.setFadeScrollBars(false);
        col.add(scroll).grow();
        return col;
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
        ScrollPane scroll = new ScrollPane(list, new ScrollPane.ScrollPaneStyle());
        scroll.setFadeScrollBars(false);
        page.add(scroll).grow().row();
        page.add(new Label(texts.get("caderno.dica"), inkSoft)).right().padTop(8);

        Table border = new Table();
        border.setBackground(edge);
        border.pad(3);
        border.add(page).grow();
        notebookRoot.add(border).size(Math.min(stage.getWidth() - 60, 720), Math.min(stage.getHeight() - 60, 500));
        stage.setScrollFocus(scroll);
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
        white.dispose();
    }
}
