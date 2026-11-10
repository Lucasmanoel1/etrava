package etrava.sim.world;

import etrava.sim.api.ActorView;

/** Estado de um personagem no mundo (jogador ou figurante). */
public final class ActorState implements ActorView {
    public final String id;
    public final String sprite;
    public double x;
    public double y;
    public double facing;
    public boolean moving;
    /** Figurantes: ticks parado antes de seguir para o próximo ponto. */
    public int waitTicks;

    public ActorState(String id, String sprite, double x, double y) {
        this.id = id;
        this.sprite = sprite;
        this.x = x;
        this.y = y;
        this.facing = Math.PI / 4; // virado para a câmera
    }

    @Override public String id() { return id; }
    @Override public String sprite() { return sprite; }
    @Override public double x() { return x; }
    @Override public double y() { return y; }
    @Override public double facing() { return facing; }
    @Override public boolean moving() { return moving; }
}
