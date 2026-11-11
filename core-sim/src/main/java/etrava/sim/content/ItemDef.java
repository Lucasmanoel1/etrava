package etrava.sim.content;

import com.fasterxml.jackson.databind.JsonNode;
import etrava.sim.rules.Effect;
import etrava.sim.rules.Rules;
import java.util.List;
import java.util.Map;

/**
 * Um item (content/items.yaml, RF-202).
 *
 * @param weight   peso em kg
 * @param value    valor base em moedas, antes dos modificadores (RF-262)
 * @param category arma | armadura | ferramenta | consumivel | material | mercadoria | documento | especial
 * @param tags     marcas livres; os eventos mexem no preço por elas (E2 → {@code sal})
 * @param slot     onde se equipa: mainHand | offHand | body | head | accessory; null = não se equipa
 * @param food     quanto a fome cai ao comer; 0 = não se come
 * @param use      efeitos ao usar (comer, beber, ler); ver CONTENT.md
 * @param icon     ID do asset do ícone; se omitido, vale {@code icone.<id>}
 * @param stats    números para sistemas futuros (dano, proteção, luz — M6)
 */
public record ItemDef(String id, String name, String description, double weight, int value, String category,
        List<String> tags, String slot, int food, JsonNode use, String icon, Map<String, Double> stats) {

    public static final List<String> CATEGORIES = List.of("arma", "armadura", "ferramenta", "consumivel",
            "material", "mercadoria", "documento", "especial");
    public static final List<String> SLOTS = List.of("mainHand", "offHand", "body", "head", "accessory");

    /** Item e quantidade, como aparece em estoques, contêineres e na origem. */
    public record Stack(String item, int qty) {
        public int amount() { return qty > 0 ? qty : 1; }
    }

    public String displayName() { return name != null ? name : id; }
    public List<String> tagList() { return tags != null ? tags : List.of(); }
    public String iconId() { return icon != null ? icon : "icone." + id; }
    /** Dá para comer, beber ou usar: tem comida, efeitos de uso ou cura (stat {@code cura}, M6). */
    public boolean usable() { return food > 0 || stat("cura") > 0 || (use != null && !use.isNull() && use.size() > 0); }
    public List<Effect> useEffects() { return Rules.effects(use); }
    public double stat(String name) { return stats != null ? stats.getOrDefault(name, 0.0) : 0.0; }

    /** A categoria e as marcas: é por elas que os modificadores de preço pegam o item. */
    public boolean hasTag(String tag) { return tag.equals(category) || tagList().contains(tag); }
}
