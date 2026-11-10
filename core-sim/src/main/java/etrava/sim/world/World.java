package etrava.sim.world;

import etrava.sim.api.ActorView;
import etrava.sim.api.WorldView;
import etrava.sim.rng.SplitMix64;
import etrava.sim.time.GameClock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Raiz do estado da simulação (SPEC 3.5). Tudo o que muda durante o jogo mora
 * aqui e é gravado no save; a {@code Simulation} só guarda o que dá para
 * recalcular (grade de navegação, listas de conveniência).
 */
public final class World implements WorldView {
    public static final String PLAYER_SPRITE = "personagem.aprendiz";

    public final long seed;
    public final SplitMix64 rng;
    public final GameClock clock;
    public final ActorState player = new ActorState("jogador", PLAYER_SPRITE);
    /** Todos os NPCs do mundo, na ordem do conteúdo. */
    public final Map<String, ActorState> npcs = new LinkedHashMap<>();
    public final Map<String, Boolean> flags = new TreeMap<>();
    public final Map<String, Double> vars = new TreeMap<>();
    /** Efeitos futuros, mantidos em ordem de (tick, seq). */
    public final List<Scheduled> scheduled = new ArrayList<>();
    public final List<ChronicleEntry> chronicle = new ArrayList<>();
    public long nextSeq;

    public String areaId;
    public boolean hasDestination;
    public double destinationX;
    public double destinationY;
    /** Direção do movimento por teclado (WASD), em mundo; (0, 0) parado. */
    public double dirX, dirY;

    /** NPCs visíveis na área carregada; recalculado pela simulação. */
    public final List<ActorState> visible = new ArrayList<>();

    public World(long seed, long ticksPerDay) {
        this.seed = seed;
        this.rng = new SplitMix64(seed);
        this.clock = new GameClock(ticksPerDay);
    }

    @Override public long seed() { return seed; }
    @Override public GameClock clock() { return clock; }
    @Override public String areaId() { return areaId; }
    @Override public ActorView player() { return player; }
    @Override public List<ActorState> actors() { return visible; }
    @Override public double playerX() { return player.x; }
    @Override public double playerY() { return player.y; }
    @Override public double playerFacing() { return player.facing; }
    @Override public boolean playerMoving() { return player.moving; }
    @Override public boolean hasDestination() { return hasDestination; }
    @Override public double destinationX() { return destinationX; }
    @Override public double destinationY() { return destinationY; }
    @Override public boolean flag(String name) { return flags.getOrDefault(name, false); }
    @Override public double var(String name) { return vars.getOrDefault(name, 0.0); }
    @Override public List<ChronicleEntry> chronicle() { return chronicle; }
    @Override public ActorView npc(String id) { return npcs.get(id); }
    @Override public String npcArea(String id) {
        ActorState n = npcs.get(id);
        return n == null ? null : n.areaId;
    }
}
