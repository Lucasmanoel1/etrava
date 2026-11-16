package etrava.client.ui.telas;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import etrava.client.ui.Foco;
import etrava.client.ui.Ui;
import etrava.sim.api.DialogueView;
import etrava.sim.api.WorldView;
import etrava.sim.content.FactDef;
import etrava.sim.world.KnowledgeEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A conversa (PRD 10.2), no tema do V7: caixa de pergaminho no pé da tela com o retrato emoldurado ao lado,
 * o nome de quem fala, as últimas falas em tinta suave, a fala atual e as opções numeradas. As teclas 1 a 9
 * seguem no {@code EtravaGame}; as setas e o Enter andam e escolhem pelo {@link Foco}. Muitas opções rolam
 * (VT-08). A caixa fica abaixo do centro: o cenário continua à vista.
 */
public final class TelaConversa {
    /** Quantos fatos marcados viram opção de "perguntar sobre" numa conversa. */
    public static final int MAX_PERGUNTAS = 3;

    private final Contexto c;
    private final Table raiz = new Table();
    private final List<String> perguntas = new ArrayList<>();
    private IntConsumer aoEscolher = i -> { };
    private Object mostrada;
    private int linhas = -1;

    public TelaConversa(Contexto c) {
        this.c = c;
        raiz.setFillParent(true);
        raiz.bottom().pad(0, 0, 8, 0);
        Ui.esconder(raiz);
    }

    public Table raiz() { return raiz; }

    public void aoEscolher(IntConsumer handler) { this.aoEscolher = handler; }

    /** Fatos oferecidos como "perguntar sobre" na conversa aberta, na ordem das opções que vêm depois das da conversa. */
    public List<String> perguntas() { return perguntas; }

    /** A tela precisa ser remontada (a janela mudou de tamanho). */
    public void invalidar() {
        mostrada = null;
        linhas = -1;
    }

    /** Monta ou esconde conforme haja conversa; só remonta quando a conversa muda. */
    public void atualizar(WorldView view, DialogueView d) {
        if (d == null) {
            Ui.esconder(raiz);
            mostrada = null;
            linhas = -1;
        } else if (d != mostrada || d.history().size() != linhas) {
            montar(view, d);
            mostrada = d;
            linhas = d.history().size();
        }
    }

    private void montar(WorldView view, DialogueView d) {
        raiz.clearChildren();
        raiz.setVisible(true);
        var tema = c.tema;
        Foco foco = c.ui.foco();

        // texto: as duas falas anteriores apagadas, a atual, as opções
        Table texto = new Table();
        texto.top().left();
        texto.defaults().left().growX();
        List<DialogueView.Line> historia = d.history();
        for (int i = Math.max(0, historia.size() - 3); i < historia.size() - 1; i++) {
            Label antiga = new Label(historia.get(i).speaker() + ": " + historia.get(i).text(), tema.tintaSuave);
            antiga.setWrap(true);
            texto.add(antiga).padBottom(3).row();
        }
        Label fala = new Label(d.text(), tema.tinta);
        fala.setWrap(true);
        texto.add(fala).padTop(2).padBottom(8).row();

        perguntas.clear();
        List<String> rotulos = new ArrayList<>();
        for (DialogueView.Choice o : d.choices()) rotulos.add((o.tag() != null ? "[" + o.tag() + "] " : "") + o.text());
        if (d.choices().isEmpty()) {
            rotulos.add(c.texts.get("dialogo.continuar"));
        } else {
            for (String factId : view.markedFacts()) {
                FactDef fato = c.content.facts().get(factId);
                if (fato == null || perguntas.size() >= MAX_PERGUNTAS) continue;
                String versao = view.knowledge().stream().filter(k -> k.fact().equals(factId)).findFirst()
                        .map(KnowledgeEntry::version).orElse(fato.defaultVersion());
                perguntas.add(factId);
                rotulos.add(c.texts.get("dialogo.perguntar", fato.text(versao)));
            }
        }
        float larguraNumero = new GlyphLayout(tema.fonte, rotulos.size() >= 10 ? "00." : "0.").width;
        for (int i = 0; i < rotulos.size(); i++) texto.add(opcao(i, rotulos.get(i), larguraNumero, foco)).row();

        float altura = c.stage.getHeight();
        float maxTexto = Math.max(4 * tema.fonte.getLineHeight(), altura * 0.22f);
        ScrollPane rolagem = c.ui.rolagem(texto);

        Table caixa = c.ui.fundo("ui.pergaminho");
        Table cabeca = new Table();
        cabeca.add(new Label(d.speakerName(), tema.titulo)).left();
        cabeca.add().growX();
        caixa.add(cabeca).growX().row();
        caixa.add(c.ui.divisoria()).growX().padTop(1).padBottom(3).row();
        caixa.add(rolagem).growX().maxHeight(maxTexto).minHeight(Math.min(maxTexto, 3 * tema.fonte.getLineHeight())).row();
        Label dica = new Label(c.texts.get("dialogo.dica"), tema.tintaSuave);
        dica.setWrap(true);
        dica.setAlignment(Align.right);
        caixa.add(dica).growX().padTop(4).row();

        float largura = c.ui.largura(c.stage, 0.96f, 52);
        Table corpo = new Table();
        corpo.add(retrato(d, altura)).bottom().padRight(6);
        corpo.add(caixa).growX().bottom();
        raiz.add(corpo).width(largura);
        corpo.addActor(foco);
    }

    private Table opcao(int indice, String rotulo, float larguraNumero, Foco foco) {
        var tema = c.tema;
        Table linha = new Table();
        linha.left();
        linha.pad(2, 4, 2, 4);
        Label numero = new Label((indice + 1) + ".", tema.ferrugem);
        numero.setAlignment(Align.right);
        linha.add(numero).top().padRight(6).minWidth(larguraNumero);
        Label texto = new Label(rotulo, tema.tinta);
        texto.setWrap(true);
        linha.add(texto).growX().top();
        Runnable escolher = () -> aoEscolher.accept(indice);
        c.clicavel(linha, escolher);
        foco.adicionar(linha, escolher);
        return linha;
    }

    /** O retrato na moldura do tema, ampliado por inteiro (a arte é pixel art) enquanto couber no pé da tela. */
    private Table retrato(DialogueView d, float alturaTela) {
        Texture tex = c.assets.portrait(d.portrait());
        float maxAltura = alturaTela * 0.28f;
        int escala = Math.max(1, (int) Math.floor(maxAltura / tex.getHeight()));
        Image imagem = new Image(new TextureRegionDrawable(new TextureRegion(tex)));
        float w = tex.getWidth() * escala, h = tex.getHeight() * escala;
        if (h > maxAltura && escala == 1) { // retrato maior que o espaço: encolhe sem deformar
            w *= maxAltura / h;
            h = maxAltura;
        }
        Table moldura = c.ui.moldura(imagem, Ui.Moldura.RETRATO);
        moldura.getCells().first().size(w, h);
        return moldura;
    }
}
