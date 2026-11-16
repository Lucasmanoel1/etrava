package etrava.client.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import etrava.client.assets.Assets;
import etrava.client.ui.telas.Contexto;
import etrava.client.ui.telas.TelaCaderno;
import etrava.client.ui.telas.TelaComercio;
import etrava.client.ui.telas.TelaConversa;
import etrava.client.ui.telas.TelaMapa;
import etrava.client.ui.telas.TelaMochila;
import etrava.client.ui.telas.TelaPagina;
import etrava.sim.api.Command;
import etrava.sim.api.DialogueView;
import etrava.sim.api.TradeView;
import etrava.sim.api.WorldView;
import etrava.sim.content.ContentDb;
import etrava.sim.content.SkillsDef;
import etrava.sim.economy.Economy;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Telas do jogo sobre o cenário: diálogo, caderno, mochila, ficha do personagem, comércio, contêiner, mapa e
 * as folhas cheias (documento, epílogo, derrota, morte, rendido). Tudo no tema do V7 (papel, tinta, madeira e
 * couro), sem marcadores de objetivo; cada tela grande é uma classe de {@code ui/telas/} e esta classe só as
 * junta e delega. O caderno mostra o que o personagem ouviu e viu, com a fonte — nunca o que fazer (RF-121).
 */
public final class GameUi implements Disposable {
    /** Quantos fatos marcados viram opção de "perguntar sobre" numa conversa. */
    public static final int MAX_ASK = TelaConversa.MAX_PERGUNTAS;

    private final Stage stage;
    private final ContentDb content;
    private final Texts texts;
    /** O tema novo (V7) e os utilitários de tela. */
    private final Tema tema;
    private final Ui ui;
    private final Contexto contexto;

    private final Table characterRoot = new Table();
    /** Uma classe por tela, em {@code ui/telas/}. */
    private final TelaConversa conversa;
    private final TelaComercio comercio;
    private final TelaMochila mochila;
    private final TelaCaderno caderno;
    private final TelaMapa mapa;
    private final TelaPagina pagina;
    private boolean mapOpen;
    private String mapShown = "";
    private boolean inventoryOpen, characterOpen;
    /** O que estava na tela da última vez, para só remontar quando algo mudar. */
    private String inventoryShown = "", characterShown = "";
    private Consumer<Command> onCommand = c -> { };
    private boolean epilogueOpen, deathOpen;
    private Consumer<String> onLoad = name -> { };
    private boolean notebookOpen;

    public GameUi(BitmapFont font, Assets assets, ContentDb content, Texts texts) {
        this(font, assets, content, texts, new UiStage(new ScreenViewport()));
    }

    /** O teste passa um Stage sem SpriteBatch (não há GL de verdade sem janela). */
    GameUi(BitmapFont font, Assets assets, ContentDb content, Texts texts, Stage stage) {
        this.stage = stage;
        this.content = content;
        this.texts = texts;
        tema = new Tema(assets.root(), assets.manifest(), font);
        ui = new Ui(tema);
        Contexto ctx = new Contexto(stage, tema, ui, assets, content, texts);
        conversa = new TelaConversa(ctx);
        comercio = new TelaComercio(ctx);
        mochila = new TelaMochila(ctx, assets);
        mochila.aoFechar(() -> inventoryOpen = false);
        caderno = new TelaCaderno(ctx);
        caderno.aoFechar(() -> notebookOpen = false);
        mapa = new TelaMapa(ctx);
        pagina = new TelaPagina(ctx);
        contexto = ctx;

        characterRoot.setFillParent(true);
        characterRoot.center();
        Ui.esconder(characterRoot);
        stage.addActor(conversa.raiz());
        stage.addActor(caderno.raiz());
        stage.addActor(mochila.raiz());
        stage.addActor(characterRoot);
        stage.addActor(comercio.raiz());
        stage.addActor(mapa.raiz());
        stage.addActor(pagina.raiz());
    }

    public Stage stage() { return stage; }
    public void onChoice(IntConsumer handler) { conversa.aoEscolher(handler); }
    public void onToggleMark(Consumer<String> handler) { caderno.aoMarcar(handler); }
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
        if (notebookOpen) caderno.abrir();
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
    public boolean pageOpen() { return pagina.aberta(); }

    /** Quem trata o pedido de carregar um save da tela de morte (recebe o nome do save). */
    public void onLoad(Consumer<String> handler) { this.onLoad = handler; }

    public void closeEpilogue() {
        if (deathOpen) return;
        epilogueOpen = false;
        pagina.esconder();
    }

    /** Fecha a tela de morte depois que um jogo foi carregado. */
    public void closeDeath() {
        deathOpen = false;
        closeEpilogue();
    }

    /** Um documento aberto para ler (RF-124): carta, registro, bilhete. */
    public void showDocument(String title, String text) {
        abrirPagina(title, List.of(text), texts.get("documento.dica"), List.of(fechar("pagina.fechar")), null, List.of(), true);
    }

    /** Tela do fim do capítulo (RF-163): os trechos do epílogo, um por parágrafo. O jogo continua depois. */
    public void showEpilogue(List<String> lines) {
        // painéis ilustrados entre os parágrafos (PRD 10.2): a vila no começo, a árvore no meio
        abrirPagina(texts.get("epilogo.titulo"), lines, texts.get("epilogo.dica"), List.of(fechar("pagina.continuar")), null,
                List.of("ilustracao.epilogo_vila", "ilustracao.epilogo_arvore"), false);
    }

    /** Derrota não letal (RF-340): o que se perdeu e onde se acordou. */
    public void showWake(List<String> lines) {
        abrirPagina(texts.get("derrota.titulo"), lines, texts.get("derrota.dica"), List.of(fechar("pagina.continuar")), null,
                List.of(), false);
    }

    /**
     * Morte (RF-341): o que aconteceu com o mundo, e os saves que dá para carregar.
     *
     * @param saves nome do save → como ele aparece ("Salvamento rápido — dia 3, 14:20")
     */
    public void showDeath(List<String> lines, Map<String, String> saves) {
        List<TelaPagina.Opcao> opcoes = new java.util.ArrayList<>();
        for (Map.Entry<String, String> e : saves.entrySet())
            opcoes.add(new TelaPagina.Opcao(e.getValue(), () -> onLoad.accept(e.getKey())));
        abrirPagina(texts.get("morte.titulo"), lines, texts.get("morte.dica"), opcoes,
                saves.isEmpty() ? texts.get("morte.sem_save") : null, List.of(), false);
        deathOpen = true;
    }

    /** Um inimigo rendido (RF-224): poupar, tomar o que ele leva ou acabar com ele. */
    public void showResolve(String enemyId, String name, String line) {
        List<TelaPagina.Opcao> opcoes = new java.util.ArrayList<>();
        for (String action : List.of("poupar", "saquear", "executar"))
            opcoes.add(new TelaPagina.Opcao(texts.get("rendido." + action), () -> {
                onCommand.accept(new Command.Resolve(enemyId, action));
                closeEpilogue();
            }));
        List<String> lines = line == null ? List.of(texts.get("rendido.texto", name))
                : List.of(texts.get("rendido.texto", name), "\"" + line + "\"");
        abrirPagina(texts.get("rendido.titulo"), lines, texts.get("rendido.dica"), opcoes, null, List.of(), false);
    }

    private TelaPagina.Opcao fechar(String chave) {
        return new TelaPagina.Opcao(texts.get(chave), this::closeEpilogue);
    }

    private void abrirPagina(String titulo, List<String> linhas, String dica, List<TelaPagina.Opcao> opcoes, String semOpcoes,
            List<String> arte, boolean carta) {
        pagina.mostrar(titulo, linhas, dica, opcoes, semOpcoes, arte, carta);
        epilogueOpen = true;
    }

    public void resize(int width, int height) {
        ui.escala(Escala.aplicar(stage.getViewport(), width, height));
        conversa.invalidar();
        comercio.invalidar();
        caderno.invalidar();
        pagina.remontar();
        mapShown = "";
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
                mapa.montar(view);
                mapShown = sig;
            }
        }
        mapa.mostrar(showMap);
        DialogueView d = view.dialogue();
        conversa.atualizar(view, d);
        caderno.atualizar(view, notebookOpen && d == null && !epilogueOpen);
    }

    /** "1 moeda", "12 moedas". */
    public String coins(int n) {
        return contexto.moedas(n);
    }

    /**
     * Ficha do personagem (PRD 10.2): habilidades com valor e descrição. Sem nível, sem pontos (RF-181).
     * Painel de madeira, lista com a rolagem do tema, dica flutuante em cada habilidade, Esc fecha,
     * setas/Tab andam entre as habilidades.
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
        painel.add(new Label(contexto.fome(view.hunger()), view.hunger() >= Economy.HUNGRY ? tema.ferrugem : tema.tintaSuave))
                .left().padTop(6).row();
        painel.add(new Label(texts.get("ficha.dica"), tema.tintaSuave)).right().padTop(4);
        painel.addActor(foco);
        characterRoot.add(painel).width(ui.largura(stage, 0.92f, 30)).height(ui.altura(stage, 0.92f, 24));
    }

    /** Vai direto a uma aba (0 a 4); para o console de depuração e as capturas. */
    public void notebookTabTo(int tab) {
        caderno.irParaAba(tab);
    }

    /** Muda de aba com as setas (para quem joga pelo teclado). */
    public void notebookTab(int delta) {
        caderno.mudarAba(delta);
    }

    @Override
    public void dispose() {
        stage.dispose();
        tema.dispose();
    }
}
