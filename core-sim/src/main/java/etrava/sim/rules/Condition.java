package etrava.sim.rules;

import java.util.List;

/**
 * Condições sobre o estado do mundo (SPEC 4.1). São dados, não script: o
 * validador consegue analisá-las sem executar nada.
 * Em {@code who}/{@code npc}, o valor "npc" significa "o NPC desta conversa".
 */
public sealed interface Condition {
    /** Sempre verdadeira; é o que vale quando o conteúdo não declara condição. */
    Condition ALWAYS = new All(List.of());

    record All(List<Condition> of) implements Condition {}

    record Any(List<Condition> of) implements Condition {}

    record Not(Condition of) implements Condition {}

    record Flag(String name, boolean value) implements Condition {}

    /** Variável numérica dentro de [min, max]; limites ausentes não restringem. */
    record Var(String name, Double min, Double max) implements Condition {}

    /** Alguém ("player" ou um NPC) conhece o fato, em qualquer versão. */
    record Knows(String who, String fact) implements Condition {}

    /** Opinião do NPC sobre o jogador dentro de [min, max]. */
    record Opinion(String npc, Integer min, Integer max) implements Condition {}

    /** Dia e hora do jogo; a faixa de horas pode atravessar a meia-noite. */
    record Time(Integer dayMin, Integer dayMax, Double hourFrom, Double hourTo) implements Condition {}

    /** O NPC está numa área. */
    record NpcIn(String npc, String area) implements Condition {}

    /** Verdadeira com a probabilidade dada; usa o gerador da simulação. */
    record Chance(double probability) implements Condition {}
}
