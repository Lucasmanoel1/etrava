package etrava.sim.content;

import java.util.List;
import java.util.Map;

/**
 * Pacote de origem (content/origins/*.yaml, PRD 8.4): com o que o personagem
 * começa. No M5 só dinheiro, itens, equipamento e habilidades; local inicial,
 * relações e conhecimentos entram no M7.
 *
 * @param equipment encaixe → item já vestido ou empunhado
 * @param skills    habilidade → valor inicial (as que faltarem começam em 0)
 */
public record OriginDef(String id, String name, int money, List<ItemDef.Stack> items, Map<String, String> equipment,
        Map<String, Double> skills) {

    public static final OriginDef EMPTY = new OriginDef("vazia", null, 0, List.of(), Map.of(), Map.of());

    public List<ItemDef.Stack> itemList() { return items != null ? items : List.of(); }
    public Map<String, String> equipmentMap() { return equipment != null ? equipment : Map.of(); }
    public Map<String, Double> skillMap() { return skills != null ? skills : Map.of(); }
}
