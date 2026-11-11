package etrava.sim.world;

import etrava.sim.api.ActorView;
import etrava.sim.nav.Pathfinder.Point;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Um inimigo na área carregada (M6). Existe só enquanto o jogador está na área:
 * ao sair, quem ficou para trás some (o mundo não simula lobos fora de cena).
 * Vai para o save.
 */
public final class EnemyState implements ActorView {
    /** O que o inimigo está fazendo. */
    public static final String IDLE = "parado", CHASE = "perseguindo", WINDUP = "golpeando", FLEE = "fugindo",
            SURRENDERED = "rendido", RETURN = "voltando", LEAVING = "indo_embora";

    public final String id;
    /** Tipo, em content/enemies.yaml. */
    public final String type;
    public final String sprite;
    /** De que encontro veio (para relatórios); pode ser null. */
    public String encounter;
    public double x, y, facing;
    public boolean moving;
    public double health;
    public String state = IDLE;
    /** Onde apareceu: ele não se afasta muito daqui (leash). */
    public double homeX, homeY;
    /** Tick a partir do qual pode começar outro golpe. */
    public long nextAttackTick;
    /** Tick em que o golpe anunciado acerta (estado {@link #WINDUP}). */
    public long hitTick;
    /** Atordoado (empurrão) até este tick: não anda nem golpeia. */
    public long staggerUntil;
    /** Tick em que o caminho foi recalculado pela última vez. */
    public long pathTick;
    /** Quando começou a fugir (some depois de um tempo). */
    public long fleeSince;
    public final Deque<Point> path = new ArrayDeque<>();

    public EnemyState(String id, String type, String sprite) {
        this.id = id;
        this.type = type;
        this.sprite = sprite;
    }

    /** Ainda luta: não fugiu, não se rendeu, não está indo embora. */
    public boolean hostile() {
        return !FLEE.equals(state) && !SURRENDERED.equals(state) && !LEAVING.equals(state);
    }

    @Override public String id() { return id; }
    @Override public String sprite() { return sprite; }
    @Override public double x() { return x; }
    @Override public double y() { return y; }
    @Override public double facing() { return facing; }
    @Override public boolean moving() { return moving; }
    @Override public String activity() { return state; }
}
