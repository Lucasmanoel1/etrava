package etrava.tools;

import etrava.sim.api.ActorView;
import etrava.sim.api.Simulation;
import etrava.sim.api.WorldView;
import etrava.sim.content.ContentDb;
import etrava.sim.content.NpcDef;
import etrava.sim.time.GameClock;
import etrava.sim.world.ChronicleEntry;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Simulação headless (RF-441): roda N dias de jogo sem janela e imprime a
 * crônica e o estado final. Uso:
 * {@code gradlew simulate --args="--dias 30 --semente 42 --politica parado"}.
 * Política do jogador disponível: {@code parado} (não faz nada). Outras entram
 * quando houver o que o jogador fazer (trabalho e comércio, M5).
 */
public final class Simulate {
    public static void main(String[] args) {
        Path content = Path.of("content");
        int days = 30;
        long seed = 1;
        String policy = "parado", area = "vila_ref";
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--conteudo" -> content = Path.of(args[++i]);
                case "--dias" -> days = Integer.parseInt(args[++i]);
                case "--semente" -> seed = Long.parseLong(args[++i]);
                case "--politica" -> policy = args[++i];
                case "--area" -> area = args[++i];
                default -> { /* argumento desconhecido: ignora */ }
            }
        }
        if (!policy.equals("parado")) {
            System.err.println("Política desconhecida: " + policy + " (disponível: parado)");
            System.exit(2);
        }
        ContentDb db = ContentDb.load(content);
        if (!db.problems().isEmpty()) {
            System.err.println("Conteúdo com problemas; rode gradlew validateContent.");
            System.exit(1);
        }

        long started = System.nanoTime();
        Simulation sim = Simulation.newGame(db, seed, area);
        WorldView v = sim.view();
        sim.fastForward(days * v.clock().ticksPerDay());
        double seconds = (System.nanoTime() - started) / 1e9;

        System.out.printf(Locale.ROOT, "Simulação: %d dias, semente %d, política %s, área %s (%.2f s)%n",
                days, seed, policy, area, seconds);
        System.out.println("Fim: " + v.clock().format());
        System.out.println("Hash do estado: " + sim.stateHash());
        System.out.println();
        System.out.println("NPCs:");
        for (NpcDef def : db.npcs().values()) {
            ActorView npc = v.npc(def.id());
            System.out.printf(Locale.ROOT, "  %-12s %-12s em %s (%.1f, %.1f)%n", def.id(), npc.activity(),
                    v.npcArea(def.id()), npc.x(), npc.y());
        }
        System.out.println();
        System.out.println("Crônica (" + v.chronicle().size() + " registros):");
        GameClock stamp = new GameClock(v.clock().ticksPerDay());
        for (ChronicleEntry c : v.chronicle()) {
            if (c.type().equals("dia")) continue; // a virada de cada dia só polui o relatório
            stamp.setTick(c.tick());
            System.out.println("  " + stamp.format() + "  [" + c.type() + "] " + c.text());
        }
    }
}
