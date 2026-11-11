package etrava.sim;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import etrava.sim.api.ActorView;
import etrava.sim.api.Command;
import etrava.sim.api.SimEvent;
import etrava.sim.api.Simulation;
import etrava.sim.content.ContentDb;
import etrava.sim.content.NpcDef;
import etrava.sim.rules.Effect;
import etrava.sim.save.SaveGame;
import etrava.sim.save.SaveGame.SaveException;
import etrava.sim.world.ChronicleEntry;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** M2: tempo, agendas, eventos agendados, save/load e determinismo (AT-03, AT-05, AT-06, AT-07). */
class WorldSimulationTest {
    private static final String AREA = "vila";
    private static ContentDb content;

    @BeforeAll
    static void load() {
        content = ContentDb.load(Path.of(System.getProperty("etrava.content", "../content")));
    }

    private static Simulation newSim(long seed) {
        return Simulation.newGame(content, seed, AREA);
    }

    private static List<SimEvent> run(Simulation sim, int ticks) {
        List<SimEvent> all = new ArrayList<>();
        for (int i = 0; i < ticks; i++) all.addAll(sim.step());
        return all;
    }

    private static long hours(Simulation sim, double h) {
        return Math.round(h * sim.view().clock().ticksPerHour());
    }

    private static List<String> chronicleText(Simulation sim) {
        return sim.view().chronicle().stream().map(c -> c.tick() + ":" + c.type() + ":" + c.text()).toList();
    }

    // ---------------------------------------------------------------- agendas

    @Test
    void agendaDosNpcsCobreODiaInteiro() {
        assertEquals(23, content.npcs().size());
        for (NpcDef npc : content.npcs().values()) {
            for (int minute = 0; minute < 1440; minute += 5) {
                assertNotNull(npc.blockAt(minute), npc.id() + " sem bloco no minuto " + minute);
            }
        }
        NpcDef.Block night = content.npcs().get("ferreiro").blockAt(2 * 60);
        assertEquals("dormir", night.activity());
        assertEquals("vila", night.area());
        assertEquals("casa_doran", night.spot());
        assertTrue(night.hidden());
        assertTrue(night.wellFormed());
    }

    @Test
    void npcsComecamOndeAAgendaManda() {
        Simulation sim = newSim(1); // 06:00
        ActorView ferreiro = sim.view().npc("ferreiro");
        assertEquals("trabalhar", ferreiro.activity());
        assertEquals("vila", sim.view().npcArea("ferreiro"));
        assertEquals(14.8, ferreiro.x(), 1e-9); // na forja
        // o viajante ainda dorme na taverna: está na área, mas não aparece
        assertEquals("dormir", sim.view().npc("viajante").activity());
        assertTrue(sim.view().actors().stream().noneMatch(a -> a.id().equals("viajante")));
        assertTrue(sim.view().actors().stream().anyMatch(a -> a.id().equals("ferreiro")));
        assertNull(sim.view().npc("ninguem"));
        assertNull(sim.view().npcArea("ninguem"));
    }

    @Test
    void blocoNovoNoMesmoLugarTrocaAAtividade() {
        Simulation sim = newSim(1);
        sim.fastForward(hours(sim, 22)); // 04:00: o velho Anselmo dorme dentro de casa
        assertEquals("dormir", sim.view().npc("anselmo").activity());
        assertTrue(sim.view().actors().stream().noneMatch(a -> a.id().equals("anselmo")));
        run(sim, (int) hours(sim, 3.2)); // 07:12: senta à porta, no mesmo ponto
        assertEquals("sentar", sim.view().npc("anselmo").activity());
        assertTrue(sim.view().actors().stream().anyMatch(a -> a.id().equals("anselmo")));
        sim.fastForward(hours(sim, 24)); // e o mesmo vale no avanço rápido
        assertEquals("sentar", sim.view().npc("anselmo").activity());
    }

    @Test
    void npcAndaAteONovoLugarQuandoOBlocoMuda() {
        Simulation sim = newSim(1);
        ActorView aldea = sim.view().npc("aldea"); // 06:00–09:00 no poço
        sim.fastForward(hours(sim, 2.9));           // 08:54
        double x0 = aldea.x(), y0 = aldea.y();
        assertEquals("buscar_agua", aldea.activity());
        boolean walked = false;
        for (int i = 0; i < 30 * 60; i++) {          // passa das 09:00 em tempo real de jogo
            sim.step();
            walked |= aldea.moving();
            assertFalse(sim.nav().isBlockedAt(aldea.x(), aldea.y()), "NPC dentro de obstáculo");
        }
        assertTrue(walked, "depois das 09:00 ela deveria andar até o pomar");
        assertTrue(Math.hypot(aldea.x() - x0, aldea.y() - y0) > 2);
        assertEquals("trabalhar", sim.view().npc("aldea").activity());
        assertEquals(24.0, aldea.x(), 1e-6);
    }

    @Test
    void npcSaiDaAreaPelaEntradaEContinuaExistindoLaFora() {
        Simulation sim = newSim(1);
        sim.fastForward(hours(sim, 1.5));            // 07:30: viajante na praça
        ActorView viajante = sim.view().npc("viajante");
        assertEquals("vila", sim.view().npcArea("viajante"));
        assertTrue(sim.view().actors().contains(viajante));
        run(sim, (int) hours(sim, 0.6));             // passa das 08:00: começa a ir embora, a pé
        assertEquals(Simulation.WALKING, viajante.activity());
        assertEquals("vila", sim.view().npcArea("viajante"));
        run(sim, 30 * 40);                           // tempo de sobra para chegar à saída
        assertEquals("estrada", sim.view().npcArea("viajante"));
        assertFalse(sim.view().actors().contains(viajante));
        assertEquals("viajar", viajante.activity());
        sim.fastForward(hours(sim, 9.5));            // 17:30 em diante: de volta, no balcão da taverna
        assertEquals("taverna", sim.view().npcArea("viajante"));
    }

    @Test
    void jogadorEncontraOViajanteNaOutraArea() {
        Simulation sim = newSim(1);
        sim.fastForward(hours(sim, 4));              // 10:00
        sim.submit(new Command.EnterArea("estrada", null));
        List<SimEvent> events = sim.step();
        assertTrue(events.contains(new SimEvent.AreaChanged("vila", "estrada")));
        assertEquals("estrada", sim.view().areaId());
        assertEquals(60.0, sim.view().playerX(), 1e-9);
        // no Posto, de manhã: o viajante, o carregador e o comerciante (Vessa ainda dorme)
        assertEquals(List.of("viajante", "tobias", "comerciante_posto"),
                sim.view().actors().stream().map(a -> a.id()).sorted(java.util.Comparator.reverseOrder()).toList());
        sim.submit(new Command.EnterArea("nao_existe", null)); // ignorado
        sim.step();
        assertEquals("estrada", sim.view().areaId());
    }

    // ---------------------------------------------------------------- tempo

    @Test
    void esperarAvancaAsHorasPedidasComLimiteDe12() {
        Simulation sim = newSim(1);
        sim.submit(new Command.Wait(3));
        List<SimEvent> events = sim.step();
        assertEquals("Dia 1, 09:00", sim.view().clock().format());
        SimEvent.TimeSkipped skip = (SimEvent.TimeSkipped) events.get(0);
        assertFalse(skip.slept());
        assertFalse(skip.interrupted());
        sim.submit(new Command.Wait(99));
        sim.step();
        assertEquals("Dia 1, 21:00", sim.view().clock().format());
    }

    @Test
    void dormirVaiAteAsSeisDaManhaSeguinte() {
        Simulation sim = newSim(1);
        sim.fastForward(hours(sim, 16.5));           // 22:30
        sim.submit(new Command.Sleep());
        List<SimEvent> events = sim.step();
        assertEquals("Dia 2, 06:00", sim.view().clock().format());
        assertTrue(((SimEvent.TimeSkipped) events.get(0)).slept());
        assertTrue(chronicleText(sim).stream().anyMatch(s -> s.contains("dia:Dia 2")));
        assertEquals("trabalhar", sim.view().npc("ferreiro").activity(), "o mundo acordou junto");
    }

    /** AT-07: dormir 12 h processa todos os gatilhos do intervalo, na ordem. */
    @Test
    void eventoDas3DaManhaAconteceMesmoDormindo() {
        Simulation sim = newSim(1);
        sim.fastForward(hours(sim, 12));             // 18:00
        long start = sim.view().clock().tick();
        long threeAm = start + hours(sim, 9);
        // agendados fora de ordem de propósito
        sim.schedule(threeAm + 50, false, List.of(new Effect.Chronicle("teste", "c", "terceiro")));
        sim.schedule(threeAm, false, List.of(new Effect.SetFlag("sino_tocou", true),
                new Effect.SetVar("badaladas", 3), new Effect.Chronicle("teste", "a", "primeiro")));
        sim.schedule(threeAm, false, List.of(new Effect.Chronicle("teste", "b", "segundo")));
        sim.submit(new Command.Sleep());
        sim.step();

        assertEquals("Dia 2, 06:00", sim.view().clock().format());
        assertTrue(sim.view().flag("sino_tocou"));
        assertEquals(3, sim.view().var("badaladas"));
        assertFalse(sim.view().flag("nunca"));
        assertEquals(0, sim.view().var("nunca"));
        List<ChronicleEntry> mine = sim.view().chronicle().stream().filter(c -> c.type().equals("teste")).toList();
        assertEquals(List.of("primeiro", "segundo", "terceiro"), mine.stream().map(ChronicleEntry::text).toList());
        assertEquals(threeAm, mine.get(0).tick(), "o evento fica registrado na hora em que estava marcado");
        assertEquals("Dia 2, 03:00", formatAt(sim, mine.get(0).tick()));
    }

    private static String formatAt(Simulation sim, long tick) {
        etrava.sim.time.GameClock c = new etrava.sim.time.GameClock(sim.view().clock().ticksPerDay());
        c.setTick(tick);
        return c.format();
    }

    @Test
    void eventoQueInterrompeAcordaOJogador() {
        Simulation sim = newSim(1);
        sim.fastForward(hours(sim, 16));             // 22:00
        long knock = sim.view().clock().tick() + hours(sim, 4);
        sim.schedule(knock, true, List.of(new Effect.Chronicle("teste", "", "batem à porta")));
        sim.submit(new Command.Sleep());
        SimEvent.TimeSkipped skip = (SimEvent.TimeSkipped) sim.step().get(0);
        assertTrue(skip.interrupted());
        assertEquals(knock, sim.view().clock().tick());
        assertEquals("Dia 2, 02:00", sim.view().clock().format());
    }

    @Test
    void eventoAgendadoNoPassadoDisparaNoProximoTick() {
        Simulation sim = newSim(1);
        sim.schedule(0, false, List.of(new Effect.SetFlag("atrasado", true)));
        assertFalse(sim.view().flag("atrasado"));
        sim.step();
        assertTrue(sim.view().flag("atrasado"));
    }

    // ---------------------------------------------------------------- determinismo (AT-03)

    private static Simulation scriptedRun(long seed) {
        Simulation sim = newSim(seed);
        sim.schedule(hours(sim, 30), false, List.of(new Effect.Chronicle("teste", "", "evento do segundo dia")));
        sim.submit(new Command.MoveTo(22.0, 30.0));
        run(sim, 200);
        sim.submit(new Command.Wait(5));
        run(sim, 50);
        sim.submit(new Command.MoveDir(1, 0));
        run(sim, 40);
        sim.submit(new Command.MoveDir(0, 0));
        sim.submit(new Command.EnterArea("estrada", null));
        run(sim, 300);
        sim.submit(new Command.EnterArea("vila", null));
        sim.submit(new Command.Sleep());
        run(sim, 500);
        return sim;
    }

    @Test
    void mesmaSementeEMesmosComandosDaoCronicaEEstadoIdenticos() {
        Simulation a = scriptedRun(42);

        // reexecuta a partir do registro de comandos gravado
        Simulation b = newSim(42);
        b.schedule(hours(b, 30), false, List.of(new Effect.Chronicle("teste", "", "evento do segundo dia")));
        List<Command.Timed> log = a.commandLog();
        int next = 0;
        while (b.view().clock().tick() < a.view().clock().tick()) {
            while (next < log.size() && log.get(next).tick() == b.view().clock().tick()) {
                b.submit(log.get(next++).command());
            }
            b.step();
        }
        assertEquals(log.size(), next);
        assertEquals(chronicleText(a), chronicleText(b));
        assertEquals(a.stateHash(), b.stateHash());
        assertFalse(chronicleText(a).isEmpty());
        assertNotEquals(a.stateHash(), scriptedRun(43).stateHash(), "outra semente, outro estado");
    }

    // ---------------------------------------------------------------- save/load (AT-05, AT-06)

    /** AT-05: salvar no dia 7, carregar, comparar; avançar 5 dias nos dois e comparar de novo. */
    @Test
    void salvarECarregarPreservaOEstadoEOFuturo(@TempDir Path dir) throws SaveException {
        Simulation original = newSim(7);
        original.schedule(hours(original, 24 * 8), true, List.of(new Effect.SetFlag("oitavo_dia", true),
                new Effect.SetVar("x", 1.5), new Effect.Chronicle("teste", "ref", "evento futuro")));
        original.fastForward(hours(original, 24 * 6 + 3)); // dia 7, 09:00
        original.submit(new Command.MoveTo(30.0, 26.0));
        run(original, 40);                                 // salva no meio de uma caminhada
        assertEquals(7, original.view().clock().day());

        Path file = dir.resolve("teste" + SaveGame.EXTENSION);
        original.save(file, "2026-11-10T20:00:00");
        assertFalse(Files.exists(dir.resolve("teste" + SaveGame.EXTENSION + ".tmp")), "não sobra arquivo temporário");
        SaveGame.Meta meta = SaveGame.readMeta(file);
        assertEquals(SaveGame.SCHEMA_VERSION, meta.schemaVersion());
        assertEquals(7, meta.day());
        assertEquals("vila", meta.areaId());
        assertEquals("2026-11-10T20:00:00", meta.savedAt());

        Simulation loaded = Simulation.load(content, file);
        assertEquals(original.stateHash(), loaded.stateHash());
        assertEquals(original.view().playerX(), loaded.view().playerX());
        assertTrue(loaded.view().playerMoving());

        for (Simulation sim : List.of(original, loaded)) {
            run(sim, 600);                                 // termina a caminhada
            sim.submit(new Command.Sleep());
            run(sim, 10);
            sim.fastForward(hours(sim, 24 * 4));
        }
        assertEquals(original.stateHash(), loaded.stateHash());
        assertEquals(chronicleText(original), chronicleText(loaded));
        assertTrue(loaded.view().flag("oitavo_dia"));
        assertEquals(1.5, loaded.view().var("x"));
        assertTrue(loaded.view().clock().day() >= 12);

        original.save(file, "depois"); // salvar por cima funciona
        assertEquals("depois", SaveGame.readMeta(file).savedAt());
    }

    @Test
    void saveCorrompidoGeraErroTratado(@TempDir Path dir) throws IOException {
        Path garbage = dir.resolve("lixo.etrava");
        Files.writeString(garbage, "isto não é um save");
        SaveException e = assertThrows(SaveException.class, () -> Simulation.load(content, garbage));
        assertTrue(e.getMessage().contains("corrompido"), e.getMessage());
        assertNull(SaveGame.readMeta(garbage));

        Path missing = dir.resolve("nao_existe.etrava");
        assertThrows(SaveException.class, () -> Simulation.load(content, missing));
        assertNull(SaveGame.readMeta(missing));

        Path partial = dir.resolve("parcial.etrava"); // ZIP válido, mas sem o estado
        writeZip(partial, "{}", null);
        assertThrows(SaveException.class, () -> Simulation.load(content, partial));

        Path broken = dir.resolve("quebrado.etrava"); // estado com efeito que não existe
        writeZip(broken, "{}", "{\"schemaVersion\":" + SaveGame.SCHEMA_VERSION
                + ",\"meta\":{\"ticksPerDay\":36000,\"area\":\"vila_ref\"},"
                + "\"scheduled\":[{\"tick\":5,\"effects\":[{\"explodir\":{}}]}]}");
        e = assertThrows(SaveException.class, () -> Simulation.load(content, broken));
        assertTrue(e.getMessage().contains("corrompido"), e.getMessage());
    }

    @Test
    void saveDeVersaoAntigaEhMigradoOuRecusado(@TempDir Path dir) throws Exception {
        Simulation sim = newSim(3);
        sim.fastForward(hours(sim, 5));
        ObjectNode state = SaveGame.toJson(sim.stateWorld());
        // simula o formato anterior, em que "vars" se chamava "variaveis"
        int old = SaveGame.SCHEMA_VERSION - 1;
        state.put("schemaVersion", old);
        state.set("variaveis", state.remove("vars"));
        ((ObjectNode) state.get("variaveis")).put("moedas", 12.0);
        Path oldFile = dir.resolve("antigo.etrava");
        writeZip(oldFile, "{}", state.toString());

        // a 4 e a 5 têm migração em produção (M6, M7); uma versão sem migração nenhuma é recusada
        assertDoesNotThrow(() -> SaveGame.read(oldFile).world());
        ObjectNode ancient = state.deepCopy();
        ancient.put("schemaVersion", 3);
        Path ancientFile = dir.resolve("antiquissimo.etrava");
        writeZip(ancientFile, "{}", ancient.toString());
        SaveException refused = assertThrows(SaveException.class, () -> SaveGame.read(ancientFile));
        assertTrue(refused.getMessage().contains("versão antiga"), refused.getMessage());

        SaveGame.Migration v0 = new SaveGame.Migration() {
            @Override public int from() { return old; }
            @Override public void apply(ObjectNode s) { s.set("vars", s.remove("variaveis")); }
        };
        SaveGame.Loaded migrated = SaveGame.read(oldFile, List.of(v0));
        assertEquals(12.0, migrated.world().var("moedas"));
        assertEquals(sim.view().clock().tick(), migrated.world().clock().tick());

        state.put("schemaVersion", SaveGame.SCHEMA_VERSION + 1);
        Path future = dir.resolve("futuro.etrava");
        writeZip(future, "{}", state.toString());
        SaveException newer = assertThrows(SaveException.class, () -> SaveGame.read(future));
        assertTrue(newer.getMessage().contains("versão mais nova"), newer.getMessage());
    }

    @Test
    void npcNovoNoConteudoEntraNoJogoCarregado(@TempDir Path dir) throws Exception {
        Simulation sim = newSim(3);
        ObjectNode state = SaveGame.toJson(sim.stateWorld());
        ((ObjectNode) state.get("npcs")).remove("moleiro");                 // save de antes de o moleiro existir
        ((ObjectNode) state.get("npcs")).set("fantasma", state.get("npcs").get("aldea").deepCopy()); // e com um NPC que saiu
        Path file = dir.resolve("s.etrava");
        writeZip(file, "{}", state.toString());
        Simulation loaded = Simulation.load(content, file);
        assertEquals("trabalhar", loaded.view().npc("moleiro").activity());
        assertEquals("moinho", loaded.view().npcArea("moleiro"));
        run(loaded, 30 * 60 * 2); // o NPC sem definição não derruba nada
        assertNotNull(loaded.view().npc("fantasma"));
    }

    private static void writeZip(Path file, String meta, String state) throws IOException {
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("meta.json"));
            zip.write(meta.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            if (state != null) {
                zip.putNextEntry(new ZipEntry("state.json"));
                zip.write(state.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    // ---------------------------------------------------------------- desempenho (RNF-024, RNF-025)

    @Test
    @Timeout(60)
    void trintaDiasHeadlessCabemNoTempo() {
        Simulation sim = newSim(11);
        sim.fastForward(hours(sim, 24 * 30));
        assertEquals(31, sim.view().clock().day());
        assertEquals(30, sim.view().chronicle().stream().filter(c -> c.type().equals("dia")).count());
    }

    @Test
    @Timeout(3)
    void saltoDeDozeHorasEhRapido() {
        Simulation sim = newSim(11);
        sim.submit(new Command.Wait(12));
        sim.step();
        assertEquals("Dia 1, 18:00", sim.view().clock().format());
    }
}
