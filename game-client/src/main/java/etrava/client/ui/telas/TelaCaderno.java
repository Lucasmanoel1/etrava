package etrava.client.ui.telas;

import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import etrava.client.ui.Foco;
import etrava.client.ui.Ui;
import etrava.sim.api.Command;
import etrava.sim.api.WorldView;
import etrava.sim.content.FactDef;
import etrava.sim.content.NpcDef;
import etrava.sim.world.KnowledgeEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * O caderno (PRD 10.2), no tema do V7: um livro aberto em duas páginas, com as abas-fita saindo da borda direita
 * (a aba atual mais comprida). A página da esquerda é a lista da aba (ouvi dizer, vi e li, pessoas, lugares,
 * documentos); a da direita, o detalhe do item escolhido, com a de quem veio e quando. Cada página rola sozinha.
 * Nos fatos, o jogador marca o que quer poder perguntar numa conversa ({@code onToggleMark}). Só registra o que o
 * personagem ouviu, viu e leu: nenhum objetivo, nenhuma instrução (RF-121, AT-20).
 * <p>
 * Teclado: cima e baixo andam na lista, esquerda e direita trocam de aba (também com o foco nas abas), Enter marca
 * o fato escolhido (ou abre o documento), Esc fecha.
 */
public final class TelaCaderno {
    public static final int ABAS = 5;
    /** As abas, na ordem: 0 ouvi dizer, 1 vi e li, 2 pessoas, 3 lugares, 4 documentos. */
    private static final String[] NOMES = {"ouvi", "vi", "pessoas", "lugares", "documentos"};

    /** Onde o foco do teclado fica depois de refazer a tela. */
    private enum ModoFoco { MANTER, ABA, PRIMEIRO, NENHUM }

    /** Um item da lista e o que o detalhe mostra dele; {@code fazer} é a ação do botão do detalhe (ou null). */
    private record Item(String chave, String linha1, boolean destaque, String linha2, String titulo, String sub,
            String corpo, boolean marcado, String acao, Runnable fazer) {}

    private final Contexto c;
    private final Table raiz = new Table();
    private Consumer<String> aoMarcar = f -> { };
    private Runnable aoFechar = () -> { };

    private int aba;
    private final String[] escolhaPorAba = new String[ABAS];
    private boolean pendente = true;
    private String assinatura = "";
    private ModoFoco modo = ModoFoco.NENHUM;
    private WorldView ultima;

    // o que está na tela
    private Foco foco;
    private ScrollPane lista, detalhe;
    private List<Item> itens = List.of();
    private final List<Table> linhas = new ArrayList<>();
    private Label dTitulo, dSub, dNota, dCorpo;
    private TextButton dAcao;
    private com.badlogic.gdx.scenes.scene2d.ui.Cell<Label> cSub;

    public TelaCaderno(Contexto c) {
        this.c = c;
        raiz.setFillParent(true);
        raiz.center();
        Ui.esconder(raiz);
    }

    public Table raiz() { return raiz; }

    /** Quem trata a marca de um fato (recebe o id do fato). */
    public void aoMarcar(Consumer<String> acao) { this.aoMarcar = acao; }

    /** O que Esc faz (fechar o caderno). */
    public void aoFechar(Runnable acao) { this.aoFechar = acao; }

    /** A tela precisa ser remontada (abriu, ou a janela mudou de tamanho). */
    public void invalidar() {
        pendente = true;
        modo = ModoFoco.MANTER;
    }

    /** Abre o caderno: o foco do teclado recomeça sem ninguém escolhido. */
    public void abrir() {
        pendente = true;
        modo = ModoFoco.NENHUM;
    }

    public void irParaAba(int tab) {
        aba = Math.floorMod(tab, ABAS);
        pendente = true;
        modo = ModoFoco.NENHUM;
    }

    public void mudarAba(int delta) {
        irParaAba(aba + delta);
    }

    /** Monta (se algo mudou) ou esconde. */
    public void atualizar(WorldView view, boolean aberto) {
        if (!aberto) {
            Ui.esconder(raiz);
            return;
        }
        String sig = view.knowledge().size() + "|" + view.markedFacts().size();
        if (pendente || !sig.equals(assinatura)) {
            assinatura = sig;
            pendente = false;
            montar(view);
        }
        raiz.setVisible(true);
    }

    // ---------------------------------------------------------------- montagem

    private void montar(WorldView view) {
        var tema = c.tema;
        ultima = view;
        boolean manter = modo == ModoFoco.MANTER;
        int focado = foco == null ? -1 : foco.indice();
        float rolado = lista == null || !manter ? 0 : lista.getScrollY();
        raiz.clearChildren();
        linhas.clear();
        foco = c.ui.foco().aoFechar(aoFechar);
        itens = itens(view);
        String esc = escolhaPorAba[aba];
        if (itens.stream().noneMatch(i -> i.chave().equals(esc))) escolhaPorAba[aba] = itens.isEmpty() ? null : itens.get(0).chave();

        // medidas: o livro e a coluna de abas juntos cabem na tela, centrados, com largura máxima
        float lh = tema.fonte.getLineHeight();
        float textoMax = 0;
        for (String n : NOMES) textoMax = Math.max(textoMax, new GlyphLayout(tema.fonte, c.texts.get("caderno.aba." + n)).width);
        int[] pa = tema.pad("ui.aba");
        float abaLarg = (float) Math.ceil(textoMax) + pa[0] + pa[1] + 2, abaAtivaLarg = abaLarg + 12, encosto = 8;
        // largura máxima: nunca mais larga que 1,75 vezes a altura da tela (21:9 não estica o livro)
        float total = Math.min(Math.min(c.stage.getWidth() * 0.97f, 64 * lh), c.stage.getHeight() * 1.75f);
        float livroL = Math.max(200, total - abaAtivaLarg + encosto);
        float pw = (float) Math.floor(livroL / 2), ph = c.ui.altura(c.stage, 0.92f, 34);

        // página da esquerda: a lista
        Table esq = c.ui.fundo("ui.livro_esq");
        esq.top();
        esq.add(new Label(c.texts.get("caderno.aba." + NOMES[aba]), tema.ferrugem)).left().growX().row();
        esq.add(c.ui.divisoria()).growX().padTop(2).padBottom(4).row();
        Table col = new Table();
        col.top().left();
        col.defaults().left().growX();
        if (itens.isEmpty()) {
            Label vazio = new Label(c.texts.get("caderno.vazio"), tema.tintaSuave);
            vazio.setWrap(true);
            col.add(vazio).padTop(4).row();
        }
        for (Item it : itens) col.add(linha(it)).padBottom(2).row();
        lista = c.ui.rolagem(col);
        esq.add(lista).grow().row();

        // página da direita: o detalhe
        Table dir = c.ui.fundo("ui.livro_dir");
        dir.top();
        dTitulo = new Label("", tema.ferrugem);
        dTitulo.setWrap(true);
        dSub = new Label("", tema.tintaSuave);
        dSub.setWrap(true);
        dNota = new Label(c.texts.get("caderno.marcado"), tema.ferrugem);
        dNota.setWrap(true);
        dCorpo = new Label("", tema.tinta);
        dCorpo.setWrap(true);
        dir.add(dTitulo).growX().row();
        dir.add(c.ui.divisoria()).growX().padTop(2).padBottom(4).row();
        cSub = dir.add(dSub).growX();
        dir.row();
        Table texto = new Table();
        texto.top().left();
        texto.defaults().left().growX();
        texto.add(dCorpo).padTop(4).row();
        texto.add(dNota).padTop(6).row();
        detalhe = c.ui.rolagem(texto);
        dir.add(detalhe).grow().row();
        dAcao = c.botao("", this::acionar);
        dir.add(dAcao).right().padTop(6).row();
        Label dica = new Label(c.texts.get("caderno.dica"), tema.tintaSuave);
        dica.setWrap(true);
        dica.setAlignment(Align.right);
        dir.add(dica).growX().padTop(4);

        Table livro = new Table();
        livro.add(esq).size(pw, ph);
        livro.add(dir).size(pw, ph);

        // o foco: as linhas, o botão do detalhe, depois as abas
        for (int i = 0; i < itens.size(); i++) {
            Item it = itens.get(i);
            foco.adicionar(linhas.get(i), () -> ativar(it.chave()), d -> mudar(d, ModoFoco.PRIMEIRO));
        }
        foco.adicionar(dAcao);
        foco.aoFocar(i -> {
            if (i < itens.size()) selecionar(itens.get(i).chave());
        });

        // as abas-fita saem da borda direita do livro, por cima dele
        Table abas = new Table();
        abas.top().left();
        for (int i = 0; i < ABAS; i++) {
            int destino = i;
            final TextButton[] ref = new TextButton[1];
            TextButton b = new TextButton(c.texts.get("caderno.aba." + NOMES[i]), c.estiloAba);
            ref[0] = b;
            b.getLabel().setAlignment(Align.left);
            b.setProgrammaticChangeEvents(false);
            b.setChecked(i == aba);
            b.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (destino != aba) irParaAba(destino);
                    else ref[0].setChecked(true); // clicar na aba atual não a desmarca
                }
            });
            abas.add(b).left().width(i == aba ? abaAtivaLarg : abaLarg).padBottom(3).row();
            foco.adicionar(b, () -> irParaAba(destino), d -> mudar(d, ModoFoco.ABA));
        }
        Table conj = new Table();
        conj.top();
        conj.add(livro);
        conj.add(abas).top().padLeft(-encosto).padTop(22);
        raiz.add(conj);
        raiz.addActor(foco);

        selecionar(escolhaPorAba[aba]);
        int n = itens.size(), total2 = n + 1 + ABAS;
        switch (modo) {
            case MANTER -> foco.restaurar(focado < total2 ? focado : -1);
            case ABA -> foco.restaurar(n + 1 + aba);
            case PRIMEIRO -> foco.restaurar(n > 0 ? 0 : n + 1 + aba);
            default -> foco.restaurar(-1);
        }
        modo = ModoFoco.MANTER;
        raiz.validate();
        raiz.invalidateHierarchy();
        raiz.validate();
        Contexto.rolarPara(raiz, lista, rolado);
    }

    /** Troca de aba por tecla (setas): o foco acompanha. */
    private void mudar(int delta, ModoFoco depois) {
        mudarAba(delta);
        modo = depois;
    }

    // ---------------------------------------------------------------- itens

    private List<Item> itens(WorldView view) {
        List<Item> r = new ArrayList<>();
        switch (aba) {
            case 0, 1 -> {
                for (KnowledgeEntry k : view.knowledge()) {
                    boolean visto = !k.certainty().equals("rumor") && !k.certainty().equals("claim");
                    if (visto != (aba == 1)) continue;
                    FactDef fato = c.content.facts().get(k.fact());
                    if (fato == null) continue;
                    boolean marcado = view.markedFacts().contains(k.fact());
                    String dia = c.texts.get("caderno.dia", k.day()), fonte = fonte(k), texto = fato.text(k.version());
                    r.add(new Item(k.fact(), (marcado ? "* " : "") + dia + " · " + fonte, false, texto, dia, fonte, texto, marcado,
                            c.texts.get(marcado ? "caderno.desmarcar" : "caderno.marcar"), () -> aoMarcar.accept(k.fact())));
                }
            }
            case 2 -> {
                for (NpcDef npc : c.content.npcs().values()) {
                    if (!view.flag("conheceu." + npc.id())) continue;
                    String estado = switch (String.valueOf(view.npcStatus(npc.id()))) {
                        case "departed" -> c.texts.get("pessoas.partiu");
                        case "dead" -> c.texts.get("pessoas.morreu");
                        default -> "";
                    };
                    int op = view.opinion(npc.id());
                    String humor = c.texts.get(op >= 40 ? "pessoas.gosta" : op >= 10 ? "pessoas.simpatia"
                            : op > -10 ? "pessoas.neutro" : op > -40 ? "pessoas.desconfia" : "pessoas.detesta");
                    String sobre = npc.about() != null ? npc.about() : "";
                    r.add(new Item(npc.id(), npc.displayName() + (estado.isEmpty() ? "" : " · " + estado), true,
                            (sobre.isEmpty() ? "" : sobre + " ") + humor, npc.displayName(), estado,
                            (sobre.isEmpty() ? "" : sobre + "\n\n") + humor, false, null, null));
                }
            }
            case 3 -> {
                Map<String, List<String>> porLugar = new LinkedHashMap<>();
                for (var e : c.content.areaEntries().values()) {
                    if (!view.flag("visitou." + e.id())) continue;
                    String lugar = e.place() != null ? e.place() : e.displayName();
                    porLugar.computeIfAbsent(lugar, k -> new ArrayList<>()).add(e.displayName());
                }
                for (Map.Entry<String, List<String>> e : porLugar.entrySet()) {
                    List<String> partes = e.getValue().stream().filter(n -> !n.equals(e.getKey())).toList(); // o próprio nome não repete
                    r.add(new Item(e.getKey(), e.getKey(), true, String.join(" · ", partes), e.getKey(), "",
                            String.join("\n", partes), false, null, null));
                }
            }
            default -> {
                for (String id : view.inventory().keySet()) {
                    var item = c.content.items().get(id);
                    if (item == null || !item.document()) continue;
                    String desc = item.description() == null ? "" : item.description();
                    r.add(new Item(id, item.displayName(), true, desc, item.displayName(), "", desc, false,
                            c.texts.get("caderno.ler"), () -> c.comando.accept(new Command.UseItem(id))));
                }
            }
        }
        return r;
    }

    /** "Fulano disse:", "Ouvi de passagem:"… — de onde veio a anotação (RF-104). */
    private String fonte(KnowledgeEntry k) {
        String src = k.source();
        if (src.startsWith("npc:")) {
            NpcDef npc = c.content.npcs().get(src.substring(4));
            String nome = npc != null ? npc.displayName() : src.substring(4);
            return c.texts.get(k.certainty().equals("rumor") ? "fonte.rumor" : "fonte.claim", nome);
        }
        if (src.startsWith("conversa:")) return c.texts.get("fonte.conversa");
        return switch (k.certainty()) {
            case "witnessed" -> c.texts.get("fonte.witnessed");
            case "read" -> c.texts.get("fonte.read");
            case "verified" -> c.texts.get("fonte.verified");
            default -> c.texts.get("fonte.mundo");
        };
    }

    // ---------------------------------------------------------------- lista e detalhe

    /** O começo do texto para a lista (até uma frase curta, cortado numa palavra); o resto está no detalhe. */
    private static String previa(String texto) {
        final int max = 96;
        String t = texto.replace("\n", " ");
        if (t.length() <= max) return t;
        int corte = t.lastIndexOf(' ', max);
        return t.substring(0, corte > 40 ? corte : max).stripTrailing() + "…";
    }

    private Table linha(Item it) {
        var tema = c.tema;
        var sobre = tema.peca("ui.pergaminho_escuro");
        Table l = new Table();
        l.pad(3, 6, 4, 6);
        l.left();
        l.defaults().left().growX();
        Label a = new Label(it.linha1(), it.destaque() || it.marcado() ? tema.ferrugem : tema.tintaSuave);
        Label b = new Label(previa(it.linha2()), it.marcado() ? tema.ferrugem : tema.tinta);
        a.setWrap(true);
        b.setWrap(true);
        l.add(a).row();
        if (!it.linha2().isEmpty()) l.add(b).row();
        l.setTouchable(Touchable.enabled);
        l.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ativar(it.chave());
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor from) {
                super.enter(event, x, y, pointer, from);
                if (!it.chave().equals(escolhaPorAba[aba])) l.setBackground(sobre);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor to) {
                super.exit(event, x, y, pointer, to);
                if (!it.chave().equals(escolhaPorAba[aba])) l.setBackground((com.badlogic.gdx.scenes.scene2d.utils.Drawable) null);
            }
        });
        linhas.add(l);
        return l;
    }

    /** Marca a linha escolhida e mostra o detalhe dela, sem refazer a tela. */
    private void selecionar(String chave) {
        escolhaPorAba[aba] = chave;
        Item sel = null;
        for (int i = 0; i < itens.size(); i++) {
            boolean s = itens.get(i).chave().equals(chave);
            linhas.get(i).setBackground(s ? c.tema.peca("ui.encaixe") : null);
            if (s) sel = itens.get(i);
        }
        boolean mudou = sel != null && !sel.titulo().contentEquals(dTitulo.getText());
        if (sel == null) {
            dTitulo.setText("");
            dSub.setText("");
            cSub.height(com.badlogic.gdx.scenes.scene2d.ui.Value.zero);
            dCorpo.setText(itens.isEmpty() ? "" : c.texts.get("caderno.sem_selecao"));
            dNota.setVisible(false);
            dAcao.setVisible(false);
            return;
        }
        dTitulo.setText(sel.titulo());
        dSub.setText(sel.sub());
        cSub.height(sel.sub().isEmpty() ? com.badlogic.gdx.scenes.scene2d.ui.Value.zero : com.badlogic.gdx.scenes.scene2d.ui.Value.prefHeight);
        dCorpo.setText(sel.corpo());
        dNota.setVisible(sel.marcado());
        dAcao.setVisible(sel.acao() != null);
        if (sel.acao() != null) dAcao.setText(sel.acao());
        dCorpo.invalidateHierarchy();
        if (mudou) detalhe.setScrollY(0);
    }

    /** Clicar (ou Enter) numa linha escolhe; na que já está escolhida, faz a ação do detalhe (marcar, ler). */
    private void ativar(String chave) {
        if (!chave.equals(escolhaPorAba[aba])) {
            selecionar(chave);
            return;
        }
        acionar();
    }

    private void acionar() {
        for (Item it : itens)
            if (it.chave().equals(escolhaPorAba[aba]) && it.fazer() != null) {
                it.fazer().run();
                return;
            }
    }
}
