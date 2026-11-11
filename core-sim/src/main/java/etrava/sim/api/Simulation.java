package etrava.sim.api;

import etrava.sim.content.AreaDef;
import etrava.sim.content.ContentDb;
import etrava.sim.content.NpcDef;
import etrava.sim.nav.NavGrid;
import etrava.sim.nav.Pathfinder;
import etrava.sim.nav.Pathfinder.Point;
import etrava.sim.rules.Effect;
import etrava.sim.save.SaveGame;
import etrava.sim.story.Story;
import etrava.sim.time.GameClock;
import etrava.sim.world.ActorState;
import etrava.sim.world.ChronicleEntry;
import etrava.sim.world.Scheduled;
import etrava.sim.world.World;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fachada da simulação. A apresentação envia {@link Command}s, chama
 * {@link #step()} em passo fixo e lê o estado por {@link #view()}.
 * Não depende de tela, som nem do relógio do sistema.
 *
 * <p>Duas escalas (SPEC 3.2): a cada tick, movimento na área carregada; a cada
 * 10 minutos de jogo, as agendas de todos os NPCs do mundo. Esperar, dormir e a
 * simulação headless pulam de fronteira em fronteira, disparando os eventos
 * agendados na ordem certa.
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
    private static final int SLEEP_UNTIL_HOUR = 6;

    private final ContentDb content;
    private final World world;
    private AreaDef area;
    private NavGrid nav;

    private final List<Command> pending = new ArrayList<>();
    private final List<Command.Timed> commandLog = new ArrayList<>();
    private final Story story;
    /** Eventos gerados fora de um comando (efeitos agendados); saem no fim do próximo passo. */
    private final List<SimEvent> buffered = new ArrayList<>();

    private Simulation(ContentDb content, World world) {
        this.content = content;
        this.world = world;
        this.story = new Story(content, world, this::schedule);
    }

    public static Simulation newGame(ContentDb content, long seed, String areaId) {
        World world = new World(seed, GameClock.DEFAULT_TICKS_PER_DAY);
        for (NpcDef def : content.npcs().values()) world.npcs.put(def.id(), new ActorState(def.id(), def.sprite()));
        Simulation sim = new Simulation(content, world);
        for (NpcDef def : content.npcs().values()) sim.story.initNpc(def);
        sim.loadArea(areaId);
        sim.placePlayer(AreaDef.PLAYER_SPAWN);
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
        // só os recém-chegados são posicionados: mexer nos demais mudaria o estado carregado
        int minute = world.clock.minuteOfDay();
        for (ActorState npc : world.npcs.values()) {
            NpcDef def = content.npcs().get(npc.id);
            NpcDef.Block block = def == null ? null : def.blockAt(minute);
            if (npc.areaId == null && block != null) {
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

    /** Enfileira um comando; ele é aplicado no início do próximo tick. */
    public void submit(Command command) {
        pending.add(command);
    }

    /** Avança um tick e devolve o que a apresentação precisa saber. */
    public List<SimEvent> step() {
        List<SimEvent> events = new ArrayList<>();
        boolean skipped = false;
        for (Command c : pending) {
            commandLog.add(new Command.Timed(world.clock.tick(), c));
            skipped |= apply(c, events);
        }
        pending.clear();
        // durante uma conversa com o jogador o mundo fica parado (RF-105)
        if (!skipped && world.dialogue == null) { // um salto de tempo já consumiu este passo
            movePlayer(events);
            for (ActorState npc : world.npcs.values()) {
                if (!npc.path.isEmpty() && area.id().equals(npc.areaId)) {
                    if (followPath(npc, STROLL_SPEED * DT)) arrive(npc);
                }
            }
            approachTalk(events);
            if (world.dialogue == null) {
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
     * Avanço rápido, sem interrupção (depuração e simulação headless): pula de
     * fronteira em fronteira, sem rodar os ticks detalhados.
     */
    public void fastForward(long ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks negativo");
        skipTime(ticks, false);
        refreshVisible();
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
        if (target % agendaInterval() == 0) updateAgendas(snap);
        if (target % world.clock.ticksPerHour() == 0) story.spreadRumors();
        if (target % world.clock.ticksPerDay() == 0) {
            chronicle("dia", "", "Dia " + world.clock.day());
        }
        return interrupted;
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
        boolean talking = world.dialogue != null;
        boolean talkCommand = c instanceof Command.Choose || c instanceof Command.Advance
                || c instanceof Command.LeaveDialogue || c instanceof Command.AskAbout || c instanceof Command.MarkFact;
        if (talking != talkCommand && !(c instanceof Command.MarkFact)) return false; // comando fora de hora
        if (!talkCommand && !(c instanceof Command.Talk)) world.pendingTalk = null;
        switch (c) {
            case Command.Talk t -> {
                ActorState npc = world.npcs.get(t.npcId());
                if (npc == null || npc.hidden || !area.id().equals(npc.areaId)) return false;
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
            case Command.EnterArea e -> {
                if (content.area(e.areaId()) == null || e.areaId().equals(world.areaId)) return false;
                String from = world.areaId;
                // quem estava a caminho na área antiga termina o trajeto fora de cena
                for (ActorState npc : world.npcs.values()) if (!npc.path.isEmpty()) arriveAtTarget(npc);
                loadArea(e.areaId());
                placePlayer(e.spawn() == null ? AreaDef.PLAYER_SPAWN : e.spawn());
                chronicle("jogador.area", e.areaId(), "Foi para " + e.areaId());
                events.add(new SimEvent.AreaChanged(from, e.areaId()));
            }
        }
        return false;
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
        double step = WALK_SPEED * DT, dirX = world.dirX, dirY = world.dirY;
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
        int minute = world.clock.minuteOfDay();
        for (ActorState npc : world.npcs.values()) {
            NpcDef def = content.npcs().get(npc.id);
            if (def == null) continue; // NPC do save que saiu do conteúdo
            NpcDef.Block block = def.blockAt(minute);
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
            } else if (here) { // sai desta área pelo ponto de entrada, se houver
                Point exit = area.spots().get(ENTRY_SPOT);
                if (exit == null) arriveAtTarget(npc);
                else walk(npc, exit, true);
            } else { // chega de outra área
                Point entry = area.spots().get(ENTRY_SPOT);
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
        NpcDef def = content.npcs().get(npc.id);
        NpcDef.Block block = def == null ? null : def.blockAt(world.clock.minuteOfDay());
        boolean inPlace = block != null && block.at().equals(npc.target);
        npc.activity = inPlace ? block.activity() : "";
        npc.hidden = inPlace && block.hidden();
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
            if (!npc.hidden && area.id().equals(npc.areaId)) world.visible.add(npc);
        }
    }
}
