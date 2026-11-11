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
import etrava.sim.api.DialogueView;
import etrava.sim.api.WorldView;
import etrava.sim.content.ContentDb;
import etrava.sim.content.FactDef;
import etrava.sim.content.NpcDef;
import etrava.sim.world.KnowledgeEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Telas do jogo sobre o cenário: diálogo e caderno. Aparência de papel e tinta
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
        stage.addActor(dialogueRoot);
        stage.addActor(notebookRoot);
    }

    public Stage stage() { return stage; }
    public void onChoice(IntConsumer handler) { this.onChoice = handler; }
    public void onToggleMark(Consumer<String> handler) { this.onToggleMark = handler; }
    public boolean notebookOpen() { return notebookOpen; }
    public List<String> askFacts() { return askFacts; }

    public void toggleNotebook() {
        notebookOpen = !notebookOpen;
        notebookEntries = -1;
    }

    public void closeNotebook() { notebookOpen = false; }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        dialogueLines = -1;
        notebookEntries = -1;
    }

    /** Atualiza as telas conforme o estado e desenha. */
    public void render(WorldView view, float dt) {
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
        boolean showNotebook = notebookOpen && d == null;
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
