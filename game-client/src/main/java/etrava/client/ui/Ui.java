package etrava.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.Tooltip;
import com.badlogic.gdx.scenes.scene2d.ui.TooltipManager;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import java.util.Locale;

/**
 * Utilitários para montar telas sobre o {@link Tema} (V7, SPEC-visual §8): painel, botão, rolagem,
 * dica, divisória, ícone, moldura, larguras por proporção, cursor e navegação por teclado.
 * Uma instância por tema (o {@code GameUi} cria a sua); {@link #esconder} e {@link #mostrar} são estáticos.
 * Medidas em unidades do Stage (já na escala {@link Escala}); nenhuma largura fixa em pixels.
 */
public final class Ui {
    /** Os cursores do tema (SPEC-visual §8.3). */
    public enum Tipo {
        APONTAR, FALAR, ENTRAR, PEGAR, ATACAR, ESPERAR;

        String nome() { return name().toLowerCase(Locale.ROOT); }
    }

    /** Molduras pequenas do tema. */
    public enum Moldura {
        RETRATO("ui.moldura_retrato"), ICONE("ui.moldura_icone"), ICONE_SEL("ui.moldura_icone_sel"), ENCAIXE("ui.encaixe");

        final String id;

        Moldura(String id) { this.id = id; }
    }

    private final Tema tema;
    private int k = 1;
    private Tipo cursorAtual;

    public Ui(Tema tema) {
        this.tema = tema;
        TooltipManager dicas = TooltipManager.getInstance();
        dicas.initialTime = 0.25f; // atraso curto
        dicas.subsequentTime = 0.05f;
        dicas.resetTime = 0.4f;
        dicas.animations = false;
        dicas.maxWidth = 22 * tema.fonte.getLineHeight();
    }

    public Tema tema() { return tema; }

    /** A escala da interface mudou ({@link Escala#k}): os cursores são refeitos nela. */
    public void escala(int k) {
        if (k == this.k && cursorAtual != null) return;
        this.k = k;
        cursor(cursorAtual == null ? Tipo.APONTAR : cursorAtual, true);
    }

    // ---------------------------------------------------------------- foco e visibilidade

    /**
     * Esconde um painel: {@code setVisible(false)} <b>e</b> tira dele o foco de teclado e de rolagem do
     * Stage (SPEC-visual §8.1). É o único jeito de esconder painel: uma lista invisível com foco engolia
     * a roda do mouse. Pode ser chamado a cada quadro.
     */
    public static void esconder(Actor ator) {
        boolean estava = ator.isVisible();
        ator.setVisible(false);
        Stage stage = ator.getStage();
        if (stage != null) stage.unfocus(ator);
        if (estava) TooltipManager.getInstance().hideAll(); // dica de um painel que sumiu não fica pendurada
    }

    /** Mostra ou esconde (com {@link #esconder}) conforme {@code visivel}. */
    public static void mostrar(Actor ator, boolean visivel) {
        if (visivel) ator.setVisible(true);
        else esconder(ator);
    }

    /** Um {@link Foco} novo, com o contorno do tema, para pôr como último filho do painel. */
    public Foco foco() {
        return new Foco(tema.peca("ui.foco"));
    }

    // ---------------------------------------------------------------- peças

    /**
     * Painel principal: moldura de madeira com miolo de pergaminho, recuo do tema, título em
     * tinta de ferrugem e uma divisória sob ele. O chamador continua acrescentando linhas à tabela.
     */
    public Table painel(String titulo) {
        Table t = new Table();
        t.setBackground(painelComTrama());
        int[] p = tema.pad("ui.painel_madeira");
        t.pad(p[2], p[0], p[3], p[1]);
        t.top().left();
        if (titulo != null) {
            t.add(new Label(titulo, tema.titulo)).left().growX().row();
            t.add(divisoria()).growX().padTop(2).padBottom(6).row();
        }
        return t;
    }

    private Drawable painelTrama;

    /**
     * O fundo do painel: a moldura de madeira e, por baixo do conteúdo e dentro dela, a trama do pergaminho
     * ({@code ui.papel_trama}) ladrilhada sem esticar, para o miolo não ser uma cor chapada.
     */
    private Drawable painelComTrama() {
        if (painelTrama != null) return painelTrama;
        Drawable moldura = tema.peca("ui.painel_madeira");
        Drawable trama = tema.peca("ui.papel_trama");
        if (!(trama instanceof TextureRegionDrawable regiao) || regiao.getRegion().getTexture() == null) return painelTrama = moldura;
        Texture textura = regiao.getRegion().getTexture();
        textura.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        float larg = regiao.getRegion().getRegionWidth(), alt = regiao.getRegion().getRegionHeight();
        BaseDrawable d = new BaseDrawable() {
            @Override
            public void draw(Batch batch, float x, float y, float w, float h) {
                moldura.draw(batch, x, y, w, h);
                // por dentro da madeira e da sombra que ela joga no papel (13 px em cima e à esquerda, 12 embaixo e à direita)
                float ix = x + 13, iy = y + 12, iw = w - 25, ih = h - 25;
                if (iw > 0 && ih > 0) batch.draw(textura, ix, iy, iw, ih, 0f, ih / alt, iw / larg, 0f);
            }
        };
        d.setMinWidth(moldura.getMinWidth());
        d.setMinHeight(moldura.getMinHeight());
        return painelTrama = d;
    }

    /** Tabela com o fundo e o recuo de uma peça do tema (pergaminho, couro, bilhete, balão…). */
    public Table fundo(String idPeca) {
        Table t = new Table();
        t.setBackground(tema.peca(idPeca));
        int[] p = tema.pad(idPeca);
        t.pad(p[2], p[0], p[3], p[1]);
        return t;
    }

    public TextButton botao(String texto, Runnable acao) {
        TextButton b = new TextButton(texto, tema.botao);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (acao != null) acao.run();
            }
        });
        return b;
    }

    public Label texto(String texto) { return new Label(texto, tema.tinta); }

    /** Texto de quebra automática; a largura vem da célula da tabela. */
    public Label paragrafo(String texto) {
        Label l = new Label(texto, tema.tinta);
        l.setWrap(true);
        return l;
    }

    /** Lista rolável padrão (SPEC-visual §8.1): barra visível quando não cabe, roda sob o cursor, sem foco grudado. */
    public ScrollPane rolagem(Actor conteudo) {
        return Scrolls.of(conteudo, tema.rolagem);
    }

    /** Dica flutuante (tooltip) do tema sobre o ator, com atraso curto; o libGDX a mantém dentro da tela. */
    public Tooltip<Label> dica(Actor ator, String texto) {
        TextTooltip dica = new TextTooltip(texto, TooltipManager.getInstance(), tema.dica);
        dica.setInstant(false);
        ator.addListener(dica);
        return dica;
    }

    public Image divisoria() {
        return new Image(tema.peca("ui.divisoria"));
    }

    public Image divisoriaVertical() {
        return new Image(tema.peca("ui.divisoria_v"));
    }

    /** Imagem de uma peça simples do tema ({@code ui.sol}, {@code ui.lua}, {@code ui.bolsa}…), no tamanho natural. */
    public Image icone(String id) {
        return new Image(tema.peca(id));
    }

    /** O conteúdo (retrato, ícone…) dentro de uma moldura do tema. */
    public Table moldura(Actor conteudo, Moldura tipo) {
        Table t = fundo(tipo.id);
        t.add(conteudo);
        return t;
    }

    // ---------------------------------------------------------------- medidas

    /**
     * Largura de um painel: a {@code fracao} da largura virtual do Stage, no máximo {@code maxLinhas}
     * alturas de linha da fonte (nada de pixels fixos: acompanha o tamanho do texto).
     */
    public float largura(Stage stage, float fracao, float maxLinhas) {
        return Math.min(stage.getWidth() * fracao, maxLinhas * tema.fonte.getLineHeight());
    }

    /** Altura de um painel: a {@code fracao} da altura virtual, no máximo {@code maxLinhas} alturas de linha. */
    public float altura(Stage stage, float fracao, float maxLinhas) {
        return Math.min(stage.getHeight() * fracao, maxLinhas * tema.fonte.getLineHeight());
    }

    // ---------------------------------------------------------------- cursor

    /** Troca o cursor do sistema pelo do tema (ampliado por k); sem a imagem, volta ao cursor padrão do sistema. */
    public void cursor(Tipo tipo) {
        cursor(tipo, false);
    }

    private void cursor(Tipo tipo, boolean refazer) {
        if (tipo == cursorAtual && !refazer) return;
        cursorAtual = tipo;
        if (Gdx.graphics == null) return;
        Cursor c = tema.cursor(tipo.nome(), k);
        if (c != null) Gdx.graphics.setCursor(c);
        else Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow);
    }

    public Tipo cursorAtual() { return cursorAtual; }
}
