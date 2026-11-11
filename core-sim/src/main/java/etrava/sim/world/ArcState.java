package etrava.sim.world;

/**
 * Onde um arco de mundo está (SPEC 3.5).
 *
 * @param deadline tick em que o prazo do estágio vence; -1 em estágio terminal
 */
public record ArcState(String stage, long enteredTick, long deadline) {}
