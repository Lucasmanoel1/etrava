package etrava.sim.economy;

import etrava.sim.api.SimEvent;
import etrava.sim.api.TradeView;
import etrava.sim.content.ContainerDef;
import etrava.sim.content.ContentDb;
import etrava.sim.content.FactDef;
import etrava.sim.content.ItemDef;
import etrava.sim.content.JobDef;
import etrava.sim.content.MerchantDef;
import etrava.sim.content.NpcDef;
import etrava.sim.content.OriginDef;
import etrava.sim.content.SkillsDef;
import etrava.sim.story.Story;
import etrava.sim.world.ActorState;
import etrava.sim.world.ChronicleEntry;
import etrava.sim.world.KnowledgeEntry;
import etrava.sim.world.MerchantState;
import etrava.sim.world.TradeSession;
import etrava.sim.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A vida material do personagem (SPEC 4.8): mochila e equipamento, fome,
 * habilidades que sobem pelo uso, preços, comerciantes, contêineres com dono e
 * trabalhos. Trabalha sobre o {@link World}; não guarda estado próprio.
 */
public final class Economy {
    /** A fome vai de 0 a 100 em um dia sem comer. */
    public static final double HUNGER_PER_DAY = 100;
    /** Daqui para cima o personagem está com fome; com {@link #STARVING}, faminto (paga e aprende pela metade). */
    public static final double HUNGRY = 60, STARVING = 90;
    /** Carga que se leva sem ficar lento: base + por ponto de Vigor (RF-200). */
    public static final double BASE_CAPACITY = 25, CAPACITY_PER_VIGOR = 0.25;
    /** Fração da velocidade com carga demais. */
    public static final double OVERLOAD_SPEED = 0.6;
    /** O comerciante paga por um item esta fração do que cobraria. */
    public static final double SELL_RATIO = 0.4;
    /** Opinião ±100 mexe ±20% no preço; Comércio 100 dá até 25% (RF-262). */
    public static final double OPINION_WEIGHT = 1 / 500.0, COMMERCE_WEIGHT = 1 / 400.0;
    /** A habilidade do trabalho aumenta o pagamento em até 50%. */
    public static final double PAY_SKILL_BONUS = 0.5;
    /** Distância para mexer num contêiner. */
    public static final double CONTAINER_RANGE = 2.5;
    /** Ato registrado ao pegar de um contêiner com dono (deeds.yaml). */
    public static final String THEFT_DEED = "roubo";
    public static final String COMMERCE = "comercio", OBSERVATION = "observacao", VIGOR = "vigor";
    private static final String SLEEPING = "dormir", PLAYER = "player";

    private final ContentDb content;
    private final World world;
    private final Story story;

    public Economy(ContentDb content, World world, Story story) {
        this.content = content;
        this.world = world;
        this.story = story;
    }

    // ---------------------------------------------------------------- início

    /** Jogo novo: o que a origem dá ao personagem, e comerciantes e contêineres cheios. */
    public void newGame(OriginDef origin) {
        world.money = origin.money();
        for (ItemDef.Stack s : origin.itemList()) give(s.item(), s.amount());
        for (Map.Entry<String, String> e : origin.equipmentMap().entrySet()) {
            if (content.items().containsKey(e.getValue())) world.equipment.put(e.getKey(), e.getValue());
        }
        for (SkillsDef.Skill s : content.skills().list()) {
            world.skills.put(s.id(), clamp(origin.skillMap().getOrDefault(s.id(), 0.0)));
        }
        ensureState();
    }

    /** Comerciantes, contêineres e habilidades que o mundo ainda não conhece (jogo novo ou conteúdo mais novo que o save). */
    public void ensureState() {
        for (MerchantDef m : content.merchants().values()) {
            if (world.merchants.containsKey(m.id())) continue;
            MerchantState s = new MerchantState();
            s.cash = m.cash();
            for (MerchantDef.Entry e : m.stockList()) s.stock.put(e.item(), e.max());
            s.lastRestockDay = world.clock.day();
            world.merchants.put(m.id(), s);
        }
        for (ContainerDef c : content.containers().values()) {
            if (world.containers.containsKey(c.id())) continue;
            world.containers.put(c.id(), fill(c));
            world.containerRestock.put(c.id(), world.clock.day());
        }
        for (SkillsDef.Skill s : content.skills().list()) world.skills.putIfAbsent(s.id(), 0.0);
    }

    private static Map<String, Integer> fill(ContainerDef c) {
        Map<String, Integer> items = new TreeMap<>();
        for (ItemDef.Stack s : c.itemList()) items.merge(s.item(), s.amount(), Integer::sum);
        return items;
    }

    // ---------------------------------------------------------------- tempo

    /** A cada 10 minutos de jogo, a fome sobe um pouco. */
    public void tenMinutes() {
        world.hunger = Math.min(100, world.hunger + HUNGER_PER_DAY / 144);
    }

    /** Uma vez por dia (meia-noite): comerciantes e donos de contêineres repõem o que acabou (RF-261). */
    public void daily() {
        int day = world.clock.day();
        for (MerchantDef m : content.merchants().values()) {
            MerchantState s = world.merchants.get(m.id());
            if (s == null || day - s.lastRestockDay < m.restockEvery()) continue;
            restock(m, s);
            s.lastRestockDay = day;
        }
        for (ContainerDef c : content.containers().values()) {
            Integer last = world.containerRestock.get(c.id());
            if (c.restockDays() <= 0 || last == null || day - last < c.restockDays()) continue;
            Map<String, Integer> items = world.containers.computeIfAbsent(c.id(), k -> new TreeMap<>());
            for (ItemDef.Stack st : c.itemList()) items.merge(st.item(), st.amount(), Math::max);
            world.containerRestock.put(c.id(), day);
        }
    }

    /** Estoque de volta aos limites: o que caiu abaixo do mínimo volta ao máximo; o que passou do máximo some. */
    private static void restock(MerchantDef m, MerchantState s) {
        for (MerchantDef.Entry e : m.stockList()) {
            int qty = s.qty(e.item());
            if (qty < e.min() || qty > e.max()) s.stock.put(e.item(), e.max());
        }
        if (s.cash < m.cash()) s.cash = m.cash();
    }

    // ---------------------------------------------------------------- habilidades

    public double skill(String id) {
        return world.skills.getOrDefault(id, 0.0);
    }

    /**
     * Treina uma habilidade (RF-181/182): o ganho cai conforme ela sobe, pela curva
     * de content/skills.yaml, e cai pela metade com o personagem faminto. Devolve o ganho.
     */
    public double train(String id, double base) {
        if (content.skills().skill(id) == null || base <= 0) return 0;
        double value = skill(id);
        double gain = base * Math.pow(1 - value / 100, content.skills().curveExponent()) * (starving() ? 0.5 : 1);
        world.skills.put(id, clamp(value + gain));
        return gain;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }

    // ---------------------------------------------------------------- fome, mochila e corpo

    public boolean hungry() { return world.hunger >= HUNGRY; }
    public boolean starving() { return world.hunger >= STARVING; }

    /** Quantos deste item o jogador tem, na mochila e no corpo. */
    public int count(String itemId) {
        int n = world.inventory.getOrDefault(itemId, 0);
        for (String equipped : world.equipment.values()) if (equipped.equals(itemId)) n++;
        return n;
    }

    public void give(String itemId, int qty) {
        if (qty <= 0 || !content.items().containsKey(itemId)) return;
        world.inventory.merge(itemId, qty, Integer::sum);
    }

    /** Tira da mochila e, se faltar, do corpo. Devolve quantos tirou. */
    public int take(String itemId, int qty) {
        int taken = 0;
        while (taken < qty && world.inventory.getOrDefault(itemId, 0) > 0) {
            removeOne(itemId);
            taken++;
        }
        for (String slot : new ArrayList<>(world.equipment.keySet())) {
            if (taken < qty && world.equipment.get(slot).equals(itemId)) {
                world.equipment.remove(slot);
                taken++;
            }
        }
        return taken;
    }

    private void removeOne(String itemId) {
        int left = world.inventory.getOrDefault(itemId, 0) - 1;
        if (left > 0) world.inventory.put(itemId, left);
        else world.inventory.remove(itemId);
    }

    /** Peso de tudo o que o personagem leva, na mochila e no corpo. */
    public double weight() {
        double total = 0;
        for (Map.Entry<String, Integer> e : world.inventory.entrySet()) total += itemWeight(e.getKey()) * e.getValue();
        for (String item : world.equipment.values()) total += itemWeight(item);
        return total;
    }

    private double itemWeight(String itemId) {
        ItemDef def = content.items().get(itemId);
        return def == null ? 0 : def.weight();
    }

    /** Quanto se leva sem ficar lento: base, mais Vigor, mais o que o equipamento ajuda a carregar (stat {@code carga}). */
    public double capacity() {
        double extra = 0;
        for (String item : world.equipment.values()) {
            ItemDef def = content.items().get(item);
            if (def != null) extra += def.stat("carga");
        }
        return BASE_CAPACITY + CAPACITY_PER_VIGOR * skill(VIGOR) + extra;
    }

    /** Carga demais: o personagem anda devagar (RF-200). */
    public boolean overloaded() { return weight() > capacity(); }

    /** Come, bebe ou usa um item da mochila. Devolve false se não deu. */
    public boolean use(String itemId, List<SimEvent> events) {
        ItemDef def = content.items().get(itemId);
        if (def == null || !def.usable() || world.inventory.getOrDefault(itemId, 0) <= 0) return false;
        if (!def.document()) removeOne(itemId); // ler não gasta o papel
        if (def.food() > 0) world.hunger = Math.max(0, world.hunger - def.food());
        story.applyAll(def.useEffects(), null, "item:" + itemId, events);
        events.add(new SimEvent.ItemUsed(itemId));
        return true;
    }

    /** Veste ou empunha um item da mochila; o que estava no encaixe volta para a mochila. */
    public boolean equip(String itemId) {
        ItemDef def = content.items().get(itemId);
        if (def == null || def.slot() == null || world.inventory.getOrDefault(itemId, 0) <= 0) return false;
        removeOne(itemId);
        String old = world.equipment.put(def.slot(), itemId);
        if (old != null) give(old, 1);
        return true;
    }

    public boolean unequip(String slot) {
        String old = world.equipment.remove(slot);
        if (old == null) return false;
        give(old, 1);
        return true;
    }

    // ---------------------------------------------------------------- reputação

    /**
     * Reputação numa comunidade (RF-280): a média das opiniões dos membros presentes.
     * Não é um número onisciente — só muda quando cada pessoa muda de ideia, e cada uma
     * só muda pelo que viu ou ouviu (RF-281). Comunidade sem ninguém presente: 0.
     */
    public int reputation(String faction) {
        int sum = 0, n = 0;
        for (NpcDef def : content.npcs().values()) {
            ActorState npc = world.npcs.get(def.id());
            if (!faction.equals(def.faction()) || npc == null || !npc.present()) continue;
            sum += world.opinion(def.id());
            n++;
        }
        return n == 0 ? 0 : Math.round((float) sum / n);
    }

    // ---------------------------------------------------------------- preços

    /** Modificador regional (RF-262/263): o produto dos modificadores da categoria e das marcas do item. */
    public double regional(ItemDef item) {
        double mod = world.priceMod(item.category());
        for (String tag : item.tagList()) mod *= world.priceMod(tag);
        return mod;
    }

    /** Um evento mexe no preço de uma categoria ou marca (efeito {@code priceMod}). */
    public void priceMod(String tag, double factor) {
        if (factor <= 0) return;
        world.priceMods.put(tag, world.priceMod(tag) * factor);
        world.chronicle.add(new ChronicleEntry(world.clock.tick(), "preco", tag,
                String.format(java.util.Locale.ROOT, "preço de '%s' agora ×%.2f", tag, world.priceMod(tag))));
    }

    private double opinionFactor(MerchantDef m) {
        return world.opinion(m.npc()) * OPINION_WEIGHT;
    }

    /** Quanto o comerciante cobra: valor × regional × opinião × Comércio (RF-262). */
    public int buyPrice(MerchantDef m, ItemDef item) {
        double price = item.value() * regional(item) * (1 - opinionFactor(m)) * (1 - skill(COMMERCE) * COMMERCE_WEIGHT);
        return Math.max(1, (int) Math.round(price));
    }

    /**
     * Quanto o comerciante paga ao jogador. Sempre menos do que cobraria: não há como
     * lucrar indo e voltando. Coisa barata demais vale 0 — ele não compra.
     */
    public int sellPrice(MerchantDef m, ItemDef item) {
        double price = item.value() * regional(item) * SELL_RATIO * (1 + opinionFactor(m))
                * (1 + skill(COMMERCE) * COMMERCE_WEIGHT);
        return Math.max(0, (int) Math.floor(price));
    }

    /** O efeito da opinião no preço de compra, em %, para a tela de comércio. */
    public int opinionPercent(MerchantDef m) {
        return (int) Math.round(-opinionFactor(m) * 100);
    }

    // ---------------------------------------------------------------- comércio e contêineres

    /** O comerciante está atendendo: presente, acordado, à vista, na área do jogador, e quer negociar. */
    public boolean canTrade(MerchantDef m) {
        ActorState npc = world.npcs.get(m.npc());
        return npc != null && npc.present() && !npc.hidden && !SLEEPING.equals(npc.activity)
                && world.areaId.equals(npc.areaId) && story.test(m.condition(), m.npc());
    }

    public boolean openTrade(String merchantId, List<SimEvent> events) {
        MerchantDef m = content.merchants().get(merchantId);
        if (m == null || !canTrade(m)) {
            if (m != null) events.add(new SimEvent.AmbientLine(null, m.npc(),
                    content.text("comercio.recusa", "Hoje não.")));
            return false;
        }
        world.trade = new TradeSession(merchantId, false);
        events.add(new SimEvent.TradeOpened(merchantId, false));
        return true;
    }

    /** Ponto do mapa onde fica o contêiner, ou null se ele não está na área carregada. */
    public double[] containerPoint(ContainerDef c) {
        if (!c.wellFormed() || !c.area().equals(world.areaId) || content.area(c.area()) == null) return null;
        if (!story.test(c.condition(), c.owner())) return null; // ainda não está lá (ou não está mais)
        var p = content.area(c.area()).spots().get(c.spot());
        return p == null ? null : new double[] {p.x(), p.y()};
    }

    public boolean nearContainer(ContainerDef c) {
        double[] p = containerPoint(c);
        return p != null && Math.hypot(p[0] - world.player.x, p[1] - world.player.y) <= CONTAINER_RANGE;
    }

    public boolean openContainer(String containerId, List<SimEvent> events) {
        ContainerDef c = content.containers().get(containerId);
        if (c == null || !nearContainer(c)) return false;
        world.trade = new TradeSession(containerId, true);
        events.add(new SimEvent.TradeOpened(containerId, true));
        return true;
    }

    public void closeTrade(List<SimEvent> events) {
        if (world.trade == null) return;
        events.add(new SimEvent.TradeClosed(world.trade.partnerId()));
        world.trade = null;
    }

    /** Compra uma unidade do comerciante aberto. */
    public boolean buy(String itemId) {
        MerchantDef m = openMerchant();
        ItemDef item = content.items().get(itemId);
        if (m == null || item == null) return false;
        MerchantState s = world.merchants.get(m.id());
        int price = buyPrice(m, item);
        if (s.qty(itemId) <= 0 || world.money < price) return false;
        s.stock.put(itemId, s.qty(itemId) - 1);
        s.cash += price;
        world.money -= price;
        give(itemId, 1);
        train(COMMERCE, 0.4);
        return true;
    }

    /** Vende uma unidade ao comerciante aberto, se ele compra esse tipo de coisa e tem caixa. */
    public boolean sell(String itemId) {
        MerchantDef m = openMerchant();
        ItemDef item = content.items().get(itemId);
        if (m == null || item == null || !m.buys(item) || world.inventory.getOrDefault(itemId, 0) <= 0) return false;
        MerchantState s = world.merchants.get(m.id());
        int price = sellPrice(m, item);
        if (price <= 0 || s.cash < price) return false;
        removeOne(itemId);
        s.cash -= price;
        s.stock.merge(itemId, 1, Integer::sum);
        world.money += price;
        train(COMMERCE, 0.4);
        return true;
    }

    /**
     * Pega uma unidade do contêiner aberto. Se ele tem dono, é roubo: quem estiver
     * vendo muda de opinião e passa a contar (RF-204, RF-089). Ninguém vendo, ninguém sabe.
     */
    public boolean takeFromContainer(String itemId, List<SimEvent> events) {
        if (world.trade == null || !world.trade.container()) return false;
        ContainerDef c = content.containers().get(world.trade.partnerId());
        Map<String, Integer> items = world.containers.get(c.id());
        if (items == null || items.getOrDefault(itemId, 0) <= 0) return false;
        items.put(itemId, items.get(itemId) - 1);
        give(itemId, 1);
        if (c.owner() != null) {
            int seen = story.deed(THEFT_DEED);
            if (seen > 0) events.add(new SimEvent.DeedSeen(THEFT_DEED, seen));
        }
        return true;
    }

    private MerchantDef openMerchant() {
        if (world.trade == null || world.trade.container()) return null;
        MerchantDef m = content.merchants().get(world.trade.partnerId());
        return m != null && world.merchants.containsKey(m.id()) ? m : null;
    }

    /** O comércio ou contêiner aberto, com os preços de agora; null se nada está aberto. */
    public TradeView tradeView() {
        TradeSession t = world.trade;
        if (t == null) return null;
        List<TradeView.Offer> offers = new ArrayList<>(), wants = new ArrayList<>();
        if (t.container()) {
            ContainerDef c = content.containers().get(t.partnerId());
            if (c == null) return null;
            for (Map.Entry<String, Integer> e : world.containers.getOrDefault(c.id(), Map.of()).entrySet()) {
                ItemDef item = content.items().get(e.getKey());
                if (item != null && e.getValue() > 0)
                    offers.add(new TradeView.Offer(item.id(), item.displayName(), e.getValue(), 0, true));
            }
            NpcDef owner = c.owner() == null ? null : content.npcs().get(c.owner());
            return new TradeView(c.id(), c.displayName(), true, -1, 0, offers, wants,
                    owner == null ? null : owner.displayName());
        }
        MerchantDef m = content.merchants().get(t.partnerId());
        MerchantState s = world.merchants.get(t.partnerId());
        if (m == null || s == null) return null;
        for (MerchantDef.Entry e : m.stockList()) {
            ItemDef item = content.items().get(e.item());
            if (item == null || s.qty(e.item()) <= 0) continue;
            int price = buyPrice(m, item);
            offers.add(new TradeView.Offer(item.id(), item.displayName(), s.qty(e.item()), price, world.money >= price));
        }
        for (Map.Entry<String, Integer> e : world.inventory.entrySet()) {
            ItemDef item = content.items().get(e.getKey());
            if (item == null || !m.buys(item)) continue;
            int price = sellPrice(m, item);
            wants.add(new TradeView.Offer(item.id(), item.displayName(), e.getValue(), price, price > 0 && s.cash >= price));
        }
        NpcDef npc = content.npcs().get(m.npc());
        return new TradeView(m.id(), npc != null ? npc.displayName() : m.id(), false, s.cash, opinionPercent(m),
                offers, wants, null);
    }

    // ---------------------------------------------------------------- trabalho

    /**
     * O trabalho pode começar agora (RF-264): dentro da janela de horário, ainda não
     * feito hoje, com quem dá o trabalho presente, acordado, à vista e na mesma área —
     * e disposto (condição do arquivo; ex.: não oferece a quem ele detesta, RF-282).
     */
    public boolean canWork(JobDef job) {
        if (job == null || !job.wellFormed()) return false;
        if (!job.opensAt(world.clock.minuteOfDay())) return false;
        if (world.jobDay.getOrDefault(job.id(), 0) == world.clock.day()) return false;
        ActorState giver = world.npcs.get(job.giver());
        if (giver == null || !giver.present() || giver.hidden || SLEEPING.equals(giver.activity)
                || !world.areaId.equals(giver.areaId)) return false;
        return story.test(job.condition(), job.giver());
    }

    /**
     * Fim do trabalho: paga pelas horas trabalhadas, treina as habilidades, aplica os
     * efeitos do arquivo e, às vezes, quem deu o trabalho conta uma novidade — o
     * trabalho é também onde se ouvem coisas (PRD 7.2).
     *
     * @param hungry o personagem começou o trabalho faminto (rende metade)
     * @return o resultado, para a apresentação
     */
    public SimEvent.JobFinished finishJob(JobDef job, double hoursWorked, boolean hungry, boolean interrupted,
            List<SimEvent> events) {
        double fraction = Math.max(0, Math.min(1, hoursWorked / job.hours()));
        double skillBonus = job.paySkill() == null ? 0 : skill(job.paySkill()) / 100 * PAY_SKILL_BONUS;
        int pay = (int) Math.round(job.pay() * fraction * (1 + skillBonus) * (hungry ? 0.5 : 1));
        world.money += pay;
        world.jobDay.put(job.id(), world.clock.day());
        for (Map.Entry<String, Double> t : job.trainMap().entrySet()) train(t.getKey(), t.getValue() * hoursWorked);
        if (fraction >= 0.99) story.applyAll(job.finishEffects(), job.giver(), "npc:" + job.giver(), events);
        String heard = null;
        if (job.gossipChance() > 0 && world.rng.chance(job.gossipChance())) {
            KnowledgeEntry news = story.news(job.giver());
            if (news != null) {
                FactDef fact = content.facts().get(news.fact());
                story.learn(PLAYER, news.fact(), news.version(), "claim", "npc:" + job.giver(), events);
                heard = fact.text(news.version());
            }
        }
        world.chronicle.add(new ChronicleEntry(world.clock.tick(), "trabalho", job.id(),
                String.format(java.util.Locale.ROOT, "%s: %.1f h, %d moedas", job.displayName(), hoursWorked, pay)));
        return new SimEvent.JobFinished(job.id(), hoursWorked, pay, interrupted, job.giver(), heard);
    }
}
