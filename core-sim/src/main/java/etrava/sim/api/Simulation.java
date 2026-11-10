package etrava.sim.api;

import etrava.sim.content.AreaDef;
import etrava.sim.content.ContentDb;
import etrava.sim.nav.NavGrid;
import etrava.sim.nav.Pathfinder;
import etrava.sim.nav.Pathfinder.Point;
import etrava.sim.time.GameClock;
import etrava.sim.world.World;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Fachada da simulação. A apresentação envia {@link Command}s, chama
 * {@link #step()} em passo fixo e lê o estado por {@link #view()}.
 * Não depende de tela, som nem do relógio do sistema.
 */
public final class Simulation {
    /** Duração de um tick em segundos reais. */
    public static final double DT = 1.0 / GameClock.TICKS_PER_SECOND;
    /** Velocidade de caminhada, em metros por segundo. */
    public static final double WALK_SPEED = 3.5;

    private final ContentDb content;
    private final World world;
    private AreaDef area;
    private NavGrid nav;

    private final List<Command> pending = new ArrayList<>();
    private final List<Command.Timed> commandLog = new ArrayList<>();
    private final Deque<Point> path = new ArrayDeque<>();
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
        world.playerX = p.x();
        world.playerY = p.y();
        stop();
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
        world.clock.advance(1);
        return events;
    }

    /**
     * Avanço rápido (esperar, dormir, headless). No M0 só o relógio anda; a
     * partir do M2 processa blocos de hora e eventos agendados, em ordem.
     */
    public void fastForward(long ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks negativo");
        stop();
        world.clock.advance(ticks);
    }

    private void apply(Command c, List<SimEvent> events) {
        switch (c) {
            case Command.MoveTo m -> {
                dirX = dirY = 0;
                path.clear();
                path.addAll(Pathfinder.findPath(nav, world.playerX, world.playerY, m.x(), m.y()));
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
                stop();
                if (!nav.isBlockedAt(t.x(), t.y())) {
                    world.playerX = t.x();
                    world.playerY = t.y();
                }
            }
        }
    }

    private void stop() {
        path.clear();
        dirX = dirY = 0;
        world.hasDestination = false;
        world.playerMoving = false;
    }

    private void movePlayer(List<SimEvent> events) {
        double step = WALK_SPEED * DT;
        if (dirX != 0 || dirY != 0) {
            double nx = world.playerX + dirX * step, ny = world.playerY + dirY * step;
            boolean moved = false;
            // desliza ao longo de paredes: tenta o movimento inteiro, depois cada eixo
            if (!nav.isBlockedAt(nx, ny)) {
                world.playerX = nx;
                world.playerY = ny;
                moved = true;
            } else if (dirX != 0 && !nav.isBlockedAt(nx, world.playerY)) {
                world.playerX = nx;
                moved = true;
            } else if (dirY != 0 && !nav.isBlockedAt(world.playerX, ny)) {
                world.playerY = ny;
                moved = true;
            }
            world.playerFacing = Math.atan2(dirY, dirX);
            world.playerMoving = moved;
            return;
        }
        if (path.isEmpty()) {
            world.playerMoving = false;
            return;
        }
        double remaining = step;
        while (remaining > 0 && !path.isEmpty()) {
            Point target = path.peekFirst();
            double dx = target.x() - world.playerX, dy = target.y() - world.playerY;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist > 1e-9) world.playerFacing = Math.atan2(dy, dx);
            if (dist <= remaining) {
                world.playerX = target.x();
                world.playerY = target.y();
                remaining -= dist;
                path.pollFirst();
            } else {
                world.playerX += dx / dist * remaining;
                world.playerY += dy / dist * remaining;
                remaining = 0;
            }
        }
        world.playerMoving = true;
        if (path.isEmpty()) {
            world.playerMoving = false;
            world.hasDestination = false;
            events.add(new SimEvent.PlayerArrived(world.playerX, world.playerY));
        }
    }
}
