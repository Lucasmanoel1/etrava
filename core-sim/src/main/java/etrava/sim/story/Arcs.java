package etrava.sim.story;

import etrava.sim.api.SimEvent;
import etrava.sim.content.ArcDef;
import etrava.sim.content.ContentDb;
import etrava.sim.world.ArcState;
import etrava.sim.world.ChronicleEntry;
import etrava.sim.world.World;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor dos arcos de mundo (RF-140, SPEC 4.5). Cada arco é uma máquina de
 * estados: sai de um estágio por uma transição (algo mudou no mundo) ou pelo
 * prazo (ninguém fez nada). Como todo estágio não terminal tem prazo, todo arco
 * chega a um desfecho sozinho (RF-141). O estado mora no {@link World}.
 */
public final class Arcs {
    /** Limite de mudanças encadeadas numa mesma avaliação, contra conteúdo que anda em círculo. */
    private static final int MAX_STEPS = 32;

    private final ContentDb content;
    private final World world;
    private final Story story;

    public Arcs(ContentDb content, World world, Story story) {
        this.content = content;
        this.world = world;
        this.story = story;
    }

    /** Põe no estágio inicial os arcos que o mundo ainda não conhece (jogo novo, ou conteúdo mais novo que o save). */
    public void init(List<SimEvent> events) {
        for (ArcDef def : content.arcs().values()) {
            if (!world.arcs.containsKey(def.id())) enter(def, def.start(), events);
        }
    }

    /** Confere transições e prazos de todos os arcos, até nada mais mudar. */
    public void evaluate(List<SimEvent> events) {
        for (int step = 0; step < MAX_STEPS; step++) {
            boolean changed = false;
            for (ArcDef def : content.arcs().values()) {
                ArcState state = world.arcs.get(def.id());
                ArcDef.Stage stage = state == null ? null : def.stage(state.stage());
                if (stage == null || stage.terminal()) continue;
                String to = null;
                for (ArcDef.Transition t : stage.transitions()) {
                    if (story.test(t.when(), null)) {
                        to = t.to();
                        break;
                    }
                }
                if (to == null && stage.timeout() != null && state.deadline() >= 0
                        && world.clock.tick() >= state.deadline()) {
                    to = pick(stage.timeout());
                }
                if (to != null && def.stage(to) != null) {
                    enter(def, to, events);
                    changed = true;
                }
            }
            if (!changed) return;
        }
    }

    /** Leva um arco direto para um estágio (efeito {@code arc}). Arco já encerrado não volta. */
    public void force(String arcId, String to, List<SimEvent> events) {
        ArcDef def = content.arcs().get(arcId);
        ArcState state = world.arcs.get(arcId);
        if (def == null || def.stage(to) == null || (state != null && to.equals(state.stage()))) return;
        ArcDef.Stage current = state == null ? null : def.stage(state.stage());
        if (current != null && current.terminal()) return;
        enter(def, to, events);
    }

    /** Desfecho de um arco encerrado (leve, ambiguo, tragico, neutro), ou null se ainda corre. */
    public String outcome(String arcId) {
        ArcDef def = content.arcs().get(arcId);
        ArcState state = world.arcs.get(arcId);
        ArcDef.Stage stage = def == null || state == null ? null : def.stage(state.stage());
        return stage != null && stage.terminal() ? stage.outcome() : null;
    }

    /** O prazo venceu: opção de prioridade cuja condição passa; senão, sorteio por peso. */
    private String pick(ArcDef.Timeout timeout) {
        for (ArcDef.Option o : timeout.options()) {
            if (o.weight() <= 0 && story.test(o.when(), null)) return o.to();
        }
        List<ArcDef.Option> pool = new ArrayList<>();
        double total = 0;
        for (ArcDef.Option o : timeout.options()) {
            if (o.weight() > 0 && story.test(o.when(), null)) {
                pool.add(o);
                total += o.weight();
            }
        }
        if (pool.isEmpty()) return null;
        if (pool.size() == 1) return pool.get(0).to();
        double roll = world.rng.nextDouble() * total;
        for (ArcDef.Option o : pool) {
            roll -= o.weight();
            if (roll < 0) return o.to();
        }
        return pool.get(pool.size() - 1).to();
    }

    private void enter(ArcDef def, String stageId, List<SimEvent> events) {
        ArcDef.Stage stage = def.stage(stageId);
        if (stage == null) return;
        ArcState old = world.arcs.get(def.id());
        ArcDef.Stage previous = old == null ? null : def.stage(old.stage());
        if (previous != null) story.applyAll(previous.onExit(), null, Story.WORLD, events);

        long now = world.clock.tick(), deadline = -1;
        if (!stage.terminal() && stage.timeout() != null) {
            // o ritmo de eventos estica os prazos: Normal ×1, Contemplativo ×2 (RF-145)
            double ticksPerHour = world.clock.ticksPerHour() * world.pace;
            long jitter = Math.round(stage.timeout().jitterHours() * ticksPerHour);
            deadline = now + Math.round(stage.timeout().hours() * ticksPerHour)
                    + (jitter > 0 ? world.rng.nextInt((int) jitter + 1) : 0);
        }
        world.arcs.put(def.id(), new ArcState(stageId, now, deadline));
        String text = def.displayName() + ": " + (stage.chronicle() != null ? stage.chronicle() : stageId)
                + (stage.terminal() ? " [desfecho " + stage.outcome() + "]" : "");
        // o ponto de partida de um arco só entra na crônica se o conteúdo tiver algo a dizer sobre ele
        if (old != null || stage.chronicle() != null)
            world.chronicle.add(new ChronicleEntry(now, "arco", def.id() + ":" + stageId, text));
        story.arcChanged();
        story.applyAll(stage.onEnter(), null, Story.WORLD, events);
    }
}
