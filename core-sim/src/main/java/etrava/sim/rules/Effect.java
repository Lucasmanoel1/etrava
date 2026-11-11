package etrava.sim.rules;

import java.util.List;

/**
 * Efeitos sobre o estado do mundo (SPEC 4.1). O vocabulário cresce com os
 * sistemas; cada tipo novo precisa de leitura/escrita em {@link Rules}.
 * Em {@code who}/{@code npc}, o valor "npc" significa "o NPC desta conversa".
 */
public sealed interface Effect {
    record SetFlag(String name, boolean value) implements Effect {}

    record SetVar(String name, double value) implements Effect {}

    record AddVar(String name, double value) implements Effect {}

    /**
     * Alguém passa a conhecer um fato.
     *
     * @param who       "player" ou um NPC
     * @param version   versão do fato; null = a primeira
     * @param certainty rumor | claim | witnessed | read | verified
     */
    record Learn(String who, String fact, String version, String certainty) implements Effect {}

    /** Muda a opinião de um NPC sobre o jogador. */
    record Opinion(String npc, int add) implements Effect {}

    /** Escreve uma linha na crônica do mundo. */
    record Chronicle(String type, String ref, String text) implements Effect {}

    /** Agenda outros efeitos para daqui a algumas horas de jogo (consequência tardia, RF-162). */
    record Schedule(double hours, boolean interrupts, List<Effect> effects) implements Effect {}
}
