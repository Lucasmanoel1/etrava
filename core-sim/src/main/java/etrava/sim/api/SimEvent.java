package etrava.sim.api;

/** O que a simulação avisa à apresentação a cada tick. Canal de mão única. */
public sealed interface SimEvent {

    /** O jogador chegou ao destino do último MoveTo. */
    record PlayerArrived(double x, double y) implements SimEvent {}

    /** O destino pedido era inalcançável; o jogador vai até o ponto mais próximo. */
    record DestinationAdjusted(double x, double y) implements SimEvent {}

    /**
     * O tempo saltou (esperar ou dormir).
     *
     * @param slept       true se foi sono; false se foi espera
     * @param interrupted true se algo interrompeu antes da hora prevista
     */
    record TimeSkipped(long fromTick, long toTick, boolean slept, boolean interrupted) implements SimEvent {}

    /** O caderno ganhou uma entrada nova. */
    record FactLearned(String factId, String version) implements SimEvent {}

    record DialogueOpened(String npcId) implements SimEvent {}

    record DialogueClosed(String npcId) implements SimEvent {}

    /** Fala de uma conversa ambiente (ou resmungo de um NPC), para legenda sobre quem fala (RF-088). */
    record AmbientLine(String ambientId, String npcId, String text) implements SimEvent {}

    /** O jogador mudou de área; a apresentação precisa recarregar o cenário. */
    record AreaChanged(String fromArea, String toArea) implements SimEvent {}

    /** Primeira vez num lugar nomeado (RF-007): vai para o caderno e para o mapa. */
    record PlaceDiscovered(String place, String areaId) implements SimEvent {}

    /** Um objeto do cenário mudou de variante (a árvore brotou, foi cortada…). */
    record PropChanged(String key, String state) implements SimEvent {}

    /** O capítulo terminou: a apresentação mostra o epílogo (RF-163). O jogo continua depois. */
    record ChapterEnded(java.util.List<String> epilogue) implements SimEvent {}

    /** Abriu um comércio ou um contêiner; o relógio fica parado até fechar (RF-324). */
    record TradeOpened(String partnerId, boolean container) implements SimEvent {}

    record TradeClosed(String partnerId) implements SimEvent {}

    /** O jogador comeu, bebeu ou usou um item. */
    record ItemUsed(String itemId) implements SimEvent {}

    /** Alguém viu o jogador fazer o que não devia. */
    record DeedSeen(String deedId, int witnesses) implements SimEvent {}

    /**
     * Um trabalho terminou (ou foi interrompido).
     *
     * @param heard texto da novidade que quem deu o trabalho contou, ou null
     */
    record JobFinished(String jobId, double hours, int pay, boolean interrupted, String giver, String heard)
            implements SimEvent {}

    /** O trabalho não pôde começar (fora de hora, quem dá o trabalho não está, já foi feito hoje). */
    record JobRefused(String jobId) implements SimEvent {}

    // ---- combate e perigo (M6)

    /**
     * Um golpe. {@code attacker}/{@code target} são "jogador" ou o ID de um inimigo (ou de um NPC agredido).
     *
     * @param damage 0 = errou (ou esquivou)
     */
    record Hit(String attacker, String target, double damage, boolean heavy) implements SimEvent {}

    /** Inimigos apareceram na área (um encontro começou). */
    record EnemiesAppeared(String encounterId, int count) implements SimEvent {}

    /**
     * Um inimigo mudou de situação: fugiu, rendeu, morreu, poupado, saqueado, executado, desistiu, foi_embora.
     *
     * @param line fala dele nesse momento, ou null
     */
    record EnemyChanged(String enemyId, String what, String line) implements SimEvent {}

    /** O jogador pegou o que um inimigo levava. */
    record Looted(String enemyId, java.util.List<String> items, int money) implements SimEvent {}

    /** Não dá para fazer isso agora (em combate, sem fôlego, recarga). {@code reason}: chave de texto da interface. */
    record Refused(String reason) implements SimEvent {}

    /**
     * Derrota não letal (RF-340): o personagem acorda horas depois, talvez roubado, e o mundo andou.
     *
     * @param itemsLost itens levados (IDs)
     * @param finder    NPC que o encontrou, ou null
     */
    record PlayerDefeated(long fromTick, long toTick, int moneyLost, java.util.List<String> itemsLost,
            String wakeArea, String finder, String by) implements SimEvent {}

    /** Morte de verdade (RF-341): a apresentação mostra o que aconteceu com o mundo e oferece carregar. */
    record PlayerDied(String by, java.util.List<String> summary) implements SimEvent {}

    /** O jogador se rendeu; os saqueadores levaram isto. */
    record PlayerSurrendered(int moneyLost) implements SimEvent {}
}
