package etrava.sim.story;

import etrava.sim.api.DialogueView;
import etrava.sim.api.SimEvent;
import etrava.sim.content.AmbientDef;
import etrava.sim.content.ArcDef;
import etrava.sim.content.ContentDb;
import etrava.sim.content.DeedDef;
import etrava.sim.content.DialogueDef;
import etrava.sim.content.EpilogueDef;
import etrava.sim.content.FactDef;
import etrava.sim.content.NpcDef;
import etrava.sim.content.PropDef;
import etrava.sim.economy.Economy;
import etrava.sim.rules.Condition;
import etrava.sim.rules.Effect;
import etrava.sim.world.ActorState;
import etrava.sim.world.ArcState;
import etrava.sim.world.ChronicleEntry;
import etrava.sim.world.MemoryEntry;
import etrava.sim.world.DialogueSession;
import etrava.sim.world.KnowledgeEntry;
import etrava.sim.world.World;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * A parte narrativa da simulação: condições e efeitos, quem sabe o quê, rumores
 * entre NPCs, memória e opinião, atos do jogador, diálogos, conversas ambientes
 * e o epílogo. Trabalha sempre sobre o {@link World}; não guarda estado próprio
 * além de dois avisos para a {@code Simulation} ("algo mudou", "reveja as agendas").
 */
public final class Story {
    /** Distância, em metros, para conversar com alguém. */
    public static final double TALK_RANGE = 2.2;
    /** Até onde o jogador escuta uma conversa alheia. */
    public static final double HEAR_RANGE = 9.0, HEAR_PER_OBSERVATION = 0.06;
    /** Ganho-base de habilidade ao usá-la numa conversa, e ao escutar uma conversa alheia até o fim. */
    public static final double SKILL_CHOICE_GAIN = 1.0, OBSERVATION_GAIN = 0.5;
    /** NPCs a esta distância uns dos outros estão "juntos": conversam e trocam rumores. */
    public static final double GROUP_RANGE = 6.0;
    /** Chance de um NPC passar um rumor a outro a cada hora juntos, e de ele sair distorcido. */
    public static final double RUMOR_CHANCE = 0.5, DISTORT_CHANCE = 0.15;
    /** Até onde alguém vê o que o jogador faz (RF-089). */
    public static final double WITNESS_RANGE = 10.0;
    /** Quantas lembranças um NPC guarda; as mais antigas somem (RF-084). */
    public static final int MEMORY_LIMIT = 20;
    /** Com opinião daqui para baixo, o NPC se recusa a conversar (RF-089). */
    public static final int REFUSE_OPINION = -40;
    /** Parentes e amigos contam mais um ao outro; rivais, menos (RF-086). */
    public static final double CLOSE_FACTOR = 1.5, RIVAL_FACTOR = 0.4;
    /** Fonte do conhecimento que vem de arcos e eventos, não de uma pessoa. */
    public static final String WORLD = "mundo";
    private static final int LINE_TICKS = 80, AMBIENT_CHECK_TICKS = 25;
    private static final String PLAYER = "player", THIS_NPC = "npc", SLEEPING = "dormir";

    /** Como o {@link Story} pede à simulação para agendar efeitos. */
    public interface Scheduler {
        void schedule(long atTick, boolean interrupts, List<Effect> effects);
    }

    private final ContentDb content;
    private final World world;
    private final Scheduler scheduler;
    private Arcs arcs;
    private Economy economy;
    private etrava.sim.combat.Combat combat;
    /** Um efeito mexeu no mundo desde a última vez que os arcos foram conferidos. */
    private boolean dirty;
    /** Algo de que as agendas dependem mudou (estágio de arco, situação de NPC). */
    private boolean agendaDirty;

    public Story(ContentDb content, World world, Scheduler scheduler) {
        this.content = content;
        this.world = world;
        this.scheduler = scheduler;
    }

    public void setArcs(Arcs arcs) { this.arcs = arcs; }
    public void setEconomy(Economy economy) { this.economy = economy; }
    public void setCombat(etrava.sim.combat.Combat combat) { this.combat = combat; }

    /** Devolve true (uma vez) se algum efeito foi aplicado desde a última consulta. */
    public boolean takeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    public boolean takeAgendaDirty() {
        boolean was = agendaDirty;
        agendaDirty = false;
        return was;
    }

    void arcChanged() { agendaDirty = true; }

    // ---------------------------------------------------------------- condições

    /** @param npc o NPC da conversa atual (para "npc" em who/npc), ou null */
    public boolean test(Condition c, String npc) {
        return switch (c) {
            case Condition.All a -> a.of().stream().allMatch(x -> test(x, npc));
            case Condition.Any a -> a.of().stream().anyMatch(x -> test(x, npc));
            case Condition.Not n -> !test(n.of(), npc);
            case Condition.Flag f -> world.flag(f.name()) == f.value();
            case Condition.Var v -> (v.min() == null || world.var(v.name()) >= v.min())
                    && (v.max() == null || world.var(v.name()) <= v.max());
            case Condition.Knows k -> knows(resolve(k.who(), npc), k.fact());
            case Condition.Opinion o -> {
                int value = world.opinion(resolve(o.npc(), npc));
                yield (o.min() == null || value >= o.min()) && (o.max() == null || value <= o.max());
            }
            case Condition.Time t -> {
                int day = world.clock.day();
                double hour = world.clock.minuteOfDay() / 60.0;
                boolean dayOk = (t.dayMin() == null || day >= t.dayMin()) && (t.dayMax() == null || day <= t.dayMax());
                boolean hourOk = true;
                if (t.hourFrom() != null && t.hourTo() != null) {
                    hourOk = t.hourFrom() <= t.hourTo() ? hour >= t.hourFrom() && hour < t.hourTo()
                            : hour >= t.hourFrom() || hour < t.hourTo();
                }
                yield dayOk && hourOk;
            }
            case Condition.NpcIn n -> {
                ActorState actor = world.npcs.get(resolve(n.npc(), npc));
                yield actor != null && n.area().equals(actor.areaId);
            }
            case Condition.NpcStatus n -> {
                ActorState actor = world.npcs.get(resolve(n.npc(), npc));
                yield actor != null && n.status().equals(actor.status);
            }
            case Condition.Arc a -> {
                ArcDef def = content.arcs().get(a.id());
                ArcState state = world.arcs.get(a.id());
                ArcDef.Stage stage = def == null || state == null ? null : def.stage(state.stage());
                if (stage == null) yield false;
                boolean stageOk = a.stages().isEmpty() || a.stages().contains(stage.id());
                yield stageOk && (a.outcome() == null || (stage.terminal() && a.outcome().equals(stage.outcome())));
            }
            case Condition.Prop p -> p.state().equals(propState(p.key()));
            case Condition.Remembers r -> {
                boolean found = false;
                for (MemoryEntry m : world.memories(resolve(r.npc(), npc))) {
                    found |= m.type().equals(r.type()) && (r.subject() == null || r.subject().equals(m.subject()));
                }
                yield found;
            }
            case Condition.Chance ch -> world.rng.chance(ch.probability());
            case Condition.Has h -> (h.item() == null || economy.count(h.item()) >= h.qty())
                    && (h.money() == null || world.money >= h.money());
            case Condition.Skill sk -> {
                double value = economy.skill(sk.name());
                yield (sk.min() == null || value >= sk.min()) && (sk.max() == null || value <= sk.max());
            }
            case Condition.Reputation r -> {
                int value = economy.reputation(r.faction());
                yield (r.min() == null || value >= r.min()) && (r.max() == null || value <= r.max());
            }
            case Condition.Job j -> economy.canWork(content.jobs().get(j.id()));
        };
    }

    /** Variante atual de um prop com chave: a que o mundo guarda, ou a inicial do conteúdo. */
    public String propState(String key) {
        String state = world.props.get(key);
        if (state != null) return state;
        PropDef def = content.keyedProp(key);
        return def == null ? null : def.initialState();
    }

    private static String resolve(String who, String npc) {
        return THIS_NPC.equals(who) && npc != null ? npc : who;
    }

    public boolean knows(String who, String fact) {
        return PLAYER.equals(who) ? world.knows(fact) : world.npcKnowledge(who).containsKey(fact);
    }

    // ---------------------------------------------------------------- efeitos

    /**
     * @param npc    o NPC da conversa atual, ou null
     * @param source de onde vem o conhecimento que este efeito der ("npc:id", "conversa:id", "mundo")
     */
    public void apply(Effect effect, String npc, String source, List<SimEvent> events) {
        dirty = true;
        switch (effect) {
            case Effect.SetFlag f -> world.flags.put(f.name(), f.value());
            case Effect.SetVar v -> world.vars.put(v.name(), v.value());
            case Effect.AddVar v -> world.vars.merge(v.name(), v.value(), Double::sum);
            case Effect.Learn l -> learn(resolve(l.who(), npc), l.fact(), l.version(), l.certainty(), source, events);
            case Effect.Opinion o -> addOpinion(resolve(o.npc(), npc), o.add());
            case Effect.SetProp p -> {
                if (!p.state().equals(propState(p.key()))) {
                    world.props.put(p.key(), p.state());
                    world.chronicle.add(new ChronicleEntry(world.clock.tick(), "prop", p.key(),
                            p.key() + " agora está: " + p.state()));
                    if (events != null) events.add(new SimEvent.PropChanged(p.key(), p.state()));
                }
            }
            case Effect.Arc a -> {
                if (arcs != null) arcs.force(a.id(), a.to(), events);
            }
            case Effect.NpcStatus n -> setStatus(resolve(n.npc(), npc), n.status());
            case Effect.Remember r -> remember(resolve(r.npc(), npc), r.type(), r.subject());
            case Effect.Deed d -> deed(d.id());
            case Effect.Chronicle c ->
                    world.chronicle.add(new ChronicleEntry(world.clock.tick(), c.type(), c.ref(), c.text()));
            case Effect.Schedule s -> scheduler.schedule(
                    world.clock.tick() + Math.round(s.hours() * world.clock.ticksPerHour()), s.interrupts(), s.effects());
            case Effect.Give g -> {
                if (g.item() != null) economy.give(g.item(), g.qty());
                world.money += Math.max(0, g.money());
            }
            case Effect.Take t -> {
                if (t.item() != null) economy.take(t.item(), t.qty());
                world.money = Math.max(0, world.money - Math.max(0, t.money()));
            }
            case Effect.PriceMod p -> economy.priceMod(p.tag(), p.factor());
            case Effect.Train t -> economy.train(t.skill(), t.amount());
            case Effect.StartJob j -> world.pendingJob = j.id();
            case Effect.StartTrade t -> world.pendingTrade = t.id();
            case Effect.Encounter en -> {
                if (combat != null) combat.forceEncounter(en.id(), events);
            }
        }
    }

    public void applyAll(List<Effect> effects, String npc, String source, List<SimEvent> events) {
        for (Effect e : effects) apply(e, npc, source, events);
    }

    /** Alguém passa a saber de um fato. Devolve true se era novidade. */
    public boolean learn(String who, String factId, String version, String certainty, String source,
            List<SimEvent> events) {
        FactDef fact = content.facts().get(factId);
        if (fact == null) return false;
        String v = version != null && fact.hasVersion(version) ? version : fact.defaultVersion();
        KnowledgeEntry entry = new KnowledgeEntry(factId, v, certainty, source, world.clock.day(), world.areaId);
        if (PLAYER.equals(who)) {
            for (KnowledgeEntry k : world.playerKnowledge) {
                if (k.fact().equals(factId) && k.version().equals(v) && k.source().equals(source)) return false;
            }
            world.playerKnowledge.add(entry);
            if (events != null) events.add(new SimEvent.FactLearned(factId, v));
            return true;
        }
        if (!world.npcs.containsKey(who)) return false;
        // um NPC fica com a primeira versão que ouviu
        boolean fresh = world.npcKnowledge.computeIfAbsent(who, k -> new LinkedHashMap<>())
                .putIfAbsent(factId, entry) == null;
        // quem fica sabendo de um ato do jogador por boca dos outros também muda de ideia, só que menos (RF-281)
        DeedDef deed = fresh && !"witnessed".equals(certainty) ? content.deedByFact(factId) : null;
        if (deed != null) {
            addOpinion(who, deed.hearsay());
            remember(who, "ouviu:" + deed.id(), PLAYER);
        }
        return fresh;
    }

    // ---------------------------------------------------------------- opinião, memória, atos

    private void addOpinion(String npcId, int delta) {
        world.opinions.put(npcId, Math.max(-100, Math.min(100, world.opinion(npcId) + delta)));
    }

    /** O NPC guarda um acontecimento; passando do limite, esquece o mais antigo (RF-084). */
    public void remember(String npcId, String type, String subject) {
        if (!world.npcs.containsKey(npcId)) return;
        List<MemoryEntry> list = world.memories.computeIfAbsent(npcId, k -> new ArrayList<>());
        list.add(new MemoryEntry(type, subject, world.clock.day(), world.npcs.get(npcId).areaId));
        while (list.size() > MEMORY_LIMIT) list.remove(0);
    }

    /**
     * O jogador comete um ato. Quem está por perto, acordado e à vista, vê: muda de
     * opinião, guarda na memória e passa a poder contar aos outros. Sem testemunha,
     * ninguém fica sabendo (RF-089, RF-281). Devolve quantos viram.
     */
    public int deed(String deedId) {
        DeedDef def = content.deeds().get(deedId);
        if (def == null) return 0;
        List<String> witnesses = new ArrayList<>();
        for (ActorState npc : world.npcs.values()) {
            if (!npc.present() || npc.hidden || SLEEPING.equals(npc.activity) || !world.areaId.equals(npc.areaId)
                    || Math.hypot(npc.x - world.player.x, npc.y - world.player.y) > WITNESS_RANGE) continue;
            witnesses.add(npc.id);
            addOpinion(npc.id, def.witness());
            remember(npc.id, def.id(), PLAYER);
            if (def.fact() != null) learn(npc.id, def.fact(), null, "witnessed", "viu", null);
        }
        world.chronicle.add(new ChronicleEntry(world.clock.tick(), "ato", def.id(), def.displayName()
                + (witnesses.isEmpty() ? " — ninguém viu" : " — visto por " + String.join(", ", witnesses))));
        return witnesses.size();
    }

    /** Um NPC parte, morre, some por um tempo ou volta (RF-087). */
    private void setStatus(String npcId, String status) {
        ActorState npc = world.npcs.get(npcId);
        if (npc == null || status.equals(npc.status)) return;
        npc.status = status;
        npc.path.clear();
        npc.moving = false;
        npc.leaving = false;
        npc.target = null; // ao voltar, a agenda decide de novo onde ele fica
        npc.hidden = !npc.present();
        npc.activity = "";
        agendaDirty = true;
        NpcDef def = content.npcs().get(npcId);
        world.chronicle.add(new ChronicleEntry(world.clock.tick(), "npc", npcId,
                (def != null ? def.displayName() : npcId) + ": " + status));
    }

    // ---------------------------------------------------------------- epílogo

    /**
     * Monta o epílogo do capítulo (RF-163): em cada espaço, o fragmento de maior
     * prioridade cuja condição passa. Espaço sem fragmento válido fica de fora.
     */
    public List<String> composeEpilogue() {
        EpilogueDef def = content.epilogue();
        List<String> lines = new ArrayList<>();
        if (def == null) return lines;
        for (EpilogueDef.Slot slot : def.slots()) {
            List<EpilogueDef.Fragment> candidates = new ArrayList<>();
            for (EpilogueDef.Fragment f : def.fragments()) if (f.slot().equals(slot.id())) candidates.add(f);
            candidates.sort((a, b) -> Integer.compare(b.priority(), a.priority())); // estável: empate = ordem do arquivo
            for (EpilogueDef.Fragment f : candidates) {
                if (test(f.when(), null)) {
                    lines.add(f.text());
                    break;
                }
            }
        }
        return lines;
    }

    /** O que um NPC já sabe e pensa ao começar o jogo. */
    public void initNpc(NpcDef def) {
        world.opinions.put(def.id(), def.opinion());
        ActorState actor = world.npcs.get(def.id());
        if (actor != null && NpcDef.STATUSES.contains(def.initialStatus())) {
            actor.status = def.initialStatus();
            actor.hidden = !actor.present();
        }
        for (String ref : def.knowsList()) {
            learn(def.id(), AmbientDef.factId(ref), AmbientDef.factVersion(ref), "witnessed", "inicio", null);
        }
    }

    // ---------------------------------------------------------------- rumores

    /**
     * Uma vez por hora de jogo: NPCs que estão juntos (e acordados) contam uns aos
     * outros o que sabem. Às vezes a história chega torta (RF-122).
     */
    public void spreadRumors() {
        List<ActorState> npcs = new ArrayList<>(world.npcs.values());
        for (int i = 0; i < npcs.size(); i++) {
            for (int j = i + 1; j < npcs.size(); j++) {
                ActorState a = npcs.get(i), b = npcs.get(j);
                if (!a.present() || !b.present() || !together(a, b)
                        || SLEEPING.equals(a.activity) || SLEEPING.equals(b.activity)) continue;
                tell(a, b);
                tell(b, a);
            }
        }
    }

    private static boolean together(ActorState a, ActorState b) {
        return a.areaId != null && a.areaId.equals(b.areaId) && Math.hypot(a.x - b.x, a.y - b.y) <= GROUP_RANGE;
    }

    private void tell(ActorState speaker, ActorState listener) {
        NpcDef speakerDef = content.npcs().get(speaker.id);
        String bond = speakerDef == null ? null : speakerDef.relationWith(listener.id);
        double chance = RUMOR_CHANCE * (bond == null ? 1 : bond.equals("rivalidade") ? RIVAL_FACTOR : CLOSE_FACTOR);
        if (!world.rng.chance(chance)) return;
        List<KnowledgeEntry> news = new ArrayList<>();
        for (KnowledgeEntry k : world.npcKnowledge(speaker.id).values()) {
            FactDef fact = content.facts().get(k.fact());
            if (fact != null && fact.spreads() && !knows(listener.id, k.fact())) news.add(k);
        }
        if (news.isEmpty()) return;
        KnowledgeEntry told = news.get(world.rng.nextInt(news.size()));
        FactDef fact = content.facts().get(told.fact());
        String version = told.version();
        if (fact.versionList().size() > 1 && world.rng.chance(DISTORT_CHANCE)) {
            version = fact.versionList().get(world.rng.nextInt(fact.versionList().size())).id();
        }
        learn(listener.id, told.fact(), version, "rumor", "npc:" + speaker.id, null);
    }

    // ---------------------------------------------------------------- diálogos

    /** Abre a conversa com um NPC. Devolve false se ele não tem o que dizer. */
    public boolean startDialogue(String npcId, List<SimEvent> events) {
        NpcDef def = content.npcs().get(npcId);
        DialogueDef dialogue = def == null || def.dialogue() == null ? null : content.dialogues().get(def.dialogue());
        if (dialogue == null) {
            events.add(new SimEvent.AmbientLine(null, npcId, content.text("npc.sem_conversa", "…")));
            return false;
        }
        if (world.opinion(npcId) <= REFUSE_OPINION) { // não quer conversa com quem fez o que fez (RF-089)
            events.add(new SimEvent.AmbientLine(null, npcId, content.text("npc.recusa", "Com você eu não falo.")));
            return false;
        }
        DialogueSession session = new DialogueSession(dialogue.id(), npcId, def.displayName(), def.portraitId());
        world.dialogue = session;
        events.add(new SimEvent.DialogueOpened(npcId));
        for (DialogueDef.Start s : dialogue.start()) {
            if (test(s.when(), npcId)) {
                enter(session, dialogue, s.target(), events);
                return true;
            }
        }
        close(events);
        return false;
    }

    private void enter(DialogueSession session, DialogueDef dialogue, String nodeId, List<SimEvent> events) {
        DialogueDef.Node node = dialogue.nodes().get(nodeId);
        if (node == null) {
            close(events);
            return;
        }
        String text = node.text();
        if (node.gossip() != null) { // nó de novidades: conta algo que o jogador ainda não sabe (RF-125)
            KnowledgeEntry news = news(session.npcId);
            DialogueDef.Node fallback = dialogue.nodes().get(node.gossip());
            if (news == null) {
                if (fallback != null && fallback.gossip() == null) enter(session, dialogue, node.gossip(), events);
                else close(events);
                return;
            }
            text = (text.isBlank() ? "" : text + " ") + content.facts().get(news.fact()).text(news.version());
            learn(PLAYER, news.fact(), news.version(), "claim", "npc:" + session.npcId, events);
        }
        session.nodeId = nodeId;
        String speaker = node.speaker() != null ? node.speaker() : session.npcId;
        applyAll(node.effects(), session.npcId, "npc:" + speaker, events);
        NpcDef speakerDef = content.npcs().get(speaker);
        session.speakerName = speakerDef != null ? speakerDef.displayName() : session.npcName;
        session.text = text;
        session.history.add(new DialogueView.Line(session.speakerName, text));
        session.choices.clear();
        session.choiceIndex.clear();
        for (int i = 0; i < node.choices().size(); i++) {
            DialogueDef.Choice c = node.choices().get(i);
            if (test(c.when(), session.npcId)) {
                session.choices.add(new DialogueView.Choice(c.text(), c.tag()));
                session.choiceIndex.add(i);
            }
        }
        session.canContinue = session.choices.isEmpty();
    }

    /**
     * A novidade mais recente que o NPC sabe e o jogador não: só o que vira rumor,
     * nada além da camada V1, e nunca os atos do próprio jogador.
     */
    public KnowledgeEntry news(String npcId) {
        KnowledgeEntry newest = null;
        for (KnowledgeEntry k : world.npcKnowledge(npcId).values()) {
            FactDef fact = content.facts().get(k.fact());
            if (fact != null && fact.spreads() && fact.layerIndex() <= 1 && !world.knows(k.fact())
                    && content.deedByFact(k.fact()) == null) newest = k;
        }
        return newest;
    }

    private DialogueDef.Node currentNode() {
        DialogueDef dialogue = content.dialogues().get(world.dialogue.dialogueId);
        return dialogue == null ? null : dialogue.nodes().get(world.dialogue.nodeId);
    }

    public void choose(int index, List<SimEvent> events) {
        DialogueSession s = world.dialogue;
        DialogueDef.Node node = s == null ? null : currentNode();
        if (node == null || index < 0 || index >= s.choiceIndex.size()) return;
        DialogueDef.Choice choice = node.choices().get(s.choiceIndex.get(index));
        s.history.add(new DialogueView.Line(content.text("dialogo.voce", "Você"), choice.text()));
        applyAll(choice.effects(), s.npcId, "npc:" + s.npcId, events);
        trainBy(choice.when()); // usar a Lábia (ou outra habilidade) numa conversa treina a habilidade (RF-180)
        if (choice.target() == null) close(events);
        else enter(s, content.dialogues().get(s.dialogueId), choice.target(), events);
    }

    /** Treina cada habilidade exigida pela condição de uma escolha. */
    private void trainBy(Condition c) {
        switch (c) {
            case Condition.Skill sk -> economy.train(sk.name(), SKILL_CHOICE_GAIN);
            case Condition.All a -> a.of().forEach(this::trainBy);
            case Condition.Any a -> a.of().forEach(this::trainBy);
            default -> { /* nada a treinar */ }
        }
    }

    public void advance(List<SimEvent> events) {
        DialogueSession s = world.dialogue;
        DialogueDef.Node node = s == null ? null : currentNode();
        if (s == null || !s.canContinue) return;
        if (node == null || node.next() == null) close(events);
        else enter(s, content.dialogues().get(s.dialogueId), node.next(), events);
    }

    /** O jogador vira as costas no meio da conversa; o nó pode reagir a isso (RF-107). */
    public void leave(List<SimEvent> events) {
        DialogueSession s = world.dialogue;
        if (s == null) return;
        DialogueDef.Node node = currentNode();
        if (node != null) applyAll(node.onLeave(), s.npcId, "npc:" + s.npcId, events);
        close(events);
    }

    /**
     * Perguntar ao NPC sobre um fato do caderno (RF-126). Ele responde com a
     * versão que conhece — que pode ser outra — ou diz que não sabe.
     */
    public void askAbout(String factId, List<SimEvent> events) {
        DialogueSession s = world.dialogue;
        FactDef fact = content.facts().get(factId);
        if (s == null || fact == null || !world.knows(factId)) return;
        String mine = world.playerKnowledge.stream().filter(k -> k.fact().equals(factId)).findFirst()
                .map(KnowledgeEntry::version).orElse(fact.defaultVersion());
        s.history.add(new DialogueView.Line(content.text("dialogo.voce", "Você"),
                content.text("dialogo.pergunta", "Ouvi uma coisa:") + " " + fact.text(mine)));
        KnowledgeEntry theirs = world.npcKnowledge(s.npcId).get(factId);
        String answer;
        if (theirs == null) {
            answer = content.text("dialogo.nao_sei", "Disso eu não sei nada.");
        } else if (theirs.version().equals(mine)) {
            answer = content.text("dialogo.ouvi_igual", "Foi o que chegou aqui também.");
            learn(PLAYER, factId, theirs.version(), "claim", "npc:" + s.npcId, events);
        } else {
            answer = fact.text(theirs.version());
            learn(PLAYER, factId, theirs.version(), "claim", "npc:" + s.npcId, events);
        }
        s.speakerName = s.npcName;
        s.text = answer;
        s.history.add(new DialogueView.Line(s.npcName, answer));
    }

    private void close(List<SimEvent> events) {
        if (world.dialogue == null) return;
        events.add(new SimEvent.DialogueClosed(world.dialogue.npcId));
        world.dialogue = null;
    }

    // ---------------------------------------------------------------- conversas ambientes

    /** Chamado a cada tick, só na simulação detalhada: toca a conversa em curso ou procura uma nova. */
    public void tickAmbient(List<SimEvent> events) {
        long tick = world.clock.tick();
        if (world.ambientId != null) {
            AmbientDef def = content.ambients().get(world.ambientId);
            if (def == null || !gathered(def)) { // alguém foi embora: a conversa morre
                world.ambientId = null;
                return;
            }
            if (tick < world.ambientNextTick) return;
            if (world.ambientLine < def.lines().size()) {
                AmbientDef.Line line = def.lines().get(world.ambientLine++);
                events.add(new SimEvent.AmbientLine(def.id(), line.speaker(), line.text()));
                world.ambientNextTick = tick + LINE_TICKS;
                return;
            }
            finishAmbient(def, events);
            return;
        }
        if (tick % AMBIENT_CHECK_TICKS != 0) return;
        for (AmbientDef def : content.ambients().values()) {
            Long last = world.ambientLast.get(def.id());
            if (last != null && (def.cooldownHours() <= 0
                    || tick - last < def.cooldownHours() * world.clock.ticksPerHour())) continue;
            if (!gathered(def) || !playerHears(def) || !test(def.when(), null)) continue;
            world.ambientId = def.id();
            world.ambientLine = 0;
            world.ambientNextTick = tick;
            return;
        }
    }

    private void finishAmbient(AmbientDef def, List<SimEvent> events) {
        String speaker = def.lines().get(0).speaker();
        for (String ref : def.facts()) {
            for (String p : def.participants()) {
                learn(p, AmbientDef.factId(ref), AmbientDef.factVersion(ref), "rumor", "npc:" + speaker, null);
            }
            if (playerHears(def)) {
                learn(PLAYER, AmbientDef.factId(ref), AmbientDef.factVersion(ref), "rumor", "conversa:" + def.id(), events);
            }
        }
        if (playerHears(def)) economy.train(Economy.OBSERVATION, OBSERVATION_GAIN); // escutar treina Observação
        world.ambientLast.put(def.id(), world.clock.tick());
        world.ambientId = null;
    }

    /** Todos os participantes na área carregada, à vista, parados e perto uns dos outros. */
    private boolean gathered(AmbientDef def) {
        ActorState first = null;
        for (String id : def.participants()) {
            ActorState a = world.npcs.get(id);
            if (a == null || a.hidden || !a.path.isEmpty() || !world.areaId.equals(a.areaId)) return false;
            if (first == null) first = a;
            else if (Math.hypot(a.x - first.x, a.y - first.y) > GROUP_RANGE) return false;
        }
        return first != null;
    }

    private boolean playerHears(AmbientDef def) {
        ActorState first = world.npcs.get(def.participants().get(0));
        return first != null && Math.hypot(first.x - world.player.x, first.y - world.player.y) <= hearRange();
    }

    /** Quem tem Observação escuta conversas alheias de mais longe (RF-184): até +6 m com 100. */
    public double hearRange() {
        return HEAR_RANGE + HEAR_PER_OBSERVATION * (economy == null ? 0 : economy.skill(Economy.OBSERVATION));
    }
}
