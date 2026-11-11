package etrava.sim;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import etrava.sim.api.Command;
import etrava.sim.api.DialogueView;
import etrava.sim.api.SimEvent;
import etrava.sim.api.Simulation;
import etrava.sim.content.ContentDb;
import etrava.sim.content.FactDef;
import etrava.sim.rules.Condition;
import etrava.sim.rules.Effect;
import etrava.sim.rules.Rules;
import etrava.sim.save.SaveGame;
import etrava.sim.save.SaveGame.SaveException;
import etrava.sim.validation.ContentValidator;
import etrava.sim.world.KnowledgeEntry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** M3: fatos e conhecimento, diálogos, conversas ambientes, rumores e validador narrativo (AT-04, AT-08). */
class StoryTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static Path contentDir, assetsDir;
    private static ContentDb content;

    @BeforeAll
    static void load() {
        contentDir = Path.of(System.getProperty("etrava.content", "../content"));
        assetsDir = Path.of(System.getProperty("etrava.assets", "../assets"));
        content = ContentDb.load(contentDir);
    }

    private static Simulation newSim(long seed) {
        return Simulation.newGame(content, seed, "vila_ref");
    }

    /** Leva o arco da árvore, estágio por estágio, até onde o teste precisa (cada um ensina fatos aos NPCs). */
    private static void arc(Simulation sim, String... stages) {
        for (String stage : stages) sim.applyNow(List.of(new Effect.Arc("a1_nalia", stage)));
    }

    private static List<SimEvent> run(Simulation sim, int ticks) {
        List<SimEvent> all = new ArrayList<>();
        for (int i = 0; i < ticks; i++) all.addAll(sim.step());
        return all;
    }

    private static long hours(Simulation sim, double h) {
        return Math.round(h * sim.view().clock().ticksPerHour());
    }

    /** Manda falar com o NPC e anda até a conversa abrir. */
    private static DialogueView talk(Simulation sim, String npc) {
        sim.submit(new Command.Talk(npc));
        for (int i = 0; i < 30 * 40 && sim.view().dialogue() == null; i++) sim.step();
        assertNotNull(sim.view().dialogue(), "a conversa com " + npc + " deveria ter começado");
        return sim.view().dialogue();
    }

    private static void choose(Simulation sim, String choiceText) {
        List<DialogueView.Choice> choices = sim.view().dialogue().choices();
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).text().equals(choiceText)) {
                sim.submit(new Command.Choose(i));
                sim.step();
                return;
            }
        }
        fail("escolha não disponível: \"" + choiceText + "\" em " + choices);
    }

    private static List<String> choiceTexts(Simulation sim) {
        return sim.view().dialogue().choices().stream().map(DialogueView.Choice::text).toList();
    }

    private static KnowledgeEntry entry(Simulation sim, String fact, String source) {
        return sim.view().knowledge().stream()
                .filter(k -> k.fact().equals(fact) && k.source().equals(source)).findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- regras

    @Test
    void condicoesEEfeitosSaoLidosDoFormatoDeConteudo() throws Exception {
        JsonNode c = JSON.readTree("{\"all\":[{\"flag\":\"festa\"},{\"not\":{\"opinion\":{\"npc\":\"moleiro\",\"max\":-10}}},"
                + "{\"any\":[{\"knows\":{\"fact\":\"arvore_voltou\"}},{\"var\":{\"name\":\"x\",\"min\":2}}]},"
                + "{\"time\":{\"dayMin\":2,\"hourFrom\":22,\"hourTo\":4}},{\"npc\":{\"id\":\"aldea\",\"area\":\"vila_ref\"}},"
                + "{\"chance\":0.5},{\"flag\":{\"name\":\"y\",\"value\":false}}]}");
        Condition.All all = (Condition.All) Rules.condition(c);
        assertEquals(7, all.of().size());
        assertEquals(new Condition.Flag("festa", true), all.of().get(0));
        assertEquals(new Condition.Knows("player", "arvore_voltou"), ((Condition.Any) all.of().get(2)).of().get(0));
        assertSame(Condition.ALWAYS, Rules.condition(null));
        assertThrows(IllegalArgumentException.class, () -> Rules.condition(JSON.readTree("{\"voar\":1}")));
        assertThrows(IllegalArgumentException.class, () -> Rules.condition(JSON.readTree("[1]")));

        List<Effect> effects = List.of(new Effect.SetFlag("a", false), new Effect.SetVar("b", 2.5),
                new Effect.AddVar("b", 1), new Effect.Learn("player", "arvore_voltou", "exagero", "rumor"),
                new Effect.Learn("npc", "mo_parada", null, "claim"), new Effect.Opinion("moleiro", -3),
                new Effect.Chronicle("t", "r", "texto"),
                new Effect.Schedule(2.5, true, List.of(new Effect.SetFlag("depois", true))));
        for (Effect e : effects) assertEquals(e, Rules.effect(Rules.toJson(e)), "ida e volta de " + e);
        assertEquals(new Effect.SetFlag("z", true), Rules.effect(JSON.readTree("{\"setFlag\":\"z\"}")));
        assertThrows(IllegalArgumentException.class, () -> Rules.effect(JSON.readTree("{\"explodir\":{}}")));
        assertThrows(IllegalArgumentException.class, () -> Rules.effect(JSON.readTree("{\"learn\":{\"who\":\"player\"}}")));
        assertThrows(IllegalArgumentException.class, () -> Rules.effects(JSON.readTree("{\"a\":1}")));
    }

    @Test
    void condicoesSaoAvaliadasContraOMundo() {
        Simulation sim = newSim(1); // dia 1, 06:00
        sim.schedule(0, false, List.of(new Effect.SetFlag("festa", true), new Effect.SetVar("x", 2),
                new Effect.AddVar("x", 1.5), new Effect.Opinion("moleiro", -200),
                new Effect.Schedule(1, false, List.of(new Effect.SetFlag("depois", true)))));
        sim.step();
        assertTrue(sim.view().flag("festa"));
        assertEquals(3.5, sim.view().var("x"));
        assertEquals(-100, sim.view().opinion("moleiro"), "a opinião não passa de -100");
        assertFalse(sim.view().flag("depois"));
        sim.fastForward(hours(sim, 1.1));
        assertTrue(sim.view().flag("depois"), "efeito agendado por outro efeito");
    }

    // ---------------------------------------------------------------- conteúdo e conhecimento inicial

    @Test
    void conteudoNarrativoCarrega() {
        assertEquals(List.of(), content.problems());
        FactDef tree = content.facts().get("arvore_voltou");
        assertEquals("V0", tree.layer());
        assertEquals("base", tree.defaultVersion());
        assertTrue(tree.hasVersion("exagero"));
        assertTrue(tree.text("exagero").contains("encostou"));
        assertEquals(tree.text("base"), tree.text("nao_existe"));
        assertTrue(tree.spreads());
        assertFalse(content.facts().get("curandeira_olhou_arvore").spreads());
        assertEquals(1, content.facts().get("curandeira_olhou_arvore").layerIndex());
        assertEquals(8, content.dialogues().size());
        assertEquals(5, content.ambients().size());
        assertEquals("Você", content.text("dialogo.voce", "?"));
        assertEquals("padrão", content.text("chave.que.nao.existe", "padrão"));
        assertEquals("retrato.moleiro", content.npcs().get("moleiro").portraitId());
    }

    @Test
    void npcsComecamSabendoOQueOConteudoDiz() {
        Simulation sim = newSim(1);
        assertEquals("base", sim.view().npcKnowledge("moleiro").get("mo_parada").version());
        assertNull(sim.view().npcKnowledge("moleiro").get("arvore_voltou"), "a árvore ainda não voltou");
        arc(sim, "restauracao"); // o arco ensina a cada um a sua versão
        assertEquals("base", sim.view().npcKnowledge("moleiro").get("arvore_voltou").version());
        assertEquals("exagero", sim.view().npcKnowledge("aldea").get("arvore_voltou").version());
        assertEquals("ceticismo", sim.view().npcKnowledge("taberneira").get("arvore_voltou").version());
        assertTrue(sim.view().npcKnowledge("ferreiro").isEmpty());
        assertEquals(10, sim.view().opinion("moleiro"));
        assertEquals(0, sim.view().opinion("aldea"));
        assertTrue(sim.view().knowledge().isEmpty(), "o jogador começa sem nada no caderno");
        assertNull(sim.view().dialogue());
    }

    // ---------------------------------------------------------------- diálogo de exemplo: três caminhos

    @Test
    void caminhoDaMo_conversaPausaOMundo() {
        Simulation sim = newSim(1);
        DialogueView d = talk(sim, "moleiro");
        assertEquals("Moleiro", d.npcName());
        assertEquals("retrato.moleiro", d.portrait());
        assertEquals("Se é farinha, só amanhã. A mó está parada.", d.text());
        // sem ter ouvido falar da árvore, esse caminho nem aparece
        assertEquals(List.of("Parada, com trigo esperando?", "E a menina, como vai?", "Volto amanhã, então."),
                choiceTexts(sim));
        assertEquals("claim", entry(sim, "mo_parada", "npc:moleiro").certainty());

        long tick = sim.view().clock().tick();
        double x = sim.view().playerX();
        sim.submit(new Command.MoveTo(24, 24)); // ignorado durante a conversa
        run(sim, 30);
        assertEquals(tick, sim.view().clock().tick(), "o relógio para durante o diálogo (RF-105)");
        assertEquals(x, sim.view().playerX());

        choose(sim, "Parada, com trigo esperando?");
        assertEquals("Pedra também cansa.", sim.view().dialogue().text());
        choose(sim, "Pedra não cansa.");
        assertEquals("Então sou eu.", sim.view().dialogue().text());
        assertTrue(sim.view().dialogue().canContinue());
        assertEquals(12, sim.view().opinion("moleiro"));
        assertEquals(5, sim.view().dialogue().history().size());
        assertEquals("Você", sim.view().dialogue().history().get(1).speaker());
        sim.submit(new Command.Advance());
        List<SimEvent> events = sim.step();
        assertNull(sim.view().dialogue());
        assertTrue(events.contains(new SimEvent.DialogueClosed("moleiro")));
        run(sim, 5);
        assertTrue(sim.view().clock().tick() > tick, "fora da conversa o tempo volta a andar");
    }

    @Test
    void caminhoDaArvore_soAbreParaQuemJaOuviuFalar() {
        Simulation sim = newSim(1);
        arc(sim, "restauracao");
        talk(sim, "aldea");
        choose(sim, "Alguma novidade?");
        assertEquals("exagero", entry(sim, "arvore_voltou", "npc:aldea").version());
        choose(sim, "Hm.");
        assertNull(sim.view().dialogue());

        talk(sim, "moleiro");
        assertTrue(choiceTexts(sim).contains("Ouvi falar da árvore."));
        choose(sim, "Ouvi falar da árvore.");
        choose(sim, "Dizem que foi ela quem fez.");
        assertEquals("Dizem muita coisa. Ontem diziam que ia chover.", sim.view().dialogue().text());
        assertEquals(4, sim.view().opinion("moleiro"));
        assertTrue(sim.view().flag("moleiro_fechou"));
        sim.submit(new Command.Advance());
        sim.step();

        // a escolha tem consequência: da próxima vez ele não dá conversa
        assertEquals("A farinha sai amanhã. É só isso.", talk(sim, "moleiro").text());
        assertTrue(sim.view().dialogue().choices().isEmpty());
    }

    @Test
    void caminhoDaArvoreComJeito_levaAPistaDaCurandeira() {
        Simulation sim = newSim(1);
        arc(sim, "restauracao", "insonia", "vozes", "impasse");
        talk(sim, "aldea");
        choose(sim, "Alguma novidade?");
        choose(sim, "Encostou e pronto?");
        sim.submit(new Command.Advance());
        sim.step();
        talk(sim, "moleiro");
        choose(sim, "Ouvi falar da árvore.");
        choose(sim, "Meu tio diz que árvore velha engana. Parece morta e não está.");
        assertEquals("É. Deve ser isso.", sim.view().dialogue().text());
        choose(sim, "E a menina?");
        assertEquals("Ela não dorme. Desde aquele dia. Fica olhando o quintal.", sim.view().dialogue().text());
        assertNotNull(entry(sim, "nalia_nao_dorme", "npc:moleiro"));
        choose(sim, "Já chamaram a curandeira?");
        KnowledgeEntry clue = entry(sim, "curandeira_olhou_arvore", "npc:moleiro");
        assertEquals("claim", clue.certainty(), "pista de camada V1 chega como afirmação de alguém, não como verdade");
        assertEquals(1, clue.day());
        assertEquals("vila_ref", clue.place());
        assertEquals("claim", entry(sim, "arvore_tira_algo", "npc:moleiro").certainty());
        assertEquals(16, sim.view().opinion("moleiro"));
    }

    @Test
    void abandonarAConversaTemConsequencia() {
        Simulation sim = newSim(1);
        arc(sim, "restauracao", "insonia");
        talk(sim, "aldea");
        choose(sim, "Alguma novidade?");
        choose(sim, "Hm.");
        talk(sim, "moleiro");
        choose(sim, "Ouvi falar da árvore.");
        choose(sim, "Meu tio diz que árvore velha engana. Parece morta e não está.");
        choose(sim, "E a menina?");
        sim.submit(new Command.LeaveDialogue()); // sai bem quando ele se abriu
        sim.step();
        assertNull(sim.view().dialogue());
        assertEquals(12, sim.view().opinion("moleiro"), "+6 pela gentileza, -4 por virar as costas");
    }

    @Test
    void caminhoDaMenina_ePerguntarSobreFatosDoCaderno() {
        Simulation sim = newSim(1);
        arc(sim, "restauracao");
        talk(sim, "moleiro");
        choose(sim, "E a menina, como vai?");
        assertEquals("Crescendo.", sim.view().dialogue().text());
        choose(sim, "Faz tempo que não a vejo na praça.");
        assertEquals("Tem tido serviço em casa.", sim.view().dialogue().text());
        sim.submit(new Command.Advance());
        sim.step();

        talk(sim, "aldea");
        sim.submit(new Command.AskAbout("mo_parada")); // ela não sabe disso
        sim.step();
        assertEquals("Disso eu não sei nada.", sim.view().dialogue().text());
        choose(sim, "Alguma novidade?");
        choose(sim, "Hm.");

        talk(sim, "moleiro"); // o jogador ouviu a versão exagerada; o moleiro conta a dele
        sim.submit(new Command.AskAbout("arvore_voltou"));
        sim.step();
        assertEquals("A árvore velha do moinho brotou de novo.", sim.view().dialogue().text());
        assertEquals("base", entry(sim, "arvore_voltou", "npc:moleiro").version());
        assertEquals(2, sim.view().knowledge().stream().filter(k -> k.fact().equals("arvore_voltou")).count(),
                "duas versões do mesmo fato, de duas fontes");
        sim.submit(new Command.AskAbout("fato_que_o_jogador_nao_conhece"));
        sim.step();
        assertEquals("A árvore velha do moinho brotou de novo.", sim.view().dialogue().text());
        sim.submit(new Command.LeaveDialogue());
        sim.step();

        sim.submit(new Command.MarkFact("arvore_voltou", true));
        sim.submit(new Command.MarkFact("nunca_ouvi", true));
        sim.step();
        assertEquals(java.util.Set.of("arvore_voltou"), sim.view().markedFacts());
        sim.submit(new Command.MarkFact("arvore_voltou", false));
        sim.step();
        assertTrue(sim.view().markedFacts().isEmpty());
    }

    @Test
    void npcSemDialogoSoResmunga_eNaoSeSalvaNoMeioDeConversa(@TempDir Path dir) {
        Simulation sim = newSim(1);
        sim.submit(new Command.Talk("ninguem"));
        sim.submit(new Command.Talk("viajante")); // dormindo dentro da taverna: não dá para falar
        sim.submit(new Command.Talk("estudioso")); // ainda nem chegou ao vilarejo
        run(sim, 10);
        assertNull(sim.view().dialogue());
        // o homem de fora não tem conversa escrita: só resmunga
        sim.applyNow(List.of(new Effect.NpcStatus("estudioso", "present")));
        sim.fastForward(hours(sim, 1.5));
        sim.submit(new Command.Talk("estudioso"));
        List<SimEvent> events = run(sim, 30 * 30);
        assertNull(sim.view().dialogue());
        assertTrue(events.stream().anyMatch(e -> e instanceof SimEvent.AmbientLine l && l.npcId().equals("estudioso")));

        talk(sim, "aldea");
        SaveException e = assertThrows(SaveException.class, () -> sim.save(dir.resolve("x.etrava"), ""));
        assertTrue(e.getMessage().contains("conversa"));
    }

    // ---------------------------------------------------------------- conversas ambientes

    @Test
    void conversaAmbienteTocaPertoDoJogadorEViraRumorNoCaderno() {
        Simulation sim = newSim(1);
        arc(sim, "restauracao");
        sim.fastForward(hours(sim, 8) + 30); // 14:00 e pouco: aldeã e taberneira na praça
        sim.submit(new Command.Teleport(24.0, 27.5));
        List<SimEvent> events = run(sim, 700);
        List<SimEvent.AmbientLine> lines = events.stream().filter(e -> e instanceof SimEvent.AmbientLine)
                .map(e -> (SimEvent.AmbientLine) e).toList();
        assertEquals(6, lines.size());
        assertEquals("aldea", lines.get(0).npcId());
        assertEquals("Passou no moinho hoje?", lines.get(0).text());
        assertEquals("Então alguém regou.", lines.get(5).text());
        KnowledgeEntry heard = entry(sim, "arvore_voltou", "conversa:arvore_na_praca");
        assertEquals("rumor", heard.certainty(), "ouvido de passagem entra como rumor (RF-104)");
        assertTrue(events.contains(new SimEvent.FactLearned("arvore_voltou", "base")));
        assertEquals(0, run(sim, 600).stream().filter(e -> e instanceof SimEvent.AmbientLine).count(),
                "a mesma conversa não se repete");
    }

    @Test
    void longeDoJogadorAConversaNaoAcontece() {
        Simulation sim = newSim(1);
        sim.fastForward(hours(sim, 8) + 30);
        sim.submit(new Command.Teleport(24.5, 44.0));
        assertEquals(0, run(sim, 700).stream().filter(e -> e instanceof SimEvent.AmbientLine).count());
        assertFalse(sim.view().knows("arvore_voltou"), "perder a conversa é perder a pista — desta fonte");
    }

    @Test
    void conversaInterrompidaNaoEnsinaNada() {
        Simulation sim = newSim(1);
        sim.fastForward(hours(sim, 8) + 30);
        sim.submit(new Command.Teleport(24.0, 27.5));
        run(sim, 200); // ouve o começo
        sim.submit(new Command.Teleport(24.5, 44.0)); // e vai embora
        run(sim, 700);
        assertFalse(sim.view().knows("arvore_voltou"));
    }

    // ---------------------------------------------------------------- rumores (AT-08)

    @Test
    void rumorPlantadoNaTavernaChegaATresOutrosNpcsEmTresDias() {
        for (long seed = 1; seed <= 10; seed++) {
            Simulation sim = newSim(seed);
            for (String npc : content.npcs().keySet()) {
                assertFalse(sim.view().npcKnowledge(npc).containsKey("forasteiro_pergunta"));
            }
            sim.schedule(0, false, List.of(new Effect.Learn("taberneira", "forasteiro_pergunta", null, "witnessed")));
            sim.step();
            sim.fastForward(hours(sim, 24 * 3));
            long others = content.npcs().keySet().stream().filter(n -> !n.equals("taberneira"))
                    .filter(n -> sim.view().npcKnowledge(n).containsKey("forasteiro_pergunta")).count();
            assertTrue(others >= 3, "semente " + seed + ": o rumor só chegou a " + others + " NPCs");
            KnowledgeEntry k = sim.view().npcKnowledge("ferreiro").get("arvore_voltou");
            if (k != null) {
                assertEquals("rumor", k.certainty());
                assertTrue(k.source().startsWith("npc:"));
            }
            // o que não é para circular não circula
            assertFalse(sim.view().npcKnowledge("ferreiro").containsKey("curandeira_olhou_arvore"));
        }
    }

    @Test
    void rumoresSaoDeterministicosPelaSemente() {
        Simulation a = newSim(5), b = newSim(5), c = newSim(6);
        for (Simulation s : List.of(a, b, c)) s.fastForward(hours(s, 24 * 4));
        assertEquals(a.stateHash(), b.stateHash());
        assertNotEquals(a.stateHash(), c.stateHash());
    }

    // ---------------------------------------------------------------- save

    @Test
    void conhecimentoOpiniaoEConversaEmCursoSobrevivemAoSave(@TempDir Path dir) throws SaveException {
        Simulation original = newSim(9);
        arc(original, "restauracao");
        talk(original, "aldea");
        choose(original, "Alguma novidade?");
        choose(original, "Hm.");
        original.submit(new Command.MarkFact("arvore_voltou", true));
        original.schedule(original.view().clock().tick() + hours(original, 30), false,
                List.of(new Effect.Learn("player", "sal_subiu", "exagero", "rumor"), new Effect.Opinion("aldea", 7),
                        new Effect.Schedule(2, false, List.of(new Effect.AddVar("v", 1)))));
        original.fastForward(hours(original, 7.9));
        original.submit(new Command.Teleport(24.0, 27.5));
        run(original, 400); // 14:00: salva com a conversa da praça pela metade
        Path file = dir.resolve("m3" + SaveGame.EXTENSION);
        original.save(file, "agora");

        Simulation loaded = Simulation.load(content, file);
        assertEquals(original.stateHash(), loaded.stateHash());
        assertEquals("exagero", entry(loaded, "arvore_voltou", "npc:aldea").version());
        assertEquals(java.util.Set.of("arvore_voltou"), loaded.view().markedFacts());
        assertEquals(10, loaded.view().opinion("moleiro"));

        List<String> heardA = new ArrayList<>(), heardB = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            original.step().forEach(e -> heardA.add(e.toString()));
            loaded.step().forEach(e -> heardB.add(e.toString()));
        }
        assertEquals(heardA, heardB, "a conversa ambiente continua de onde parou");
        assertFalse(heardA.isEmpty());
        for (Simulation s : List.of(original, loaded)) s.fastForward(hours(s, 24 * 2));
        assertEquals(original.stateHash(), loaded.stateHash());
        assertNotNull(entry(loaded, "sal_subiu", "mundo"));
        assertEquals(7, loaded.view().opinion("aldea"));
        assertEquals(1, loaded.view().var("v"));
    }

    // ---------------------------------------------------------------- validador narrativo (AT-04)

    @Test
    void conteudoDoRepositorioPassaNasRegrasNarrativas() {
        ContentValidator.Report report = ContentValidator.validate(contentDir, assetsDir);
        assertEquals(List.of(), report.errors());
    }

    @Test
    void validadorRejeitaNarrativaQuebradaApontandoArquivoEItem() throws Exception {
        Path broken = Path.of(StoryTest.class.getResource("/quebrado_narrativa").toURI());
        ContentValidator.Report report = ContentValidator.validate(broken, broken.resolve("assets"));
        List<String> e = report.errors();
        String all = String.join("\n", e);
        assertHas(e, "facts.yaml", "'sem_texto' com camada inválida 'V9'", all);
        assertHas(e, "facts.yaml", "'sem_texto' sem nenhuma versão", all);
        assertHas(e, "npcs/falador.yaml", "fato inexistente 'fato_que_nao_existe'", all);
        assertHas(e, "npcs/mudo.yaml", "diálogo inexistente 'dialogo_que_nao_existe'", all);
        assertHas(e, "dialogues/conversa_ruim.yaml", "'segredo_v2' é de camada V2", all);
        assertHas(e, "dialogues/conversa_ruim.yaml", "'pista_v1' é de camada V1 e só pode aparecer como pista", all);
        assertHas(e, "dialogues/conversa_ruim.yaml", "não tem a versão 'versao_fantasma'", all);
        assertHas(e, "dialogues/conversa_ruim.yaml", "grau de certeza inválido 'palpite'", all);
        assertHas(e, "dialogues/conversa_ruim.yaml", "nó inexistente 'nao_existe'", all);
        assertHas(e, "dialogues/conversa_ruim.yaml", "precisa declarar pelo menos 2 canais", all);
        assertHas(e, "dialogues/conversa_ruim.yaml", "NPC inexistente 'ninguem'", all);
        assertHas(e, "dialogues/conversa_ruim.yaml", "nó inalcançável 'ilha'", all);
        assertHas(e, "ambient/ruim.yaml", "'intruso' fala sem estar entre os participantes", all);
        assertHas(e, "ambient/ruim.yaml", "'segredo_v2' é de camada V2", all);
        assertHas(e, "facts.yaml", "'uma_fonte' destrava ramificação", all);
        assertTrue(all.contains("só tem 1 fonte(s)"), all);
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("'publico'") && w.contains("instrução")),
                report.warnings().toString());
    }

    private static void assertHas(List<String> errors, String file, String text, String all) {
        assertTrue(errors.stream().anyMatch(x -> x.startsWith(file) && x.contains(text)),
                "faltou o erro [" + file + ": " + text + "] em:\n" + all);
    }
}
