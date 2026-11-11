package etrava.sim.world;

import etrava.sim.api.ActorView;
import etrava.sim.nav.Pathfinder.Point;
import java.util.ArrayDeque;
import java.util.Deque;

/** Estado de um personagem no mundo (jogador ou NPC). Tudo aqui vai para o save. */
public final class ActorState implements ActorView {
    public final String id;
    public final String sprite;
    public String areaId;
    public double x;
    public double y;
    public double facing = Math.PI / 4; // virado para a câmera
    public boolean moving;
    /** Caminho em andamento, em pontos de mundo. */
    public final Deque<Point> path = new ArrayDeque<>();

    // ---- só NPCs
    /** Destino atual da agenda, "area/ponto". */
    public String target;
    /** O que está fazendo (chave da agenda), ou "andando". */
    public String activity = "";
    /** Dentro de uma construção sem interior jogável: não aparece nem bloqueia. */
    public boolean hidden;
    /** Ao chegar ao fim do caminho, some desta área e reaparece no destino. */
    public boolean leaving;
    /** present | away | departed | dead. Quem não está presente some das agendas e das conversas (RF-087). */
    public String status = "present";

    public boolean present() { return "present".equals(status); }

    public ActorState(String id, String sprite) {
        this.id = id;
        this.sprite = sprite;
    }

    @Override public String id() { return id; }
    @Override public String sprite() { return sprite; }
    @Override public double x() { return x; }
    @Override public double y() { return y; }
    @Override public double facing() { return facing; }
    @Override public boolean moving() { return moving; }
    @Override public String activity() { return activity; }
}
