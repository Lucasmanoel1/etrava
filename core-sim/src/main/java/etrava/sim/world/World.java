package etrava.sim.world;

import etrava.sim.api.ActorView;
import etrava.sim.api.WorldView;
import etrava.sim.rng.SplitMix64;
import etrava.sim.time.GameClock;
import java.util.ArrayList;
import java.util.List;

/**
 * Raiz do estado da simulação. Cresce marco a marco (SPEC 3.5): por enquanto
 * guarda o relógio, o jogador e os figurantes da área carregada.
 */
public final class World implements WorldView {
    public static final String PLAYER_SPRITE = "personagem.aprendiz";

    public final long seed;
    public final SplitMix64 rng;
    public final GameClock clock;
    public final ActorState player = new ActorState("jogador", PLAYER_SPRITE, 0, 0);
    public final List<ActorState> actors = new ArrayList<>();

    public String areaId;
    public boolean hasDestination;
    public double destinationX;
    public double destinationY;

    public World(long seed, long ticksPerDay) {
        this.seed = seed;
        this.rng = new SplitMix64(seed);
        this.clock = new GameClock(ticksPerDay);
    }

    @Override public long seed() { return seed; }
    @Override public GameClock clock() { return clock; }
    @Override public String areaId() { return areaId; }
    @Override public ActorView player() { return player; }
    @Override public List<ActorState> actors() { return actors; }
    @Override public double playerX() { return player.x; }
    @Override public double playerY() { return player.y; }
    @Override public double playerFacing() { return player.facing; }
    @Override public boolean playerMoving() { return player.moving; }
    @Override public boolean hasDestination() { return hasDestination; }
    @Override public double destinationX() { return destinationX; }
    @Override public double destinationY() { return destinationY; }
}
