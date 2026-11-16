package etrava.client.ui.telas;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import etrava.client.ui.Foco;
import etrava.client.ui.Ui;
import etrava.sim.api.Command;
import etrava.sim.api.TradeView;
import etrava.sim.api.WorldView;
import etrava.sim.content.ItemDef;
import etrava.sim.economy.Economy;
import java.util.ArrayList;
import java.util.List;

/**
 * Comércio e contêiner (PRD 10.2), no tema do V7: painel de madeira com duas colunas (o que é seu, o que é
 * dele), abas-fita por categoria, ícone emoldurado, nome, quantidade e preço, dica flutuante com a descrição,
 * dinheiro, aviso de carga e o total já trocado. O contêiner é a mesma tela com uma coluna só e sem preço.
 * Clicar (ou Enter) negocia uma unidade.
 */
public final class TelaComercio {
    private enum Modo { COMPRAR, VENDER, PEGAR }

    private static final String[] ABAS = {"tudo", "comida", "equipamento", "materiais", "documentos"};

    private final Contexto c;
    private final Table raiz = new Table();
    private String assinatura = "";
    private String aba = ABAS[0];
    /** Moedas recebidas menos pagas nesta troca; zera quando a tela fecha. */
    private int saldo;
    private Foco foco;
    private List<ScrollPane> listas = new ArrayList<>();

    public TelaComercio(Contexto c) {
        this.c = c;
        raiz.setFillParent(true);
        raiz.center();
        Ui.esconder(raiz);
    }

    public Table raiz() { return raiz; }

    public void invalidar() { assinatura = ""; }

    /** @param eco consultas de peso e capacidade; pode ser null (sem aviso de carga) */
    public void atualizar(WorldView view, TradeView t, Economy eco) {
        if (t == null) {
            Ui.esconder(raiz);
            assinatura = "";
            saldo = 0;
            aba = ABAS[0];
            foco = null;
            return;
        }
        String sig = t + "|" + view.money() + "|" + aba + "|" + saldo
                + (eco == null ? "" : "|" + Math.round(eco.weight() * 10) + "|" + Math.round(eco.capacity() * 10));
        if (!sig.equals(assinatura)) {
            montar(view, t, eco);
            assinatura = sig;
        }
        raiz.setVisible(true);
    }

    private static String grupo(ItemDef item) {
        if (item == null) return "materiais";
        return switch (item.category()) {
            case "consumivel" -> "comida";
            case "arma", "armadura", "ferramenta" -> "equipamento";
            case "documento" -> "documentos";
            default -> "materiais";
        };
    }

    private boolean passa(TradeView.Offer o) {
        return aba.equals("tudo") || aba.equals(grupo(c.content.items().get(o.item())));
    }

    private void montar(WorldView view, TradeView t, Economy eco) {
        var tema = c.tema;
        // guarda o foco e a rolagem para devolver depois de refazer (cada troca remonta a tela)
        int focado = foco == null ? -1 : foco.indice();
        List<Float> posicoes = new ArrayList<>();
        for (ScrollPane p : listas) posicoes.add(p.getScrollY());
        raiz.clearChildren();
        listas = new ArrayList<>();
        foco = c.ui.foco().aoFechar(() -> c.comando.accept(new Command.CloseTrade()));

        Table painel = c.ui.painel(t.name());
        float largura = c.ui.largura(c.stage, 0.94f, 40), altura = c.ui.altura(c.stage, 0.94f, 28);
        int[] pad = tema.pad("ui.painel_madeira");
        float interno = largura - pad[0] - pad[1];

        // cabeçalho: dinheiro dos dois lados, opinião (comércio) ou dono (contêiner)
        Table cabeca = new Table();
        cabeca.left();
        if (t.container()) {
            Label dono = new Label(t.owner() == null ? c.texts.get("conteiner.sem_dono")
                    : c.texts.get("conteiner.dono", t.owner()), t.owner() == null ? tema.tintaSuave : tema.ferrugem);
            dono.setWrap(true);
            cabeca.add(dono).growX();
        } else {
            cabeca.add(c.ui.icone("ui.bolsa")).padRight(4);
            cabeca.add(new Label(c.texts.get("comercio.seu_dinheiro", c.moedas(view.money())), tema.tinta)).padRight(16);
            cabeca.add(new Label(c.texts.get("comercio.caixa", c.moedas(t.partnerCash())), tema.tintaSuave)).padRight(16);
            int op = t.opinionPercent();
            Label opiniao = new Label(op == 0 ? c.texts.get("comercio.opiniao_neutra")
                    : c.texts.get(op < 0 ? "comercio.opiniao_desconto" : "comercio.opiniao_acrescimo", Math.abs(op)), tema.tintaSuave);
            opiniao.setWrap(true);
            cabeca.add(opiniao).growX();
        }
        painel.add(cabeca).growX().padBottom(6).row();

        // abas-fita
        Table abas = new Table();
        abas.left();
        for (String nome : ABAS) {
            TextButton b = new TextButton(c.texts.get("comercio.aba." + nome), c.tema.aba);
            b.setChecked(nome.equals(aba));
            b.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    escolherAba(nome);
                }
            });
            foco.adicionar(b, () -> escolherAba(nome));
            abas.add(b).padRight(1);
        }
        painel.add(abas).left().growX().padBottom(2).row();

        // colunas
        float vao = 10;
        Table colunas = new Table();
        colunas.top();
        if (t.container()) {
            colunas.add(coluna(c.texts.get("conteiner.tem"), t.offers(), Modo.PEGAR, eco)).width(interno).grow();
        } else {
            float w = (interno - vao) / 2;
            colunas.add(coluna(c.texts.get("comercio.compra"), t.wants(), Modo.VENDER, eco)).width(w).grow();
            colunas.add().width(vao);
            colunas.add(coluna(c.texts.get("comercio.vende"), t.offers(), Modo.COMPRAR, eco)).width(w).grow();
        }
        painel.add(colunas).grow().row();

        // rodapé: carga, total da troca, dica das teclas
        Table rodape = new Table();
        if (eco != null) {
            boolean pesado = eco.overloaded();
            Label carga = new Label(c.texts.get("mochila.peso", Contexto.kg(eco.weight()), Contexto.kg(eco.capacity()))
                    + (pesado ? "  " + c.texts.get("mochila.pesado") : ""), pesado ? tema.ferrugem : tema.tinta);
            carga.setWrap(true);
            rodape.add(carga).growX();
        } else {
            rodape.add().growX();
        }
        if (!t.container()) {
            Label total = new Label(saldo == 0 ? c.texts.get("comercio.total_zero")
                    : c.texts.get(saldo > 0 ? "comercio.total_recebeu" : "comercio.total_pagou", c.moedas(Math.abs(saldo))), tema.tintaSuave);
            total.setWrap(true);
            total.setAlignment(Align.right);
            rodape.add(total).growX().padLeft(12);
        }
        painel.add(rodape).growX().padTop(6).row();
        Label dica = new Label(c.texts.get(t.container() ? "conteiner.dica" : "comercio.dica"), tema.tintaSuave);
        dica.setWrap(true);
        dica.setAlignment(Align.right);
        painel.add(dica).growX().padTop(2);

        painel.addActor(foco);
        raiz.add(painel).width(largura).height(altura);
        foco.restaurar(focado);
        raiz.validate();
        for (int i = 0; i < listas.size() && i < posicoes.size(); i++) Contexto.rolarPara(raiz, listas.get(i), posicoes.get(i));
    }

    private void escolherAba(String nome) {
        if (nome.equals(aba)) return;
        aba = nome;
        assinatura = ""; // remonta no próximo quadro
    }

    private Table coluna(String titulo, List<TradeView.Offer> ofertas, Modo modo, Economy eco) {
        var tema = c.tema;
        Table col = new Table();
        col.top().left();
        col.add(new Label(titulo, tema.ferrugem)).left().padBottom(3).row();
        Table lista = new Table();
        lista.top().left();
        lista.defaults().left().growX();
        int mostradas = 0;
        for (TradeView.Offer o : ofertas) {
            if (!passa(o)) continue;
            lista.add(linha(o, modo, eco)).padBottom(2).row();
            mostradas++;
        }
        if (mostradas == 0) lista.add(new Label(c.texts.get("comercio.nada"), tema.tintaSuave)).pad(4).row();
        ScrollPane rolagem = c.ui.rolagem(lista);
        listas.add(rolagem);
        Table fundo = c.ui.fundo("ui.pergaminho_escuro");
        fundo.add(rolagem).grow();
        col.add(fundo).grow();
        return col;
    }

    private Table linha(TradeView.Offer o, Modo modo, Economy eco) {
        var tema = c.tema;
        boolean pode = modo == Modo.PEGAR || o.possible();
        Table linha = new Table();
        linha.left();
        linha.pad(2, 2, 2, 4);
        linha.add(c.iconeEmoldurado(o.item(), false)).padRight(6);
        Table info = new Table();
        info.left();
        info.defaults().left().growX();
        Label nome = new Label(o.name(), pode ? tema.tinta : tema.tintaSuave);
        nome.setWrap(true);
        info.add(nome).row();
        Table sub = new Table();
        sub.add(new Label("x" + o.qty(), tema.tintaSuave)).left().growX();
        if (modo != Modo.PEGAR) {
            String preco = o.price() > 0 ? c.moedas(o.price()) : c.texts.get("comercio.sem_valor");
            sub.add(new Label(preco, pode ? tema.ferrugem : tema.tintaSuave)).right().padLeft(8);
        }
        info.add(sub).growX();
        linha.add(info).growX();

        Command cmd = !pode ? null : switch (modo) {
            case COMPRAR -> new Command.Buy(o.item());
            case VENDER -> new Command.Sell(o.item());
            case PEGAR -> new Command.TakeItem(o.item());
        };
        Runnable negociar = cmd == null ? null : () -> {
            if (modo == Modo.COMPRAR) saldo -= o.price();
            else if (modo == Modo.VENDER) saldo += o.price();
            c.comando.accept(cmd);
        };
        c.clicavel(linha, negociar);
        linha.setTouchable(Touchable.enabled); // a dica aparece também nas linhas que não dá para negociar
        c.ui.dica(linha, dica(o, modo, eco));
        foco.adicionar(linha, negociar);
        return linha;
    }

    /** A descrição do item, o peso e, se for o caso, por que não dá para fechar o negócio. */
    private String dica(TradeView.Offer o, Modo modo, Economy eco) {
        ItemDef item = c.content.items().get(o.item());
        StringBuilder sb = new StringBuilder(o.name());
        if (item != null) {
            if (item.description() != null && !item.description().isBlank()) sb.append('\n').append(item.description());
            sb.append('\n').append(c.texts.get("comercio.peso_item", Contexto.kg(item.weight())));
            if (modo != Modo.VENDER && eco != null && eco.weight() + item.weight() > eco.capacity())
                sb.append('\n').append(c.texts.get("comercio.passa_carga"));
        }
        if (modo == Modo.COMPRAR && !o.possible()) sb.append('\n').append(c.texts.get("comercio.sem_dinheiro"));
        if (modo == Modo.VENDER && !o.possible()) sb.append('\n').append(c.texts.get("comercio.sem_caixa"));
        return sb.toString();
    }
}
