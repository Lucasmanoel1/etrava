package etrava.sim.content;

import java.util.List;

/**
 * Figurante colocado num mapa (camada {@code spawns}, objeto de classe {@code figurante}).
 * Anda entre os pontos de {@code patrol}, parando um pouco em cada um.
 * Provisório: no M2 os NPCs passam a ter agenda própria em content/npcs/.
 */
public record ActorSpawn(String id, String sprite, double x, double y, List<String> patrol) {}
