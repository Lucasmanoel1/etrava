package etrava.client.ui.telas;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import etrava.client.assets.Assets;
import etrava.client.ui.Tema;
import etrava.client.ui.Texts;
import etrava.client.ui.Ui;
import etrava.sim.api.Command;
import etrava.sim.content.ContentDb;
import etrava.sim.content.ItemDef;
import etrava.sim.economy.Economy;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * O que as telas de {@code ui/telas/} compartilham (V7): o Stage, o tema, os dados do jogo e as pequenas peças
 * que mais de uma tela usa (ícone de item, moedas, quilos, linha clicável). Uma instância por {@code GameUi}.
 */
public final class Contexto {
    public final Stage stage;
    public final Tema tema;
    public final Ui ui;
    public final Assets assets;
    public final ContentDb content;
    public final Texts texts;
    /** Quem recebe os comandos das telas (comprar, vestir…); o {@code GameUi} o troca. */
    public Consumer<Command> comando = c -> { };

    public Contexto(Stage stage, Tema tema, Ui ui, Assets assets, ContentDb content, Texts texts) {
        this.stage = stage;
        this.tema = tema;
        this.ui = ui;
        this.assets = assets;
        this.content = content;
        this.texts = texts;
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

    /** Ícone do item (RF-200): 32 px de arte, escala inteira; item sem ícone mostra o placeholder. */
    public Image icone(String itemId) {
        ItemDef def = content.items().get(itemId);
        return new Image(assets.sprite(def != null ? def.iconId() : "icone." + itemId).region());
    }

    /** O ícone dentro da moldura do tema (40×40 com o ícone de 32). */
    public Table iconeEmoldurado(String itemId, boolean escolhido) {
        return ui.moldura(icone(itemId), escolhido ? Ui.Moldura.ICONE_SEL : Ui.Moldura.ICONE);
    }

    public static String kg(double v) {
        return String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    /** "1 moeda", "12 moedas". */
    public String moedas(int n) {
        return n == 1 ? texts.get("moeda.uma") : texts.get("moeda.varias", Integer.toString(n));
    }

    public String fome(double hunger) {
        if (hunger >= Economy.STARVING) return texts.get("fome.faminto");
        if (hunger >= Economy.HUNGRY) return texts.get("fome.com_fome");
        return texts.get(hunger >= 35 ? "fome.pouca" : "fome.satisfeito");
    }

    /**
     * Faz da tabela uma linha clicável com realce sob o cursor (pergaminho escuro). Sem {@code acao}
     * a linha não reage (item que não dá para escolher).
     */
    public void clicavel(Table linha, Runnable acao) {
        if (acao == null) return;
        linha.setTouchable(Touchable.enabled);
        var realce = tema.peca("ui.pergaminho_escuro");
        linha.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                acao.run();
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor from) {
                super.enter(event, x, y, pointer, from);
                linha.setBackground(isOver() ? realce : null);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor to) {
                super.exit(event, x, y, pointer, to);
                linha.setBackground(isOver() ? realce : null);
            }
        });
    }

    /** Devolve a posição de uma lista depois de refazer a tela (a lista não volta ao topo a cada troca). */
    public static void rolarPara(Table raiz, ScrollPane lista, float y) {
        if (lista == null || y <= 0) return;
        raiz.validate();
        lista.layout();
        lista.setScrollY(y);
        lista.updateVisualScroll();
    }
}
