package etrava.sim.rules;

/**
 * Efeitos sobre o estado do mundo (SPEC 4.1). O vocabulário cresce com os
 * sistemas: no M2 existem só os necessários para eventos agendados.
 */
public sealed interface Effect {
    record SetFlag(String name, boolean value) implements Effect {}

    record SetVar(String name, double value) implements Effect {}

    /** Escreve uma linha na crônica do mundo. */
    record Chronicle(String type, String ref, String text) implements Effect {}
}
