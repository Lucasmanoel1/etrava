package etrava.sim;

import static org.junit.jupiter.api.Assertions.*;

import etrava.sim.api.Simulation;
import etrava.sim.content.AssetManifest;
import etrava.sim.content.ContentDb;
import etrava.sim.content.FactDef;
import etrava.sim.content.ItemDef;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** M7: conteúdo completo — AT-19 (quantidades do PRD 13.2), AT-20 (sem instruções) e AT-21 (tom). */
class ContentM7Test {
    private static ContentDb content;
    private static AssetManifest manifest;
    /** As histórias do Capítulo I: três arcos maiores e cinco menores. */
    private static final List<String> STORIES = List.of("a1_nalia", "a2_avel", "a3_estrutura",
            "h1_frutas", "h2_divisa", "h3_apostas", "h4_posto", "h5_festa");

    @BeforeAll
    static void load() {
        content = ContentDb.load(Path.of(System.getProperty("etrava.content", "../content")));
        manifest = AssetManifest.load(Path.of(System.getProperty("etrava.assets", "../assets")));
    }

    private static long items(String... categories) {
        Set<String> set = Set.of(categories);
        return content.items().values().stream().filter(i -> set.contains(i.category())).count();
    }

    /** AT-19: o conteúdo do MVP bate com o mínimo da tabela 13.2. */
    @Test
    void at19_quantidadesDoMvp() {
        long named = content.npcs().values().stream().filter(n -> !"C".equals(n.layer())).count();
        long layerA = content.npcs().values().stream().filter(n -> "A".equals(n.layer())).count();
        assertTrue(named >= 20, "NPCs nomeados: " + named);
        assertTrue(layerA >= 8, "NPCs de camada A: " + layerA);
        assertTrue(content.ambients().size() >= 40, "conversas ambientes: " + content.ambients().size());
        assertTrue(content.facts().size() >= 80, "fatos: " + content.facts().size());
        long docs = content.items().values().stream().filter(ItemDef::document).count();
        assertTrue(docs >= 10, "documentos legíveis: " + docs);
        assertTrue(content.items().size() >= 40, "itens: " + content.items().size());
        assertTrue(items("arma", "armadura") >= 8, "armas e armaduras");
        assertTrue(items("consumivel") >= 10, "consumíveis");
        assertTrue(items("material", "mercadoria") >= 10, "materiais e mercadorias");
        assertTrue(items("documento", "especial") >= 10, "documentos e especiais");
        assertEquals(2, content.merchants().size());
        assertTrue(content.jobs().size() >= 3);
        assertTrue(content.enemies().size() >= 3);
        assertTrue(content.epilogue().fragments().size() >= 15, "fragmentos de epílogo");
        for (String arc : STORIES) assertNotNull(content.arcs().get(arc), "falta a história " + arc);
        for (String e : List.of("e1_ferrovia", "e2_sal", "e3_sacerdote", "e4_berta", "e5_estrada"))
            assertNotNull(content.arcs().get(e), "falta o evento externo " + e);
        // áreas (6.3): vilarejo, 4 interiores, estrada e Posto, estrutura
        long interiors = content.areaEntries().values().stream().filter(ContentDb.AreaEntry::interior).count();
        assertTrue(interiors >= 4, "interiores: " + interiors);
        for (String a : List.of("vila", "estrada", "estrutura")) assertNotNull(content.area(a), "falta a área " + a);
        // música (RF-401): pelo menos 5 faixas
        long tracks = manifest.entries().keySet().stream().filter(id -> id.startsWith("musica.")).count();
        assertTrue(tracks >= 5, "faixas de música: " + tracks);
        // um retrato por NPC nomeado
        content.npcs().values().stream().filter(n -> !"C".equals(n.layer()))
                .forEach(n -> assertTrue(manifest.has(n.portraitId()), "sem retrato: " + n.id()));
    }

    private static final Set<String> IMPERATIVES = Set.of("vá", "va", "fale", "procure", "encontre", "leve", "pegue",
            "volte", "visite", "converse", "siga", "busque", "descubra", "ajude", "investigue", "pergunte", "ache",
            "mate", "entregue", "traga", "fala", "vai", "use", "abra", "compre", "venda", "colete");

    /** AT-20: o caderno não manda fazer nada — nenhum fato começa com um verbo no imperativo. */
    @Test
    void at20_cadernoSemInstrucoes() {
        for (FactDef f : content.facts().values()) {
            for (var v : f.versions()) {
                String first = v.text().trim().split("[\\s,.:;!]+")[0].toLowerCase(Locale.ROOT);
                assertFalse(IMPERATIVES.contains(first), "fato '" + f.id() + "' parece uma instrução: " + v.text());
            }
        }
    }

    /**
     * AT-21: tom. Sem o jogador, em várias sementes, pelo menos duas histórias terminam leves
     * ou felizes e no máximo metade termina em tragédia.
     */
    @Test
    void at21_tomDosDesfechosPadrao() {
        for (long seed = 1; seed <= 8; seed++) {
            Simulation sim = Simulation.newGame(content, seed, "vila");
            sim.fastForward(Math.round(30 * sim.view().clock().ticksPerDay()));
            int light = 0, tragic = 0;
            for (String arc : STORIES) {
                String outcome = sim.arcOutcome(arc);
                assertNotNull(outcome, "semente " + seed + ": " + arc + " não terminou sem o jogador");
                if (outcome.equals("leve")) light++;
                if (outcome.equals("tragico")) tragic++;
            }
            assertTrue(light >= 2, "semente " + seed + ": só " + light + " história(s) leve(s)");
            assertTrue(tragic <= STORIES.size() / 2, "semente " + seed + ": " + tragic + " tragédias");
        }
    }
}
