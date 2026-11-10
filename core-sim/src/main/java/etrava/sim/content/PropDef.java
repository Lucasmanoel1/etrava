package etrava.sim.content;

/**
 * Definição de um prop (props.yaml). Dimensões em metros.
 *
 * @param footprint largura (eixo x) e profundidade (eixo y) da base
 * @param height    altura, usada pela apresentação
 * @param blocks    se impede a passagem
 */
public record PropDef(String id, String category, double[] footprint, double height, boolean blocks) {
    public double width() { return footprint != null && footprint.length > 0 ? footprint[0] : 1.0; }
    public double depth() { return footprint != null && footprint.length > 1 ? footprint[1] : width(); }
}
