package etrava.sim.world;

/** Uma linha da crônica interna do mundo (RF-143): o que aconteceu, e quando. */
public record ChronicleEntry(long tick, String type, String ref, String text) {}
