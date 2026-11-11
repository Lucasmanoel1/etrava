package etrava.sim.world;

/**
 * Uma lembrança de um NPC (RF-084): o que aconteceu, com quem, quando e onde.
 *
 * @param type    tipo do acontecimento ("roubo", "ouviu:roubo", "ajuda"…)
 * @param subject de quem se trata: "player" ou o ID de um NPC
 */
public record MemoryEntry(String type, String subject, int day, String place) {}
