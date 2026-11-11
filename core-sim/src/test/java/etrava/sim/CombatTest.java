package etrava.sim;

import static org.junit.jupiter.api.Assertions.*;

import etrava.sim.api.ActorView;
import etrava.sim.api.Command;
import etrava.sim.api.SimEvent;
import etrava.sim.api.Simulation;
import etrava.sim.combat.Combat;
import etrava.sim.content.ContentDb;
import etrava.sim.rules.Effect;
import etrava.sim.save.SaveGame;
import etrava.sim.validation.ContentValidator;
import etrava.sim.world.EnemyState;
import etrava.sim.world.World;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** M6: combate, inimigos, fuga e rendição, derrota não letal e morte, estrutura enterrada e luz (AT-13, AT-14). */
class CombatTest {
    private static final String ROAD = "area_teste", RUIN = "estrutura";
    private static ContentDb content;

    @BeforeAll
    static void load() {
        content = ContentDb.load(Path.of(System.getProperty("etrava.content", "../content")));
    }

    private static long hours(Simulation sim, double h) {
        return Math.round(h * sim.view().clock().ticksPerHour());
    }

    /** Leva o relógio até a próxima vez que marcar esta hora. */
    private static void at(Simulation sim, double hour) {
        double now = sim.view().clock().minuteOfDay() / 60.0;
        sim.fastForward(hours(sim, (hour - now + 24) % 24));
    }

    private static List<SimEvent> step(Simulation sim, Command... commands) {
        for (Command c : commands) sim.submit(c);
        return sim.step();
    }

    /** Roda até o evento aparecer (ou estourar o limite). Devolve todos os eventos. */
    private static List<SimEvent> runUntil(Simulation sim, int maxTicks, Class<? extends SimEvent> until) {
        List<SimEvent> all = new ArrayList<>();
        for (int i = 0; i < maxTicks; i++) {
            List<SimEvent> ev = sim.step();
            all.addAll(ev);
            if (ev.stream().anyMatch(until::isInstance)) break;
        }
        return all;
    }

    /** Força um encontro; quem tenha aparecido sozinho enquanto o relógio corria sai antes. */
    private static void encounter(Simulation sim, String id) {
        sim.stateWorld().enemies.clear();
        sim.applyNow(List.of(new Effect.Encounter(id)));
    }

    private static EnemyState first(Simulation sim) {
        assertFalse(sim.view().enemies().isEmpty(), "esperava algum inimigo na área");
        return sim.view().enemies().get(0);
    }

    /** Põe o jogador a {@code d} metros de um inimigo, num ponto livre. */
    private static void besides(Simulation sim, EnemyState e, double d) {
        for (double a = 0; a < Math.PI * 2; a += Math.PI / 8) {
            double x = e.x + Math.cos(a) * d, y = e.y + Math.sin(a) * d;
            if (!sim.nav().isBlockedAt(x, y)) {
                step(sim, new Command.Teleport(x, y));
                return;
            }
        }
        fail("nenhum ponto livre perto do inimigo");
    }

    // ---------------------------------------------------------------- conteúdo

    @Test
    void conteudoDoM6CarregaEValida() {
        assertEquals(3, content.enemies().size());
        assertTrue(content.enemies().get("saqueador").human());
        assertTrue(content.enemies().get("vulto").fearsLight());
        assertEquals(3, content.encounters().size());
        ContentDb.AreaEntry ruin = content.areaEntry(RUIN);
        assertTrue(ruin.dark() && ruin.lethal());
        assertEquals("area_teste/beira_estrada", content.areaEntry(ROAD).wakeAt());
        assertEquals(List.of(), ContentValidator.validate(content));
    }

    @Test
    void validadorPegaCombateQuebrado_eArcoQueSoTerminaBemNaBriga() throws Exception {
        Path broken = Path.of(CombatTest.class.getResource("/quebrado_combate").toURI());
        List<String> errors = ContentValidator.validate(broken);
        String all = String.join("\n", errors);
        assertHas(errors, "enemies.yaml", "tipo inválido 'dragao'", all);
        assertHas(errors, "enemies.yaml", "precisa de health > 0", all);
        assertHas(errors, "enemies.yaml", "flee vai de 0 a 1", all);
        assertHas(errors, "enemies.yaml", "item inexistente 'ouro_de_tolo'", all);
        assertHas(errors, "encounters.yaml", "área inexistente 'lugar_nenhum'", all);
        assertHas(errors, "encounters.yaml", "ponto inexistente 'a/beco_fantasma'", all);
        assertHas(errors, "encounters.yaml", "inimigo inexistente 'fantasma'", all);
        assertHas(errors, "encounters.yaml", "chance precisa estar entre 0", all);
        assertHas(errors, "encounters.yaml", "sem 'cooldown' e sem 'once'", all);
        assertHas(errors, "deeds.yaml", "falta o ato 'execucao'", all);
        assertHas(errors, "deeds.yaml", "falta o ato 'agressao'", all);
        assertHas(errors, "areas/areas.yaml", "saída num ponto inexistente 'porta_fantasma'", all);
        assertHas(errors, "areas/areas.yaml", "saída para área inexistente 'lugar_nenhum'", all);
        assertHas(errors, "areas/areas.yaml", "ponto inexistente 'a/chegada_fantasma'", all);
        assertHas(errors, "areas/areas.yaml", "acordar numa área letal", all);
        assertHas(errors, "areas/areas.yaml", "NPC inexistente 'ninguem'", all);
        // AT-13 / RF-225: o desfecho bom não pode depender de vencer uma luta
        assertHas(errors, "arcs/briga.yaml", "'so_na_briga': o desfecho 'paz' só se alcança vencendo um combate", all);
        assertFalse(all.contains("'briga_opcional'"), "lutar pode ser um caminho, desde que não o único:\n" + all);
    }

    private static void assertHas(List<String> errors, String file, String text, String all) {
        assertTrue(errors.stream().anyMatch(e -> e.startsWith(file) && e.contains(text)),
                "esperava erro em " + file + " contendo \"" + text + "\"; vieram:\n" + all);
    }

    // ---------------------------------------------------------------- AT-13

    @Test
    void at13_nenhumArcoExigeVencerCombate() {
        // o validador já confere caminho pacífico até cada desfecho (conteudoDoM6CarregaEValida);
        // aqui, a prova em jogo: 30 dias sem lutar e todo arco chega a um desfecho
        Simulation sim = Simulation.newGame(content, 11, "vila_ref");
        sim.fastForward(hours(sim, 24 * 30));
        for (var e : sim.view().arcs().entrySet())
            assertNotNull(sim.arcOutcome(e.getKey()), "arco sem desfecho sem combate: " + e.getKey());
        assertTrue(sim.view().chronicle().stream().noneMatch(c -> c.type().startsWith("combate")));
        assertTrue(sim.stateWorld().vars.keySet().stream().noneMatch(k -> k.startsWith(Combat.KILLS_VAR)));
    }

    // ---------------------------------------------------------------- AT-14

    @Test
    void at14_perderParaSaqueadores_acordaHorasDepoisRoubadoEOMundoAndou() {
        Simulation sim = Simulation.newGame(content, 7, ROAD);
        at(sim, 18);
        sim.applyNow(List.of(new Effect.Give("sal_medicinal", 1, 30)));
        int money = sim.view().money();
        encounter(sim, "saqueadores_estrada");
        assertEquals(2, sim.view().enemies().size());
        besides(sim, first(sim), 1.0);
        sim.stateWorld().health = 6; // já chegou machucado
        long before = sim.view().clock().tick();

        List<SimEvent> events = runUntil(sim, 3000, SimEvent.PlayerDefeated.class);
        SimEvent.PlayerDefeated d = events.stream().filter(e -> e instanceof SimEvent.PlayerDefeated)
                .map(e -> (SimEvent.PlayerDefeated) e).findFirst().orElseThrow(() -> new AssertionError("não perdeu"));
        assertEquals("saqueador", d.by());
        double slept = (d.toTick() - d.fromTick()) / (double) sim.view().clock().ticksPerHour();
        assertTrue(slept >= Simulation.DEFEAT_MIN_HOURS && slept <= Simulation.DEFEAT_MAX_HOURS + 0.01, "dormiu " + slept);
        assertTrue(sim.view().clock().tick() - before >= hours(sim, Simulation.DEFEAT_MIN_HOURS), "o mundo andou");
        assertTrue(d.moneyLost() > 0 && sim.view().money() == money - d.moneyLost(), "roubado");
        assertTrue(d.itemsLost().contains("sal_medicinal"), "levaram o que valia mais: " + d.itemsLost());
        assertFalse(sim.view().dead());
        assertTrue(sim.view().enemies().isEmpty(), "ninguém ataca quem acabou de acordar");
        var spot = content.area(ROAD).spots().get("beira_estrada");
        assertEquals(spot.x(), sim.view().playerX(), 1e-6);
        assertEquals(spot.y(), sim.view().playerY(), 1e-6);
        assertEquals(Math.round(sim.combat().maxHealth() * Simulation.DEFEAT_HEALTH), sim.view().health(), 1e-6);
        // quem achou o jogador sabe, lembra, e comenta
        assertEquals("comerciante_posto", d.finder());
        assertTrue(sim.view().npcKnowledge("comerciante_posto").containsKey(Simulation.FOUND_FACT));
        assertTrue(sim.view().memories("comerciante_posto").stream().anyMatch(m -> m.type().equals("socorreu")));
        assertTrue(sim.view().chronicle().stream().anyMatch(c -> c.type().equals("jogador.derrotado")));
    }

    @Test
    void at14_morrerNaEstruturaMostraOMundoEPermiteCarregar(@TempDir Path dir) throws Exception {
        Simulation sim = Simulation.newGame(content, 3, RUIN);
        Path save = dir.resolve("antes.etrava");
        sim.save(save, "teste");
        String hashBefore = sim.stateHash();
        encounter(sim, "vulto_camara");
        besides(sim, first(sim), 1.0);
        sim.stateWorld().health = 5;
        List<SimEvent> events = runUntil(sim, 3000, SimEvent.PlayerDied.class);
        SimEvent.PlayerDied died = events.stream().filter(e -> e instanceof SimEvent.PlayerDied)
                .map(e -> (SimEvent.PlayerDied) e).findFirst().orElseThrow(() -> new AssertionError("não morreu"));
        assertEquals("vulto", died.by());
        assertFalse(died.summary().isEmpty(), "a tela de morte mostra o que aconteceu com o mundo");
        assertTrue(sim.view().dead());
        long tick = sim.view().clock().tick();
        step(sim, new Command.MoveTo(5, 5));
        assertEquals(tick, sim.view().clock().tick(), "morto, o mundo não anda");
        assertThrows(SaveGame.SaveException.class, () -> sim.save(dir.resolve("morto.etrava"), "x"));
        Simulation loaded = Simulation.load(content, save);
        assertFalse(loaded.view().dead());
        assertEquals(hashBefore, loaded.stateHash());
    }

    // ---------------------------------------------------------------- golpes, fuga e rendição

    @Test
    void jogadorAbateLobo_treinaLuta_eLevaAPele() {
        Simulation sim = Simulation.newGame(content, 5, ROAD);
        at(sim, 21);
        World w = sim.stateWorld();
        w.inventory.put("machadinha", 1);
        step(sim, new Command.Equip("machadinha"));
        w.skills.put("luta", 70.0);
        encounter(sim, "lobos_estrada");
        EnemyState wolf = first(sim);
        String id = wolf.id;
        besides(sim, wolf, 1.2);
        w.health = 10_000; // o teste é sobre o golpe do jogador
        List<SimEvent> events = new ArrayList<>();
        for (int i = 0; i < 2000 && sim.combat().enemy(id) != null; i++) {
            EnemyState e = sim.combat().enemy(id);
            if (!e.hostile()) break;
            if (!id.equals(sim.view().attackTarget())) sim.submit(new Command.Attack(id, true));
            events.addAll(sim.step());
        }
        assertTrue(events.stream().anyMatch(e -> e instanceof SimEvent.Hit h && h.attacker().equals(Combat.PLAYER)
                && h.damage() > 0));
        assertTrue(sim.view().skills().get("luta") > 70, "Luta sobe lutando");
        boolean killed = sim.view().var(Combat.KILLS_VAR + "lobo") >= 1;
        boolean fled = events.stream().anyMatch(e -> e instanceof SimEvent.EnemyChanged c && c.what().equals("fugiu"));
        assertTrue(killed || fled, "o lobo cai ou foge");
        if (killed) assertTrue(sim.view().inventory().getOrDefault("pele_lobo", 0) >= 1);
    }

    @Test
    void rendido_poupar_saquear_ouExecutarDianteDeTestemunha() {
        Simulation sim = Simulation.newGame(content, 9, ROAD);
        at(sim, 17);
        encounter(sim, "saqueadores_estrada");
        List<EnemyState> two = List.copyOf(sim.view().enemies());
        two.forEach(e -> e.state = EnemyState.SURRENDERED);
        // saquear: leva o que ele tinha, e ele vai embora
        int money = sim.view().money();
        besides(sim, two.get(0), 1.5);
        List<SimEvent> ev = step(sim, new Command.Resolve(two.get(0).id, Combat.ROB));
        assertEquals(money + 5, sim.view().money());
        assertTrue(sim.view().inventory().containsKey("faca"));
        assertEquals(EnemyState.LEAVING, two.get(0).state);
        assertTrue(ev.stream().anyMatch(e -> e instanceof SimEvent.EnemyChanged c && c.line() != null));
        // longe demais não dá
        ev = step(sim, new Command.Teleport(two.get(1).x + 6, two.get(1).y));
        if (Math.hypot(sim.view().playerX() - two.get(1).x, sim.view().playerY() - two.get(1).y) > Combat.RESOLVE_RANGE) {
            ev = step(sim, new Command.Resolve(two.get(1).id, Combat.EXECUTE));
            assertTrue(ev.stream().anyMatch(e -> e instanceof SimEvent.Refused r && r.reason().equals("combate.longe")));
        }
        // executar à vista do comerciante do Posto: ele viu, e muda de ideia
        ActorView merchant = sim.view().npc("comerciante_posto");
        EnemyState other = two.get(1);
        other.x = merchant.x() + 2;
        other.y = merchant.y();
        besides(sim, other, 1.2);
        int opinion = sim.view().opinion("comerciante_posto");
        ev = step(sim, new Command.Resolve(other.id, Combat.EXECUTE));
        assertTrue(ev.stream().anyMatch(e -> e instanceof SimEvent.DeedSeen d && d.deedId().equals("execucao")));
        assertEquals(opinion - 50, sim.view().opinion("comerciante_posto"));
        assertNull(sim.combat().enemy(other.id));
    }

    @Test
    void jogadorSeRende_levamODinheiroEVaoEmbora() {
        Simulation sim = Simulation.newGame(content, 4, ROAD);
        at(sim, 18);
        sim.applyNow(List.of(new Effect.Give(null, 0, 12)));
        int money = sim.view().money();
        encounter(sim, "saqueadores_estrada");
        besides(sim, first(sim), 2.0);
        List<SimEvent> ev = step(sim, new Command.Surrender());
        SimEvent.PlayerSurrendered s = ev.stream().filter(e -> e instanceof SimEvent.PlayerSurrendered)
                .map(e -> (SimEvent.PlayerSurrendered) e).findFirst().orElseThrow();
        assertEquals(Math.round(money * Combat.ROB_FRACTION), s.moneyLost());
        assertEquals(money - s.moneyLost(), sim.view().money());
        assertTrue(sim.view().enemies().stream().noneMatch(EnemyState::hostile));
        // lobo não aceita rendição
        Simulation night = Simulation.newGame(content, 4, ROAD);
        at(night, 22);
        encounter(night, "lobos_estrada");
        besides(night, first(night), 2.0);
        ev = step(night, new Command.Surrender());
        assertTrue(ev.stream().anyMatch(e -> e instanceof SimEvent.Refused r && r.reason().equals("combate.rendicao_ninguem")));
    }

    @Test
    void esquivaEvitaOGolpeAnunciado_eHabilidadesTemRecarga() {
        Simulation sim = Simulation.newGame(content, 2, ROAD);
        at(sim, 18);
        encounter(sim, "saqueadores_estrada");
        EnemyState e = first(sim);
        sim.view().enemies().removeIf(x -> x != e);
        besides(sim, e, 1.0);
        double health = sim.view().health();
        long now = sim.view().clock().tick();
        e.state = EnemyState.WINDUP;
        e.hitTick = now + 3;
        e.nextAttackTick = now + 10_000;
        List<SimEvent> ev = new ArrayList<>(step(sim, new Command.Dodge(0, 0)));
        for (int i = 0; i < 4; i++) ev.addAll(sim.step());
        assertTrue(ev.stream().anyMatch(x -> x instanceof SimEvent.Hit h && h.attacker().equals(e.id) && h.damage() == 0));
        assertEquals(health, sim.view().health(), 1e-9, "esquivando, nada acerta");
        assertTrue(sim.view().stamina() < sim.combat().maxStamina(), "esquivar cansa");

        sim.stateWorld().stamina = sim.combat().maxStamina();
        besides(sim, e, 1.0);
        ev = step(sim, new Command.Ability(Combat.PUSH));
        assertTrue(sim.combat().cooldownLeft(Combat.PUSH) > 0);
        assertTrue(ev.stream().anyMatch(x -> x instanceof SimEvent.EnemyChanged c && c.what().equals("empurrado")));
        ev = step(sim, new Command.Ability(Combat.PUSH));
        assertTrue(ev.stream().anyMatch(x -> x instanceof SimEvent.Refused r && r.reason().equals("combate.recarga")));
        sim.stateWorld().stamina = 3;
        ev = step(sim, new Command.Ability(Combat.HEAVY));
        assertTrue(ev.stream().anyMatch(x -> x instanceof SimEvent.Refused r && r.reason().equals("combate.sem_folego")));
    }

    @Test
    void emCombateNaoSeEsperaNemSalva_eFugirParaLongeFazODesistir(@TempDir Path dir) {
        Simulation sim = Simulation.newGame(content, 6, ROAD);
        at(sim, 18);
        encounter(sim, "saqueadores_estrada");
        besides(sim, first(sim), 3.0);
        sim.step();
        assertTrue(sim.combat().inCombat());
        long tick = sim.view().clock().tick();
        List<SimEvent> ev = step(sim, new Command.Wait(2));
        assertTrue(ev.stream().anyMatch(e -> e instanceof SimEvent.Refused r && r.reason().equals("combate.agora_nao")));
        assertTrue(sim.view().clock().tick() - tick < 5, "não esperou");
        assertThrows(SaveGame.SaveException.class, () -> sim.save(dir.resolve("x.etrava"), "x"));
        // corre para longe: eles desistem e voltam para onde estavam
        sim.stateWorld().health = 10_000;
        EnemyState e = first(sim);
        double far = 0;
        List<SimEvent> all = new ArrayList<>();
        for (var p : new double[][] {{33, 5}, {5, 5}, {35, 12}}) {
            double d = Math.hypot(p[0] - e.homeX, p[1] - e.homeY);
            if (d > far && !sim.nav().isBlockedAt(p[0], p[1])) {
                far = d;
                all.addAll(step(sim, new Command.Teleport(p[0], p[1])));
            }
        }
        for (int i = 0; i < 600; i++) all.addAll(sim.step());
        assertTrue(all.stream().anyMatch(x -> x instanceof SimEvent.EnemyChanged c && c.what().equals("desistiu")));
        assertFalse(sim.combat().inCombat());
    }

    @Test
    void ataqueNoMeioDoSonoInterrompe_eSaveGuardaOsInimigos(@TempDir Path dir) throws Exception {
        Simulation interrupted = null;
        for (long seed = 1; seed <= 40 && interrupted == null; seed++) {
            Simulation sim = Simulation.newGame(content, seed, ROAD);
            at(sim, 20.5);
            List<SimEvent> ev = step(sim, new Command.Sleep());
            if (ev.stream().anyMatch(e -> e instanceof SimEvent.TimeSkipped t && t.interrupted())
                    && !sim.view().enemies().isEmpty()) interrupted = sim;
        }
        assertNotNull(interrupted, "em 40 noites na estrada, ao menos uma deveria ter visita (RF-323)");
        Simulation sim = interrupted;
        int hour = sim.view().clock().hour();
        assertTrue(hour >= 20 || hour < 6, "acordou de noite: " + hour);
        // inimigos parados longe: dá para salvar, e o save os traz de volta
        step(sim, new Command.Teleport(5, 5));
        sim.view().enemies().forEach(e -> {
            e.state = EnemyState.IDLE;
            e.x = e.homeX = 33;
            e.y = e.homeY = 5;
        });
        assertFalse(sim.combat().inCombat());
        Path file = dir.resolve("noite.etrava");
        sim.save(file, "x");
        Simulation loaded = Simulation.load(content, file);
        assertEquals(sim.stateHash(), loaded.stateHash());
        assertEquals(sim.view().enemies().size(), loaded.view().enemies().size());
    }

    @Test
    void saveDaVersao4CarregaComVidaCheia(@TempDir Path dir) throws Exception {
        Simulation sim = Simulation.newGame(content, 8, "vila_ref");
        Path file = dir.resolve("v5.etrava");
        sim.save(file, "x");
        // reescreve o state.json como a versão 4 gravava: sem o bloco de combate
        Path old = dir.resolve("v4.etrava");
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(file));
                ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(old))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                in.transferTo(buf);
                String text = buf.toString(StandardCharsets.UTF_8);
                if (e.getName().equals("state.json")) {
                    var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(text);
                    ((com.fasterxml.jackson.databind.node.ObjectNode) json).remove("combat");
                    ((com.fasterxml.jackson.databind.node.ObjectNode) json).put("schemaVersion", 4);
                    text = json.toString();
                }
                out.putNextEntry(new ZipEntry(e.getName()));
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        Simulation loaded = Simulation.load(content, old);
        assertEquals(loaded.combat().maxHealth(), loaded.view().health(), 1e-9);
        assertEquals(loaded.combat().maxStamina(), loaded.view().stamina(), 1e-9);
        assertTrue(loaded.view().enemies().isEmpty());
    }

    // ---------------------------------------------------------------- estrutura, luz e agressão

    @Test
    void descerPeloBuraco_aEstruturaEEscura_eALamparinaAumentaOQueSeVe() {
        Simulation sim = Simulation.newGame(content, 12, ROAD);
        at(sim, 12);
        assertEquals(Double.POSITIVE_INFINITY, sim.combat().lightRadius(), "de dia, na estrada, vê-se tudo");
        var hole = content.area(ROAD).spots().get("buraco");
        step(sim, new Command.Teleport(hole.x() - 2, hole.y()));
        List<SimEvent> ev = new ArrayList<>();
        for (int i = 0; i < 90 && RUIN.equals(sim.view().areaId()) == false; i++)
            ev.addAll(step(sim, new Command.MoveDir(1, 0)));
        assertEquals(RUIN, sim.view().areaId(), "andar até o buraco é descer");
        step(sim, new Command.MoveDir(0, 0));
        assertTrue(ev.stream().anyMatch(e -> e instanceof SimEvent.AreaChanged a && a.toArea().equals(RUIN)));
        assertTrue(ev.stream().anyMatch(e -> e instanceof SimEvent.EnemiesAppeared a && a.encounterId().equals("vulto_camara")),
                "o que mora lá embaixo está no fundo da câmara");
        assertFalse(sim.combat().inCombat(), "longe, ainda não percebeu ninguém");
        assertEquals(Combat.DARK_LIGHT, sim.combat().lightRadius(), 1e-9);
        sim.stateWorld().inventory.put("lamparina", 1);
        step(sim, new Command.Equip("lamparina"));
        assertEquals(Combat.DARK_LIGHT + 6 * Combat.LIGHT_PER_POINT, sim.combat().lightRadius(), 1e-9);
        // o nicho tem o que não se explica
        sim.submit(new Command.OpenContainer("nicho_estrutura"));
        for (int i = 0; i < 600 && sim.view().trade() == null; i++) sim.step();
        assertNotNull(sim.view().trade(), "chegou ao nicho");
        assertTrue(sim.tradeView().offers().stream().anyMatch(o -> o.item().equals("placa_simbolos")));
        step(sim, new Command.CloseTrade());
    }

    @Test
    void empurrarUmMoradorEAgressao() {
        Simulation sim = Simulation.newGame(content, 13, ROAD);
        at(sim, 12);
        ActorView merchant = sim.view().npc("comerciante_posto");
        step(sim, new Command.Teleport(merchant.x() + 1, merchant.y()));
        int opinion = sim.view().opinion("comerciante_posto");
        List<SimEvent> ev = step(sim, new Command.Strike("comerciante_posto"));
        assertTrue(ev.stream().anyMatch(e -> e instanceof SimEvent.DeedSeen d && d.deedId().equals("agressao")));
        assertEquals(opinion - 45, sim.view().opinion("comerciante_posto"));
        assertTrue(ev.stream().anyMatch(e -> e instanceof SimEvent.AmbientLine l && "comerciante_posto".equals(l.npcId())));
    }

    @Test
    void vidaVoltaComOTempo_eUnguentoCura() {
        Simulation sim = Simulation.newGame(content, 14, "vila_ref");
        sim.stateWorld().health = 20;
        sim.fastForward(hours(sim, 1));
        assertEquals(20 + 6 * Combat.HEAL_PER_10_MIN, sim.view().health(), 1e-9);
        sim.stateWorld().inventory.put("unguento_sal", 1);
        step(sim, new Command.UseItem("unguento_sal"));
        assertEquals(20 + 6 * Combat.HEAL_PER_10_MIN + 20, sim.view().health(), 1e-9);
    }
}
