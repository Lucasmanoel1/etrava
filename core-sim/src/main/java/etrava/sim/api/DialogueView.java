package etrava.sim.api;

import java.util.List;

/** O que a tela de diálogo precisa mostrar. */
public interface DialogueView {
    /** Uma escolha visível. {@code tag} é o rótulo entre colchetes (ex.: "Lábia"), ou null. */
    record Choice(String text, String tag) {}

    /** Uma fala já dita nesta conversa. */
    record Line(String speaker, String text) {}

    String npcId();
    String npcName();
    /** ID do asset de retrato do NPC. */
    String portrait();
    String speakerName();
    String text();
    List<Choice> choices();
    /** Sem escolhas: a conversa segue (ou termina) quando o jogador avança. */
    boolean canContinue();
    List<Line> history();
}
