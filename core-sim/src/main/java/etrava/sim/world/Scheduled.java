package etrava.sim.world;

import etrava.sim.rules.Effect;
import java.util.List;

/**
 * Efeitos marcados para um tick futuro (RF-162). A ordem de disparo é
 * (tick, seq): quem foi agendado primeiro dispara primeiro no mesmo tick.
 *
 * @param interrupts se true, interrompe quem estiver esperando ou dormindo (RF-323)
 */
public record Scheduled(long tick, long seq, boolean interrupts, List<Effect> effects) {}
