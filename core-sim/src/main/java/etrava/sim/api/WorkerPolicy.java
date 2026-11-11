package etrava.sim.api;

import etrava.sim.content.ContentDb;
import etrava.sim.content.ItemDef;
import etrava.sim.content.JobDef;
import etrava.sim.content.MerchantDef;
import etrava.sim.economy.Economy;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Política de jogador headless {@code trabalhador} (SPEC 9): trabalha no que
 * houver, compra comida na taverna à tarde, come quando tem fome e dorme à noite.
 * Tudo por {@link Command}s, como um jogador faria — então a partida fica no
 * registro de comandos e pode ser reexecutada. Serve para provar que dá para
 * viver só trabalhando e comerciando (marco M5) e para o {@code gradlew simulate}.
 */
public final class WorkerPolicy {
    /** Come quando a fome passa disto. */
    public static final double EAT_AT = 50;
    /** Compra comida quando tem menos que isto de "comida guardada" (soma do quanto cada item sacia). */
    public static final int FOOD_RESERVE = 110;
    private static final int SHOP_FROM = 15, SLEEP_FROM = 22, WAKE_AT = 6;

    private final Simulation sim;
    private final ContentDb content;
    private final String shop;
    /** A maior fome vista entre uma decisão e outra (para os testes). */
    private double maxHunger;

    public WorkerPolicy(Simulation sim, String shop) {
        this.sim = sim;
        this.content = sim.content();
        this.shop = shop;
    }

    public double maxHunger() { return maxHunger; }

    /** Joga {@code days} dias de jogo. */
    public void run(int days) {
        long end = sim.view().clock().tick() + days * sim.view().clock().ticksPerDay();
        while (sim.view().clock().tick() < end) decide();
    }

    /** Uma decisão: comer, trabalhar, comprar, dormir ou esperar uma hora. */
    private void decide() {
        WorldView v = sim.view();
        maxHunger = Math.max(maxHunger, v.hunger());
        if (v.trade() != null) {
            sim.submit(new Command.CloseTrade());
            sim.step();
            return;
        }
        eat();
        int hour = v.clock().hour();
        for (JobDef job : content.jobs().values()) {
            if (sim.economy().canWork(job)) {
                sim.submit(new Command.Work(job.id()));
                sim.step();
                return;
            }
        }
        if (hour >= SHOP_FROM && hour < SLEEP_FROM && foodStored() < FOOD_RESERVE && shopOpen()) {
            shop();
            return;
        }
        if (hour >= SLEEP_FROM || hour < WAKE_AT - 1) {
            sim.submit(new Command.Sleep());
        } else {
            sim.submit(new Command.Wait(1));
        }
        sim.step();
    }

    /** Come o que sacia menos, até a fome baixar. Comer não gasta tempo. */
    private void eat() {
        while (sim.view().hunger() >= EAT_AT) {
            String food = sim.view().inventory().keySet().stream()
                    .map(id -> content.items().get(id)).filter(i -> i != null && i.food() > 0)
                    .min(Comparator.comparingInt(ItemDef::food)).map(ItemDef::id).orElse(null);
            if (food == null) return;
            sim.submit(new Command.UseItem(food));
            sim.stepPaused();
        }
    }

    private int foodStored() {
        int total = 0;
        for (Map.Entry<String, Integer> e : sim.view().inventory().entrySet()) {
            ItemDef item = content.items().get(e.getKey());
            if (item != null) total += item.food() * e.getValue();
        }
        return total;
    }

    private boolean shopOpen() {
        MerchantDef m = content.merchants().get(shop);
        return m != null && sim.economy().canTrade(m);
    }

    /** Compra o que der mais comida por moeda até ter reserva para um dia. */
    private void shop() {
        sim.submit(new Command.OpenTrade(shop));
        sim.step();
        for (int guard = 0; guard < 20 && foodStored() < FOOD_RESERVE; guard++) {
            TradeView t = sim.tradeView();
            if (t == null) break;
            TradeView.Offer best = bestFood(t.offers());
            if (best == null) break;
            sim.submit(new Command.Buy(best.item()));
            sim.stepPaused();
        }
        sim.submit(new Command.CloseTrade());
        sim.step();
    }

    private TradeView.Offer bestFood(List<TradeView.Offer> offers) {
        TradeView.Offer best = null;
        double bestRatio = 0;
        for (TradeView.Offer o : offers) {
            ItemDef item = content.items().get(o.item());
            if (!o.possible() || item == null || item.food() <= 0) continue;
            double ratio = item.food() / (double) o.price();
            if (ratio > bestRatio) {
                bestRatio = ratio;
                best = o;
            }
        }
        return best;
    }

    /** Atalho para quem só quer saber se a fome está sob controle. */
    public static boolean starving(WorldView v) {
        return v.hunger() >= Economy.STARVING;
    }
}
