package etrava.sim.api;

import etrava.sim.time.GameClock;

/** Vista somente leitura do estado, para a apresentação. */
public interface WorldView {
    long seed();
    GameClock clock();
    String areaId();
    double playerX();
    double playerY();
    /** Direção para onde o jogador está virado, em radianos no plano do mundo. */
    double playerFacing();
    boolean playerMoving();
    boolean hasDestination();
    double destinationX();
    double destinationY();
}
