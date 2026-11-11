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

    /** O NPC está presente, fora (away), foi embora (departed) ou morreu (dead). */
    record NpcStatus(String npc, String status) implements Condition {}

    /**
     * Estado de um arco de mundo.
     *
     * @param stages  o arco está num destes estágios (vazio = qualquer)
     * @param outcome o arco terminou com este desfecho (null = não importa)
     */
    record Arc(String id, List<String> stages, String outcome) implements Condition {}

    /** Um objeto do cenário com variantes está neste estado (árvore viva, morta, cortada). */
    record Prop(String key, String state) implements Condition {}

    /** O NPC guarda na memória um acontecimento deste tipo (e, se dito, com este sujeito). */
    record Remembers(String npc, String type, String subject) implements Condition {}

    /** Verdadeira com a probabilidade dada; usa o gerador da simulação. */
    record Chance(double probability) implements Condition {}

    /** O jogador tem o item (na mochila ou no corpo), na quantidade dada, e/ou ao menos este dinheiro. */
    record Has(String item, int qty, Integer money) implements Condition {}

    /** Habilidade do jogador dentro de [min, max] (RF-180). Escolha de diálogo com esta condição treina a habilidade. */
    record Skill(String name, Double min, Double max) implements Condition {}

    /** Reputação do jogador numa comunidade: média das opiniões dos membros presentes (RF-280). */
    record Reputation(String faction, Integer min, Integer max) implements Condition {}

    /** O trabalho pode começar agora: na janela de horário, com quem o dá disponível, ainda não feito hoje. */
    record Job(String id) implements Condition {}

    /** O jogador está nesta área agora ({@code {here: estrada}}). */
    record Here(String area) implements Condition {}
}
