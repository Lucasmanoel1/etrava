package etrava.sim;

import static org.junit.jupiter.api.Assertions.*;

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

/** Manifesto de assets e cena de referência (M1). */
class AssetsAndActorsTest {
    private static final String AREA = "vila";
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
        assertTrue(house.allFiles().size() >= 2);

        // arte da Fase V (V1): arte.yaml substitui a provisória com o mesmo ID e densidade dobrada
        AssetManifest.Entry grass = manifest.get("tile.grama");
        assertNotNull(grass.file() != null ? grass.file() : grass.files());
        assertTrue(grass.priority() > manifest.get("tile.pedra").priority(), "grama avança sobre a pedra");
        assertEquals(4, manifest.get("tile.pedra").scaleOr(), "arte antiga: 4 px de mundo por pixel");

        AssetManifest.Entry hero = manifest.get("personagem.aprendiz");
        assertEquals(8, hero.directions());
        assertEquals(2, hero.scaleOr(), "arte nova: 2 px de mundo por pixel (D-086)");
        assertEquals(8, hero.animations().get("andar").frames());
        assertTrue(hero.animations().get("andar").stride() > 0, "o andar avança pela distância");
        assertTrue(hero.animations().get("parado").fps() > 0);
        assertNotNull(manifest.get("prop.arvore").variants(), "o carvalho tem variantes de tamanho");
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
        // AT-19: todo NPC nomeado tem retrato de verdade (nenhum arquivo ausente no manifesto)
        assertTrue(report.warnings().stream().noneMatch(w -> w.contains("arquivo ausente")), report.warnings().toString());
        AssetManifest manifest = AssetManifest.load(assetsDir);
        for (var npc : content.npcs().values())
            if (!"C".equals(npc.layer())) assertTrue(manifest.has(npc.portraitId()), "sem retrato: " + npc.id());
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
        assertEquals(List.of("grama", "pedra", "plantio", "terra"), List.copyOf(area.terrainsUsed()));
        assertTrue(area.props().stream().anyMatch(p -> p.propId().equals("casa_taverna")));
        assertTrue(area.spots().containsKey("entrada"));
        assertEquals("personagem.aldea", content.npcs().get("aldea").sprite());
    }

    @Test
    void construcaoBloqueiaAPassagem() {
        Simulation sim = Simulation.newGame(content, 1, AREA);
        assertTrue(sim.nav().isBlockedAt(22.5, 24.5), "centro da oficina");
        assertFalse(sim.nav().isBlockedAt(22.5, 27.6), "diante da porta");
    }

    @Test
    void jogadorTemSpriteEVista() {
        Simulation sim = Simulation.newGame(content, 1, AREA);
        assertEquals("personagem.aprendiz", sim.view().player().sprite());
        assertEquals(sim.view().playerX(), sim.view().player().x());
        assertFalse(sim.view().player().moving());
    }
}
