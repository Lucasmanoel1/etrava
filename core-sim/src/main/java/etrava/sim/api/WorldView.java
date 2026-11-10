package etrava.sim.api;

import etrava.sim.time.GameClock;
import etrava.sim.world.ChronicleEntry;
import java.util.List;

/** Vista somente leitura do estado, para a apresentação. */
public interface WorldView {
    long seed();
    GameClock clock();
    String areaId();
    ActorView player();
    /** NPCs visíveis na área carregada. */
    List<? extends ActorView> actors();
    double playerX();
    double playerY();
    /** Direção para onde o jogador está virado, em radianos no plano do mundo. */
    double playerFacing();
    boolean playerMoving();
    boolean hasDestination();
    double destinationX();
    double destinationY();
    boolean flag(String name);
    double var(String name);
    /** Crônica interna do mundo — só para depuração e relatórios (RF-143). */
    List<ChronicleEntry> chronicle();
    /** Qualquer NPC do mundo, esteja onde estiver; null se não existir. */
    ActorView npc(String id);
    /** Área em que o NPC está agora; null se não existir. */
    String npcArea(String id);
}
