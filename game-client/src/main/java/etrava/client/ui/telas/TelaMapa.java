package etrava.client.ui.telas;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import etrava.client.ui.Ui;
import etrava.sim.api.WorldView;

/**
 * O mapa ilustrado (M, RF-385), no tema do V7: painel de madeira com a ilustração numa moldura, sem deformar, as
 * marcas dos lugares por onde o jogador já passou com o nome numa etiqueta pequena de pergaminho e, no lugar
 * onde ele está, um aro e o nome em ferrugem. Nenhum objetivo, nada que pisca.
 */
public final class TelaMapa {
    private final Contexto c;
    private final Table raiz = new Table();

    public TelaMapa(Contexto c) {
        this.c = c;
        raiz.setFillParent(true);
        raiz.center();
        Ui.esconder(raiz);
    }

    public Table raiz() { return raiz; }

    public void mostrar(boolean visivel) { Ui.mostrar(raiz, visivel); }

    public void montar(WorldView view) {
        raiz.clearChildren();
        var tema = c.tema;
        Stage stage = c.stage;
        Texture arte = c.assets.texture("ilustracao.mapa");
        float aspecto = arte == null ? 16f / 9f : (float) arte.getWidth() / arte.getHeight();

        int[] pm = tema.pad("ui.painel_madeira"), pr = tema.pad("ui.moldura_retrato");
        float lh = tema.fonte.getLineHeight();
        float painelMax = c.ui.largura(stage, 0.94f, 80);
        float livreL = painelMax - pm[0] - pm[1] - pr[0] - pr[1];
        // altura: o painel todo cabe em 94% da tela (título, divisória, dica e recuos incluídos)
        float livreA = stage.getHeight() * 0.94f - pm[2] - pm[3] - pr[2] - pr[3] - (lh + 12) - (lh + 8);
        float w = Math.max(80, Math.min(livreL, livreA * aspecto)), h = w / aspecto;

        Group mapa = new Group();
        mapa.setSize(w, h);
        if (arte != null) {
            Image img = new Image(arte);
            img.setSize(w, h);
            img.setTouchable(Touchable.disabled);
            mapa.addActor(img);
        }
        // as marcas em duas passadas: primeiro todas as marcas, depois as etiquetas por cima
        var visitadas = new java.util.ArrayList<etrava.sim.content.ContentDb.AreaEntry>();
        for (var e : c.content.areaEntries().values()) {
            if (e.mapPos() == null || e.mapPos().size() < 2 || !view.flag("visitou." + e.id())) continue;
            visitadas.add(e);
        }
        for (var e : visitadas) {
            float x = (float) (e.mapPos().get(0) * w), y = (float) ((1 - e.mapPos().get(1)) * h);
            boolean aqui = e.id().equals(view.areaId());
            if (aqui) {
                Image aro = new Image(tema.peca("ui.moldura_icone_sel"));
                aro.setBounds(x - 11, y - 11, 22, 22);
                aro.setTouchable(Touchable.disabled);
                mapa.addActor(aro);
            }
            Image ponto = new Image(tema.peca(aqui ? "ui.barra_vida" : "ui.barra_fundo"));
            ponto.setBounds(x - 4, y - 4, 8, 8);
            ponto.setTouchable(Touchable.disabled);
            mapa.addActor(ponto);
        }
        for (var e : visitadas) {
            float x = (float) (e.mapPos().get(0) * w), y = (float) ((1 - e.mapPos().get(1)) * h);
            boolean aqui = e.id().equals(view.areaId());
            Table etiqueta = c.ui.fundo(aqui ? "ui.pergaminho" : "ui.pergaminho_escuro");
            etiqueta.pad(1, 4, 2, 4);
            etiqueta.setTouchable(Touchable.disabled);
            String nome = aqui ? c.texts.get("mapa.voce", e.displayName()) : e.displayName();
            etiqueta.add(new Label(nome, aqui ? tema.ferrugem : tema.tinta));
            etiqueta.pack();
            float ex = Math.min(w - etiqueta.getWidth(), Math.max(0, x - etiqueta.getWidth() / 2));
            float ey = y + (aqui ? 14 : 7);
            if (ey + etiqueta.getHeight() > h) ey = y - 7 - etiqueta.getHeight(); // sem espaço em cima: vai para baixo
            etiqueta.setPosition(Math.round(ex), Math.round(ey));
            mapa.addActor(etiqueta);
        }

        Table painel = c.ui.painel(c.texts.get("mapa.titulo"));
        Table moldura = c.ui.fundo("ui.moldura_retrato"); // um Group não tem tamanho preferido: a célula o fixa
        moldura.add(mapa).size(w, h);
        painel.add(moldura).center().row();
        Label dica = new Label(c.texts.get("mapa.dica"), tema.tintaSuave);
        dica.setWrap(true);
        dica.setAlignment(Align.right);
        painel.add(dica).growX().padTop(6);
        raiz.add(painel).width(w + pm[0] + pm[1] + pr[0] + pr[1]);
        raiz.validate();
    }
}
