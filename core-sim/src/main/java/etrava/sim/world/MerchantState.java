package etrava.sim.world;

import java.util.Map;
import java.util.TreeMap;

/** O que muda num comerciante: caixa, estoque e o dia da última reposição. Vai para o save. */
public final class MerchantState {
    public int cash;
    /** Item → quantidade; itens que acabaram ficam com 0. */
    public final Map<String, Integer> stock = new TreeMap<>();
    public int lastRestockDay;

    public int qty(String item) { return stock.getOrDefault(item, 0); }
}
