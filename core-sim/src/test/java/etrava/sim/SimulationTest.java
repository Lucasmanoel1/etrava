package etrava.sim;

import static org.junit.jupiter.api.Assertions.*;

import etrava.sim.api.Command;
import etrava.sim.api.SimEvent;
import etrava.sim.api.Simulation;
import etrava.sim.api.WorldView;
import etrava.sim.content.AreaDef;
import etrava.sim.content.ContentDb;
import etrava.sim.validation.ContentValidator;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Testes com o conteúdo real do repositório (pasta content/). */
class SimulationTest {
    private static final String AREA = "area_teste";
    private static ContentDb content;

    @BeforeAll
    static void loadContent() {
        content = ContentDb.load(Path.of(System.getProperty("etrava.content", "../content")));
    }

    private static Simulation newSim() {
        return Simulation.newGame(content, 42, AREA);
    }

    private static List<SimEvent> run(Simulation sim, int ticks) {
        List<SimEvent> all = new ArrayList<>();
        for (int i = 0; i < ticks; i++) all.addAll(sim.step());
        return all;
    }

    @Test
    void conteudoDoRepositorioEhValido() {
        assertEquals(List.of(), ContentValidator.validate(content));
    }

    @Test
    void mapaDeTesteCarregaCamadas() {
        AreaDef area = content.area(AREA);
        assertEquals(40, area.width());
        assertEquals("terra", area.terrainAt(19, 2));
        assertEquals("pedra", area.terrainAt(15, 15));
        assertEquals("grama", area.terrainAt(2, 2));
        assertEquals(AreaDef.NO_TERRAIN, area.terrainAt(-1, 0));
        assertTrue(area.collisionAt(0, 0));
        assertTrue(area.collisionAt(99, 99));
        assertFalse(area.collisionAt(9, 11), "a porta da oficina fica aberta");
        assertTrue(area.spots().containsKey("oficina_bancada"));
        assertFalse(area.props().isEmpty());
    }

    @Test
    void jogadorComecaNoSpawn() {
        WorldView v = newSim().view();
        assertEquals(20.5, v.playerX(), 1e-9);
        assertEquals(23.5, v.playerY(), 1e-9);
        assertEquals(AREA, v.areaId());
        assertEquals(42, v.seed());
    }

    @Test
    void cliqueLevaOJogadorAteODestino() {
        Simulation sim = newSim();
        sim.submit(new Command.MoveTo(17.5, 28.5));
        List<SimEvent> events = run(sim, 30 * 10);
        assertTrue(events.stream().anyMatch(e -> e instanceof SimEvent.PlayerArrived));
        assertEquals(17.5, sim.view().playerX(), 1e-6);
        assertEquals(28.5, sim.view().playerY(), 1e-6);
        assertFalse(sim.view().playerMoving());
        assertFalse(sim.view().hasDestination());
    }

    @Test
    void jogadorEntraNaOficinaPelaPortaSemAtravessarParede() {
        Simulation sim = newSim();
        sim.submit(new Command.MoveTo(8.5, 9.0)); // ponto "oficina_bancada", dentro da construção
        for (int i = 0; i < 30 * 30; i++) {
            sim.step();
            assertFalse(sim.nav().isBlockedAt(sim.view().playerX(), sim.view().playerY()),
                    "jogador dentro de obstáculo no tick " + i);
        }
        assertEquals(8.5, sim.view().playerX(), 1e-6);
        assertEquals(9.0, sim.view().playerY(), 1e-6);
    }

    @Test
    void cliqueEmObstaculoAjustaODestino() {
        Simulation sim = newSim();
        sim.submit(new Command.MoveTo(20, 20)); // em cima do poço
        List<SimEvent> events = run(sim, 30 * 10);
        assertTrue(events.stream().anyMatch(e -> e instanceof SimEvent.DestinationAdjusted));
        assertFalse(sim.nav().isBlockedAt(sim.view().playerX(), sim.view().playerY()));
        assertTrue(Math.hypot(sim.view().playerX() - 20, sim.view().playerY() - 20) < 2.0);
    }

    @Test
    void wasdAndaEParaNaParede() {
        Simulation sim = newSim();
        sim.submit(new Command.MoveDir(0, 1)); // sul, em direção à borda do mapa
        run(sim, 30 * 20);
        assertTrue(sim.view().playerY() > 38.0, "deveria ter chegado perto da borda");
        assertFalse(sim.nav().isBlockedAt(sim.view().playerX(), sim.view().playerY()));
        assertFalse(sim.view().playerMoving(), "encostado na parede não está andando");
        sim.submit(new Command.MoveDir(0, 0));
        run(sim, 2);
        double y = sim.view().playerY();
        run(sim, 30);
        assertEquals(y, sim.view().playerY(), 1e-9);
    }

    @Test
    void wasdCancelaCaminhoDoClique() {
        Simulation sim = newSim();
        sim.submit(new Command.MoveTo(17.5, 28.5));
        run(sim, 5);
        assertTrue(sim.view().hasDestination());
        sim.submit(new Command.MoveDir(1, 0));
        run(sim, 5);
        assertFalse(sim.view().hasDestination());
        assertTrue(sim.view().playerMoving());
    }

    @Test
    void teleporteIgnoraDestinoBloqueado() {
        Simulation sim = newSim();
        sim.submit(new Command.Teleport(3.5, 3.5));
        sim.step();
        assertEquals(3.5, sim.view().playerX(), 1e-9);
        sim.submit(new Command.Teleport(0.5, 0.5)); // parede da borda
        sim.step();
        assertEquals(3.5, sim.view().playerX(), 1e-9);
    }

    @Test
    void avancoRapidoMoveSoORelogio() {
        Simulation sim = newSim();
        sim.fastForward(sim.view().clock().ticksPerHour() * 3);
        assertEquals("Dia 1, 09:00", sim.view().clock().format());
        assertThrows(IllegalArgumentException.class, () -> sim.fastForward(-1));
    }

    @Test
    void areaInexistenteFalhaComMensagemClara() {
        assertThrows(IllegalArgumentException.class, () -> Simulation.newGame(content, 1, "nao_existe"));
    }

    /** Semente igual + mesmos comandos nos mesmos ticks → mesmo resultado (base do AT-03). */
    @Test
    void reexecutarComandosGravadosDaOMesmoEstado() {
        Simulation a = newSim();
        a.submit(new Command.MoveTo(8.5, 9.0));
        run(a, 120);
        a.submit(new Command.MoveDir(1, -1));
        run(a, 45);
        a.submit(new Command.MoveTo(30.5, 20.5));
        run(a, 400);

        Simulation b = newSim();
        List<Command.Timed> log = a.commandLog();
        int next = 0;
        while (b.view().clock().tick() < a.view().clock().tick()) {
            while (next < log.size() && log.get(next).tick() == b.view().clock().tick()) {
                b.submit(log.get(next++).command());
            }
            b.step();
        }
        assertEquals(log.size(), next);
        assertEquals(a.view().playerX(), b.view().playerX());
        assertEquals(a.view().playerY(), b.view().playerY());
        assertEquals(a.view().playerFacing(), b.view().playerFacing());
        assertEquals(a.commandLog(), b.commandLog());
    }

    // ---------- validador com conteúdo quebrado de propósito (AT-04, parte do M0) ----------

    @Test
    void validadorRejeitaConteudoQuebradoApontandoArquivoEItem() throws URISyntaxException {
        Path broken = Path.of(SimulationTest.class.getResource("/quebrado").toURI());
        List<String> problems = ContentValidator.validate(broken);
        assertTrue(has(problems, "areas/ruim.tmj", "prop inexistente 'prop_fantasma'"), problems.toString());
        assertTrue(has(problems, "areas/ruim.tmj", "spawn 'jogador' está em local bloqueado"), problems.toString());
        assertTrue(has(problems, "areas/areas.yaml", "nao_existe.tmj"), problems.toString());
    }

    @Test
    void validadorAcusaPastaSemConteudo() {
        List<String> problems = ContentValidator.validate(Path.of("pasta_que_nao_existe"));
        assertTrue(has(problems, "props.yaml", "não foi possível ler"), problems.toString());
        assertTrue(has(problems, "areas/areas.yaml", "nenhuma área carregada"), problems.toString());
    }

    private static boolean has(List<String> problems, String file, String text) {
        return problems.stream().anyMatch(p -> p.startsWith(file + ":") && p.contains(text));
    }
}
