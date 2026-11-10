package etrava.sim.api;

import etrava.sim.content.ActorSpawn;
import etrava.sim.content.AreaDef;
import etrava.sim.content.ContentDb;
import etrava.sim.nav.NavGrid;
import etrava.sim.nav.Pathfinder;
import etrava.sim.nav.Pathfinder.Point;
import etrava.sim.time.GameClock;
import etrava.sim.world.ActorState;
import etrava.sim.world.World;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fachada da simulação. A apresentação envia {@link Command}s, chama
 * {@link #step()} em passo fixo e lê o estado por {@link #view()}.
 * Não depende de tela, som nem do relógio do sistema.
 */
public final class Simulation {
    /** Duração de um tick em segundos reais. */
    public static final double DT = 1.0 / GameClock.TICKS_PER_SECOND;
    /** Velocidade de caminhada do jogador, em metros por segundo. */
    public static final double WALK_SPEED = 3.5;
    /** Figurantes andam sem pressa. */
    public static final double STROLL_SPEED = 1.5;

    private final ContentDb content;
    private final World world;
    private AreaDef area;
    private NavGrid nav;

    private final List<Command> pending = new ArrayList<>();
    private final List<Command.Timed> commandLog = new ArrayList<>();
    private final Map<ActorState, Deque<Point>> paths = new IdentityHashMap<>();
    private final Map<ActorState, ActorSpawn> spawns = new IdentityHashMap<>();
    private double dirX, dirY;

    private Simulation(ContentDb content, World world) {
        this.content = content;
        this.world = world;
    }

    public static Simulation newGame(ContentDb content, long seed, String areaId) {
        Simulation sim = new Simulation(content, new World(seed, GameClock.DEFAULT_TICKS_PER_DAY));
        sim.enterArea(areaId, AreaDef.PLAYER_SPAWN);
        return sim;
    }

    private void enterArea(String areaId, String spawn) {
        AreaDef def = content.area(areaId);
        if (def == null) throw new IllegalArgumentException("área inexistente: " + areaId);
        this.area = def;
        this.nav = def.buildNavGrid(content.props());
        world.areaId = areaId;
        Point p = def.spawns().get(spawn);
        if (p == null) p = new Point(def.width() / 2.0, def.height() / 2.0);
        world.player.x = p.x();
        world.player.y = p.y();
        paths.clear();
        spawns.clear();
        paths.put(world.player, new ArrayDeque<>());
        world.actors.clear();
        for (ActorSpawn s : def.actors()) {
            ActorState actor = new ActorState(s.id(), s.sprite(), s.x(), s.y());
            actor.waitTicks = 30 + world.rng.nextInt(90);
            world.actors.add(actor);
            paths.put(actor, new ArrayDeque<>());
            spawns.put(actor, s);
        }
        stopPlayer();
    }

    public WorldView view() { return world; }
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
        for (Command c : pending) {
            commandLog.add(new Command.Timed(world.clock.tick(), c));
            apply(c, events);
        }
        pending.clear();
        movePlayer(events);
        for (ActorState actor : world.actors) stroll(actor);
        world.clock.advance(1);
        return events;
    }

    /**
     * Avanço rápido (esperar, dormir, headless). Por enquanto só o relógio anda; a
     * partir do M2 processa blocos de hora e eventos agendados, em ordem.
     */
    public void fastForward(long ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks negativo");
        stopPlayer();
        world.clock.advance(ticks);
    }

    private void apply(Command c, List<SimEvent> events) {
        ActorState player = world.player;
        Deque<Point> path = paths.get(player);
        switch (c) {
            case Command.MoveTo m -> {
                dirX = dirY = 0;
                path.clear();
                path.addAll(Pathfinder.findPath(nav, player.x, player.y, m.x(), m.y()));
                if (path.isEmpty()) {
                    world.hasDestination = false;
                } else {
                    Point end = path.peekLast();
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
                    dirX = dirY = 0;
                } else {
                    dirX = m.dx() / len;
                    dirY = m.dy() / len;
                    path.clear();
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
        }
    }

    private void stopPlayer() {
        paths.get(world.player).clear();
        dirX = dirY = 0;
        world.hasDestination = false;
        world.player.moving = false;
    }

    private void movePlayer(List<SimEvent> events) {
        ActorState player = world.player;
        double step = WALK_SPEED * DT;
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
        boolean wasWalking = !paths.get(player).isEmpty();
        boolean arrived = followPath(player, step);
        if (wasWalking && arrived) {
            world.hasDestination = false;
            events.add(new SimEvent.PlayerArrived(player.x, player.y));
        }
    }

    /** Anda pelo caminho do personagem. Devolve true quando não há mais caminho. */
    private boolean followPath(ActorState actor, double step) {
        Deque<Point> path = paths.get(actor);
        double remaining = step;
        while (remaining > 0 && !path.isEmpty()) {
            Point target = path.peekFirst();
            double dx = target.x() - actor.x, dy = target.y() - actor.y;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist > 1e-9) actor.facing = Math.atan2(dy, dx);
            if (dist <= remaining) {
                actor.x = target.x();
                actor.y = target.y();
                remaining -= dist;
                path.pollFirst();
            } else {
                actor.x += dx / dist * remaining;
                actor.y += dy / dist * remaining;
                remaining = 0;
            }
        }
        actor.moving = !path.isEmpty();
        return path.isEmpty();
    }

    /** Figurante: espera um pouco, escolhe outro ponto da ronda e vai até lá. */
    private void stroll(ActorState actor) {
        Deque<Point> path = paths.get(actor);
        if (!path.isEmpty()) {
            if (followPath(actor, STROLL_SPEED * DT)) actor.waitTicks = 90 + world.rng.nextInt(240);
            return;
        }
        if (actor.waitTicks > 0) {
            actor.waitTicks--;
            return;
        }
        List<String> stops = spawns.get(actor).patrol();
        actor.waitTicks = 60;
        if (stops.isEmpty()) return;
        Point target = area.spots().get(stops.get(world.rng.nextInt(stops.size())));
        if (target != null) path.addAll(Pathfinder.findPath(nav, actor.x, actor.y, target.x(), target.y()));
    }
}
