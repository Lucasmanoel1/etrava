package etrava.sim;

import static org.junit.jupiter.api.Assertions.*;

import etrava.sim.api.ActorView;
import etrava.sim.api.Command;
import etrava.sim.api.Simulation;
import etrava.sim.content.AreaDef;
import etrava.sim.content.AssetManifest;
import etrava.sim.content.ContentDb;
import etrava.sim.content.PropDef;
import etrava.sim.validation.ContentValidator;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Manifesto de assets, cena de referência e figurantes (M1). */
class AssetsAndActorsTest {
    private static final String AREA = "vila_ref";
    private static Path contentDir, assetsDir;
    private static ContentDb content;

    @BeforeAll
    static void load() {
        contentDir = Path.of(System.getProperty("etrava.content", "../content"));
        assetsDir = Path.of(System.getProperty("etrava.assets", "../assets"));
        content = ContentDb.load(contentDir);
    }

    // ---------- manifesto ----------

    @Test
    void manifestoCarregaSpritesTilesEFolhas() {
        AssetManifest manifest = AssetManifest.load(assetsDir);
        assertEquals(List.of(), manifest.problems());

        AssetManifest.Entry house = manifest.get("prop.casa_oficina");
        assertEquals("sprite", house.type());
        assertEquals(2, house.anchor().length);
        assertNotNull(house.emissive(), "a casa tem camada de brilho para a noite");
        assertEquals(2, house.allFiles().size());

        AssetManifest.Entry grass = manifest.get("tile.grama");
        assertEquals(3, grass.files().size());
        assertTrue(grass.priority() > manifest.get("tile.pedra").priority(), "grama avança sobre a pedra");

        AssetManifest.Entry hero = manifest.get("personagem.aprendiz");
        assertEquals(8, hero.directions());
        assertEquals(8, hero.animations().get("walk").frames());
        assertTrue(hero.animations().get("idle").fps() > 0);
        assertFalse(manifest.has("nao.existe"));
        assertNull(manifest.get("nao.existe"));
    }

    @Test
    void arquivoPendenteEstaNoManifestoMasSemImagem() {
        AssetManifest manifest = AssetManifest.load(assetsDir);
        assertTrue(manifest.has("prop.bancada"), "pendentes.yaml declara o ID mesmo sem a imagem");
    }

    @Test
    void manifestoAusenteViraProblemaENaoExcecao() {
        AssetManifest manifest = AssetManifest.load(Path.of("pasta_que_nao_existe"));
        assertTrue(manifest.entries().isEmpty());
        assertEquals(1, manifest.problems().size());
    }

    // ---------- validador com assets ----------

    @Test
    void conteudoEAssetsDoRepositorioSaoValidos() {
        ContentValidator.Report report = ContentValidator.validate(contentDir, assetsDir);
        assertEquals(List.of(), report.errors());
        assertTrue(report.ok());
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("prop.bancada") && w.contains("arquivo ausente")),
                "imagem ausente é aviso, não erro: " + report.warnings());
    }

    @Test
    void validadorAcusaAssetInexistente() throws URISyntaxException {
        Path broken = Path.of(AssetsAndActorsTest.class.getResource("/quebrado").toURI());
        ContentValidator.Report report = ContentValidator.validate(broken, broken.resolve("assets"));
        assertFalse(report.ok());
        assertTrue(report.errors().stream().anyMatch(e -> e.startsWith("assets/manifest:")), report.errors().toString());
        assertTrue(report.errors().stream().anyMatch(e -> e.startsWith("props.yaml:")
                && e.contains("asset inexistente 'prop.caixote'")), report.errors().toString());
        assertTrue(report.errors().stream().anyMatch(e -> e.contains("terreno 'grama'")
                && e.contains("'tile.grama'")), report.errors().toString());
    }

    // ---------- props e cena de referência ----------

    @Test
    void propsTemSpritePadraoELuzes() {
        PropDef lamp = content.props().get("lampiao");
        assertEquals("prop.lampiao", lamp.spriteId());
        assertEquals(1, lamp.lightList().size());
        assertEquals(3, lamp.lightList().get(0).offset().length);
        assertTrue(lamp.lightList().get(0).radius() > 0);
        assertTrue(content.props().get("caixote").lightList().isEmpty());
        assertEquals(7.0, content.props().get("casa_oficina").width());
        assertEquals(5.0, content.props().get("casa_oficina").depth());
    }

    @Test
    void cenaDeReferenciaTemConstrucoesTerrenosEFigurante() {
        AreaDef area = content.area(AREA);
        assertEquals(List.of("grama", "pedra", "terra"), List.copyOf(area.terrainsUsed()));
        assertTrue(area.props().stream().anyMatch(p -> p.propId().equals("casa_taverna")));
        assertEquals(1, area.actors().size());
        assertEquals("aldea", area.actors().get(0).id());
        assertEquals("personagem.aldea", area.actors().get(0).sprite());
        assertTrue(area.actors().get(0).patrol().contains("poco"));
    }

    @Test
    void construcaoBloqueiaAPassagem() {
        Simulation sim = Simulation.newGame(content, 1, AREA);
        assertTrue(sim.nav().isBlockedAt(14.5, 15.5), "centro da oficina");
        assertFalse(sim.nav().isBlockedAt(14.5, 19.2), "diante da porta");
    }

    // ---------- figurantes ----------

    @Test
    void figuranteAndaEntreOsPontosSemAtravessarObstaculos() {
        Simulation sim = Simulation.newGame(content, 42, AREA);
        ActorView actor = sim.view().actors().get(0);
        double startX = actor.x(), startY = actor.y();
        double farthest = 0;
        boolean walked = false;
        for (int i = 0; i < 30 * 120; i++) {
            sim.step();
            walked |= actor.moving();
            farthest = Math.max(farthest, Math.hypot(actor.x() - startX, actor.y() - startY));
            assertFalse(sim.nav().isBlockedAt(actor.x(), actor.y()), "figurante dentro de obstáculo no tick " + i);
        }
        assertTrue(walked);
        assertTrue(farthest > 3, "em dois minutos o figurante deveria ter saído do lugar");
        assertEquals("personagem.aldea", actor.sprite());
        assertEquals("aldea", actor.id());
    }

    @Test
    void figurantesSaoDeterministicosPelaSemente() {
        double[] a = strollAndReport(7), b = strollAndReport(7), c = strollAndReport(8);
        assertArrayEquals(a, b);
        assertFalse(java.util.Arrays.equals(a, c), "sementes diferentes levam a passeios diferentes");
    }

    private static double[] strollAndReport(long seed) {
        Simulation sim = Simulation.newGame(content, seed, AREA);
        sim.submit(new Command.MoveTo(22.0, 30.0));
        for (int i = 0; i < 30 * 90; i++) sim.step();
        ActorView actor = sim.view().actors().get(0);
        return new double[] {actor.x(), actor.y(), actor.facing(), sim.view().player().x(), sim.view().player().y()};
    }

    @Test
    void jogadorTemSpriteEVista() {
        Simulation sim = Simulation.newGame(content, 1, AREA);
        assertEquals("personagem.aprendiz", sim.view().player().sprite());
        assertEquals(sim.view().playerX(), sim.view().player().x());
        assertFalse(sim.view().player().moving());
    }
}
