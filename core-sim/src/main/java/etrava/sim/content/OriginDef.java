package etrava.sim.content;

import java.util.List;
import java.util.Map;

/**
 * Pacote de origem (content/origins/*.yaml, PRD 8.4): com o que o personagem
 * começa: dinheiro, itens, equipamento, habilidades, onde acorda no primeiro dia,
 * o que já sabe e como as pessoas o veem.
 *
 * @param equipment encaixe → item já vestido ou empunhado
 * @param skills    habilidade → valor inicial (as que faltarem começam em 0)
 * @param start     onde o personagem começa, "area/ponto de chegada" (spawns do mapa)
 * @param knows     fatos que ele já sabe ao começar (fato ou fato:versão), como coisa vista
 * @param opinions  opinião inicial de NPCs sobre ele, somada à do arquivo do NPC
 * @param flags     flags ligadas no início (o que a origem é: "aprendiz_de_avel")
 */
public record OriginDef(String id, String name, String description, int money, List<ItemDef.Stack> items,
        Map<String, String> equipment, Map<String, Double> skills, String start, List<String> knows,
        Map<String, Integer> opinions, List<String> flags) {

    public static final OriginDef EMPTY = new OriginDef("vazia", null, null, 0, List.of(), Map.of(), Map.of(), null,
            List.of(), Map.of(), List.of());

    public List<String> knowList() { return knows != null ? knows : List.of(); }
    public Map<String, Integer> opinionMap() { return opinions != null ? opinions : Map.of(); }
    public List<String> flagList() { return flags != null ? flags : List.of(); }
    public String startArea() { return start == null || start.indexOf('/') < 0 ? start : start.substring(0, start.indexOf('/')); }
    public String startSpawn() { return start == null || start.indexOf('/') < 0 ? null : start.substring(start.indexOf('/') + 1); }

    public List<ItemDef.Stack> itemList() { return items != null ? items : List.of(); }
    public Map<String, String> equipmentMap() { return equipment != null ? equipment : Map.of(); }
    public Map<String, Double> skillMap() { return skills != null ? skills : Map.of(); }
}
