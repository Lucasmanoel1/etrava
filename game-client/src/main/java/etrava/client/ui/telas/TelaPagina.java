package etrava.client.ui.telas;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
import etrava.client.ui.Foco;
import etrava.client.ui.Ui;
import java.util.List;

/**
 * A folha cheia sobre o jogo parado (V7): o documento para ler (folha estreita, como uma carta), o epílogo, a
 * derrota (acordar), a morte e a decisão sobre o rendido. Uma folha de pergaminho centralizada com título, texto
 * que rola, ilustrações emolduradas entre os parágrafos (epílogo), opções como botões do tema e a dica. As
 * opções entram no {@link Foco}: setas e Enter andam e escolhem.
 */
public final class TelaPagina {
    /** Uma opção da folha: o rótulo do botão e o que ele faz. */
    public record Opcao(String rotulo, Runnable acao) {}

    private final Contexto c;
    private final Table raiz = new Table();
    /** Remonta a folha aberta (a janela mudou de tamanho). */
    private Runnable refazer;

    public TelaPagina(Contexto c) {
        this.c = c;
        raiz.setFillParent(true);
        raiz.center();
        Ui.esconder(raiz);
    }

    public Table raiz() { return raiz; }

    public boolean aberta() { return raiz.isVisible(); }

    public void esconder() {
        refazer = null;
        Ui.esconder(raiz);
    }

    /** A janela mudou de tamanho: refaz a folha aberta nas medidas novas. */
    public void remontar() {
        if (refazer != null && raiz.isVisible()) refazer.run();
    }

    /**
     * @param linhas    um parágrafo por item
     * @param opcoes    botões da folha, de cima para baixo (pode ser vazio)
     * @param semOpcoes texto em tinta suave no lugar dos botões quando não há nenhum (pode ser null)
     * @param arte      ilustrações: a primeira antes do primeiro parágrafo, a segunda no meio (pode ser vazio)
     * @param carta     folha de documento, estreita; senão pergaminho largo
     */
    public void mostrar(String titulo, List<String> linhas, String dica, List<Opcao> opcoes, String semOpcoes,
            List<String> arte, boolean carta) {
        refazer = () -> montar(titulo, linhas, dica, opcoes, semOpcoes, arte, carta);
        refazer.run();
    }

    private void montar(String titulo, List<String> linhas, String dica, List<Opcao> opcoes, String semOpcoes,
            List<String> arte, boolean carta) {
        raiz.clearChildren();
        var tema = c.tema;
        Stage stage = c.stage;
        Foco foco = c.ui.foco();

        String peca = carta ? "ui.documento" : "ui.pergaminho";
        int[] p = tema.pad(peca);
        int respiro = 8;
        Table pagina = c.ui.fundo(peca);
        pagina.pad(p[2] + respiro, p[0] + respiro, p[3] + respiro, p[1] + respiro);
        float largura = carta ? c.ui.largura(stage, 0.9f, 24)
                : c.ui.largura(stage, 0.92f, arte.isEmpty() ? 36 : 44);
        float alturaMax = c.ui.altura(stage, 0.94f, 40);
        float interno = largura - p[0] - p[1] - 2 * respiro - 12; // 12: a barra de rolagem

        Label cabeca = new Label(titulo, tema.ferrugem);
        cabeca.setWrap(true);
        pagina.add(cabeca).growX().row();
        pagina.add(c.ui.divisoria()).growX().padTop(2).padBottom(6).row();

        Table corpo = new Table();
        corpo.top().left();
        corpo.defaults().left().growX();
        for (int i = 0; i < linhas.size(); i++) {
            int quadro = arte.isEmpty() ? -1 : i == 0 ? 0 : i == linhas.size() / 2 ? 1 : -1;
            if (quadro >= 0 && quadro < arte.size()) {
                Texture tex = c.assets.texture(arte.get(quadro));
                if (tex != null) corpo.add(ilustracao(tex, interno)).center().fill(false).padBottom(10).row();
            }
            Label paragrafo = new Label(linhas.get(i), tema.tinta);
            paragrafo.setWrap(true);
            corpo.add(paragrafo).padBottom(8).row();
        }
        ScrollPane rolagem = c.ui.rolagem(corpo);
        pagina.add(rolagem).grow().padTop(2).row();

        if (!opcoes.isEmpty()) {
            Table botoes = new Table();
            boolean unico = opcoes.size() == 1; // um botão só (continuar, fechar): no tamanho dele, à direita
            for (Opcao o : opcoes) {
                TextButton b = c.botao(o.rotulo(), o.acao());
                if (unico) {
                    botoes.add(b).right().expandX().padBottom(4).row();
                } else {
                    b.getLabel().setWrap(true);
                    b.getLabelCell().growX();
                    botoes.add(b).growX().padBottom(4).row();
                }
                foco.adicionar(b);
            }
            pagina.add(botoes).growX().padTop(8).row();
        } else if (semOpcoes != null) {
            Label sem = new Label(semOpcoes, tema.tintaSuave);
            sem.setWrap(true);
            pagina.add(sem).growX().padTop(8).row();
        }
        Label dicaLabel = new Label(dica, tema.tintaSuave);
        dicaLabel.setWrap(true);
        dicaLabel.setAlignment(Align.right);
        pagina.add(dicaLabel).growX().padTop(4);
        pagina.addActor(foco);

        var cel = raiz.add(pagina).width(largura).maxHeight(alturaMax);
        if (carta) cel.minHeight(Math.min(alturaMax, stage.getHeight() * 0.55f));
        raiz.setVisible(true);
        for (int i = 0; i < 3; i++) {
            raiz.invalidateHierarchy();
            raiz.validate();
        }
    }

    /** A ilustração numa moldura do tema, na proporção da imagem, sem passar de um terço da altura da tela. */
    private Table ilustracao(Texture tex, float larguraLivre) {
        int[] m = c.tema.pad("ui.moldura_retrato");
        float aspecto = (float) tex.getWidth() / tex.getHeight();
        float h = Math.min((larguraLivre - m[0] - m[1]) / aspecto, c.stage.getHeight() * 0.34f);
        float w = h * aspecto;
        Image img = new Image(tex);
        Table moldura = c.ui.fundo("ui.moldura_retrato");
        moldura.add(img).size(w, h);
        return moldura;
    }
}
