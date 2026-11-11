package etrava.sim.content;

import com.fasterxml.jackson.databind.JsonNode;
import etrava.sim.rules.Condition;
import etrava.sim.rules.Rules;
import java.util.List;

/**
 * Um comerciante (content/merchants.yaml, RF-261): quem vende, o que tem, quanto
 * dinheiro guarda e de quanto em quanto tempo repõe.
 *
 * @param npc         o NPC que atende; só negocia acordado, à vista e na mesma área do jogador
 * @param cash        dinheiro em caixa; volta a esse valor na reposição se tiver caído abaixo
 * @param stock       o que vende: item, mínimo e máximo do estoque
 * @param buys        categorias ou marcas que ele compra do jogador
 * @param restockDays de quantos em quantos dias repõe o estoque
 * @param when        condição para aceitar negociar (com "npc" = o próprio comerciante); null = sempre
 */
public record MerchantDef(String id, String npc, int cash, List<Entry> stock, List<String> buys, int restockDays,
        JsonNode when) {

    /** @param min estoque abaixo disso é reposto; @param max teto do estoque (o excedente some na reposição) */
    public record Entry(String item, int min, int max) {}

    public List<Entry> stockList() { return stock != null ? stock : List.of(); }
    public List<String> buysList() { return buys != null ? buys : List.of(); }
    public int restockEvery() { return restockDays > 0 ? restockDays : 3; }
    public Condition condition() { return Rules.condition(when); }

    public Entry entry(String itemId) {
        for (Entry e : stockList()) if (e.item().equals(itemId)) return e;
        return null;
    }

    /** Compra este item do jogador? */
    public boolean buys(ItemDef item) {
        for (String tag : buysList()) if (item.hasTag(tag)) return true;
        return false;
    }
}
