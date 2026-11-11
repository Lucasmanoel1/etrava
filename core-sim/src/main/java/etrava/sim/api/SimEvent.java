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
}
