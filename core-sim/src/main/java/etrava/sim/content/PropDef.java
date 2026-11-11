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
 * @param states    variantes (RF-043): estado → ID do asset; o primeiro é o estado inicial.
 *                  Só vale para props posicionados no mapa com uma {@code key}.
 */
public record PropDef(String id, String category, double[] footprint, double height, boolean blocks,
        String sprite, List<Light> lights, java.util.LinkedHashMap<String, String> states) {

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
    public java.util.Map<String, String> stateMap() { return states != null ? states : java.util.Map.of(); }

    /** Estado em que o prop começa, ou null se ele não tem variantes. */
    public String initialState() { return stateMap().isEmpty() ? null : stateMap().keySet().iterator().next(); }

    /** Asset a desenhar num estado; cai no estado inicial (ou no sprite normal) se o estado não existir. */
    public String spriteFor(String state) {
        String id = state == null ? null : stateMap().get(state);
        return id != null ? id : stateMap().isEmpty() ? spriteId() : stateMap().values().iterator().next();
    }
}
