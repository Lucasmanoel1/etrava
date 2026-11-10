package etrava.sim.content;

import java.util.List;

/**
 * Definição de um prop (props.yaml). Dimensões em metros.
 *
 * @param footprint largura (eixo x) e profundidade (eixo y) da base
 * @param height    altura aproximada (usada só por placeholders)
 * @param blocks    se impede a passagem
 * @param sprite    ID do asset; se omitido, vale {@code prop.<id>}
 * @param lights    luzes que o prop acende à noite (janelas, lamparina)
 */
public record PropDef(String id, String category, double[] footprint, double height, boolean blocks,
        String sprite, List<Light> lights) {

    /**
     * @param offset posição da luz relativa ao centro da base: x, y, z em metros
     * @param radius alcance em metros
     * @param color  r, g, b de 0 a 1
     */
    public record Light(double[] offset, double radius, double[] color) {}

    public double width() { return footprint != null && footprint.length > 0 ? footprint[0] : 1.0; }
    public double depth() { return footprint != null && footprint.length > 1 ? footprint[1] : width(); }
    public String spriteId() { return sprite != null ? sprite : "prop." + id; }
    public List<Light> lightList() { return lights != null ? lights : List.of(); }
}
