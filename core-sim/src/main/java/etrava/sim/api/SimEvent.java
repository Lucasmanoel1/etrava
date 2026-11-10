package etrava.sim.api;

/** O que a simulação avisa à apresentação a cada tick. Canal de mão única. */
public sealed interface SimEvent {

    /** O jogador chegou ao destino do último MoveTo. */
    record PlayerArrived(double x, double y) implements SimEvent {}

    /** O destino pedido era inalcançável; o jogador vai até o ponto mais próximo. */
    record DestinationAdjusted(double x, double y) implements SimEvent {}
}
