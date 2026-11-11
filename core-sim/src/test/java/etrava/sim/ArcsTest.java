package etrava.sim;

import static org.junit.jupiter.api.Assertions.*;

import etrava.sim.api.Command;
import etrava.sim.api.DialogueView;
import etrava.sim.api.SimEvent;
import etrava.sim.api.Simulation;
import etrava.sim.content.ArcDef;
import etrava.sim.content.ContentDb;
import etrava.sim.content.NpcDef;
import etrava.sim.rules.Condition;
import etrava.sim.rules.Effect;
import etrava.sim.rules.Rules;
import etrava.sim.save.SaveGame;
import etrava.sim.validation.ContentValidator;
import etrava.sim.world.ChronicleEntry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * M4: mundo independente — motor de arcos, eventos externos, variantes de
 * locais, memória e opinião, reação a atos do jogador e epílogo (AT-01, AT-02).
 */
class ArcsTest {
    private static final String A1 = "a1_nalia";
    private static final List<String> EXTERNAL = List.of("e1_ferrovia", "e2_sal", "e3_sacerdote", "e4_berta", "e5_estrada");
    private static ContentDb content;

    @BeforeAll
    static void load() {
        content = ContentDb.load(Path.of(System.getProperty("etrava.content", "../content")));
    }

    private static Simulation newSim(long seed) {
        return Simulation.newGame(content, seed, "vila_ref");
    }

    private static long days(Simulation sim, double d) {
        return Math.round(d * sim.view().clock().ticksPerDay());
    }

    private static String stage(Simulation sim, String arc) {
        return sim.view().arcs().get(arc).stage();
    }

    private static void arc(Simulation sim, String... stages) {
        for (String s : stages) sim.applyNow(List.of(new Effect.Arc(A1, s)));
    }

    private static List<SimEvent> run(Simulation sim, int ticks) {
        List<SimEvent> all = new ArrayList<>();
        for (int i = 0; i < ticks; i++) all.addAll(sim.step());
        return all;
    }

    private static DialogueView talk(Simulation sim, String npc) {
        sim.submit(new Command.Talk(npc));
        for (int i = 0; i < 30 * 60 && sim.view().dialogue() == null; i++) sim.step();
        assertNotNull(sim.view().dialogue(), "a conversa com " + npc + " deveria ter começado");
        return sim.view().dialogue();
    }

    private static void choose(Simulation sim, String text) {
        List<DialogueView.Choice> choices = sim.view().dialogue().choices();
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).text().equals(text)) {
                sim.submit(new Command.Choose(i));
                sim.step();
                return;
            }
        }
        fail("escolha não disponível: \"" + text + "\" em " + choices);
    }

    private static void endTalk(Simulation sim) {
        sim.submit(new Command.LeaveDialogue());
        sim.step();
    }

    // ---------------------------------------------------------------- AT-01 e AT-02

    @Test
    void at01_jogadorParado30Dias_arcoEEventosExternosChegamAoFim() {
        Simulation sim = newSim(42);
        sim.fastForward(days(sim, 30));

        assertNotNull(sim.arcOutcome(A1), "A1 precisa ter um desfecho sem o jogador (RF-141)");
        for (String id : EXTERNAL) assertNotNull(sim.arcOutcome(id), id + " deveria ter ocorrido");

        // o relatório (a crônica) lista as mudanças de estado
        List<ChronicleEntry> chronicle = sim.view().chronicle();
        assertTrue(chronicle.stream().anyMatch(c -> c.type().equals("arco") && c.ref().equals(A1 + ":restauracao")));
        assertTrue(chronicle.stream().anyMatch(c -> c.type().equals("prop") && c.ref().equals("arvore_nalia")));
        assertTrue(chronicle.stream().anyMatch(c -> c.type().equals("arco") && c.text().contains("[desfecho ")));
        assertTrue(chronicle.stream().anyMatch(c -> c.type().equals("capitulo")));

        // os eventos externos deixam fatos na rede de rumores (RF-142): chegam a quem nunca saiu do vilarejo
        Set<String> villagersWhoHeard = new HashSet<>();
        for (String npc : List.of("moleiro", "aldea", "taberneira", "ferreiro", "curandeira", "nalia")) {
            if (sim.view().npcKnowledge(npc).containsKey("sal_subiu")) villagersWhoHeard.add(npc);
        }
        assertFalse(villagersWhoHeard.isEmpty(), "a notícia do sal deveria ter chegado a alguém do vilarejo");

        // epílogo coerente mesmo sem o jogador ter feito nada (RF-163)
        assertTrue(sim.view().flag(Simulation.CHAPTER_ENDED));
        assertTrue(sim.view().epilogue().size() >= 6, "epílogo curto demais: " + sim.view().epilogue());
        assertTrue(sim.view().knowledge().isEmpty(), "parado, o personagem não soube de nada");
    }

    @Test
    void at02_vinteSementes_desfechosDiferentesEPeloMenosUmNaoTragico() {
        Set<String> endings = new HashSet<>(), outcomes = new HashSet<>();
        for (long seed = 1; seed <= 20; seed++) {
            Simulation sim = newSim(seed);
            sim.fastForward(days(sim, 30));
            assertNotNull(sim.arcOutcome(A1), "semente " + seed + ": A1 sem desfecho");
            for (String id : EXTERNAL) assertNotNull(sim.arcOutcome(id), "semente " + seed + ": " + id);
            endings.add(stage(sim, A1));
            outcomes.add(sim.arcOutcome(A1));
        }
        assertTrue(endings.size() >= 2, "esperados pelo menos 2 desfechos distintos de A1, vieram " + endings);
        assertTrue(outcomes.stream().anyMatch(o -> !o.equals("tragico")), "nenhuma semente terminou sem tragédia");
        // sem o jogador, ninguém junta voluntários nem convence Nalia a soltar a árvore
        assertFalse(endings.contains("custo_dividido"));
        assertFalse(endings.contains("arvore_morre"));
    }

    @Test
    void mesmaSementeDaAMesmaHistoria_eOSaveNoMeioDoArcoNaoMudaNada(@TempDir Path dir) throws Exception {
        Simulation a = newSim(7), b = newSim(7);
        a.fastForward(days(a, 7));
        b.fastForward(days(b, 7));
        assertEquals(a.stateHash(), b.stateHash());

        Path file = dir.resolve("m4" + SaveGame.EXTENSION);
        a.save(file, "agora");
        Simulation loaded = Simulation.load(content, file);
        assertEquals(a.stateHash(), loaded.stateHash());
        assertEquals(stage(a, A1), stage(loaded, A1));
        a.fastForward(days(a, 23));
        loaded.fastForward(days(loaded, 23));
        assertEquals(a.stateHash(), loaded.stateHash(), "o arco segue igual depois de carregar (AT-05)");
        assertEquals(a.view().epilogue(), loaded.view().epilogue());
    }

    // ---------------------------------------------------------------- motor

    @Test
    void arcoComecaNoEstagioInicialEAndaPeloPrazo() {
        Simulation sim = newSim(3);
        assertEquals("antes", stage(sim, A1));
        assertEquals("morta", content.keyedProp("arvore_nalia").initialState());
        assertNull(sim.view().propState("arvore_nalia"));
        long deadline = sim.view().arcs().get(A1).deadline();
        long start = sim.view().clock().tick();
        assertTrue(deadline >= start + days(sim, 1) && deadline <= start + days(sim, 1.5), "1 dia + até 12 h de folga");

        sim.fastForward(deadline - start - 1);
        assertEquals("antes", stage(sim, A1));
        sim.fastForward(sim.view().clock().ticksPerHour());
        assertEquals("restauracao", stage(sim, A1));
        assertEquals("viva", sim.view().propState("arvore_nalia"), "o cenário muda com o arco (RF-043)");
        assertNotNull(sim.view().npcKnowledge("moleiro").get("arvore_voltou"));
        assertEquals("exagero", sim.view().npcKnowledge("aldea").get("arvore_voltou").version());
    }

    @Test
    void ritmoContemplativoDobraOsPrazos() {
        Simulation normal = newSim(3);
        Simulation slow = Simulation.newGame(content, 3, "vila_ref", Simulation.PACE_CONTEMPLATIVE);
        long start = normal.view().clock().tick();
        long fast = normal.view().arcs().get(A1).deadline() - start, lazy = slow.view().arcs().get(A1).deadline() - start;
        assertTrue(fast <= days(normal, 1.5), "normal: 1 dia + até 12 h");
        assertTrue(lazy >= days(slow, 2) && lazy <= days(slow, 3), "contemplativo: 2 dias + até 24 h, veio " + lazy);
        slow.fastForward(days(slow, 30));
        assertEquals(2.0, slow.stateWorld().pace);
    }

    @Test
    void efeitoMudaOEstagio_eArcoEncerradoNaoVolta() {
        Simulation sim = newSim(1);
        arc(sim, "impasse");
        assertEquals("impasse", stage(sim, A1));
        arc(sim, "estagio_que_nao_existe");
        assertEquals("impasse", stage(sim, A1));
        arc(sim, "nalia_piora");
        assertEquals("tragico", sim.arcOutcome(A1));
        assertEquals(-1, sim.view().arcs().get(A1).deadline());
        arc(sim, "impasse");
        assertEquals("nalia_piora", stage(sim, A1), "desfecho é definitivo");
    }

    @Test
    void estudiosoSoSalvaSeANoticiaChegouAEstrada() {
        // passou pela região, mas ninguém da estrada soube da menina: o prazo cai no sorteio comum
        Simulation unheard = newSim(5);
        arc(unheard, "impasse");
        unheard.applyNow(List.of(new Effect.SetFlag("estudioso_na_regiao", true)));
        unheard.stateWorld().npcKnowledge.getOrDefault("viajante", new java.util.LinkedHashMap<>()).remove("nalia_ouve_vozes");
        assertEquals("impasse", stage(unheard, A1));

        Simulation heard = newSim(5);
        arc(heard, "restauracao", "insonia", "vozes", "impasse");
        heard.applyNow(List.of(new Effect.SetFlag("estudioso_na_regiao", true),
                new Effect.Learn("viajante", "nalia_ouve_vozes", null, "rumor")));
        assertEquals("away", heard.view().npcStatus("estudioso"));
        heard.fastForward(days(heard, 12));
        assertEquals("estabilizada", stage(heard, A1));
        assertEquals("ambiguo", heard.arcOutcome(A1));
        assertEquals("viva", heard.view().propState("arvore_nalia"));
        // consequência tardia (RF-162): ele fica três dias e parte; a ausência é persistente (RF-087)
        assertEquals("departed", heard.view().npcStatus("estudioso"));
        assertTrue(heard.view().chronicle().stream().anyMatch(c -> c.type().equals("npc") && c.text().contains("present")));
    }

    // ---------------------------------------------------------------- o jogador intervém

    @Test
    void jogadorJuntaTresVoluntarios_eOCustoEDividido() {
        Simulation sim = newSim(1);
        arc(sim, "restauracao", "insonia", "vozes", "impasse");
        sim.fastForward(sim.view().clock().ticksPerHour() * 4); // 10:00: a curandeira está no quintal do moinho

        talk(sim, "curandeira");
        choose(sim, "O que a menina tem?");
        choose(sim, "Tem jeito?");
        assertTrue(sim.view().knows("custo_pode_ser_dividido"));
        assertEquals("claim", sim.view().knowledge().stream()
                .filter(k -> k.fact().equals("custo_pode_ser_dividido")).findFirst().orElseThrow().certainty(),
                "pista V1 entra como afirmação de alguém, nunca como verdade");
        choose(sim, "E aqui, daria?");
        choose(sim, "Mas é uma.");
        endTalk(sim);
        assertEquals(1, sim.view().var("voluntarios"));

        talk(sim, "moleiro");
        choose(sim, "É sobre a menina. Dá para repartir o que a árvore tira.");
        choose(sim, "Três pessoas. Um pouco de cada.");
        endTalk(sim);
        assertEquals("impasse", stage(sim, A1), "dois ainda não bastam");

        talk(sim, "nalia");
        choose(sim, "A curandeira fala em repartir o peso.");
        choose(sim, "Eu entro.");
        assertEquals("custo_dividido", stage(sim, A1), "o arco reage na hora em que o terceiro entra");
        assertEquals("leve", sim.arcOutcome(A1));
        assertEquals("viva", sim.view().propState("arvore_nalia"));
        assertTrue(sim.view().memories("nalia").stream().anyMatch(m -> m.type().equals("ajuda") && m.subject().equals("player")));
        endTalk(sim);
        assertEquals("Dormi a noite inteira. Agora quem boceja é o vilarejo.", talk(sim, "nalia").text());
        endTalk(sim);
        assertTrue(sim.previewEpilogue().stream().anyMatch(l -> l.startsWith("Nalia dorme.")));
        assertTrue(sim.previewEpilogue().stream().anyMatch(l -> l.startsWith("Você também dorme um pouco pior")));
    }

    @Test
    void jogadorPodeApoiarOCorteOuFazerNaliaSoltarAArvore() {
        Simulation cut = newSim(1);
        arc(cut, "restauracao", "insonia", "vozes", "impasse");
        talk(cut, "ferreiro");
        assertTrue(cut.view().knows("ferreiro_quer_cortar"));
        choose(cut, "Eu ajudo.");
        assertEquals("pai_corta_arvore", stage(cut, A1));
        assertEquals("cortada", cut.view().propState("arvore_nalia"));
        assertEquals(-25, cut.view().opinion("nalia"));
        assertTrue(cut.previewEpilogue().stream().anyMatch(l -> l.contains("toco claro")));

        Simulation release = newSim(1);
        arc(release, "restauracao", "insonia", "vozes", "impasse");
        release.applyNow(List.of(new Effect.Learn("player", "arvore_tira_algo", null, "claim")));
        release.fastForward(release.view().clock().ticksPerHour() * 3); // 09:00: Nalia trabalha à porta do moinho
        talk(release, "nalia");
        choose(release, "Ela está te gastando. Você sabe.");
        choose(release, "Então solta.");
        assertEquals("arvore_morre", stage(release, A1));
        assertEquals("morta", release.view().propState("arvore_nalia"));
    }

    // ---------------------------------------------------------------- agendas, presença, novidades

    @Test
    void arcoSobrepoeAAgenda_naliaPassaANoiteNaArvore() {
        Simulation sim = newSim(1);
        sim.fastForward(sim.view().clock().ticksPerHour() * 17); // 23:00 do dia 1
        assertEquals("dormir", sim.view().npc("nalia").activity());
        arc(sim, "restauracao", "insonia");
        assertEquals(Simulation.WALKING, sim.view().npc("nalia").activity(), "a agenda reage assim que o estágio muda (RF-083)");
        sim.fastForward(sim.view().clock().ticksPerHour());
        assertEquals("vigiar", sim.view().npc("nalia").activity());
        assertTrue(sim.view().actors().stream().anyMatch(a -> a.id().equals("nalia")), "ela está do lado de fora");
        NpcDef nalia = content.npcs().get("nalia");
        assertEquals("dormir", nalia.blockAt(23 * 60).activity(), "sem o arco, a rotina normal cobre a noite");
        arc(sim, "vozes", "impasse", "nalia_piora");
        sim.fastForward(sim.view().clock().ticksPerHour() * 12);
        assertEquals("de_cama", sim.view().npc("nalia").activity());
        assertTrue(sim.view().actors().stream().noneMatch(a -> a.id().equals("nalia")));
    }

    @Test
    void quemPartiuSomeDoMundo_eVoltaSeOEstadoMudar() {
        Simulation sim = newSim(1);
        assertTrue(sim.view().actors().stream().anyMatch(a -> a.id().equals("aldea")));
        sim.applyNow(List.of(new Effect.NpcStatus("aldea", "departed")));
        assertEquals("departed", sim.view().npcStatus("aldea"));
        assertTrue(sim.view().actors().stream().noneMatch(a -> a.id().equals("aldea")));
        sim.submit(new Command.Talk("aldea"));
        run(sim, 60);
        assertNull(sim.view().dialogue());
        sim.fastForward(days(sim, 2));
        assertTrue(sim.view().actors().stream().noneMatch(a -> a.id().equals("aldea")), "a ausência é persistente");
        assertTrue(Rules.condition(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .set("npc", new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                        .put("id", "aldea").put("status", "departed"))) instanceof Condition.NpcStatus);
        sim.applyNow(List.of(new Effect.NpcStatus("aldea", "present")));
        assertTrue(sim.view().actors().stream().anyMatch(a -> a.id().equals("aldea")));
    }

    @Test
    void viajanteContaAsNovidadesDeFora() {
        Simulation sim = newSim(1);
        sim.applyNow(List.of(new Effect.Arc("e2_sal", "subiu")));
        sim.fastForward(sim.view().clock().ticksPerHour() + 100); // 07:00 e pouco: o viajante está na praça
        talk(sim, "viajante");
        choose(sim, "O que há de novo lá fora?");
        assertEquals("Na estrada, dizem: O sal de remédio subiu de preço no Posto.", sim.view().dialogue().text());
        assertEquals("claim", sim.view().knowledge().stream().filter(k -> k.fact().equals("sal_subiu"))
                .findFirst().orElseThrow().certainty());
        choose(sim, "Mais alguma coisa?");
        assertTrue(sim.view().knows("trilhos_caldris"));
        choose(sim, "Mais alguma coisa?");
        assertEquals("Nada que valha a viagem.", sim.view().dialogue().text(), "sem novidade, ele diz que não tem");
    }

    // ---------------------------------------------------------------- memória, opinião, atos

    @Test
    void atoSoPesaParaQuemViuOuFicouSabendo() {
        Simulation sim = newSim(1);
        // 06:00: a aldeã está no poço, a poucos metros; o moleiro, longe, à porta do moinho
        int before = sim.view().opinion("aldea"), miller = sim.view().opinion("moleiro");
        sim.applyNow(List.of(new Effect.Deed("roubo")));
        assertEquals(before - 30, sim.view().opinion("aldea"));
        assertEquals(miller, sim.view().opinion("moleiro"), "quem não viu não muda de atitude (RF-281)");
        assertEquals("witnessed", sim.view().npcKnowledge("aldea").get("jogador_roubou").certainty());
        assertTrue(sim.view().memories("aldea").stream().anyMatch(m -> m.type().equals("roubo") && m.day() == 1));
        assertTrue(sim.view().memories("moleiro").isEmpty());
        assertTrue(sim.view().chronicle().stream().anyMatch(c -> c.type().equals("ato") && c.text().contains("aldea")));

        // o ato vira rumor; quem ouve também muda, só que menos
        sim.fastForward(days(sim, 4));
        long heard = content.npcs().keySet().stream().filter(id -> sim.view().memories(id).stream()
                .anyMatch(m -> m.type().equals("ouviu:roubo"))).count();
        assertTrue(heard >= 1, "em quatro dias o caso deveria ter corrido");
        for (String id : content.npcs().keySet()) {
            if (sim.view().memories(id).stream().anyMatch(m -> m.type().equals("ouviu:roubo")))
                assertEquals(content.npcs().get(id).opinion() - 12, sim.view().opinion(id), id);
        }
    }

    @Test
    void atoSemTestemunhaNaoMudaNada_eQuemDetestaOJogadorNaoConversa() {
        Simulation sim = newSim(1);
        sim.submit(new Command.Teleport(8.0, 8.0)); // canto vazio do mapa
        sim.step();
        sim.applyNow(List.of(new Effect.Deed("agressao"), new Effect.Deed("ato_que_nao_existe")));
        for (String id : content.npcs().keySet())
            assertEquals(content.npcs().get(id).opinion(), sim.view().opinion(id), id);
        assertTrue(sim.view().chronicle().stream().anyMatch(c -> c.text().contains("ninguém viu")));

        sim.applyNow(List.of(new Effect.Opinion("aldea", -50)));
        sim.submit(new Command.Teleport(24.5, 29.5));
        sim.submit(new Command.Talk("aldea"));
        List<SimEvent> events = run(sim, 30 * 20);
        assertNull(sim.view().dialogue(), "recusa de diálogo (RF-089)");
        assertTrue(events.contains(new SimEvent.AmbientLine(null, "aldea", "Com você eu não falo.")));
    }

    @Test
    void memoriaTemLimite_eCondicaoConsultaALembranca() throws Exception {
        Simulation sim = newSim(1);
        for (int i = 0; i < 30; i++) sim.applyNow(List.of(new Effect.Remember("moleiro", "visita", "player")));
        sim.applyNow(List.of(new Effect.Remember("moleiro", "ajuda", "nalia")));
        assertEquals(etrava.sim.story.Story.MEMORY_LIMIT, sim.view().memories("moleiro").size());
        assertEquals("ajuda", sim.view().memories("moleiro").get(etrava.sim.story.Story.MEMORY_LIMIT - 1).type());

        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        Effect remember = Rules.effect(json.readTree("{\"remember\":{\"npc\":\"aldea\",\"type\":\"favor\"}}"));
        assertEquals(new Effect.Remember("aldea", "favor", "player"), remember);
        assertEquals(remember, Rules.effect(Rules.toJson(remember)));
        for (Effect e : List.of(new Effect.SetProp("arvore_nalia", "viva"), new Effect.Arc(A1, "impasse"),
                new Effect.NpcStatus("aldea", "away"), new Effect.Deed("roubo"))) {
            assertEquals(e, Rules.effect(Rules.toJson(e)), "efeito agendado precisa sobreviver ao save");
        }
        Condition c = Rules.condition(json.readTree(
                "{\"all\":[{\"remembers\":{\"npc\":\"moleiro\",\"type\":\"ajuda\",\"subject\":\"nalia\"}},"
                        + "{\"arc\":{\"id\":\"a1_nalia\",\"stage\":[\"antes\",\"impasse\"]}},"
                        + "{\"prop\":{\"key\":\"arvore_nalia\",\"state\":\"morta\"}},"
                        + "{\"npc\":{\"id\":\"estudioso\",\"status\":\"away\"}}]}"));
        assertInstanceOf(Condition.All.class, c);
        // usada num diálogo de teste: a condição passa contra o mundo
        sim.schedule(sim.view().clock().tick() + 1, false, List.of(new Effect.SetFlag("marcou", true)));
        run(sim, 2);
        assertTrue(sim.view().flag("marcou"));
    }

    // ---------------------------------------------------------------- epílogo e conteúdo

    @Test
    void epilogoMudaComOEstadoDoMundo() {
        Simulation sim = newSim(11);
        assertTrue(sim.view().epilogue().isEmpty());
        assertTrue(sim.previewEpilogue().contains("No moinho, a história da menina e da árvore ainda não acabou."),
                "arco em andamento usa o fragmento sem condição");
        arc(sim, "impasse", "pai_corta_arvore");
        List<String> cut = sim.previewEpilogue();
        assertTrue(cut.stream().anyMatch(l -> l.startsWith("O moleiro cortou a árvore")));
        assertTrue(cut.stream().anyMatch(l -> l.contains("toco claro")));
        assertTrue(cut.stream().noneMatch(l -> l.contains("sal de remédio")), "o sal ainda não subiu: o trecho não entra");
        sim.applyNow(List.of(new Effect.Arc("e2_sal", "subiu"), new Effect.Opinion("moleiro", -60)));
        List<String> later = sim.previewEpilogue();
        assertTrue(later.stream().anyMatch(l -> l.contains("sal de remédio")));
        assertTrue(later.stream().anyMatch(l -> l.startsWith("Há portas em Pedra-Mansa")), "prioridade maior vence o padrão");
        assertEquals(cut.get(0), later.get(0), "a ordem dos espaços é fixa");

        assertTrue(content.epilogue().fragments().size() >= 15, "RF-163 pede pelo menos 15 fragmentos");
        List<SimEvent> events = new ArrayList<>();
        sim.submit(new Command.Sleep());
        for (int i = 0; i < 40 && sim.view().epilogue().isEmpty(); i++) {
            events.addAll(sim.step());
            sim.submit(new Command.Sleep());
        }
        assertFalse(sim.view().epilogue().isEmpty());
        assertEquals(31, sim.view().clock().day(), "o fim do capítulo interrompe o sono");
        assertTrue(events.stream().anyMatch(e -> e instanceof SimEvent.ChapterEnded c && c.epilogue().equals(sim.view().epilogue())));
        int size = sim.view().epilogue().size();
        sim.fastForward(days(sim, 3));
        assertEquals(size, sim.view().epilogue().size(), "o epílogo é montado uma vez; o jogo continua");
    }

    @Test
    void conteudoDosArcosCarregaEPassaNoValidador() {
        assertEquals(List.of(), ContentValidator.validate(content));
        assertEquals(7, content.arcs().size());
        ArcDef a1 = content.arcs().get(A1);
        assertTrue(a1.major());
        assertEquals("Nalia e a árvore", a1.displayName());
        assertEquals(7 * 24, a1.stage("impasse").timeout().hours());
        long endings = a1.stages().values().stream().filter(ArcDef.Stage::terminal).count();
        assertEquals(5, endings);
        assertEquals("arcs/a1_nalia.yaml", content.arcFile(A1));
        assertEquals("roubo", content.deedByFact("jogador_roubou").id());
        assertNull(content.deedByFact("arvore_voltou"));
        assertEquals("prop.toco", content.keyedProp("arvore_nalia").spriteFor("cortada"));
        assertEquals("prop.arvore_morta", content.keyedProp("arvore_nalia").spriteFor("estado_que_nao_existe"));
        assertEquals("familia", content.npcs().get("nalia").relationWith("moleiro"));
    }

    @Test
    void validadorRejeitaArcoSemDesfechoEEpilogoSemGarantia() throws Exception {
        Path broken = Path.of(ArcsTest.class.getResource("/quebrado_arcos").toURI());
        List<String> errors = ContentValidator.validate(broken);
        String all = String.join("\n", errors);
        assertHas(errors, "arcs/ruins.yaml", "estágio 'espera' não tem prazo", all);
        assertHas(errors, "arcs/ruins.yaml", "aponta para estágio inexistente 'lugar_nenhum'", all);
        assertHas(errors, "arcs/ruins.yaml", "pode voltar a si mesmo", all);
        assertHas(errors, "arcs/ruins.yaml", "precisa de pelo menos uma saída sem condição", all);
        assertHas(errors, "arcs/ruins.yaml", "desfecho sem 'outcome' válido", all);
        assertHas(errors, "arcs/ruins.yaml", "não tem nenhum desfecho", all);
        assertHas(errors, "arcs/ruins.yaml", "só tem desfecho trágico", all);
        assertHas(errors, "arcs/ruins.yaml", "nenhum prop do mapa tem a chave 'porta_fantasma'", all);
        assertHas(errors, "arcs/ruins.yaml", "ato inexistente 'feitico'", all);
        assertHas(errors, "arcs/ruins.yaml", "arco maior 'sem_prazo' não tem espaço", all);
        assertHas(errors, "epilogues/cap1.yaml", "precisa de um fragmento sem condição", all);
        assertHas(errors, "epilogues/cap1.yaml", "fragmento em espaço inexistente 'sumido'", all);
        assertHas(errors, "epilogues/cap1.yaml", "arco inexistente 'arco_fantasma'", all);
        assertHas(errors, "npcs/andarilho.yaml", "situação inválida 'evaporado'", all);
        assertHas(errors, "npcs/andarilho.yaml", "tipo de relação inválido 'inveja'", all);
    }

    private static void assertHas(List<String> errors, String file, String text, String all) {
        assertTrue(errors.stream().anyMatch(e -> e.startsWith(file) && e.contains(text)),
                "esperava erro em " + file + " contendo \"" + text + "\"; vieram:\n" + all);
    }
}
