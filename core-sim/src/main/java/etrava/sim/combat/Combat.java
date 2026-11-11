package etrava.sim.combat;

import etrava.sim.api.SimEvent;
import etrava.sim.content.AreaDef;
import etrava.sim.content.ContentDb;
import etrava.sim.content.EncounterDef;
import etrava.sim.content.EnemyDef;
import etrava.sim.content.ItemDef;
import etrava.sim.economy.Economy;
import etrava.sim.nav.NavGrid;
import etrava.sim.nav.Pathfinder;
import etrava.sim.nav.Pathfinder.Point;
import etrava.sim.story.Story;
import etrava.sim.time.GameClock;
import etrava.sim.world.ActorState;
import etrava.sim.world.ChronicleEntry;
import etrava.sim.world.EnemyState;
import etrava.sim.world.World;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Combate e perigo (SPEC 4.8, PRD 7.3): em tempo real, só na área carregada.
 * Vida e fôlego; golpe, esquiva e duas habilidades com recarga; inimigos que
 * perseguem, golpeiam com aviso, desistem, fogem ou se rendem; encontros por
 * área e hora; a luz que o personagem leva no escuro.
 *
 * <p>A derrota em si (acordar horas depois, ou morrer) mexe no tempo e na área,
 * e por isso fica com a {@code Simulation}; aqui só se decide se houve e quem venceu.
 * Trabalha sobre o {@link World}; não guarda estado próprio.
 */
public final class Combat {
    public static final String PLAYER = "jogador";
    public static final String HEAVY = "golpe_pesado", PUSH = "empurrao";
    public static final List<String> ABILITIES = List.of(HEAVY, PUSH);
    public static final String SPARE = "poupar", ROB = "saquear", EXECUTE = "executar";
    /** Atos (deeds.yaml) que o combate dispara. */
    public static final String EXECUTION_DEED = "execucao", AGGRESSION_DEED = "agressao";
    /** Variável com quantos de cada inimigo o jogador abateu: {@code abatidos.<tipo>}. */
    public static final String KILLS_VAR = "abatidos.";
    public static final String FIGHTING = "luta";
    /** Duração de um tick em segundos reais (o mesmo da Simulation). */
    private static final double DT = 1.0 / GameClock.TICKS_PER_SECOND;

    /** Vida e fôlego: base + por ponto de Vigor. */
    public static final double BASE_HEALTH = 100, BASE_STAMINA = 100, PER_VIGOR = 0.5;
    /** Custo de fôlego. */
    public static final double ATTACK_COST = 10, HEAVY_COST = 30, PUSH_COST = 20, DODGE_COST = 22;
    /** Fôlego que volta por segundo, depois de um respiro sem esforço. */
    public static final double STAMINA_REGEN = 24, REGEN_DELAY = 0.7;
    /** Vida que volta a cada 10 minutos de jogo fora de combate (sem estar faminto). */
    public static final double HEAL_PER_10_MIN = 3;
    public static final double FIST_DAMAGE = 2, PLAYER_REACH = 1.6;
    public static final double HEAVY_MULT = 2.2, HEAVY_COOLDOWN = 6, PUSH_COOLDOWN = 8;
    public static final double PUSH_DISTANCE = 2.5, PUSH_STAGGER = 1.4;
    public static final double DODGE_DISTANCE = 2.6, DODGE_TIME = 0.3;
    /** Distância para resolver o destino de um rendido, render-se, ou empurrar um NPC. */
    public static final double RESOLVE_RANGE = 4, SURRENDER_RANGE = 9, STRIKE_RANGE = 2.2;
    /** Fugitivos e quem vai embora somem depois disto, ou quando ficam longe. */
    public static final double FLEE_TIME = 6, GONE_DISTANCE = 16;
    /** Raio de luz (m): no escuro de uma ruína, numa noite aberta, e o que cada ponto de "luz" do equipamento soma. */
    public static final double DARK_LIGHT = 1.5, NIGHT_LIGHT = 4.5, LIGHT_PER_POINT = 0.75;
    public static final int NIGHT_FROM = 20, NIGHT_TO = 5;
    /** O que os saqueadores levam: esta fração do dinheiro e até tantos itens da mochila. */
    public static final double ROB_FRACTION = 0.6;
    public static final int ROB_ITEMS = 2;

    private final ContentDb content;
    private final World world;
    private final Story story;
    private final Economy economy;
    private final Supplier<NavGrid> nav;
    private final Supplier<AreaDef> area;

    public Combat(ContentDb content, World world, Story story, Economy economy, Supplier<NavGrid> nav,
            Supplier<AreaDef> area) {
        this.content = content;
        this.world = world;
        this.story = story;
        this.economy = economy;
        this.nav = nav;
        this.area = area;
    }

    // ---------------------------------------------------------------- números do personagem

    public double maxHealth() { return BASE_HEALTH + PER_VIGOR * economy.skill(Economy.VIGOR); }
    public double maxStamina() { return BASE_STAMINA + PER_VIGOR * economy.skill(Economy.VIGOR); }

    private double luta() { return economy.skill(FIGHTING); }

    /** Soma de um número dos itens equipados (dano, protecao, luz). */
    public double equipped(String stat) {
        double total = 0;
        for (String item : world.equipment.values()) {
            ItemDef def = content.items().get(item);
            if (def != null) total += def.stat(stat);
        }
        return total;
    }

    /** Dano da arma na mão (ou do punho). */
    public double weaponDamage() {
        String item = world.equipment.get("mainHand");
        ItemDef def = item == null ? null : content.items().get(item);
        return def != null && def.stat("dano") > 0 ? def.stat("dano") : FIST_DAMAGE;
    }

    /** Segundos entre golpes: armas mais pesadas são mais lentas. */
    public double attackInterval() {
        String item = world.equipment.get("mainHand");
        ItemDef def = item == null ? null : content.items().get(item);
        return 0.5 + (def != null ? def.weight() * 0.12 : 0);
    }

    public long cooldownLeft(String ability) {
        return Math.max(0, world.cooldowns.getOrDefault(ability, 0L) - world.clock.tick());
    }

    // ---------------------------------------------------------------- luz e escuridão (RF-008)

    public boolean darkArea() {
        ContentDb.AreaEntry e = content.areaEntry(world.areaId);
        return e != null && e.dark();
    }

    public boolean night() {
        int h = world.clock.hour();
        return h >= NIGHT_FROM || h < NIGHT_TO;
    }

    /** Raio que o personagem enxerga, em metros; infinito com luz do dia. A lamparina aumenta. */
    public double lightRadius() {
        if (!darkArea() && !night()) return Double.POSITIVE_INFINITY;
        return (darkArea() ? DARK_LIGHT : NIGHT_LIGHT) + equipped("luz") * LIGHT_PER_POINT;
    }

    private boolean carriesLight() { return equipped("luz") > 0; }

    // ---------------------------------------------------------------- estado geral

    private EnemyDef def(EnemyState e) { return content.enemies().get(e.type); }

    private double dist(EnemyState e) { return Math.hypot(e.x - world.player.x, e.y - world.player.y); }

    /** Há alguém lutando contra o jogador: não dá para esperar, dormir, trabalhar, conversar nem salvar. */
    public boolean inCombat() {
        for (EnemyState e : world.enemies) {
            EnemyDef d = def(e);
            if (d == null || !e.hostile()) continue;
            if (EnemyState.CHASE.equals(e.state) || EnemyState.WINDUP.equals(e.state) || dist(e) <= d.aggroOr())
                return true;
        }
        return false;
    }

    public EnemyState enemy(String id) {
        for (EnemyState e : world.enemies) if (e.id.equals(id)) return e;
        return null;
    }

    /** A cada 10 minutos de jogo: fora de combate, a vida volta devagar e o fôlego todo. */
    public void tenMinutes() {
        if (inCombat()) return;
        world.stamina = maxStamina();
        if (!economy.starving()) world.health = Math.min(maxHealth(), world.health + HEAL_PER_10_MIN);
    }

    /** Cura (unguento): até o máximo. */
    public void heal(double amount) {
        world.health = Math.min(maxHealth(), world.health + amount);
    }

    /** Ao sair de uma área, quem ficou lá some (fora de cena o mundo não simula lobos). */
    public void leaveArea() {
        world.enemies.clear();
        world.attackTarget = null;
        world.attackRepeat = false;
        world.dodgeTicks = 0;
    }

    private long ticks(double seconds) {
        return Math.max(1, Math.round(seconds * GameClock.TICKS_PER_SECOND));
    }

    // ---------------------------------------------------------------- encontros

    /**
     * Sorteia os encontros da área carregada (ao chegar e a cada hora). Devolve true se
     * algum começou — o que interrompe sono e espera (RF-323).
     */
    public boolean checkEncounters(List<SimEvent> events) {
        boolean started = false;
        for (EncounterDef enc : content.encounters().values()) {
            if (!world.areaId.equals(enc.area()) || !available(enc)) continue;
            if (!story.test(enc.condition(), null)) continue;
            if (!world.rng.chance(enc.chance())) continue;
            started |= spawn(enc, events) > 0;
        }
        return started;
    }

    private boolean available(EncounterDef enc) {
        Long last = world.encounterLast.get(enc.id());
        if (last == null) return true;
        if (enc.once()) return false;
        return world.clock.tick() - last >= Math.round(enc.cooldown() * world.clock.ticksPerHour());
    }

    /** Efeito {@code encounter} (RF-102, "iniciar combate"): força o encontro, se o jogador estiver na área dele. */
    public int forceEncounter(String id, List<SimEvent> events) {
        EncounterDef enc = content.encounters().get(id);
        if (enc == null || !world.areaId.equals(enc.area())) return 0;
        return spawn(enc, events);
    }

    private int spawn(EncounterDef enc, List<SimEvent> events) {
        Point origin = spawnPoint(enc);
        int count = 0;
        for (EncounterDef.Group g : enc.groups()) {
            EnemyDef d = content.enemies().get(g.enemy());
            if (d == null) continue;
            for (int i = 0; i < g.amount(); i++) {
                EnemyState e = new EnemyState(d.id() + "#" + (++world.nextEnemySeq), d.id(), d.spriteId());
                Point p = near(origin, count == 0 ? 0 : 1.3);
                e.x = e.homeX = p.x();
                e.y = e.homeY = p.y();
                e.health = d.health();
                e.encounter = enc.id();
                e.facing = Math.atan2(world.player.y - e.y, world.player.x - e.x);
                world.enemies.add(e);
                count++;
            }
        }
        if (count == 0) return 0;
        world.encounterLast.put(enc.id(), world.clock.tick());
        chronicle("encontro", enc.id(), "Encontro: " + enc.id() + " (" + count + ")");
        if (events != null) events.add(new SimEvent.EnemiesAppeared(enc.id(), count));
        return count;
    }

    /** Um dos pontos do encontro; sem nenhum, a uns 12 m do jogador. */
    private Point spawnPoint(EncounterDef enc) {
        List<Point> options = new ArrayList<>();
        for (String s : enc.spots()) {
            Point p = area.get().spots().get(s);
            if (p == null) p = area.get().spawns().get(s);
            if (p != null) options.add(p);
        }
        if (!options.isEmpty()) return options.get(world.rng.nextInt(options.size()));
        for (int tries = 0; tries < 16; tries++) {
            double a = world.rng.nextDouble() * Math.PI * 2;
            double x = world.player.x + Math.cos(a) * 12, y = world.player.y + Math.sin(a) * 12;
            if (!nav.get().isBlockedAt(x, y)) return new Point(x, y);
        }
        return new Point(world.player.x, world.player.y);
    }

    private Point near(Point p, double spread) {
        for (int tries = 0; tries < 8 && spread > 0; tries++) {
            double a = world.rng.nextDouble() * Math.PI * 2;
            double x = p.x() + Math.cos(a) * spread, y = p.y() + Math.sin(a) * spread;
            if (!nav.get().isBlockedAt(x, y)) return new Point(x, y);
        }
        return p;
    }

    // ---------------------------------------------------------------- comandos

    public void attack(String enemyId, boolean hold, List<SimEvent> events) {
        EnemyState e = enemy(enemyId);
        if (e == null) return;
        if (EnemyState.SURRENDERED.equals(e.state)) { // rendido não se ataca: decide-se o destino dele
            events.add(new SimEvent.Refused("combate.rendido"));
            return;
        }
        world.attackTarget = enemyId;
        world.attackRepeat = hold;
        world.dirX = world.dirY = 0;
        world.hasDestination = false;
    }

    public void release() {
        world.attackRepeat = false;
    }

    /** Golpe pesado ou empurrão (RF-222), contra o alvo atual ou o mais perto ao alcance. */
    public void ability(String ability, List<SimEvent> events) {
        if (!ABILITIES.contains(ability)) return;
        if (cooldownLeft(ability) > 0) {
            events.add(new SimEvent.Refused("combate.recarga"));
            return;
        }
        double cost = HEAVY.equals(ability) ? HEAVY_COST : PUSH_COST;
        if (world.stamina < cost) {
            events.add(new SimEvent.Refused("combate.sem_folego"));
            return;
        }
        EnemyState target = enemy(world.attackTarget);
        if (target == null || !inReach(target)) target = nearestInReach();
        if (target == null) {
            events.add(new SimEvent.Refused("combate.ninguem_ao_alcance"));
            return;
        }
        spend(cost);
        world.cooldowns.put(ability, world.clock.tick() + ticks(HEAVY.equals(ability) ? HEAVY_COOLDOWN : PUSH_COOLDOWN));
        face(target);
        if (HEAVY.equals(ability)) {
            strikeEnemy(target, HEAVY_MULT, true, events);
        } else {
            double dx = target.x - world.player.x, dy = target.y - world.player.y, d = Math.max(1e-6, Math.hypot(dx, dy));
            for (double s = PUSH_DISTANCE; s > 0.2; s -= 0.25) { // empurra até onde o chão deixa
                double nx = target.x + dx / d * s, ny = target.y + dy / d * s;
                if (nav.get().lineClear(target.x, target.y, nx, ny) && !nav.get().isBlockedAt(nx, ny)) {
                    target.x = nx;
                    target.y = ny;
                    break;
                }
            }
            target.staggerUntil = world.clock.tick() + ticks(PUSH_STAGGER);
            if (EnemyState.WINDUP.equals(target.state)) target.state = EnemyState.CHASE; // o golpe dele se perde
            target.path.clear();
            economy.train(FIGHTING, 0.3);
            events.add(new SimEvent.EnemyChanged(target.id, "empurrado", null));
        }
        world.attackReadyTick = Math.max(world.attackReadyTick, world.clock.tick() + ticks(attackInterval()));
    }

    public void dodge(double dx, double dy, List<SimEvent> events) {
        if (world.dodgeTicks > 0) return;
        if (world.stamina < DODGE_COST) {
            events.add(new SimEvent.Refused("combate.sem_folego"));
            return;
        }
        double len = Math.hypot(dx, dy);
        if (len < 1e-9) { // para trás
            dx = -Math.cos(world.player.facing);
            dy = -Math.sin(world.player.facing);
            len = 1;
        }
        spend(DODGE_COST);
        economy.train(Economy.VIGOR, 0.05);
        world.dodgeX = dx / len;
        world.dodgeY = dy / len;
        world.dodgeTicks = (int) ticks(DODGE_TIME);
        world.player.path.clear();
        world.hasDestination = false;
    }

    /** O destino de um inimigo rendido (RF-224). */
    public void resolve(String enemyId, String action, List<SimEvent> events) {
        EnemyState e = enemy(enemyId);
        EnemyDef d = e == null ? null : def(e);
        if (d == null || !EnemyState.SURRENDERED.equals(e.state)) return;
        if (dist(e) > RESOLVE_RANGE) {
            events.add(new SimEvent.Refused("combate.longe"));
            return;
        }
        switch (action) {
            case SPARE -> {
                leave(e, "poupado", d.line("poupado"), events);
                chronicle("combate.poupou", d.id(), "Poupou " + d.displayName());
            }
            case ROB -> {
                loot(e, d, events);
                leave(e, "saqueado", d.line("saqueado"), events);
                chronicle("combate.saqueou", d.id(), "Tomou o que " + d.displayName() + " levava");
            }
            case EXECUTE -> {
                int seen = story.deed(EXECUTION_DEED);
                if (seen > 0) events.add(new SimEvent.DeedSeen(EXECUTION_DEED, seen));
                kill(e, d, "executado", events);
            }
            default -> { /* ação desconhecida: nada */ }
        }
    }

    /**
     * O jogador se rende (fuga e rendição valem para os dois lados): os saqueadores
     * levam parte do dinheiro e vão embora. Animais e o que vive no escuro não aceitam.
     */
    public void surrender(List<SimEvent> events) {
        List<EnemyState> robbers = new ArrayList<>();
        for (EnemyState e : world.enemies) {
            EnemyDef d = def(e);
            if (d != null && e.hostile() && d.human() && d.robs() && dist(e) <= SURRENDER_RANGE) robbers.add(e);
        }
        if (robbers.isEmpty()) {
            events.add(new SimEvent.Refused("combate.rendicao_ninguem"));
            return;
        }
        int lost = robMoney();
        world.attackTarget = null;
        for (EnemyState e : robbers) leave(e, "foi_embora", def(e).line("vence"), events);
        chronicle("combate.rendeu", "", "Rendeu-se; levaram " + lost + " moedas");
        events.add(new SimEvent.PlayerSurrendered(lost));
    }

    /** Leva {@link #ROB_FRACTION} do dinheiro (ao menos 1, se houver). Devolve quanto. */
    public int robMoney() {
        int lost = world.money <= 0 ? 0 : Math.max(1, (int) Math.round(world.money * ROB_FRACTION));
        world.money -= lost;
        return lost;
    }

    /** Leva até {@link #ROB_ITEMS} unidades da mochila (o que vale mais; nada equipado). Devolve os IDs. */
    public List<String> robItems() {
        List<String> taken = new ArrayList<>();
        for (int i = 0; i < ROB_ITEMS; i++) {
            String best = null;
            int bestValue = 0;
            for (Map.Entry<String, Integer> it : world.inventory.entrySet()) {
                ItemDef def = content.items().get(it.getKey());
                if (def != null && it.getValue() > 0 && def.value() > bestValue) {
                    best = def.id();
                    bestValue = def.value();
                }
            }
            if (best == null || bestValue < 3) break; // ninharia ninguém leva
            economy.take(best, 1);
            taken.add(best);
        }
        return taken;
    }

    /** Agredir um NPC (ato "agressao"): quem viu — e o próprio — muda de ideia. */
    public boolean strikeNpc(String npcId, List<SimEvent> events) {
        ActorState npc = world.npcs.get(npcId);
        if (npc == null || npc.hidden || !npc.present() || !world.areaId.equals(npc.areaId)) return false;
        if (Math.hypot(npc.x - world.player.x, npc.y - world.player.y) > STRIKE_RANGE) {
            events.add(new SimEvent.Refused("combate.longe"));
            return false;
        }
        world.player.facing = Math.atan2(npc.y - world.player.y, npc.x - world.player.x);
        spend(ATTACK_COST);
        int seen = story.deed(AGGRESSION_DEED);
        if (seen > 0) events.add(new SimEvent.DeedSeen(AGGRESSION_DEED, seen));
        events.add(new SimEvent.AmbientLine(null, npcId, content.text("combate.agredido", "Ficou louco?")));
        return true;
    }

    // ---------------------------------------------------------------- a cada tick

    /**
     * O que o jogador faz neste tick por conta do combate: esquivar (substitui o
     * movimento normal) ou ir até o alvo e golpear. Devolve true se a esquiva já moveu o personagem.
     */
    public boolean playerTick(List<SimEvent> events) {
        if (world.dodgeTicks > 0) {
            double step = DODGE_DISTANCE / ticks(DODGE_TIME);
            double nx = world.player.x + world.dodgeX * step, ny = world.player.y + world.dodgeY * step;
            if (!nav.get().isBlockedAt(nx, ny)) {
                world.player.x = nx;
                world.player.y = ny;
            }
            world.player.moving = true;
            world.dodgeTicks--;
            return true;
        }
        EnemyState target = enemy(world.attackTarget);
        if (target == null || !target.hostile() && !EnemyState.SURRENDERED.equals(target.state)) {
            world.attackTarget = null;
            return false;
        }
        if (EnemyState.SURRENDERED.equals(target.state)) {
            world.attackTarget = null;
            world.player.path.clear();
            return false;
        }
        if (!inReach(target)) {
            long now = world.clock.tick();
            Point end = world.player.path.peekLast();
            boolean stale = end == null || Math.hypot(end.x() - target.x, end.y() - target.y) > 1.0;
            if (stale && (world.player.path.isEmpty() || now % 6 == 0)) { // recalcula 5 vezes por segundo
                world.player.path.clear();
                world.player.path.addAll(Pathfinder.findPath(nav.get(), world.player.x, world.player.y, target.x, target.y));
                if (world.player.path.isEmpty()) world.attackTarget = null; // não há como chegar
            }
            return false;
        }
        world.player.path.clear();
        world.player.moving = false;
        face(target);
        if (world.clock.tick() < world.attackReadyTick) return false;
        if (world.stamina < ATTACK_COST) return false; // sem fôlego: espera recuperar
        spend(ATTACK_COST);
        world.attackReadyTick = world.clock.tick() + ticks(attackInterval());
        strikeEnemy(target, 1, false, events);
        if (!world.attackRepeat) world.attackTarget = null;
        return false;
    }

    private boolean inReach(EnemyState e) {
        return dist(e) <= PLAYER_REACH;
    }

    private EnemyState nearestInReach() {
        EnemyState best = null;
        for (EnemyState e : world.enemies) {
            if (!e.hostile() || !inReach(e)) continue;
            if (best == null || dist(e) < dist(best)) best = e;
        }
        return best;
    }

    private void face(EnemyState e) {
        world.player.facing = Math.atan2(e.y - world.player.y, e.x - world.player.x);
    }

    private void spend(double stamina) {
        world.stamina = Math.max(0, world.stamina - stamina);
        world.exertTick = world.clock.tick();
    }

    /** Um golpe do jogador: chance pela Luta (e pela luz), dano pela arma e pela Luta, menos a proteção do alvo. */
    private void strikeEnemy(EnemyState e, double mult, boolean heavy, List<SimEvent> events) {
        EnemyDef d = def(e);
        if (d == null) return;
        double chance = 0.72 + (luta() - d.skill()) / 200;
        if (darkArea() && !carriesLight()) chance -= 0.2; // no escuro, sem luz, se erra mais
        chance = clamp(chance, 0.15, 0.95);
        if (!world.rng.chance(chance)) {
            events.add(new SimEvent.Hit(PLAYER, e.id, 0, heavy));
            return;
        }
        double dmg = weaponDamage() * (0.85 + world.rng.nextDouble() * 0.3) * (1 + luta() / 100) * mult;
        if (d.fearsLight() && carriesLight() && dist(e) <= lightRadius()) dmg *= 1.5;
        dmg = Math.max(1, dmg - d.protection());
        e.health -= dmg;
        economy.train(FIGHTING, 0.5);
        events.add(new SimEvent.Hit(PLAYER, e.id, dmg, heavy));
        if (e.health <= 0) {
            kill(e, d, "morreu", events);
            return;
        }
        if (d.flee() > 0 && e.health <= d.health() * d.flee() && e.hostile()) {
            if (d.human() && world.rng.chance(d.surrender())) {
                e.state = EnemyState.SURRENDERED;
                e.path.clear();
                e.moving = false;
                world.attackTarget = null;
                world.attackRepeat = false;
                chronicle("combate.rendido", d.id(), d.displayName() + " se rendeu");
                events.add(new SimEvent.EnemyChanged(e.id, "rendeu", d.line("rende")));
            } else {
                e.state = EnemyState.FLEE;
                e.fleeSince = world.clock.tick();
                e.path.clear();
                chronicle("combate.fugiu", d.id(), d.displayName() + " fugiu");
                events.add(new SimEvent.EnemyChanged(e.id, "fugiu", d.line("foge")));
            }
        }
    }

    private void kill(EnemyState e, EnemyDef d, String how, List<SimEvent> events) {
        world.enemies.remove(e);
        if (e.id.equals(world.attackTarget)) world.attackTarget = null;
        world.vars.merge(KILLS_VAR + d.id(), 1.0, Double::sum);
        economy.train(FIGHTING, 1.0);
        chronicle("combate.abateu", d.id(), d.displayName() + " (" + how + ")");
        events.add(new SimEvent.EnemyChanged(e.id, how, null));
        loot(e, d, events);
    }

    private void loot(EnemyState e, EnemyDef d, List<SimEvent> events) {
        List<String> items = new ArrayList<>();
        for (ItemDef.Stack s : d.lootList()) {
            if (!content.items().containsKey(s.item())) continue;
            economy.give(s.item(), s.amount());
            for (int i = 0; i < s.amount(); i++) items.add(s.item());
        }
        world.money += d.money();
        if (!items.isEmpty() || d.money() > 0) events.add(new SimEvent.Looted(e.id, items, d.money()));
    }

    private void leave(EnemyState e, String what, String line, List<SimEvent> events) {
        e.state = EnemyState.LEAVING;
        e.fleeSince = world.clock.tick();
        e.path.clear();
        if (e.id.equals(world.attackTarget)) world.attackTarget = null;
        events.add(new SimEvent.EnemyChanged(e.id, what, line));
    }

    /**
     * Inimigos: perseguem, golpeiam com aviso (dá para esquivar), desistem longe de
     * casa, fogem ou vão embora. Por fim, o fôlego do jogador volta. Devolve o inimigo
     * que derrubou o jogador neste tick, ou null.
     */
    public EnemyState enemiesTick(List<SimEvent> events) {
        long now = world.clock.tick();
        EnemyState winner = null;
        for (Iterator<EnemyState> it = world.enemies.iterator(); it.hasNext(); ) {
            EnemyState e = it.next();
            EnemyDef d = def(e);
            if (d == null) {
                it.remove();
                continue;
            }
            boolean lit = d.fearsLight() && carriesLight() && dist(e) <= lightRadius();
            double speed = d.speedOr() * (lit ? 0.5 : 1) * DT;
            e.moving = false;
            switch (e.state) {
                case EnemyState.SURRENDERED -> e.facing = Math.atan2(world.player.y - e.y, world.player.x - e.x);
                case EnemyState.FLEE, EnemyState.LEAVING -> {
                    if (now - e.fleeSince > ticks(FLEE_TIME) || dist(e) > GONE_DISTANCE) {
                        it.remove();
                        if (e.id.equals(world.attackTarget)) world.attackTarget = null;
                        continue;
                    }
                    if (e.path.isEmpty() || now - e.pathTick > ticks(1.5)) {
                        double dx = e.x - world.player.x, dy = e.y - world.player.y, len = Math.max(1e-6, Math.hypot(dx, dy));
                        repath(e, e.x + dx / len * 10, e.y + dy / len * 10);
                    }
                    walk(e, speed * (EnemyState.LEAVING.equals(e.state) ? 0.5 : 1));
                }
                case EnemyState.RETURN -> {
                    if (Math.hypot(e.x - e.homeX, e.y - e.homeY) < 0.8 || e.path.isEmpty() && now - e.pathTick > ticks(1)) {
                        e.state = EnemyState.IDLE;
                        e.path.clear();
                    } else {
                        if (e.path.isEmpty()) repath(e, e.homeX, e.homeY);
                        walk(e, speed * 0.6);
                    }
                }
                case EnemyState.IDLE -> {
                    if (dist(e) <= d.aggroOr() && world.health > 0) {
                        e.state = EnemyState.CHASE;
                        String line = d.line("ataque");
                        if (line != null) events.add(new SimEvent.EnemyChanged(e.id, "ataca", line));
                    }
                }
                case EnemyState.WINDUP -> {
                    e.facing = Math.atan2(world.player.y - e.y, world.player.x - e.x);
                    if (now >= e.hitTick) {
                        e.state = EnemyState.CHASE;
                        if (strikePlayer(e, d, lit, events)) winner = e;
                    }
                }
                default -> { // perseguindo
                    if (now < e.staggerUntil) break;
                    if (Math.hypot(e.x - e.homeX, e.y - e.homeY) > d.leashOr() || dist(e) > d.aggroOr() * 2.2) {
                        e.state = EnemyState.RETURN; // desistiu: o jogador fugiu
                        e.path.clear();
                        events.add(new SimEvent.EnemyChanged(e.id, "desistiu", null));
                        break;
                    }
                    double reach = d.reachOr();
                    if (dist(e) <= reach * 0.9) {
                        e.path.clear();
                        e.facing = Math.atan2(world.player.y - e.y, world.player.x - e.x);
                        if (now >= e.nextAttackTick && world.health > 0) {
                            e.state = EnemyState.WINDUP;
                            e.hitTick = now + ticks(d.windupOr());
                            e.nextAttackTick = now + ticks(d.attackTimeOr() * (lit ? 2.5 : 1));
                        }
                    } else {
                        if (e.path.isEmpty() || now - e.pathTick > ticks(0.5)) repath(e, world.player.x, world.player.y);
                        walk(e, speed);
                    }
                }
            }
        }
        // o fôlego volta depois de um respiro; faminto, volta pela metade (D-046)
        if (now - world.exertTick > ticks(REGEN_DELAY)) {
            double regen = STAMINA_REGEN * DT * (economy.starving() ? 0.5 : 1);
            world.stamina = Math.min(maxStamina(), world.stamina + regen);
        }
        return winner;
    }

    /** O golpe anunciado sai: acerta se o jogador ainda estiver ao alcance e não estiver esquivando. */
    private boolean strikePlayer(EnemyState e, EnemyDef d, boolean lit, List<SimEvent> events) {
        if (world.dodgeTicks > 0 || dist(e) > d.reachOr() * 1.3) {
            events.add(new SimEvent.Hit(e.id, PLAYER, 0, false));
            return false;
        }
        double chance = clamp(0.75 + (d.skill() - luta()) / 200, 0.2, 0.95);
        if (!world.rng.chance(chance)) {
            events.add(new SimEvent.Hit(e.id, PLAYER, 0, false));
            return false;
        }
        double dmg = Math.max(1, d.damage() * (0.85 + world.rng.nextDouble() * 0.3) - equipped("protecao"));
        if (lit) dmg *= 0.7;
        world.health = Math.max(0, world.health - dmg);
        economy.train(Economy.VIGOR, 0.1);
        events.add(new SimEvent.Hit(e.id, PLAYER, dmg, false));
        return world.health <= 0;
    }

    private void repath(EnemyState e, double tx, double ty) {
        e.path.clear();
        e.path.addAll(Pathfinder.findPath(nav.get(), e.x, e.y, tx, ty));
        e.pathTick = world.clock.tick();
    }

    private static void walk(EnemyState e, double step) {
        double remaining = step;
        while (remaining > 0 && !e.path.isEmpty()) {
            Point t = e.path.peekFirst();
            double dx = t.x() - e.x, dy = t.y() - e.y, d = Math.hypot(dx, dy);
            if (d > 1e-9) e.facing = Math.atan2(dy, dx);
            if (d <= remaining) {
                e.x = t.x();
                e.y = t.y();
                remaining -= d;
                e.path.pollFirst();
            } else {
                e.x += dx / d * remaining;
                e.y += dy / d * remaining;
                remaining = 0;
            }
            e.moving = true;
        }
    }


    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void chronicle(String type, String ref, String text) {
        world.chronicle.add(new ChronicleEntry(world.clock.tick(), type, ref, text));
    }
}
