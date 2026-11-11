package etrava.sim.api;

import etrava.sim.combat.Combat;
import etrava.sim.content.AreaDef;
import etrava.sim.content.EnemyDef;
import etrava.sim.content.ItemDef;
import etrava.sim.content.ContainerDef;
import etrava.sim.content.ContentDb;
import etrava.sim.content.EpilogueDef;
import etrava.sim.content.JobDef;
import etrava.sim.economy.Economy;
import etrava.sim.content.NpcDef;
import etrava.sim.content.AmbientDef;
import etrava.sim.content.OriginDef;
import etrava.sim.nav.NavGrid;
import etrava.sim.nav.Pathfinder;
import etrava.sim.nav.Pathfinder.Point;
import etrava.sim.rules.Effect;
import etrava.sim.save.SaveGame;
import etrava.sim.story.Arcs;
import etrava.sim.story.Story;
import etrava.sim.time.GameClock;
import etrava.sim.world.ActorState;
import etrava.sim.world.ChronicleEntry;
import etrava.sim.world.EnemyState;
import etrava.sim.world.Scheduled;
import etrava.sim.world.World;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Fachada da simulação. A apresentação envia {@link Command}s, chama
 * {@link #step()} em passo fixo e lê o estado por {@link #view()}.
 * Não depende de tela, som nem do relógio do sistema.
 *
 * <p>Duas escalas (SPEC 3.2): a cada tick, movimento na área carregada; a cada
 * 10 minutos de jogo, as agendas de todos os NPCs do mundo; a cada hora, os
 * rumores e os arcos de mundo. Esperar, dormir e a simulação headless pulam de
 * fronteira em fronteira, disparando os eventos agendados na ordem certa.
 */
public final class Simulation {
    /** Duração de um tick em segundos reais. */
    public static final double DT = 1.0 / GameClock.TICKS_PER_SECOND;
    /** Velocidade de caminhada do jogador, em metros por segundo. */
    public static final double WALK_SPEED = 3.5;
    /** NPCs andam sem pressa. */
    public static final double STROLL_SPEED = 1.5;
    /** Ponto por onde NPCs entram e saem de uma área, se o mapa tiver um. */
    public static final String ENTRY_SPOT = "entrada";
    public static final String WALKING = "andando";
    /** Ritmo de eventos (RF-145): multiplicador dos prazos dos arcos. */
    public static final double PACE_NORMAL = 1.0, PACE_CONTEMPLATIVE = 2.0;
    /** Flag que marca o fim do Capítulo I (o epílogo já foi montado). */
    public static final String CHAPTER_ENDED = "capitulo1_encerrado";
    private static final int SLEEP_UNTIL_HOUR = 6;
    /** Derrota não letal: acorda entre tantas horas depois, com esta fração da vida. */
    public static final int DEFEAT_MIN_HOURS = 4, DEFEAT_MAX_HOURS = 8;
    public static final double DEFEAT_HEALTH = 0.3;
    /** Fato que quem acha o jogador desacordado passa a saber (facts.yaml). */
    public static final String FOUND_FACT = "jogador_achado_desacordado";
    /** Distância de um ponto de saída que leva a outra área. */
    public static final double EXIT_RANGE = 0.9;
    /** Flags que marcam as áreas e os lugares nomeados por onde o personagem já passou (RF-007). */
    public static final String VISITED = "visitou.", VISITED_PLACE = "visitou.lugar.";

    private final ContentDb content;
    private final World world;
    private AreaDef area;
    private NavGrid nav;

    private final List<Command> pending = new ArrayList<>();
    private final List<Command.Timed> commandLog = new ArrayList<>();
    private final Story story;
    private final Arcs arcs;
    private final Economy economy;
    private final Combat combat;
    /** Eventos gerados fora de um comando (efeitos agendados); saem no fim do próximo passo. */
    private final List<SimEvent> buffered = new ArrayList<>();

    private Simulation(ContentDb content, World world) {
        this.content = content;
        this.world = world;
        this.story = new Story(content, world, this::schedule);
        this.arcs = new Arcs(content, world, story);
        this.economy = new Economy(content, world, story);
        this.combat = new Combat(content, world, story, economy, () -> nav, () -> area);
        story.setArcs(arcs);
        story.setEconomy(economy);
        story.setCombat(combat);
    }

    public static Simulation newGame(ContentDb content, long seed, String areaId) {
        return newGame(content, seed, areaId, PACE_NORMAL);
    }

    /**
     * @param areaId área inicial; null = a da origem (content/origins), que também diz o ponto de chegada
     * @param pace   ritmo de eventos: {@link #PACE_NORMAL} ou {@link #PACE_CONTEMPLATIVE} (RF-145)
     */
    public static Simulation newGame(ContentDb content, long seed, String areaId, double pace) {
        World world = new World(seed, GameClock.DEFAULT_TICKS_PER_DAY);
        world.pace = pace > 0 ? pace : PACE_NORMAL;
        for (NpcDef def : content.npcs().values()) world.npcs.put(def.id(), new ActorState(def.id(), def.sprite()));
        Simulation sim = new Simulation(content, world);
        for (NpcDef def : content.npcs().values()) sim.story.initNpc(def);
        OriginDef origin = content.origin();
        sim.economy.newGame(origin);
        for (Map.Entry<String, Integer> o : origin.opinionMap().entrySet())
            world.opinions.merge(o.getKey(), o.getValue(), (a, b) -> Math.max(-100, Math.min(100, a + b)));
        for (String f : origin.flagList()) world.flags.put(f, true);
        for (String ref : origin.knowList()) sim.story.learn(Story.PLAYER, AmbientDef.factId(ref),
                AmbientDef.factVersion(ref), "witnessed", "inicio", null);
        world.health = sim.combat.maxHealth();
        world.stamina = sim.combat.maxStamina();
        String spawn = AreaDef.PLAYER_SPAWN;
        if (areaId == null) {
            areaId = origin.startArea() != null && content.area(origin.startArea()) != null
                    ? origin.startArea() : content.areas().keySet().iterator().next();
            if (origin.startSpawn() != null) spawn = origin.startSpawn();
        }
        sim.loadArea(areaId);
        sim.placePlayer(spawn);
        sim.arcs.init(sim.buffered);
        sim.story.takeDirty();
        sim.story.takeAgendaDirty();
        sim.updateAgendas(true);
        sim.refreshVisible();
        return sim;
    }

    /** Retoma uma simulação a partir de um estado já montado (carregado de um save). */
    public static Simulation fromWorld(ContentDb content, World world) {
        Simulation sim = new Simulation(content, world);
        sim.loadArea(world.areaId);
        // NPCs que entraram no conteúdo depois do save começam onde a agenda manda
        for (NpcDef def : content.npcs().values()) {
            if (!world.npcs.containsKey(def.id())) {
                world.npcs.put(def.id(), new ActorState(def.id(), def.sprite()));
                sim.story.initNpc(def);
            }
        }
        // arcos, comerciantes e contêineres que entraram no conteúdo depois do save começam do início
        sim.arcs.init(sim.buffered);
        sim.economy.ensureState();
        if (world.health < 0) world.health = sim.combat.maxHealth(); // save migrado da versão 4
        if (world.stamina < 0) world.stamina = sim.combat.maxStamina();
        sim.story.takeDirty();
        sim.story.takeAgendaDirty();
        // save de uma versão com outro mapa: quem ficou dentro de uma parede volta ao ponto de chegada
        if (sim.nav.isBlockedAt(world.player.x, world.player.y)) sim.placePlayer(AreaDef.PLAYER_SPAWN);
        // só os recém-chegados são posicionados: mexer nos demais mudaria o estado carregado
        for (ActorState npc : world.npcs.values()) {
            NpcDef.Block block = sim.block(npc);
            if (npc.areaId == null && npc.present() && block != null) {
                npc.target = block.at();
                sim.arriveAtTarget(npc);
            }
        }
        sim.refreshVisible();
        return sim;
    }

    public static Simulation load(ContentDb content, Path file) throws SaveGame.SaveException {
        return fromWorld(content, SaveGame.read(file).world());
    }

    /** Grava o jogo. {@code savedAt} é a data real, informada pela apresentação. */
    public void save(Path file, String savedAt) throws SaveGame.SaveException {
        if (world.dialogue != null)
            throw new SaveGame.SaveException("Não dá para salvar no meio de uma conversa.");
        if (world.trade != null)
            throw new SaveGame.SaveException("Não dá para salvar com o comércio aberto.");
        if (world.dead)
            throw new SaveGame.SaveException("O personagem morreu. Carregue um jogo salvo.");
        if (combat.inCombat())
            throw new SaveGame.SaveException("Não dá para salvar no meio de uma luta.");
        SaveGame.write(file, world, savedAt);
    }

    /** Impressão digital do estado inteiro, para comparar duas simulações. */
    public String stateHash() {
        return SaveGame.hash(world);
    }

    private void loadArea(String areaId) {
        AreaDef def = content.area(areaId);
        if (def == null) throw new IllegalArgumentException("área inexistente: " + areaId);
        this.area = def;
        this.nav = def.buildNavGrid(content.props());
        world.areaId = areaId;
        world.player.areaId = areaId;
        // primeira vez num lugar nomeado: vai para o caderno e para o mapa (RF-007)
        if (!world.flag(VISITED + areaId)) {
            world.flags.put(VISITED + areaId, true);
            ContentDb.AreaEntry entry = content.areaEntry(areaId);
            String place = entry == null ? null : entry.place();
            if (place != null && !world.flag(VISITED_PLACE + place)) {
                world.flags.put(VISITED_PLACE + place, true);
                buffered.add(new SimEvent.PlaceDiscovered(place, areaId));
            }
        }
    }

    private void placePlayer(String spawn) {
        Point p = area.spawns().get(spawn);
        if (p == null) p = area.spawns().get(AreaDef.PLAYER_SPAWN);
        if (p == null) p = new Point(area.width() / 2.0, area.height() / 2.0);
        world.player.x = p.x();
        world.player.y = p.y();
        stopPlayer();
    }

    public WorldView view() { return world; }
    /** O estado mutável em si — só para o save, ferramentas e testes; a apresentação usa {@link #view()}. */
    public World stateWorld() { return world; }
    public AreaDef area() { return area; }
    public NavGrid nav() { return nav; }
    public ContentDb content() { return content; }
    public List<Command.Timed> commandLog() { return Collections.unmodifiableList(commandLog); }
    /** Mochila, preços, habilidades e trabalhos — consultas para a apresentação e as ferramentas. */
    public Economy economy() { return economy; }
    /** O comércio ou contêiner aberto, com os preços de agora; null se nada está aberto. */
    public TradeView tradeView() { return economy.tradeView(); }
    /** Vida, fôlego, recargas, luz e inimigos — consultas para a apresentação e os testes. */
    public Combat combat() { return combat; }

    /** Enfileira um comando; ele é aplicado no início do próximo tick. */
    public void submit(Command command) {
        pending.add(command);
    }

    /** Avança um tick e devolve o que a apresentação precisa saber. */
    public List<SimEvent> step() {
        List<SimEvent> events = new ArrayList<>();
        if (world.dead) { // morto não age; o mundo espera um save ser carregado (RF-341)
            for (Command c : pending) commandLog.add(new Command.Timed(world.clock.tick(), c));
            pending.clear();
            return events;
        }
        boolean skipped = false;
        for (Command c : pending) {
            commandLog.add(new Command.Timed(world.clock.tick(), c));
            skipped |= apply(c, events);
        }
        pending.clear();
        react(events, false);
        skipped |= startPending(events);
        // durante uma conversa com o jogador, ou com o comércio aberto, o mundo fica parado (RF-105, RF-324)
        if (!skipped && world.dialogue == null && world.trade == null) { // um salto de tempo já consumiu este passo
            if (!combat.playerTick(events)) movePlayer(events); // esquivando, o combate já moveu o personagem
            for (ActorState npc : world.npcs.values()) {
                if (!npc.path.isEmpty() && area.id().equals(npc.areaId)) {
                    if (followPath(npc, STROLL_SPEED * DT)) arrive(npc);
                }
            }
            EnemyState winner = combat.enemiesTick(events);
            if (world.health <= 0) {
                defeat(winner, events);
                events.addAll(buffered);
                buffered.clear();
                refreshVisible();
                return events;
            }
            approachTalk(events);
            approachContainer(events);
            approachStrike(events);
            takeExit(events);
            if (world.dialogue == null && world.trade == null) {
                advanceTo(world.clock.tick() + 1, false);
                story.tickAmbient(events);
            }
        }
        events.addAll(buffered);
        buffered.clear();
        refreshVisible();
        return events;
    }

    /**
     * Aplica os comandos pendentes sem avançar o relógio — para a apresentação com o
     * jogo parado (mochila, ficha do personagem, pausa): comer ou trocar de roupa não gasta tempo.
     */
    public List<SimEvent> stepPaused() {
        List<SimEvent> events = new ArrayList<>();
        for (Command c : pending) {
            commandLog.add(new Command.Timed(world.clock.tick(), c));
            apply(c, events);
        }
        pending.clear();
        react(events, false);
        events.addAll(buffered);
        buffered.clear();
        refreshVisible();
        return events;
    }

    /**
     * Avanço rápido, sem interrupção (depuração e simulação headless): pula de
     * fronteira em fronteira, sem rodar os ticks detalhados.
     */
    public void fastForward(long ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks negativo");
        skipTime(ticks, false);
        refreshVisible();
    }

    /**
     * Aplica efeitos agora, fora de qualquer conversa. Para o console de
     * depuração, ferramentas e testes — não é entrada do jogador nem fica no registro de comandos.
     */
    public void applyNow(List<Effect> effects) {
        for (Effect e : effects) applyEffect(e);
        react(buffered, false);
        startPending(buffered);
        refreshVisible();
    }

    /** O epílogo como sairia agora, sem encerrar o capítulo (depuração). */
    public List<String> previewEpilogue() {
        return story.composeEpilogue();
    }

    /** Desfecho de um arco encerrado, ou null se ele ainda corre. */
    public String arcOutcome(String arcId) {
        return arcs.outcome(arcId);
    }

    /** Agenda efeitos para um tick futuro (RF-162). */
    public void schedule(long atTick, boolean interrupts, List<Effect> effects) {
        long tick = Math.max(atTick, world.clock.tick() + 1);
        Scheduled item = new Scheduled(tick, world.nextSeq++, interrupts, List.copyOf(effects));
        int i = 0;
        while (i < world.scheduled.size() && world.scheduled.get(i).tick() <= tick) i++;
        world.scheduled.add(i, item);
    }

    // ---------------------------------------------------------------- tempo

    /** Leva o relógio até {@code target}, disparando o que estiver marcado. Devolve true se algo interrompe. */
    private boolean advanceTo(long target, boolean snap) {
        world.clock.setTick(target);
        boolean interrupted = false;
        while (!world.scheduled.isEmpty() && world.scheduled.get(0).tick() <= target) {
            Scheduled s = world.scheduled.remove(0);
            for (Effect e : s.effects()) applyEffect(e);
            interrupted |= s.interrupts();
        }
        if (target % agendaInterval() == 0) {
            economy.tenMinutes();
            combat.tenMinutes();
            updateAgendas(snap);
        }
        if (target % world.clock.ticksPerHour() == 0) {
            story.spreadRumors();
            arcs.evaluate(buffered); // prazos e transições, uma vez por hora (SPEC 3.2)
            interrupted |= combat.checkEncounters(buffered); // um ataque interrompe sono e espera (RF-323)
        }
        react(buffered, snap);
        if (target % world.clock.ticksPerDay() == 0) {
            chronicle("dia", "", "Dia " + world.clock.day());
            economy.daily(); // reposição dos comerciantes (SPEC 3.2: economia uma vez por dia)
            interrupted |= endChapterIfDue();
        }
        return interrupted;
    }

    /**
     * Depois de qualquer efeito: os arcos conferem na hora se algo que observam
     * mudou (SPEC 4.5), e as agendas se ajustam ao novo estágio ou à ausência de alguém.
     */
    private void react(List<SimEvent> events, boolean snap) {
        if (story.takeDirty()) {
            arcs.evaluate(events);
            story.takeDirty(); // os efeitos dos próprios arcos já foram considerados
        }
        if (story.takeAgendaDirty()) updateAgendas(snap);
    }

    /** No fim do último dia do capítulo, monta o epílogo e avisa a apresentação (RF-163, PRD 8.6). */
    private boolean endChapterIfDue() {
        EpilogueDef def = content.epilogue();
        if (def == null || world.flag(CHAPTER_ENDED) || world.clock.day() <= def.endDay()) return false;
        world.flags.put(CHAPTER_ENDED, true);
        world.epilogue.addAll(story.composeEpilogue());
        chronicle("capitulo", "1", "Fim do Capítulo I");
        buffered.add(new SimEvent.ChapterEnded(List.copyOf(world.epilogue)));
        return true;
    }

    private boolean skipTime(long ticks, boolean interruptible) {
        stopPlayer();
        for (ActorState npc : world.npcs.values()) if (!npc.path.isEmpty()) arriveAtTarget(npc);
        long end = world.clock.tick() + ticks;
        while (world.clock.tick() < end) {
            long now = world.clock.tick();
            long next = Math.min(end, (now / agendaInterval() + 1) * agendaInterval());
            if (!world.scheduled.isEmpty()) next = Math.min(next, Math.max(now + 1, world.scheduled.get(0).tick()));
            if (advanceTo(next, true) && interruptible) return true;
        }
        return false;
    }

    /** As agendas são conferidas a cada 10 minutos de jogo. */
    private long agendaInterval() {
        return world.clock.ticksPerDay() / 144;
    }

    private void applyEffect(Effect effect) {
        story.apply(effect, null, "mundo", buffered);
    }

    private void chronicle(String type, String ref, String text) {
        world.chronicle.add(new ChronicleEntry(world.clock.tick(), type, ref, text));
    }

    // ---------------------------------------------------------------- comandos

    /** Aplica um comando. Devolve true se ele fez o tempo saltar. */
    private boolean apply(Command c, List<SimEvent> events) {
        ActorState player = world.player;
        boolean talking = world.dialogue != null, trading = world.trade != null;
        boolean talkCommand = c instanceof Command.Choose || c instanceof Command.Advance
                || c instanceof Command.LeaveDialogue || c instanceof Command.AskAbout;
        boolean tradeCommand = c instanceof Command.Buy || c instanceof Command.Sell || c instanceof Command.TakeItem
                || c instanceof Command.CloseTrade;
        // valem a qualquer hora, até com o jogo parado: caderno e mochila
        boolean anytime = c instanceof Command.MarkFact || c instanceof Command.UseItem || c instanceof Command.Equip
                || c instanceof Command.Unequip;
        if (!anytime && (talking != talkCommand || trading != tradeCommand)) return false; // comando fora de hora
        if (!anytime && !talkCommand && !tradeCommand) {
            if (!(c instanceof Command.Talk)) world.pendingTalk = null;
            if (!(c instanceof Command.OpenContainer)) world.pendingContainer = null;
        }
        boolean needsPeace = c instanceof Command.Talk || c instanceof Command.Wait || c instanceof Command.Sleep
                || c instanceof Command.Work || c instanceof Command.OpenTrade || c instanceof Command.OpenContainer;
        if (needsPeace && combat.inCombat()) {
            events.add(new SimEvent.Refused("combate.agora_nao"));
            return false;
        }
        if (c instanceof Command.MoveTo || (c instanceof Command.MoveDir && !isStop(c)) || c instanceof Command.Talk
                || c instanceof Command.OpenContainer || c instanceof Command.Teleport) {
            world.attackTarget = null; // andar para outro lugar é parar de atacar
        }
        if (!(c instanceof Command.Strike)) world.pendingStrike = null;
        switch (c) {
            case Command.Talk t -> {
                ActorState npc = world.npcs.get(t.npcId());
                if (npc == null || npc.hidden || !npc.present() || !area.id().equals(npc.areaId)) return false;
                if (Math.hypot(npc.x - player.x, npc.y - player.y) <= Story.TALK_RANGE) {
                    beginTalk(npc, events);
                } else {
                    world.dirX = world.dirY = 0;
                    world.hasDestination = false;
                    player.path.clear();
                    player.path.addAll(Pathfinder.findPath(nav, player.x, player.y, npc.x, npc.y));
                    world.pendingTalk = player.path.isEmpty() ? null : npc.id;
                }
            }
            case Command.Choose ch -> story.choose(ch.index(), events);
            case Command.Advance a -> story.advance(events);
            case Command.LeaveDialogue l -> story.leave(events);
            case Command.AskAbout a -> story.askAbout(a.factId(), events);
            case Command.MarkFact m -> {
                if (m.marked() && world.knows(m.factId())) world.marked.add(m.factId());
                else world.marked.remove(m.factId());
            }
            case Command.MoveTo m -> {
                world.dirX = world.dirY = 0;
                player.path.clear();
                player.path.addAll(Pathfinder.findPath(nav, player.x, player.y, m.x(), m.y()));
                if (player.path.isEmpty()) {
                    world.hasDestination = false;
                } else {
                    Point end = player.path.peekLast();
                    world.hasDestination = true;
                    world.destinationX = end.x();
                    world.destinationY = end.y();
                    if (Math.abs(end.x() - m.x()) > 1e-9 || Math.abs(end.y() - m.y()) > 1e-9)
                        events.add(new SimEvent.DestinationAdjusted(end.x(), end.y()));
                }
            }
            case Command.MoveDir m -> {
                double len = Math.sqrt(m.dx() * m.dx() + m.dy() * m.dy());
                if (len < 1e-9) {
                    world.dirX = world.dirY = 0;
                } else {
                    world.dirX = m.dx() / len;
                    world.dirY = m.dy() / len;
                    player.path.clear();
                    world.hasDestination = false;
                }
            }
            case Command.Teleport t -> {
                stopPlayer();
                if (!nav.isBlockedAt(t.x(), t.y())) {
                    player.x = t.x();
                    player.y = t.y();
                }
            }
            case Command.Wait w -> {
                long from = world.clock.tick();
                int hours = Math.max(1, Math.min(12, w.hours()));
                boolean interrupted = skipTime(hours * world.clock.ticksPerHour(), true);
                events.add(new SimEvent.TimeSkipped(from, world.clock.tick(), false, interrupted));
                return true;
            }
            case Command.Sleep s -> {
                long from = world.clock.tick(), day = world.clock.ticksPerDay();
                long wake = day * SLEEP_UNTIL_HOUR / 24;
                long delta = Math.floorMod(wake - from % day, day);
                boolean interrupted = skipTime(delta == 0 ? day : delta, true);
                chronicle("jogador.dormiu", "", interrupted ? "Sono interrompido" : "Dormiu até de manhã");
                events.add(new SimEvent.TimeSkipped(from, world.clock.tick(), true, interrupted));
                return true;
            }
            case Command.UseItem u -> {
                ItemDef item = content.items().get(u.itemId());
                if (economy.use(u.itemId(), events) && item != null && item.stat("cura") > 0) combat.heal(item.stat("cura"));
            }
            case Command.Attack a -> combat.attack(a.enemyId(), a.hold(), events);
            case Command.ReleaseAttack r -> combat.release();
            case Command.Ability a -> combat.ability(a.ability(), events);
            case Command.Dodge d -> combat.dodge(d.dx(), d.dy(), events);
            case Command.Resolve r -> combat.resolve(r.enemyId(), r.action(), events);
            case Command.Surrender x -> combat.surrender(events);
            case Command.Strike st -> {
                ActorState npc = world.npcs.get(st.npcId());
                if (npc == null || npc.hidden || !npc.present() || !area.id().equals(npc.areaId)) return false;
                if (Math.hypot(npc.x - player.x, npc.y - player.y) <= Combat.STRIKE_RANGE) {
                    stopPlayer();
                    combat.strikeNpc(npc.id, events);
                } else {
                    world.dirX = world.dirY = 0;
                    world.hasDestination = false;
                    player.path.clear();
                    player.path.addAll(Pathfinder.findPath(nav, player.x, player.y, npc.x, npc.y));
                    world.pendingStrike = player.path.isEmpty() ? null : npc.id;
                }
            }
            case Command.Equip e -> economy.equip(e.itemId());
            case Command.Unequip u -> economy.unequip(u.slot());
            case Command.Work w -> world.pendingJob = w.jobId();
            case Command.OpenTrade o -> economy.openTrade(o.merchantId(), events);
            case Command.Buy b -> economy.buy(b.itemId());
            case Command.Sell s -> economy.sell(s.itemId());
            case Command.TakeItem t -> economy.takeFromContainer(t.itemId(), events);
            case Command.CloseTrade x -> economy.closeTrade(events);
            case Command.OpenContainer o -> {
                ContainerDef def = content.containers().get(o.containerId());
                double[] p = def == null ? null : economy.containerPoint(def);
                if (p == null) return false;
                if (economy.nearContainer(def)) {
                    stopPlayer();
                    economy.openContainer(def.id(), events);
                } else {
                    world.dirX = world.dirY = 0;
                    world.hasDestination = false;
                    player.path.clear();
                    player.path.addAll(Pathfinder.findPath(nav, player.x, player.y, p[0], p[1]));
                    world.pendingContainer = player.path.isEmpty() ? null : def.id();
                }
            }
            case Command.EnterArea e -> enterArea(e.areaId(), e.spawn(), events);
        }
        return false;
    }

    private static boolean isStop(Command c) {
        return c instanceof Command.MoveDir m && Math.abs(m.dx()) < 1e-9 && Math.abs(m.dy()) < 1e-9;
    }

    /** Troca de área: quem estava a caminho termina o trajeto fora de cena; inimigos ficam para trás. */
    private boolean enterArea(String areaId, String spawn, List<SimEvent> events) {
        if (content.area(areaId) == null || areaId.equals(world.areaId)) return false;
        String from = world.areaId;
        for (ActorState npc : world.npcs.values()) if (!npc.path.isEmpty()) arriveAtTarget(npc);
        combat.leaveArea();
        loadArea(areaId);
        placePlayer(spawn == null ? AreaDef.PLAYER_SPAWN : spawn);
        chronicle("jogador.area", areaId, "Foi para " + areaId);
        events.add(new SimEvent.AreaChanged(from, areaId));
        combat.checkEncounters(events); // quem chega pode dar de cara com alguém
        return true;
    }

    /** Saídas da área (areas.yaml): andar até o ponto leva à outra área. */
    private void takeExit(List<SimEvent> events) {
        ContentDb.AreaEntry entry = content.areaEntry(area.id());
        if (entry == null || !world.player.moving) return;
        for (ContentDb.Exit exit : entry.exitList()) {
            Point p = area.spots().get(exit.spot());
            if (p == null || Math.hypot(p.x() - world.player.x, p.y() - world.player.y) > EXIT_RANGE) continue;
            // seguindo um clique, só entra se o destino era a porta; quem só passa por ela não entra
            if (!world.player.path.isEmpty() && (!world.hasDestination
                    || Math.hypot(p.x() - world.destinationX, p.y() - world.destinationY) > EXIT_RANGE * 2)) continue;
            if (!story.test(exit.condition(), null)) continue; // fechada por enquanto
            if (enterArea(exit.to(), exit.spawn(), events)) return;
        }
    }

    /** O jogador ia agredir alguém: chegando perto, agride. */
    private void approachStrike(List<SimEvent> events) {
        if (world.pendingStrike == null) return;
        ActorState npc = world.npcs.get(world.pendingStrike);
        if (npc == null || npc.hidden || !area.id().equals(npc.areaId)) {
            world.pendingStrike = null;
        } else if (Math.hypot(npc.x - world.player.x, npc.y - world.player.y) <= Combat.STRIKE_RANGE) {
            world.pendingStrike = null;
            stopPlayer();
            combat.strikeNpc(npc.id, events);
        } else if (world.player.path.isEmpty()) {
            world.pendingStrike = null;
        }
    }

    // ---------------------------------------------------------------- derrota e morte

    /**
     * Vida zero (RF-340/341). Numa área ou contra um inimigo letal, é a morte: o mundo
     * para e a apresentação mostra o que aconteceu com ele. Senão, derrota não letal:
     * saqueadores levam dinheiro e coisas, o personagem acorda horas depois num lugar
     * plausível, ferido, e o mundo andou sem ele.
     */
    private void defeat(EnemyState winner, List<SimEvent> events) {
        EnemyDef by = winner == null ? null : content.enemies().get(winner.type);
        ContentDb.AreaEntry entry = content.areaEntry(area.id());
        String byId = by == null ? "" : by.id();
        stopPlayer();
        if ((entry != null && entry.lethal()) || (by != null && by.lethal())) {
            world.dead = true;
            world.attackTarget = null;
            chronicle("jogador.morreu", byId, "O personagem morreu" + (by == null ? "" : " (" + by.displayName() + ")"));
            events.add(new SimEvent.PlayerDied(byId, story.composeEpilogue()));
            return;
        }
        boolean robbers = false;
        for (EnemyState e : world.enemies) {
            EnemyDef d = content.enemies().get(e.type);
            robbers |= d != null && d.robs() && e.hostile();
        }
        int lost = robbers ? combat.robMoney() : 0;
        List<String> items = robbers ? combat.robItems() : List.of();
        long from = world.clock.tick();
        combat.leaveArea();
        // acorda num lugar plausível (areas.yaml: wakeAt), horas depois
        String wakeArea = area.id(), wakeSpot = null;
        if (entry != null && entry.wakeAt() != null && entry.wakeAt().indexOf('/') > 0) {
            wakeArea = entry.wakeAt().substring(0, entry.wakeAt().indexOf('/'));
            wakeSpot = entry.wakeAt().substring(entry.wakeAt().indexOf('/') + 1);
        }
        if (!wakeArea.equals(area.id()) && content.area(wakeArea) != null) {
            String fromArea = world.areaId;
            for (ActorState npc : world.npcs.values()) if (!npc.path.isEmpty()) arriveAtTarget(npc);
            loadArea(wakeArea);
            events.add(new SimEvent.AreaChanged(fromArea, wakeArea));
        }
        Point p = wakeSpot == null ? null : area.spots().get(wakeSpot);
        if (p != null && !nav.isBlockedAt(p.x(), p.y())) {
            world.player.x = p.x();
            world.player.y = p.y();
        } else {
            placePlayer(AreaDef.PLAYER_SPAWN);
        }
        int hours = DEFEAT_MIN_HOURS + world.rng.nextInt(DEFEAT_MAX_HOURS - DEFEAT_MIN_HOURS + 1);
        skipTime(hours * world.clock.ticksPerHour(), false);
        combat.leaveArea(); // ninguém ataca quem acabou de acordar
        world.health = Math.max(1, Math.round(combat.maxHealth() * DEFEAT_HEALTH));
        world.stamina = combat.maxStamina();
        String finder = entry == null ? null : entry.finder();
        ActorState f = finder == null ? null : world.npcs.get(finder);
        if (f == null || !f.present()) {
            finder = null;
        } else {
            story.learn(finder, FOUND_FACT, null, "witnessed", "viu", null);
            story.remember(finder, "socorreu", "player");
        }
        chronicle("jogador.derrotado", byId, "Derrotado" + (by == null ? "" : " por " + by.displayName())
                + "; acordou " + hours + " h depois" + (lost > 0 ? ", sem " + lost + " moedas" : "")
                + (items.isEmpty() ? "" : ", sem " + String.join(", ", items)));
        events.add(new SimEvent.PlayerDefeated(from, world.clock.tick(), lost, items, world.areaId, finder, byId));
    }

    // ---------------------------------------------------------------- trabalho e comércio

    /**
     * Trabalho ou comércio pedido por uma conversa (efeitos {@code startJob}/{@code startTrade})
     * ou por comando: começa assim que não houver conversa aberta. Devolve true se o tempo saltou.
     */
    private boolean startPending(List<SimEvent> events) {
        if (world.dialogue != null) return false;
        if (world.pendingTrade != null) {
            String id = world.pendingTrade;
            world.pendingTrade = null;
            if (world.trade == null) economy.openTrade(id, events);
        }
        if (world.pendingJob == null) return false;
        String id = world.pendingJob;
        world.pendingJob = null;
        return world.trade == null && runJob(id, events);
    }

    /**
     * Trabalhar (RF-264): uma ação temporizada, sem minijogo (PRD 7.2). O tempo salta
     * como numa espera — o mundo segue — e pode ser interrompido por um evento.
     */
    private boolean runJob(String jobId, List<SimEvent> events) {
        JobDef job = content.jobs().get(jobId);
        if (!economy.canWork(job)) {
            events.add(new SimEvent.JobRefused(jobId));
            return false;
        }
        stopPlayer();
        if (job.area().equals(area.id())) { // o personagem vai para o posto de trabalho
            Point p = spot(job.area(), job.spot());
            if (!nav.isBlockedAt(p.x(), p.y())) {
                world.player.x = p.x();
                world.player.y = p.y();
            }
        }
        long from = world.clock.tick();
        boolean hungry = economy.starving();
        boolean interrupted = skipTime(Math.round(job.hours() * world.clock.ticksPerHour()), true);
        double worked = (world.clock.tick() - from) / (double) world.clock.ticksPerHour();
        events.add(economy.finishJob(job, worked, hungry, interrupted, events));
        react(events, false);
        events.add(new SimEvent.TimeSkipped(from, world.clock.tick(), false, interrupted));
        return true;
    }

    /** O jogador está indo até um contêiner: abre ao chegar perto. */
    private void approachContainer(List<SimEvent> events) {
        if (world.pendingContainer == null) return;
        ContainerDef def = content.containers().get(world.pendingContainer);
        if (def == null || economy.containerPoint(def) == null) {
            world.pendingContainer = null;
        } else if (economy.nearContainer(def)) {
            world.pendingContainer = null;
            stopPlayer();
            economy.openContainer(def.id(), events);
        } else if (world.player.path.isEmpty()) {
            world.pendingContainer = null; // chegou o mais perto que dava e não alcançou
        }
    }

    // ---------------------------------------------------------------- conversa

    /** O jogador está indo falar com alguém: começa a conversa ao chegar perto. */
    private void approachTalk(List<SimEvent> events) {
        if (world.pendingTalk == null) return;
        ActorState npc = world.npcs.get(world.pendingTalk), player = world.player;
        if (npc == null || npc.hidden || !area.id().equals(npc.areaId)) {
            world.pendingTalk = null;
            return;
        }
        if (Math.hypot(npc.x - player.x, npc.y - player.y) <= Story.TALK_RANGE) {
            beginTalk(npc, events);
        } else if (player.path.isEmpty()) { // o NPC andou: tenta de novo, ou desiste
            player.path.addAll(Pathfinder.findPath(nav, player.x, player.y, npc.x, npc.y));
            if (player.path.isEmpty()) world.pendingTalk = null;
        }
    }

    private void beginTalk(ActorState npc, List<SimEvent> events) {
        stopPlayer();
        world.pendingTalk = null;
        world.player.facing = Math.atan2(npc.y - world.player.y, npc.x - world.player.x);
        if (npc.path.isEmpty()) npc.facing = Math.atan2(world.player.y - npc.y, world.player.x - npc.x);
        story.startDialogue(npc.id, events);
    }

    // ---------------------------------------------------------------- jogador

    private void stopPlayer() {
        world.player.path.clear();
        world.dirX = world.dirY = 0;
        world.hasDestination = false;
        world.player.moving = false;
    }

    private void movePlayer(List<SimEvent> events) {
        ActorState player = world.player;
        double speed = WALK_SPEED * (economy.overloaded() ? Economy.OVERLOAD_SPEED : 1); // carga demais pesa (RF-200)
        double step = speed * DT, dirX = world.dirX, dirY = world.dirY;
        if (dirX != 0 || dirY != 0) {
            double nx = player.x + dirX * step, ny = player.y + dirY * step;
            boolean moved = false;
            // desliza ao longo de paredes: tenta o movimento inteiro, depois cada eixo
            if (!nav.isBlockedAt(nx, ny)) {
                player.x = nx;
                player.y = ny;
                moved = true;
            } else if (dirX != 0 && !nav.isBlockedAt(nx, player.y)) {
                player.x = nx;
                moved = true;
            } else if (dirY != 0 && !nav.isBlockedAt(player.x, ny)) {
                player.y = ny;
                moved = true;
            }
            player.facing = Math.atan2(dirY, dirX);
            player.moving = moved;
            return;
        }
        boolean wasWalking = !player.path.isEmpty();
        boolean arrived = followPath(player, step);
        if (wasWalking && arrived) {
            world.hasDestination = false;
            events.add(new SimEvent.PlayerArrived(player.x, player.y));
        }
    }

    /** Anda pelo caminho do personagem. Devolve true quando não há mais caminho. */
    private static boolean followPath(ActorState actor, double step) {
        double remaining = step;
        while (remaining > 0 && !actor.path.isEmpty()) {
            Point target = actor.path.peekFirst();
            double dx = target.x() - actor.x, dy = target.y() - actor.y;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist > 1e-9) actor.facing = Math.atan2(dy, dx);
            if (dist <= remaining) {
                actor.x = target.x();
                actor.y = target.y();
                remaining -= dist;
                actor.path.pollFirst();
            } else {
                actor.x += dx / dist * remaining;
                actor.y += dy / dist * remaining;
                remaining = 0;
            }
        }
        actor.moving = !actor.path.isEmpty();
        return actor.path.isEmpty();
    }

    // ---------------------------------------------------------------- NPCs e agendas

    /**
     * Confere a agenda de todos os NPCs. Na área carregada eles andam até o
     * novo lugar; fora dela (ou com {@code snap}) são postos direto lá.
     */
    private void updateAgendas(boolean snap) {
        for (ActorState npc : world.npcs.values()) {
            if (!npc.present()) continue; // partiu, morreu ou está fora: não segue agenda (RF-087)
            NpcDef.Block block = block(npc); // null também para NPC do save que saiu do conteúdo
            if (block == null) continue;
            boolean placed = npc.areaId != null;
            if (placed && block.at().equals(npc.target)) {
                // mesmo lugar, mas o bloco pode ter mudado (dormir → trabalhar à porta de casa)
                if (snap && !npc.path.isEmpty()) arriveAtTarget(npc);
                else if (npc.path.isEmpty()) settle(npc);
                continue;
            }
            npc.target = block.at();
            boolean here = placed && area.id().equals(npc.areaId);
            boolean goingHere = area.id().equals(block.area());
            if (snap || !placed || (!here && !goingHere)) {
                arriveAtTarget(npc);
            } else if (here && goingHere) {
                Point p = spot(block.area(), block.spot());
                walk(npc, p, false);
            } else if (here) { // sai desta área pela porta que leva aonde vai, ou pelo ponto de entrada
                Point exit = doorway(block.area());
                if (exit == null) arriveAtTarget(npc);
                else walk(npc, exit, true);
            } else { // chega de outra área, pela porta que vem de lá
                Point entry = doorway(npc.areaId);
                if (entry == null) {
                    arriveAtTarget(npc);
                } else {
                    npc.areaId = area.id();
                    npc.x = entry.x();
                    npc.y = entry.y();
                    walk(npc, spot(block.area(), block.spot()), false);
                }
            }
        }
    }

    /**
     * Por onde se passa, na área carregada, para ir à (ou vir da) área dada: o ponto da saída
     * que leva até ela; senão, o ponto de entrada geral do mapa; senão, nenhum (null).
     */
    private Point doorway(String otherArea) {
        ContentDb.AreaEntry entry = content.areaEntry(area.id());
        if (entry != null && otherArea != null) {
            for (ContentDb.Exit exit : entry.exitList()) {
                if (otherArea.equals(exit.to()) && exit.when() == null) {
                    Point p = area.spots().get(exit.spot());
                    if (p != null) return p;
                }
            }
        }
        return area.spots().get(ENTRY_SPOT);
    }

    private void walk(ActorState npc, Point to, boolean leaving) {
        npc.path.clear();
        npc.path.addAll(Pathfinder.findPath(nav, npc.x, npc.y, to.x(), to.y()));
        npc.leaving = leaving;
        npc.hidden = false;
        npc.activity = WALKING;
        if (npc.path.isEmpty()) arrive(npc);
    }

    /** O NPC chegou ao fim do caminho. */
    private void arrive(ActorState npc) {
        if (npc.leaving) {
            arriveAtTarget(npc);
            return;
        }
        npc.moving = false;
        settle(npc);
    }

    /** Põe o NPC direto no destino da agenda, sem andar. */
    private void arriveAtTarget(ActorState npc) {
        npc.path.clear();
        npc.moving = false;
        npc.leaving = false;
        if (npc.target == null) return;
        int slash = npc.target.indexOf('/');
        String areaId = npc.target.substring(0, slash);
        Point p = spot(areaId, npc.target.substring(slash + 1));
        npc.areaId = areaId;
        npc.x = p.x();
        npc.y = p.y();
        settle(npc);
    }

    /** Assume a atividade do bloco atual, se o NPC estiver onde o bloco manda. */
    private void settle(ActorState npc) {
        NpcDef.Block block = block(npc);
        boolean inPlace = block != null && block.at().equals(npc.target);
        npc.activity = inPlace ? block.activity() : "";
        npc.hidden = inPlace && block.hidden();
    }

    /**
     * Bloco de agenda em vigor para o NPC agora: blocos com condição (estágio de
     * arco, flag…) têm prioridade sobre a rotina normal (RF-083).
     */
    private NpcDef.Block block(ActorState npc) {
        NpcDef def = content.npcs().get(npc.id);
        return def == null ? null : def.blockAt(world.clock.minuteOfDay(), c -> story.test(c, npc.id));
    }

    private Point spot(String areaId, String name) {
        AreaDef def = content.area(areaId);
        if (def == null) return new Point(0, 0);
        Point p = def.spots().get(name);
        return p != null ? p : new Point(def.width() / 2.0, def.height() / 2.0);
    }

    private void refreshVisible() {
        world.visible.clear();
        for (ActorState npc : world.npcs.values()) {
            if (!npc.hidden && npc.present() && area.id().equals(npc.areaId)) world.visible.add(npc);
        }
    }
}
