package etrava.client.ui.telas;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import etrava.client.assets.Assets;
import etrava.client.render.Vestimenta;
import etrava.client.ui.Foco;
import etrava.client.ui.Ui;
import etrava.sim.api.Command;
import etrava.sim.api.WorldView;
import etrava.sim.content.ItemDef;
import etrava.sim.economy.Economy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A mochila (PRD 10.2), no tema do V7: grade de encaixes com os ícones, o boneco do jogador com o que ele
 * veste (as mesmas camadas do mundo: corpo pela roupa, cabeça, mãos), os encaixes de equipamento em volta, o
 * detalhe do item escolhido com os botões usar e vestir (ou tirar), a carga com barra e o dinheiro. Clicar numa
 * coisa escolhe; clicar de novo, ou Enter, usa ou veste. As setas andam pela grade.
 */
public final class TelaMochila {
    /** Os encaixes em volta do boneco: à esquerda de cima para baixo, depois à direita. */
    private static final String[] ESQUERDA = {"head", "body", "accessory"}, DIREITA = {"mainHand", "offHand"};
    /** Direção do quadro do boneco: de frente para quem olha (a tela do jogo é isométrica, o sul é a direção 1 de 8). */
    private static final int DIRECAO_FRENTE = 1;

    private final Contexto c;
    private final Assets assets;
    private final Vestimenta vestimenta;
    private final Table raiz = new Table();
    private Runnable aoFechar = () -> { };
    /** Escolha atual: {@code i:ITEM} (na mochila) ou {@code s:ENCAIXE} (no corpo). */
    private String escolha;
    private Foco foco;
    private ScrollPane lista;
    private Table gradeTabela, zonaBoneco;
    private int gradeColunas, gradeCelulas;

    // o que o detalhe mostra, atualizado no lugar quando a escolha muda
    private final Map<String, Image> marcas = new HashMap<>();
    private Table detalheIcone;
    private Label detalheNome, detalheInfo, detalheTexto;
    private TextButton botaoUsar, botaoVestir;
    private WorldView ultimaView;
    private Economy ultimaEco;

    public TelaMochila(Contexto c, Assets assets) {
        this.c = c;
        this.assets = assets;
        this.vestimenta = Vestimenta.carregar(assets.root());
        raiz.setFillParent(true);
        raiz.center();
        Ui.esconder(raiz);
    }

    public Table raiz() { return raiz; }

    /** O que Esc faz (fechar a tela). */
    public void aoFechar(Runnable acao) { this.aoFechar = acao; }

    public void esconder() { Ui.esconder(raiz); }

    public void mostrar(boolean visivel) { Ui.mostrar(raiz, visivel); }

    // ---------------------------------------------------------------- montagem

    /** Refaz a tela (a assinatura mudou: item usado, vestido, dinheiro…). */
    public void montar(WorldView view, Economy eco) {
        montar(view, eco, 0);
    }

    /** {@code ampForcada} &gt; 0: a ampliação do boneco já medida (a segunda passada, depois de ver o tamanho do quadro). */
    private void montar(WorldView view, Economy eco, int ampForcada) {
        var tema = c.tema;
        ultimaView = view;
        ultimaEco = eco;
        int focado = foco == null ? -1 : foco.indice();
        float rolado = lista == null ? 0 : lista.getScrollY();
        raiz.clearChildren();
        marcas.clear();
        foco = c.ui.foco().aoFechar(aoFechar);

        List<String> itens = itens(view);
        if (escolha == null || !existe(view, escolha)) escolha = itens.isEmpty() ? null : "i:" + itens.get(0);

        Table painel = c.ui.painel(c.texts.get("mochila.titulo"));
        float largura = c.ui.largura(c.stage, 0.94f, 40), altura = c.ui.altura(c.stage, 0.94f, 28);
        int[] pad = tema.pad("ui.painel_madeira");
        float interno = largura - pad[0] - pad[1];
        float vao = 10;

        // tamanho do boneco: inteiro, e a coluna dele não passa de pouco mais de um terço da largura
        var corpo = assets.sheet(view.player() == null ? null : view.player().sprite());
        int ampliacao = ampForcada > 0 ? ampForcada : Math.max(1, Math.min((int) (c.stage.getHeight() * 0.40f / corpo.frameH()),
                (int) ((interno * 0.36f - 110) / corpo.frameW())));
        float larguraEsq = 2 * 40 + 2 * 8 + corpo.frameW() * ampliacao + 10 + 10;

        // direita: a grade e, embaixo, o detalhe
        float larguraDir = interno - larguraEsq - vao;
        Table direita = new Table();
        direita.top();
        int colunas = Math.max(3, (int) ((larguraDir - 10 - 9 - 4) / 42));
        direita.add(grade(itens, view, colunas)).grow().row();
        direita.add(detalhe()).growX().padTop(6);

        // esquerda: o boneco, a carga, o dinheiro
        Table esquerda = new Table();
        esquerda.add(boneco(view, corpo, ampliacao)).grow().row();
        esquerda.add(carga(view, eco, larguraEsq)).growX().padTop(6).row();
        Table dinheiro = new Table();
        dinheiro.left();
        dinheiro.add(c.ui.icone("ui.bolsa")).padRight(4);
        Label din = new Label(c.texts.get("mochila.dinheiro", c.moedas(view.money())), tema.tinta);
        din.setWrap(true);
        dinheiro.add(din).growX();
        esquerda.add(dinheiro).growX().padTop(4).row();
        Label fome = new Label(c.fome(view.hunger()), view.hunger() >= Economy.HUNGRY ? tema.ferrugem : tema.tintaSuave);
        fome.setWrap(true);
        esquerda.add(fome).growX().padTop(2).row();

        Table corpoTela = new Table();
        corpoTela.add(esquerda).width(larguraEsq).growY().padRight(vao);
        corpoTela.add(direita).grow();
        painel.add(corpoTela).grow().row();
        Label dica = new Label(c.texts.get("mochila.dica_nova"), tema.tintaSuave);
        dica.setWrap(true);
        dica.setAlignment(Align.right);
        painel.add(dica).growX().padTop(4);

        painel.addActor(foco);
        raiz.add(painel).width(largura).height(altura);
        marcar(); // mostra a escolha no detalhe e na grade
        foco.restaurar(focado);
        raiz.validate();
        encherGrade();
        Contexto.rolarPara(raiz, lista, rolado);
        if (ampForcada == 0) {
            // o boneco usa a maior ampliação inteira que cabe no quadro dele (a altura só se sabe depois do layout)
            float livre = zonaBoneco.getHeight() - zonaBoneco.getPadTop() - zonaBoneco.getPadBottom() - 8;
            int maxAlt = (int) (livre / corpo.frameH());
            int maxLarg = (int) ((interno * 0.42f - 110) / corpo.frameW());
            int melhor = Math.min(maxAlt, maxLarg);
            if (melhor > ampliacao) montar(view, eco, melhor);
        }
    }

    /** Completa a grade com encaixes vazios até a lista ficar cheia (a altura só se sabe depois do layout). */
    private void encherGrade() {
        int linhas = (int) Math.ceil(lista.getScrollHeight() / 42f), tem = gradeCelulas / gradeColunas;
        if (linhas <= tem) return;
        for (int i = 0; i < (linhas - tem) * gradeColunas; i++) {
            gradeTabela.add(celulaVazia()).size(40).pad(1);
            if ((gradeCelulas + i + 1) % gradeColunas == 0) gradeTabela.row();
        }
        gradeCelulas += (linhas - tem) * gradeColunas;
        raiz.validate();
    }

    /** Os itens da mochila na ordem das categorias do jogo. */
    private List<String> itens(WorldView view) {
        List<String> ids = new ArrayList<>();
        for (String categoria : ItemDef.CATEGORIES)
            for (String id : view.inventory().keySet()) {
                ItemDef item = c.content.items().get(id);
                if (item != null && categoria.equals(item.category())) ids.add(id);
            }
        return ids;
    }

    private boolean existe(WorldView view, String chave) {
        String id = chave.substring(2);
        return chave.startsWith("i:") ? view.inventory().getOrDefault(id, 0) > 0 : view.equipment().get(id) != null;
    }

    // ---------------------------------------------------------------- grade

    private Table grade(List<String> itens, WorldView view, int colunas) {
        var tema = c.tema;
        Table grade = new Table();
        gradeTabela = grade;
        gradeColunas = colunas;
        grade.top().left();
        int linhasVazias = 5;
        int total = Math.max(itens.size() + (colunas - itens.size() % colunas) % colunas, colunas * linhasVazias);
        for (int i = 0; i < total; i++) {
            if (i < itens.size()) {
                String id = itens.get(i);
                int qtd = view.inventory().getOrDefault(id, 0);
                Stack cel = celula("i:" + id, id, qtd, false);
                grade.add(cel).size(40).pad(1);
                foco.adicionar(cel, () -> ativar("i:" + id));
                ItemDef item = c.content.items().get(id);
                c.ui.dica(cel, (item == null ? id : item.displayName()) + (qtd > 1 ? " x" + qtd : ""));
            } else {
                grade.add(celulaVazia()).size(40).pad(1);
            }
            if ((i + 1) % colunas == 0) grade.row();
        }
        gradeCelulas = total;
        foco.grade(colunas, itens.size());
        foco.aoFocar(i -> {
            if (i < itens.size()) escolher("i:" + itens.get(i));
        });
        lista = c.ui.rolagem(grade);
        Table fundo = c.ui.fundo("ui.pergaminho_escuro");
        fundo.add(lista).grow();
        return fundo;
    }

    /**
     * Uma célula: o encaixe do tema com o ícone, a quantidade pequena no canto e, por cima, a moldura que
     * marca a escolha. A marca fica escondida até o item ser escolhido.
     */
    private Stack celula(String chave, String itemId, int qtd, boolean encaixeDeCorpo) {
        var tema = c.tema;
        Table base = c.ui.fundo("ui.encaixe");
        if (itemId != null) base.add(c.icone(itemId));
        else if (encaixeDeCorpo) base.add(new Image(tema.peca(silhueta(chave.substring(2))))).size(32); // encaixe vazio: a silhueta do que ele guarda
        else base.add().size(32);
        Stack pilha = new Stack();
        pilha.add(base);
        if (qtd > 1) {
            Table canto = new Table();
            canto.setTouchable(Touchable.disabled);
            canto.bottom().right();
            Label q = new Label(Integer.toString(qtd), tema.tinta);
            q.setFontScale(tema.fonte.getData().scaleX * 0.8f);
            canto.add(q).pad(0, 0, 3, 5);
            pilha.add(canto);
        }
        if (encaixeDeCorpo) {
            Image moldura = new Image(tema.peca("ui.moldura_icone"));
            moldura.setTouchable(Touchable.disabled);
            pilha.add(moldura);
        }
        Image marca = new Image(tema.peca("ui.moldura_icone_sel"));
        marca.setTouchable(Touchable.disabled);
        marca.setVisible(false);
        pilha.add(marca);
        marcas.put(chave, marca);
        pilha.setTouchable(Touchable.enabled);
        pilha.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (chave.equals(escolha)) ativar(chave);
                else escolher(chave);
            }
        });
        return pilha;
    }

    /** A silhueta discreta do tema para o encaixe de equipamento vazio. */
    private static String silhueta(String slot) {
        return switch (slot) {
            case "head" -> "ui.encaixe_cabeca";
            case "body" -> "ui.encaixe_corpo";
            case "mainHand" -> "ui.encaixe_mao";
            case "offHand" -> "ui.encaixe_mao2";
            default -> "ui.encaixe_acessorio";
        };
    }

    private Table celulaVazia() {
        Table vazia = c.ui.fundo("ui.encaixe");
        vazia.add().size(32);
        return vazia;
    }

    // ---------------------------------------------------------------- boneco e encaixes

    private Table boneco(WorldView view, Assets.Sheet corpo, int ampliacao) {
        Map<String, String> equip = view.equipment();
        String base = view.player() == null ? null : view.player().sprite();
        Assets.Sheet folha = assets.sheet(vestimenta.corpo(equip, base, assets::temEntrada));
        List<Assets.Sheet> camadas = new ArrayList<>();
        for (String id : vestimenta.camadas(equip, assets::temEntrada)) camadas.add(assets.sheet(id));

        Table zona = c.ui.fundo("ui.pergaminho_escuro");
        zonaBoneco = zona;
        Table esq = new Table(), dir = new Table();
        for (String slot : ESQUERDA) esq.add(encaixe(slot, view)).expand().top().padBottom(2).row();
        dir.add().expandY().row();
        for (String slot : DIREITA) dir.add(encaixe(slot, view)).expand().padBottom(2).row();
        dir.add().expandY().row();
        zona.add(esq).growY().padRight(8);
        Boneco figura = new Boneco(folha, camadas, ampliacao);
        zona.add(figura).size(figura.getWidth(), figura.getHeight()).expandY();
        zona.add(dir).growY().padLeft(8);
        return zona;
    }

    private Stack encaixe(String slot, WorldView view) {
        String itemId = view.equipment().get(slot);
        String chave = "s:" + slot;
        Stack s = celula(chave, itemId, 1, true);
        ItemDef item = itemId == null ? null : c.content.items().get(itemId);
        c.ui.dica(s, c.texts.get("encaixe." + slot) + ": " + (item == null ? c.texts.get("encaixe.vazio") : item.displayName()));
        if (itemId == null) { // encaixe vazio: só a dica, nada a escolher
            s.clearListeners();
            c.ui.dica(s, c.texts.get("encaixe." + slot) + ": " + c.texts.get("encaixe.vazio"));
            marcas.remove(chave);
        } else {
            foco.adicionar(s, () -> ativar(chave));
        }
        return s;
    }

    /** O desenho do jogador parado, de frente, com as camadas de roupa, cabeça e mãos, ampliado por inteiro. */
    private static final class Boneco extends Actor {
        private final Assets.Sheet corpo;
        private final List<Assets.Sheet> camadas;
        private final int n;

        Boneco(Assets.Sheet corpo, List<Assets.Sheet> camadas, int n) {
            this.corpo = corpo;
            this.camadas = camadas;
            this.n = n;
            setTouchable(Touchable.disabled);
            setSize(corpo.frameW() * n, corpo.frameH() * n);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            batch.setColor(1, 1, 1, parentAlpha * getColor().a);
            // o pé fica onde a âncora do corpo cai; as camadas se encaixam pela própria âncora
            float peX = getX() + corpo.anchorX() * n, peY = getY() + (corpo.frameH() - corpo.anchorY()) * n;
            desenhar(batch, corpo, peX, peY);
            for (Assets.Sheet camada : camadas) {
                if (camada.directions() == corpo.directions() && camada.has("parado")) desenhar(batch, camada, peX, peY);
            }
        }

        private void desenhar(Batch batch, Assets.Sheet folha, float peX, float peY) {
            Texture t = folha.texture();
            int dir = Math.floorMod(DIRECAO_FRENTE, Math.max(1, folha.directions()));
            int quadro = folha.anim("parado").start();
            float x = Math.round(peX - folha.anchorX() * n), y = Math.round(peY - (folha.frameH() - folha.anchorY()) * n);
            batch.draw(t, x, y, folha.frameW() * n, folha.frameH() * n,
                    quadro * folha.frameW(), dir * folha.frameH(), folha.frameW(), folha.frameH(), false, false);
        }
    }

    // ---------------------------------------------------------------- carga

    private Table carga(WorldView view, Economy eco, float largura) {
        var tema = c.tema;
        Table t = new Table();
        if (eco == null) return t;
        boolean pesado = eco.overloaded();
        Label texto = new Label(c.texts.get("mochila.peso", Contexto.kg(eco.weight()), Contexto.kg(eco.capacity())),
                pesado ? tema.ferrugem : tema.tinta);
        texto.setWrap(true);
        t.add(texto).growX().row();
        Table barra = c.ui.fundo("ui.barra_fundo");
        int[] p = tema.pad("ui.barra_fundo");
        float interno = largura - p[0] - p[1] - 4;
        double frac = eco.capacity() <= 0 ? 1 : Math.min(1.0, eco.weight() / eco.capacity());
        if (frac > 0) barra.add(new Image(tema.peca(pesado ? "ui.barra_vida" : "ui.barra_folego")))
                .width(Math.max(4, (float) (frac * interno))).height(4).left().expandX();
        else barra.add().height(4).growX();
        t.add(barra).growX().padTop(2).row();
        if (pesado) {
            Label aviso = new Label(c.texts.get("mochila.pesado"), tema.ferrugem);
            aviso.setWrap(true);
            t.add(aviso).growX().padTop(2).row();
        }
        return t;
    }

    // ---------------------------------------------------------------- detalhe

    private Table detalhe() {
        var tema = c.tema;
        Table d = c.ui.fundo("ui.pergaminho_escuro");
        detalheIcone = c.ui.moldura(new Image(), Ui.Moldura.ICONE);
        detalheNome = new Label("", tema.ferrugem);
        detalheNome.setWrap(true);
        detalheInfo = new Label("", tema.tintaSuave);
        detalheInfo.setWrap(true);
        Table nomes = new Table();
        nomes.left();
        nomes.defaults().left().growX();
        nomes.add(detalheNome).row();
        nomes.add(detalheInfo).row();
        detalheIcone.getCells().first().size(32);
        d.add(detalheIcone).top().padRight(6);
        d.add(nomes).growX().top().row();
        detalheTexto = new Label("", tema.tinta);
        detalheTexto.setWrap(true);
        Table corpoTexto = new Table();
        corpoTexto.top().left();
        corpoTexto.add(detalheTexto).growX();
        ScrollPane texto = c.ui.rolagem(corpoTexto);
        d.add(texto).colspan(2).growX().height(3 * tema.fonte.getLineHeight()).padTop(4).row();
        botaoUsar = c.botao("", this::usar);
        botaoVestir = c.botao("", this::vestir);
        Table botoes = new Table();
        botoes.add(botaoUsar).padRight(6);
        botoes.add(botaoVestir);
        d.add(botoes).colspan(2).right().padTop(4);
        foco.adicionar(botaoUsar, () -> usar());
        foco.adicionar(botaoVestir, () -> vestir());
        return d;
    }

    private void escolher(String chave) {
        if (chave.equals(escolha) && marcas.get(chave) != null && marcas.get(chave).isVisible()) return;
        escolha = chave;
        marcar();
    }

    /** Põe a marca na célula escolhida e atualiza o detalhe, sem refazer a tela. */
    private void marcar() {
        marcas.forEach((k, m) -> m.setVisible(k.equals(escolha)));
        var tema = c.tema;
        ItemDef item = escolha == null ? null : c.content.items().get(escolha.substring(2));
        boolean naMochila = escolha != null && escolha.startsWith("i:");
        if (item == null) {
            detalheIcone.setVisible(false);
            detalheNome.setText("");
            detalheInfo.setText("");
            detalheTexto.setText(c.texts.get("mochila.sem_selecao"));
            desabilitar(botaoUsar, c.texts.get("mochila.usar"));
            desabilitar(botaoVestir, c.texts.get("mochila.vestir"));
            return;
        }
        detalheIcone.setVisible(true);
        Image icone = (Image) detalheIcone.getCells().first().getActor();
        icone.setDrawable(c.icone(item.id()).getDrawable());
        int qtd = naMochila ? ultimaView.inventory().getOrDefault(item.id(), 0) : 1;
        detalheNome.setText(item.displayName());
        String peso = Contexto.kg(item.weight() * qtd) + " kg";
        detalheInfo.setText(naMochila ? (qtd > 1 ? c.texts.get("mochila.quantidade", qtd) + " · " : "") + peso
                : c.texts.get("mochila.no_corpo", c.texts.get("encaixe." + escolha.substring(2)).toLowerCase()) + " " + peso);
        detalheTexto.setText(item.description() == null ? "" : item.description());
        if (naMochila) {
            String verbo = item.usable() ? c.texts.get(item.food() > 0 ? "mochila.comer" : item.document() ? "mochila.ler" : "mochila.usar")
                    : c.texts.get("mochila.usar");
            botaoUsar.setText(verbo);
            botaoUsar.setDisabled(!item.usable());
            botaoVestir.setText(c.texts.get("mochila.vestir"));
            botaoVestir.setDisabled(item.slot() == null);
        } else {
            desabilitar(botaoUsar, c.texts.get("mochila.usar"));
            botaoVestir.setText(c.texts.get("mochila.tirar"));
            botaoVestir.setDisabled(false);
        }
        detalheTexto.invalidateHierarchy();
    }

    private static void desabilitar(TextButton b, String texto) {
        b.setText(texto);
        b.setDisabled(true);
    }

    /** O que clicar de novo (ou Enter) faz: usar, se dá; senão vestir ou tirar. */
    private void ativar(String chave) {
        if (!chave.equals(escolha)) escolher(chave);
        if (chave.startsWith("s:")) {
            vestir();
            return;
        }
        ItemDef item = c.content.items().get(chave.substring(2));
        if (item != null && item.usable()) usar();
        else vestir();
    }

    private void usar() {
        if (escolha == null || !escolha.startsWith("i:") || botaoUsar.isDisabled()) return;
        c.comando.accept(new Command.UseItem(escolha.substring(2)));
    }

    private void vestir() {
        if (escolha == null || botaoVestir.isDisabled()) return;
        if (escolha.startsWith("s:")) c.comando.accept(new Command.Unequip(escolha.substring(2)));
        else c.comando.accept(new Command.Equip(escolha.substring(2)));
    }
}
