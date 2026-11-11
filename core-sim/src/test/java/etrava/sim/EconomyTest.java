package etrava.sim;

import static org.junit.jupiter.api.Assertions.*;

import etrava.sim.api.ActorView;
import etrava.sim.api.Command;
import etrava.sim.api.DialogueView;
import etrava.sim.api.SimEvent;
import etrava.sim.api.Simulation;
import etrava.sim.api.TradeView;
import etrava.sim.api.WorkerPolicy;
import etrava.sim.content.ContentDb;
import etrava.sim.content.ItemDef;
import etrava.sim.content.MerchantDef;
import etrava.sim.economy.Economy;
import etrava.sim.rules.Effect;
import etrava.sim.validation.ContentValidator;
import etrava.sim.world.MerchantState;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** M5: itens, comércio, preços, trabalhos, habilidades, fome, contêineres com dono e reputação (AT-09). */
class EconomyTest {
    private static ContentDb content;

    @BeforeAll
    static void load() {
        content = ContentDb.load(Path.of(System.getProperty("etrava.content", "../content")));
    }

    private static Simulation newSim(long seed) {
        return Simulation.newGame(content, seed, "vila_ref");
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

    /** Põe o jogador em cima de um NPC (os pontos da agenda nunca são bloqueados). */
    private static void goTo(Simulation sim, String npcId) {
        ActorView npc = sim.view().npc(npcId);
        step(sim, new Command.Teleport(npc.x(), npc.y()));
    }

    private static int choiceIndex(Simulation sim, String startsWith) {
        List<DialogueView.Choice> choices = sim.view().dialogue().choices();
        for (int i = 0; i < choices.size(); i++) if (choices.get(i).text().startsWith(startsWith)) return i;
        fail("escolha não encontrada: " + startsWith + " em " + choices);
        return -1;
    }

    private static void give(Simulation sim, String item, int qty, int money) {
        sim.applyNow(List.of(new Effect.Give(item, qty, money)));
    }

    // ---------------------------------------------------------------- conteúdo e origem

    @Test
    void conteudoDoM5CarregaEValida() {
        assertTrue(content.items().size() >= 25);
        assertEquals(2, content.merchants().size());
        assertEquals(3, content.jobs().size());
        assertEquals(6, content.skills().list().size());
        assertEquals("pedra_mansa", content.origin().id());
        assertTrue(ContentValidator.validate(content).isEmpty(), ContentValidator.validate(content).toString());
        assertEquals("taverna", content.merchantOf("taberneira").id());
        assertNull(content.merchantOf("moleiro"));
    }

    @Test
    void personagemComecaComOQueAOrigemDa() {
        Simulation sim = newSim(1);
        var v = sim.view();
        assertEquals(8, v.money());
        assertEquals(2, v.inventory().get("pao"));
        assertEquals("martelo", v.equipment().get("mainHand"));
        assertEquals("camisa_linho", v.equipment().get("body"));
        assertEquals(20.0, v.skills().get("oficio"), 1e-9);
        assertEquals(0.0, v.hunger(), 1e-9);
        assertEquals(6, v.skills().size());
        assertEquals(1, sim.economy().count("martelo"));
    }

    // ---------------------------------------------------------------- AT-09

    @Test
    void at09_depoisDeE2OSalSobeNoPostoEAReposicaoVoltaOEstoqueAosLimites() {
        Simulation sim = newSim(9);
        Economy eco = sim.economy();
        MerchantDef posto = content.merchants().get("posto");
        ItemDef sal = content.items().get("sal_medicinal"), corda = content.items().get("corda");
        int salAntes = eco.buyPrice(posto, sal), cordaAntes = eco.buyPrice(posto, corda);

        sim.applyNow(List.of(new Effect.Arc("e2_sal", "subiu"))); // E2 acontece
        assertTrue(sim.view().flag("sal_subiu"));
        assertEquals(1.5, sim.view().priceMod("sal"), 1e-9);
        double comercio = 1 - eco.skill(Economy.COMMERCE) * Economy.COMMERCE_WEIGHT;
        assertEquals(Math.round(sal.value() * 1.5 * comercio), eco.buyPrice(posto, sal));
        assertTrue(eco.buyPrice(posto, sal) > salAntes);
        assertEquals(cordaAntes, eco.buyPrice(posto, corda), "item sem sal não muda");
        assertTrue(eco.buyPrice(posto, content.items().get("carne_salgada"))
                > Math.round(content.items().get("carne_salgada").value() * comercio));

        // o jogador esvazia o sal e entope o comerciante de pregos
        step(sim, new Command.EnterArea("area_teste", null));
        give(sim, null, 0, 500);
        give(sim, "pregos", 12, 0);
        step(sim, new Command.OpenTrade("posto"));
        assertNotNull(sim.view().trade());
        for (int i = 0; i < 5; i++) sim.submit(new Command.Buy("sal_medicinal"));
        for (int i = 0; i < 12; i++) sim.submit(new Command.Sell("pregos"));
        sim.step();
        MerchantState s = sim.stateWorld().merchants.get("posto");
        assertEquals(0, s.qty("sal_medicinal"));
        assertEquals(20, s.qty("pregos"));
        step(sim, new Command.CloseTrade());

        sim.fastForward(hours(sim, 24 * 3)); // reposição de 3 em 3 dias
        for (MerchantDef.Entry e : posto.stockList()) {
            int qty = s.qty(e.item());
            assertTrue(qty >= e.min() && qty <= e.max(), e.item() + " fora dos limites: " + qty);
        }
        assertEquals(5, s.qty("sal_medicinal"));
        assertEquals(8, s.qty("pregos"));
        assertTrue(s.cash >= posto.cash());
    }

    // ---------------------------------------------------------------- comércio

    @Test
    void comprarEVenderMexemNoDinheiroDosDoisLadosETreinamComercio() {
        Simulation sim = newSim(2);
        at(sim, 16); // a taberneira serve do lado de fora à tarde
        assertTrue(sim.economy().canTrade(content.merchants().get("taverna")));
        long tick = sim.view().clock().tick();
        step(sim, new Command.OpenTrade("taverna"));
        TradeView t = sim.tradeView();
        assertEquals("Taberneira", t.name());
        assertFalse(t.container());
        assertEquals(40, t.partnerCash());
        TradeView.Offer pao = t.offers().stream().filter(o -> o.item().equals("pao")).findFirst().orElseThrow();
        double comercio = sim.view().skills().get("comercio");

        step(sim, new Command.Buy("pao"));
        assertEquals(8 - pao.price(), sim.view().money());
        assertEquals(3, sim.view().inventory().get("pao"));
        assertEquals(40 + pao.price(), sim.tradeView().partnerCash());
        assertTrue(sim.view().skills().get("comercio") > comercio);
        assertEquals(tick, sim.view().clock().tick(), "o relógio para com o comércio aberto (RF-324)");
        assertTrue(sim.stepPaused().isEmpty());

        // a taverna compra o que sai da horta, por menos do que vende: não dá para lucrar indo e voltando
        give(sim, "legumes", 2, 0);
        ItemDef legumes = content.items().get("legumes");
        MerchantDef taverna = content.merchants().get("taverna");
        int venda = sim.tradeView().wants().stream().filter(o -> o.item().equals("legumes")).findFirst().orElseThrow().price();
        assertTrue(venda > 0 && venda < sim.economy().buyPrice(taverna, legumes));
        int antes = sim.view().money();
        step(sim, new Command.Sell("legumes"));
        assertEquals(antes + venda, sim.view().money());
        // maçã vale tão pouco que ela não compra
        assertEquals(0, sim.economy().sellPrice(taverna, content.items().get("maca")));
        step(sim, new Command.Sell("maca"));
        assertEquals(1, sim.view().inventory().get("maca"));
        // a taverna não compra ferramenta
        step(sim, new Command.Unequip("mainHand"));
        int dinheiro = sim.view().money();
        step(sim, new Command.Sell("martelo"));
        assertEquals(dinheiro, sim.view().money());
        assertTrue(sim.tradeView().wants().stream().noneMatch(o -> o.item().equals("martelo")));
        // sem dinheiro, não compra
        sim.stateWorld().money = 0;
        step(sim, new Command.Buy("queijo"));
        assertEquals(0, sim.view().inventory().getOrDefault("queijo", 0));
        assertTrue(sim.tradeView().offers().stream().noneMatch(TradeView.Offer::possible));
        // andar com o comércio aberto não vale
        step(sim, new Command.MoveTo(10, 10));
        assertFalse(sim.view().hasDestination());
        List<SimEvent> fechou = step(sim, new Command.CloseTrade());
        assertTrue(fechou.contains(new SimEvent.TradeClosed("taverna")));
        assertNull(sim.view().trade());
        assertTrue(sim.view().clock().tick() > tick);
    }

    @Test
    void opiniaoEComercioMexemNoPreco() {
        Simulation sim = newSim(3);
        Economy eco = sim.economy();
        MerchantDef posto = content.merchants().get("posto");
        ItemDef gibao = content.items().get("gibao_couro");
        int neutro = eco.buyPrice(posto, gibao), pagaNeutro = eco.sellPrice(posto, gibao);
        sim.applyNow(List.of(new Effect.Opinion("comerciante_posto", 100)));
        assertTrue(eco.buyPrice(posto, gibao) < neutro);
        assertTrue(eco.sellPrice(posto, gibao) > pagaNeutro);
        assertEquals(-20, eco.opinionPercent(posto));
        sim.applyNow(List.of(new Effect.Opinion("comerciante_posto", -200)));
        assertTrue(eco.buyPrice(posto, gibao) > neutro);
        sim.stateWorld().skills.put("comercio", 100.0);
        sim.applyNow(List.of(new Effect.Opinion("comerciante_posto", 200)));
        assertTrue(eco.sellPrice(posto, gibao) <= eco.buyPrice(posto, gibao), "mesmo no melhor caso, sem lucro garantido");
    }

    @Test
    void reputacaoRuimFechaACozinhaMasNaoAConversa() {
        Simulation sim = newSim(4);
        at(sim, 16);
        List<Effect> raiva = new ArrayList<>();
        for (String id : List.of("aldea", "curandeira", "ferreiro", "moleiro", "nalia", "taberneira"))
            raiva.add(new Effect.Opinion(id, -30 - sim.view().opinion(id)));
        sim.applyNow(raiva);
        assertEquals(-30, sim.economy().reputation("pedra_mansa"));
        assertEquals(0, sim.economy().reputation("comunidade_que_nao_existe"));
        List<SimEvent> events = step(sim, new Command.OpenTrade("taverna"));
        assertNull(sim.view().trade());
        assertTrue(events.stream().anyMatch(e -> e instanceof SimEvent.AmbientLine l && l.npcId().equals("taberneira")));

        goTo(sim, "taberneira");
        step(sim, new Command.Talk("taberneira"));
        assertNotNull(sim.view().dialogue());
        step(sim, new Command.Choose(choiceIndex(sim, "O que tem no caldeirão")));
        assertTrue(sim.view().dialogue().text().startsWith("Para você a cozinha fechou"));
    }

    @Test
    void conversaAbreOComercioQuandoAcaba() {
        Simulation sim = newSim(5);
        at(sim, 16);
        goTo(sim, "taberneira");
        step(sim, new Command.Talk("taberneira"));
        List<SimEvent> events = step(sim, new Command.Choose(choiceIndex(sim, "O que tem no caldeirão")));
        assertNull(sim.view().dialogue());
        assertTrue(events.contains(new SimEvent.TradeOpened("taverna", false)));
        assertNotNull(sim.tradeView());
    }

    // ---------------------------------------------------------------- trabalho

    @Test
    void trabalharPagaGastaTempoTreinaEUmaVezPorDia() {
        Simulation sim = newSim(6);
        double oficio = sim.view().skills().get("oficio");
        List<SimEvent> events = step(sim, new Command.Work("oficina"));
        SimEvent.JobFinished done = events.stream().filter(e -> e instanceof SimEvent.JobFinished)
                .map(e -> (SimEvent.JobFinished) e).findFirst().orElseThrow();
        assertEquals(4.0, done.hours(), 1e-9);
        assertEquals(Math.round(9 * (1 + oficio / 100 * Economy.PAY_SKILL_BONUS)), done.pay());
        assertEquals(8 + done.pay(), sim.view().money());
        assertEquals(10, sim.view().clock().hour());
        assertTrue(sim.view().skills().get("oficio") > oficio);
        assertEquals(1.0, sim.view().var("dias_de_trabalho"), 1e-9);
        assertEquals(1, sim.view().opinion("ferreiro"));
        assertTrue(sim.view().hunger() > 15, "quatro horas de trabalho dão fome");
        assertTrue(sim.view().chronicle().stream().anyMatch(c -> c.type().equals("trabalho")));

        int money = sim.view().money();
        events = step(sim, new Command.Work("oficina")); // de novo no mesmo dia: não
        assertTrue(events.contains(new SimEvent.JobRefused("oficina")));
        assertEquals(money, sim.view().money());
        // fora da janela de horário também não
        at(sim, 20);
        assertFalse(sim.economy().canWork(content.jobs().get("colheita")));
        assertTrue(step(sim, new Command.Work("nao_existe")).contains(new SimEvent.JobRefused("nao_existe")));
    }

    @Test
    void famintoRendeMetade() {
        Simulation sim = newSim(7);
        sim.stateWorld().hunger = 95;
        assertTrue(sim.economy().starving());
        List<SimEvent> events = step(sim, new Command.Work("oficina"));
        SimEvent.JobFinished done = (SimEvent.JobFinished) events.stream()
                .filter(e -> e instanceof SimEvent.JobFinished).findFirst().orElseThrow();
        double oficio = 20;
        assertEquals(Math.round(9 * (1 + oficio / 100 * Economy.PAY_SKILL_BONUS) * 0.5), done.pay());
        assertEquals(100, sim.view().hunger(), 1e-9);
    }

    @Test
    void trabalhoPelaConversaComOFerreiro() {
        Simulation sim = newSim(8);
        goTo(sim, "ferreiro");
        step(sim, new Command.Talk("ferreiro"));
        step(sim, new Command.Choose(choiceIndex(sim, "Precisa de mão")));
        List<SimEvent> events = step(sim, new Command.Choose(choiceIndex(sim, "Começo agora")));
        assertNull(sim.view().dialogue());
        assertTrue(events.stream().anyMatch(e -> e instanceof SimEvent.JobFinished j && j.jobId().equals("oficina")));
        // depois de trabalhar, a oferta some da conversa
        goTo(sim, "ferreiro");
        step(sim, new Command.Talk("ferreiro"));
        assertTrue(sim.view().dialogue().choices().stream().noneMatch(c -> c.text().startsWith("Precisa de mão")));
    }

    @Test
    void labiaNaConversaTreinaEMudaAOpiniao() {
        Simulation sim = newSim(10);
        step(sim, new Command.EnterArea("area_teste", null));
        sim.applyNow(List.of(new Effect.SetVar("dias_de_trabalho", 1)));
        goTo(sim, "comerciante_posto");
        step(sim, new Command.Talk("comerciante_posto"));
        double labia = sim.view().skills().get("labia");
        int i = choiceIndex(sim, "Pelo tanto");
        assertEquals("Lábia", sim.view().dialogue().choices().get(i).tag());
        step(sim, new Command.Choose(i));
        assertTrue(sim.view().skills().get("labia") > labia);
        assertEquals(12, sim.view().opinion("comerciante_posto"));
    }

    // ---------------------------------------------------------------- fome, mochila e corpo

    @Test
    void fomeSobeComOTempoECaiAoComer() {
        Simulation sim = newSim(11);
        sim.fastForward(hours(sim, 12));
        assertEquals(50, sim.view().hunger(), 0.5);
        List<SimEvent> events = sim.stepPaused();
        assertTrue(events.isEmpty());
        sim.submit(new Command.UseItem("pao"));
        events = sim.stepPaused();
        assertTrue(events.contains(new SimEvent.ItemUsed("pao")));
        assertEquals(20, sim.view().hunger(), 0.5);
        assertEquals(1, sim.view().inventory().get("pao"));
        sim.submit(new Command.UseItem("martelo")); // não se come martelo
        sim.stepPaused();
        assertEquals(1, sim.economy().count("martelo"));
        sim.fastForward(hours(sim, 48));
        assertEquals(100, sim.view().hunger(), 1e-9);
    }

    @Test
    void equiparTrocaOQueEstavaNoEncaixe() {
        Simulation sim = newSim(12);
        give(sim, "gibao_couro", 1, 0);
        double peso = sim.economy().weight();
        sim.submit(new Command.Equip("gibao_couro"));
        sim.stepPaused();
        assertEquals("gibao_couro", sim.view().equipment().get("body"));
        assertEquals(1, sim.view().inventory().get("camisa_linho"));
        assertEquals(peso, sim.economy().weight(), 1e-9);
        sim.submit(new Command.Equip("pao")); // pão não se veste
        sim.submit(new Command.Unequip("head")); // nada na cabeça
        sim.submit(new Command.Unequip("body"));
        sim.stepPaused();
        assertNull(sim.view().equipment().get("body"));
        assertEquals(1, sim.view().inventory().get("gibao_couro"));
        // a bolsa de cinto aumenta a carga
        double cap = sim.economy().capacity();
        give(sim, "bolsa_cinto", 1, 0);
        sim.submit(new Command.Equip("bolsa_cinto"));
        sim.stepPaused();
        assertEquals(cap + 6, sim.economy().capacity(), 1e-9);
        // efeito take tira da mochila e, se faltar, do corpo
        sim.applyNow(List.of(new Effect.Take("bolsa_cinto", 1, 3)));
        assertEquals(0, sim.economy().count("bolsa_cinto"));
        assertEquals(5, sim.view().money());
    }

    @Test
    void cargaDemaisDeixaOPersonagemLento() {
        Simulation leve = newSim(13), pesado = newSim(13);
        give(pesado, "farinha", 10, 0);
        assertTrue(pesado.economy().overloaded());
        assertFalse(leve.economy().overloaded());
        double[] andou = new double[2];
        Simulation[] sims = {leve, pesado};
        for (int i = 0; i < 2; i++) {
            double x = sims[i].view().playerX(), y = sims[i].view().playerY();
            step(sims[i], new Command.MoveDir(1, 1));
            for (int t = 0; t < 5; t++) sims[i].step();
            andou[i] = Math.hypot(sims[i].view().playerX() - x, sims[i].view().playerY() - y);
        }
        assertTrue(andou[0] > 0);
        assertEquals(Economy.OVERLOAD_SPEED, andou[1] / andou[0], 0.05);
    }

    // ---------------------------------------------------------------- contêineres com dono

    @Test
    void pegarDoQueTemDonoERouboSeAlguemVir() {
        Simulation sim = newSim(14);
        at(sim, 10); // a aldeã trabalha no feno
        double[] feno = sim.economy().containerPoint(content.containers().get("sacos_moinho"));
        step(sim, new Command.Teleport(feno[0], feno[1]));
        step(sim, new Command.OpenContainer("sacos_moinho"));
        TradeView t = sim.tradeView();
        assertTrue(t.container());
        assertEquals("Moleiro", t.owner());
        assertEquals(3, t.offers().get(0).qty());
        List<SimEvent> events = step(sim, new Command.TakeItem("farinha"));
        assertEquals(1, sim.view().inventory().get("farinha"));
        assertTrue(events.stream().anyMatch(e -> e instanceof SimEvent.DeedSeen d && d.deedId().equals("roubo")));
        assertEquals(-30, sim.view().opinion("aldea"));
        assertTrue(sim.view().npcKnowledge("aldea").containsKey("jogador_roubou"));
        step(sim, new Command.CloseTrade());

        // de madrugada, todo mundo dorme: ninguém vê
        at(sim, 2);
        double[] horta = sim.economy().containerPoint(content.containers().get("cesto_horta"));
        step(sim, new Command.Teleport(horta[0], horta[1]));
        int curandeira = sim.view().opinion("curandeira");
        step(sim, new Command.OpenContainer("cesto_horta"));
        events = step(sim, new Command.TakeItem("ervas"));
        assertTrue(events.stream().noneMatch(e -> e instanceof SimEvent.DeedSeen));
        assertEquals(curandeira, sim.view().opinion("curandeira"));
        assertEquals(1, sim.view().inventory().get("ervas"));
        step(sim, new Command.CloseTrade());

        // longe do contêiner, o personagem anda até lá e abre ao chegar
        step(sim, new Command.Teleport(feno[0] + 0.5, feno[1] + 0.5));
        step(sim, new Command.MoveTo(horta[0] - 6, horta[1]));
        for (int i = 0; i < 300 && sim.view().playerMoving(); i++) sim.step();
        step(sim, new Command.OpenContainer("cesto_horta"));
        for (int i = 0; i < 600 && sim.view().trade() == null; i++) sim.step();
        assertNotNull(sim.view().trade());
    }

    @Test
    void donoRepoeOContainerComOTempo() {
        Simulation sim = newSim(15);
        sim.stateWorld().containers.get("sacos_moinho").put("farinha", 0);
        sim.fastForward(hours(sim, 24 * 2));
        assertEquals(3, sim.stateWorld().containers.get("sacos_moinho").get("farinha"));
    }

    // ---------------------------------------------------------------- viver trabalhando (marco M5)

    @Test
    void m5_dezDiasSoTrabalhandoEComerciando() {
        Simulation sim = newSim(42);
        WorkerPolicy policy = new WorkerPolicy(sim, "taverna");
        policy.run(10);
        var v = sim.view();
        assertEquals(11, v.clock().day());
        assertTrue(policy.maxHunger() < Economy.STARVING, "nunca passou fome de verdade: " + policy.maxHunger());
        assertTrue(v.money() > 8 + 30, "juntou dinheiro: " + v.money());
        assertTrue(v.var("dias_de_trabalho") >= 18, "trabalhou: " + v.var("dias_de_trabalho"));
        assertTrue(v.skills().get("oficio") > 25 && v.skills().get("vigor") > 20);
        assertTrue(v.skills().get("comercio") > 5);
        assertTrue(v.chronicle().stream().filter(c -> c.type().equals("trabalho")).count() >= 18);
    }

    @Test
    void politicaTrabalhadorEDeterministicaESobreviveAoSave(@TempDir Path dir) throws Exception {
        Simulation a = newSim(77), b = newSim(77);
        new WorkerPolicy(a, "taverna").run(3);
        new WorkerPolicy(b, "taverna").run(3);
        assertEquals(a.stateHash(), b.stateHash());
        assertEquals(a.commandLog().size(), b.commandLog().size());

        Path file = dir.resolve("vida.etrava");
        a.save(file, "agora");
        Simulation loaded = Simulation.load(content, file);
        assertEquals(a.stateHash(), loaded.stateHash());
        assertEquals(a.view().money(), loaded.view().money());
        assertEquals(a.view().inventory(), loaded.view().inventory());
        assertEquals(a.view().skills(), loaded.view().skills());
        a.fastForward(hours(a, 30));
        loaded.fastForward(hours(loaded, 30));
        assertEquals(a.stateHash(), loaded.stateHash());

        // não se salva com o comércio aberto
        at(a, 16);
        step(a, new Command.OpenTrade("taverna"));
        assertThrows(etrava.sim.save.SaveGame.SaveException.class, () -> a.save(dir.resolve("x.etrava"), ""));
    }

    // ---------------------------------------------------------------- validador

    @Test
    void validadorApontaConteudoDeEconomiaQuebrado() throws Exception {
        Path broken = Path.of(EconomyTest.class.getResource("/quebrado_economia").toURI());
        String all = String.join("\n", ContentValidator.validate(broken));
        for (String expected : List.of("categoria inválida 'inventada'", "encaixe inválido 'pe'",
                "peso, valor e comida não podem ser negativos", "item inexistente 'sumido'",
                "habilidade duplicada 'oficio'", "item inexistente 'inexistente'", "'pao' não vai no encaixe 'head'",
                "habilidade inexistente 'voar'", "NPC inexistente 'fantasma'", "mínimo/máximo inválidos",
                "comunidade sem nenhum NPC 'guilda'", "trabalho 'sem_nada'", "ponto inexistente 'a/lugar_que_nao_existe'",
                "comerciante inexistente 'banca_fantasma'", "item inexistente 'nada'", "não tem o ato 'roubo'")) {
            assertTrue(all.contains(expected), "faltou: " + expected + "\n" + all);
        }
    }
}
