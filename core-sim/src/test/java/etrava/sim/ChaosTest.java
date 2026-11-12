package etrava.sim;

import static org.junit.jupiter.api.Assertions.*;

import etrava.sim.api.Command;
import etrava.sim.api.DialogueView;
import etrava.sim.api.Simulation;
import etrava.sim.api.WorldView;
import etrava.sim.combat.Combat;
import etrava.sim.content.ContentDb;
import etrava.sim.save.SaveGame;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * M8: teste de caos (R6). Um jogador sem juízo manda comandos ao acaso — válidos, inválidos, fora de
 * hora — por dias de jogo, em várias sementes. O núcleo não pode cair, travar numa conversa sem saída,
 * nem deixar de fechar as histórias; e o que se salva no meio da bagunça carrega idêntico.
 */
class ChaosTest {
    private static ContentDb content;

    @BeforeAll
    static void load() {
        content = ContentDb.load(Path.of(System.getProperty("etrava.content", "../content")));
    }

    @Test
    void comandosAoAcasoNaoDerrubamONucleo(@TempDir Path dir) throws Exception {
        for (long seed = 1; seed <= 6; seed++) {
            Simulation sim = Simulation.newGame(content, seed, null);
            Random rnd = new Random(seed * 977);
            long day = Math.round(sim.view().clock().ticksPerDay());
            long end = 31 * day;
            int saves = 0, stuckInDialogue = 0;
            while (sim.view().clock().tick() < end) {
                if (sim.view().dead()) { // morreu numa área letal: outra vida, mesma bagunça
                    sim = Simulation.newGame(content, seed + 100, null);
                    end = sim.view().clock().tick() + 6 * day;
                }
                sim.submit(random(sim, rnd));
                int steps = 1 + rnd.nextInt(90);
                for (int i = 0; i < steps; i++) sim.step();
                stuckInDialogue = sim.view().dialogue() != null ? stuckInDialogue + 1 : 0;
                assertTrue(stuckInDialogue < 400, "semente " + seed + ": conversa sem saída");
                if (rnd.nextInt(200) == 0) sim.fastForward(rnd.nextInt((int) day)); // dormiu, sumiu, voltou
                if (rnd.nextInt(150) == 0) {
                    Path file = dir.resolve("caos_" + seed + ".etrava");
                    try {
                        sim.save(file, "caos");
                    } catch (SaveGame.SaveException e) {
                        continue; // no meio de conversa, comércio ou luta não se salva: recusa tratada
                    }
                    assertEquals(sim.stateHash(), Simulation.load(content, file).stateHash(), "semente " + seed);
                    saves++;
                }
            }
            assertTrue(saves > 0, "semente " + seed + ": nenhum save no meio do caos");
        }
    }

    private static Command random(Simulation sim, Random rnd) {
        WorldView v = sim.view();
        DialogueView d = v.dialogue();
        if (d != null) {
            int r = rnd.nextInt(10);
            if (r == 0) return new Command.LeaveDialogue();
            if (r == 1) return new Command.Choose(rnd.nextInt(12) - 2); // inclusive índices que não existem
            return d.choices().isEmpty() ? new Command.Advance() : new Command.Choose(rnd.nextInt(d.choices().size()));
        }
        if (v.trade() != null) {
            List<String> items = new ArrayList<>(content.items().keySet());
            String item = items.get(rnd.nextInt(items.size()));
            return switch (rnd.nextInt(5)) {
                case 0 -> new Command.Buy(item);
                case 1 -> new Command.Sell(item);
                case 2 -> new Command.TakeItem(item);
                default -> new Command.CloseTrade();
            };
        }
        List<String> npcs = new ArrayList<>(content.npcs().keySet());
        List<String> items = new ArrayList<>(content.items().keySet());
        List<String> areas = new ArrayList<>(content.areas().keySet());
        List<String> jobs = new ArrayList<>(content.jobs().keySet());
        List<String> shops = new ArrayList<>(content.merchants().keySet());
        List<String> boxes = new ArrayList<>(content.containers().keySet());
        List<String> facts = new ArrayList<>(content.facts().keySet());
        String enemy = v.enemies().isEmpty() ? "ninguem" : v.enemies().get(rnd.nextInt(v.enemies().size())).id;
        return switch (rnd.nextInt(24)) {
            case 0, 1, 2, 3 -> new Command.MoveTo(v.playerX() + rnd.nextInt(41) - 20, v.playerY() + rnd.nextInt(41) - 20);
            case 4 -> new Command.MoveDir(rnd.nextInt(3) - 1, rnd.nextInt(3) - 1);
            case 5, 6, 7 -> new Command.Talk(npcs.get(rnd.nextInt(npcs.size())));
            case 8 -> new Command.Wait(1 + rnd.nextInt(4));
            case 9 -> new Command.Sleep();
            case 10 -> new Command.EnterArea(areas.get(rnd.nextInt(areas.size())), null);
            case 11 -> new Command.Work(jobs.get(rnd.nextInt(jobs.size())));
            case 12 -> new Command.OpenTrade(shops.get(rnd.nextInt(shops.size())));
            case 13 -> new Command.OpenContainer(boxes.get(rnd.nextInt(boxes.size())));
            case 14 -> new Command.UseItem(items.get(rnd.nextInt(items.size())));
            case 15 -> new Command.Equip(items.get(rnd.nextInt(items.size())));
            case 16 -> new Command.Unequip(rnd.nextBoolean() ? "mao" : "cabeca");
            case 17 -> new Command.Attack(enemy, rnd.nextBoolean());
            case 18 -> new Command.Ability(rnd.nextBoolean() ? Combat.HEAVY : Combat.PUSH);
            case 19 -> new Command.Dodge(rnd.nextInt(3) - 1, rnd.nextInt(3) - 1);
            case 20 -> new Command.Resolve(enemy, List.of(Combat.SPARE, Combat.ROB, Combat.EXECUTE).get(rnd.nextInt(3)));
            case 21 -> new Command.MarkFact(facts.get(rnd.nextInt(facts.size())), rnd.nextBoolean());
            case 22 -> new Command.AskAbout(facts.get(rnd.nextInt(facts.size())));
            default -> rnd.nextInt(6) == 0 ? new Command.Strike(npcs.get(rnd.nextInt(npcs.size()))) : new Command.Surrender();
        };
    }
}
