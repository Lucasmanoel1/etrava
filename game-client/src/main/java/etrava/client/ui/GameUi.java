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
    private boolean notebookSeenTab;
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
        boolean was = inventoryOpen || characterOpen;
        inventoryOpen = characterOpen = false;
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

    /** Tela do fim do capítulo (RF-163): os trechos do epílogo, um por parágrafo. O jogo continua depois. */
    public void showEpilogue(List<String> lines) {
        showPage(texts.get("epilogo.titulo"), lines, texts.get("epilogo.dica"), null, 760, 560);
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
        epilogueRoot.clearChildren();
        Table page = new Table();
        page.setBackground(paper);
        page.pad(24).top();
        page.add(new Label(title, rust)).left().padBottom(14).row();
        Table body = new Table();
        body.top().left();
        body.defaults().left().growX();
        for (String line : lines) {
            Label paragraph = new Label(line, ink);
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
                String verb = item.usable() ? texts.get(item.food() > 0 ? "mochila.comer" : "mochila.usar")
                        : item.slot() != null ? texts.get("mochila.vestir") : "";
                String line = item.displayName() + (e.getValue() > 1 ? " x" + e.getValue() : "") + "  ·  "
                        + kg(item.weight() * e.getValue()) + " kg" + (verb.isEmpty() ? "" : "   [" + verb + "]");
                list.add(clickable(line, action)).row();
                Label desc = new Label(item.description() == null ? "" : item.description(), inkSoft);
                desc.setWrap(true);
                list.add(desc).padLeft(12).padBottom(3).row();
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
            list.add(clickable(line, cmd)).padBottom(2).row();
        }
        if (offers.isEmpty()) list.add(new Label(texts.get("comercio.nada"), inkSoft)).row();
        ScrollPane scroll = new ScrollPane(list, new ScrollPane.ScrollPaneStyle());
        scroll.setFadeScrollBars(false);
        col.add(scroll).grow();
        return col;
    }

    // ---------------------------------------------------------------- caderno

    private void buildNotebook(WorldView view) {
        notebookRoot.clearChildren();
        Table page = new Table();
        page.setBackground(paper);
        page.pad(18).top();
        page.add(new Label(texts.get("caderno.titulo"), rust)).left().padBottom(8).row();

        Table tabs = new Table();
        tabs.add(tab(texts.get("caderno.aba.ouvi"), !notebookSeenTab, false)).padRight(8);
        tabs.add(tab(texts.get("caderno.aba.vi"), notebookSeenTab, true));
        page.add(tabs).left().padBottom(10).row();

        Table list = new Table();
        list.top().left();
        list.defaults().left().growX();
        int shown = 0;
        for (KnowledgeEntry k : view.knowledge()) {
            boolean seen = !k.certainty().equals("rumor") && !k.certainty().equals("claim");
            if (seen != notebookSeenTab) continue;
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

    private Label tab(String text, boolean active, boolean seenTab) {
        Label.LabelStyle style = new Label.LabelStyle(font, active ? INK : INK_SOFT);
        style.background = active ? paperDark : null;
        Label label = new Label(" " + text + " ", style);
        label.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                notebookSeenTab = seenTab;
                notebookEntries = -1;
            }
        });
        return label;
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
