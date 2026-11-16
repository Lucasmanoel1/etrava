package etrava.client.debug;

import etrava.sim.api.DialogueView;
import etrava.sim.api.TradeView;
import etrava.sim.api.WorldView;
import etrava.sim.content.ContentDb;
import etrava.sim.content.ItemDef;
import etrava.sim.world.KnowledgeEntry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Massa de teste do VT-08 (só em depuração): 80 itens num comércio, 60 fatos no caderno e uma conversa
 * com 12 opções, montados em memória por cima da simulação. Nada vai para {@code content/} nem para o save:
 * o jogo só troca o que entrega à interface ({@link #sobre}, {@link #comercio()}).
 */
public final class MassaVt08 {
    public static final int ITENS = 80, QUERIDOS = 30, FATOS = 60, OPCOES = 12;

    private final ContentDb content;
    private TradeView comercio;
    private DialogueView conversa;
    private List<KnowledgeEntry> fatos;

    public MassaVt08(ContentDb content) {
        this.content = content;
    }

    /** Liga uma das massas: {@code comercio}, {@code caderno}, {@code conversa}, ou {@code tudo}. */
    public void ligar(String qual) {
        boolean tudo = "tudo".equals(qual);
        if (tudo || "comercio".equals(qual)) comercio = montarComercio();
        if (tudo || "caderno".equals(qual)) fatos = montarFatos();
        if (tudo || "conversa".equals(qual)) conversa = montarConversa();
    }

    public void desligar() {
        comercio = null;
        conversa = null;
        fatos = null;
    }

    public boolean ligada() { return comercio != null || conversa != null || fatos != null; }

    /** O comércio de mentira, ou null se desligado. */
    public TradeView comercio() { return comercio; }

    /** A visão do mundo com a conversa e o caderno trocados pelos de teste (o resto vem da simulação). */
    public WorldView sobre(WorldView real) {
        if (conversa == null && fatos == null) return real;
        return (WorldView) Proxy.newProxyInstance(WorldView.class.getClassLoader(), new Class<?>[] {WorldView.class}, (p, m, a) -> {
            if (m.getParameterCount() == 0) {
                if (conversa != null && m.getName().equals("dialogue")) return conversa;
                if (fatos != null && m.getName().equals("knowledge")) return fatos;
            }
            try {
                return m.invoke(real, a);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    private TradeView montarComercio() {
        List<ItemDef> itens = new ArrayList<>(content.items().values());
        List<TradeView.Offer> oferta = new ArrayList<>(), procura = new ArrayList<>();
        for (int i = 0; i < ITENS; i++) {
            ItemDef it = itens.get(i % itens.size());
            oferta.add(new TradeView.Offer(it.id(), it.displayName() + (i >= itens.size() ? " (" + (i / itens.size() + 1) + ")" : ""),
                    1 + i % 4, 1 + it.value() + i % 7, i % 9 != 8));
        }
        for (int i = 0; i < QUERIDOS; i++) {
            ItemDef it = itens.get((i * 3) % itens.size());
            procura.add(new TradeView.Offer(it.id(), it.displayName(), 1 + i % 3, 1 + it.value() / 2, true));
        }
        return new TradeView("taberneira", "Taberneira", false, 100, 0, oferta, procura, null);
    }

    private List<KnowledgeEntry> montarFatos() {
        List<KnowledgeEntry> out = new ArrayList<>();
        for (var f : content.facts().values()) {
            if (out.size() == FATOS) break;
            out.add(new KnowledgeEntry(f.id(), f.defaultVersion(), "rumor", "taberneira", 3, "vila"));
        }
        return out;
    }

    private DialogueView montarConversa() {
        List<DialogueView.Choice> opcoes = new ArrayList<>();
        for (int i = 0; i < OPCOES; i++) {
            opcoes.add(new DialogueView.Choice("Uma pergunta comprida o bastante para quebrar em duas linhas na caixa de conversa, número "
                    + (i + 1) + ".", i % 4 == 0 ? "Lábia" : null));
        }
        List<DialogueView.Line> historico = List.of(new DialogueView.Line("Avel", "Bom dia."),
                new DialogueView.Line("Você", "Bom dia, tio."), new DialogueView.Line("Avel", "Hum."));
        return new DialogueView() {
            @Override public String npcId() { return "avel"; }
            @Override public String npcName() { return "Avel"; }
            @Override public String portrait() { return "retrato.avel"; }
            @Override public String speakerName() { return "Avel"; }
            @Override public String text() { return "Fala que você quer saber. Mas pergunta uma coisa de cada vez."; }
            @Override public List<Choice> choices() { return opcoes; }
            @Override public boolean canContinue() { return false; }
            @Override public List<Line> history() { return historico; }
        };
    }
}
