package etrava.tools;

import etrava.sim.api.ActorView;
import etrava.sim.api.Simulation;
import etrava.sim.api.WorkerPolicy;
import etrava.sim.api.WorldView;
import etrava.sim.content.ArcDef;
import etrava.sim.content.ContentDb;
import etrava.sim.content.NpcDef;
import etrava.sim.time.GameClock;
import etrava.sim.world.ChronicleEntry;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Simulação headless (RF-441): roda N dias de jogo sem janela e imprime a
 * crônica, o estado dos arcos, o que mudou no cenário e o epílogo. Uso:
 * {@code gradlew simulate --args="--dias 30 --semente 42 --politica parado"}.
 * Opções: {@code --ritmo normal|contemplativo} (RF-145) e {@code --resumo} (uma linha por arco, sem crônica).
 * Políticas do jogador: {@code parado} (não faz nada) e {@code trabalhador}
 * (trabalha, compra comida na taverna, come e dorme — SPEC 9).
 */
public final class Simulate {
    public static void main(String[] args) {
        Path content = Path.of("content");
        int days = 30;
        long seed = 1;
        String policy = "parado", area = null; // null = onde a origem começa (a oficina)
        double pace = Simulation.PACE_NORMAL;
        boolean brief = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--resumo")) {
                brief = true;
                continue;
            }
            if (i == args.length - 1) break;
            switch (args[i]) {
                case "--ritmo" -> pace = args[++i].startsWith("contempl") ? Simulation.PACE_CONTEMPLATIVE
                        : Simulation.PACE_NORMAL;
                case "--conteudo" -> content = Path.of(args[++i]);
                case "--dias" -> days = Integer.parseInt(args[++i]);
                case "--semente" -> seed = Long.parseLong(args[++i]);
                case "--politica" -> policy = args[++i];
                case "--area" -> area = args[++i];
                default -> { /* argumento desconhecido: ignora */ }
            }
        }
        if (!policy.equals("parado") && !policy.equals("trabalhador")) {
            System.err.println("Política desconhecida: " + policy + " (disponíveis: parado, trabalhador)");
            System.exit(2);
        }
        ContentDb db = ContentDb.load(content);
        if (!db.problems().isEmpty()) {
            System.err.println("Conteúdo com problemas; rode gradlew validateContent.");
            System.exit(1);
        }

        long started = System.nanoTime();
        Simulation sim = Simulation.newGame(db, seed, area, pace);
        WorldView v = sim.view();
        WorkerPolicy worker = null;
        if (policy.equals("trabalhador")) {
            worker = new WorkerPolicy(sim, "taverna");
            worker.run(days);
        } else {
            sim.fastForward(days * v.clock().ticksPerDay());
        }
        double seconds = (System.nanoTime() - started) / 1e9;

        System.out.printf(Locale.ROOT, "Simulação: %d dias, semente %d, política %s, área %s (%.2f s)%n",
                days, seed, policy, area, seconds);
        System.out.println("Fim: " + v.clock().format());
        System.out.println("Hash do estado: " + sim.stateHash());
        System.out.println();
        System.out.println("Arcos:");
        for (ArcDef def : db.arcs().values()) {
            String stage = v.arcs().get(def.id()).stage(), outcome = sim.arcOutcome(def.id());
            System.out.printf(Locale.ROOT, "  %-14s %-18s %s%n", def.id(), stage,
                    outcome != null ? "desfecho " + outcome : "em andamento");
        }
        System.out.println();
        System.out.printf(Locale.ROOT, "Personagem: %d moedas, fome %.0f, carga %.1f/%.1f kg%n", v.money(), v.hunger(),
                sim.economy().weight(), sim.economy().capacity());
        StringBuilder skills = new StringBuilder("  Habilidades:");
        v.skills().forEach((k, val) -> skills.append(String.format(Locale.ROOT, " %s %.1f", k, val)));
        System.out.println(skills);
        System.out.println("  Mochila: " + v.inventory() + "  Corpo: " + v.equipment());
        if (worker != null) System.out.printf(Locale.ROOT, "  Maior fome no caminho: %.0f%n", worker.maxHunger());
        if (brief) return;
        System.out.println();
        System.out.println("Cenário:");
        db.areas().values().forEach(a -> a.props().stream().filter(p -> p.key() != null).forEach(p -> {
            String state = v.propState(p.key());
            System.out.println("  " + p.key() + ": " + (state != null ? state : "(estado inicial)"));
        }));
        System.out.println();
        System.out.println("NPCs:");
        for (NpcDef def : db.npcs().values()) {
            ActorView npc = v.npc(def.id());
            if (!"present".equals(v.npcStatus(def.id()))) {
                System.out.printf(Locale.ROOT, "  %-12s %s%n", def.id(), v.npcStatus(def.id()));
                continue;
            }
            System.out.printf(Locale.ROOT, "  %-12s %-12s em %s (%.1f, %.1f)%n", def.id(), npc.activity(),
                    v.npcArea(def.id()), npc.x(), npc.y());
        }
        System.out.println();
        if (!v.epilogue().isEmpty()) {
            System.out.println("Epílogo:");
            for (String line : v.epilogue()) System.out.println("  " + line);
            System.out.println();
        }
        System.out.println("Crônica (" + v.chronicle().size() + " registros):");
        GameClock stamp = new GameClock(v.clock().ticksPerDay());
        for (ChronicleEntry c : v.chronicle()) {
            if (c.type().equals("dia")) continue; // a virada de cada dia só polui o relatório
            stamp.setTick(c.tick());
            System.out.println("  " + stamp.format() + "  [" + c.type() + "] " + c.text());
        }
    }
}
