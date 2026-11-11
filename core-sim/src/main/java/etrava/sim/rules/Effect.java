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

    /** Troca a variante de um objeto do cenário (RF-043). */
    record SetProp(String key, String state) implements Effect {}

    /** Leva um arco de mundo direto para um estágio. */
    record Arc(String id, String to) implements Effect {}

    /** Muda a situação de um NPC: present | away | departed | dead (RF-087). */
    record NpcStatus(String npc, String status) implements Effect {}

    /** O NPC guarda um acontecimento na memória (RF-084). */
    record Remember(String npc, String type, String subject) implements Effect {}

    /** O jogador comete um ato (content/deeds.yaml); só pesa para quem viu ou ficou sabendo (RF-089, RF-281). */
    record Deed(String id) implements Effect {}

    /** O jogador recebe um item (item != null) e/ou dinheiro. */
    record Give(String item, int qty, int money) implements Effect {}

    /** O jogador perde um item e/ou dinheiro (só até o que tem). */
    record Take(String item, int qty, int money) implements Effect {}

    /** Multiplica o modificador de preço dos itens com esta categoria ou marca (RF-263; E2 → sal). */
    record PriceMod(String tag, double factor) implements Effect {}

    /** Treina uma habilidade do jogador; o ganho real diminui conforme ela sobe (RF-182). */
    record Train(String skill, double amount) implements Effect {}

    /** Começa um trabalho de content/jobs.yaml assim que a conversa acabar. */
    record StartJob(String id) implements Effect {}

    /** Abre o comércio de content/merchants.yaml assim que a conversa acabar. */
    record StartTrade(String id) implements Effect {}

    /** Inicia um encontro de content/encounters.yaml (RF-102, "iniciar combate"), se o jogador estiver na área dele. */
    record Encounter(String id) implements Effect {}
}
