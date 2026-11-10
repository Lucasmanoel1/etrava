package etrava.sim.api;

import etrava.sim.time.GameClock;
import java.util.List;

/** Vista somente leitura do estado, para a apresentação. */
public interface WorldView {
    long seed();
    GameClock clock();
    String areaId();
    ActorView player();
    /** Outros personagens presentes na área carregada. */
    List<? extends ActorView> actors();
    double playerX();
    double playerY();
    /** Direção para onde o jogador está virado, em radianos no plano do mundo. */
    double playerFacing();
    boolean playerMoving();
    boolean hasDestination();
    double destinationX();
    double destinationY();
}
