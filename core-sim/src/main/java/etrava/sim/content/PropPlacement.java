package etrava.sim.content;

/** Um prop posicionado em uma área. {@code key} identifica props com variantes de estado. */
public record PropPlacement(String propId, String key, double x, double y, double rotation) {}
