package etrava.sim.world;

import etrava.sim.api.WorldView;
import etrava.sim.rng.SplitMix64;
import etrava.sim.time.GameClock;

/**
 * Raiz do estado da simulação. No M0 guarda só o necessário para andar pela
 * área de teste; cresce marco a marco (SPEC 3.5).
 */
public final class World implements WorldView {
    public final long seed;
    public final SplitMix64 rng;
    public final GameClock clock;

    public String areaId;
    public double playerX;
    public double playerY;
    public double playerFacing;
    public boolean playerMoving;
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
    @Override public double playerX() { return playerX; }
    @Override public double playerY() { return playerY; }
    @Override public double playerFacing() { return playerFacing; }
    @Override public boolean playerMoving() { return playerMoving; }
    @Override public boolean hasDestination() { return hasDestination; }
    @Override public double destinationX() { return destinationX; }
    @Override public double destinationY() { return destinationY; }
}
