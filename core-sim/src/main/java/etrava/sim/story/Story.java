package etrava.sim.story;

import etrava.sim.api.DialogueView;
import etrava.sim.api.SimEvent;
import etrava.sim.content.AmbientDef;
import etrava.sim.content.ContentDb;
import etrava.sim.content.DialogueDef;
import etrava.sim.content.FactDef;
import etrava.sim.content.NpcDef;
import etrava.sim.rules.Condition;
import etrava.sim.rules.Effect;
import etrava.sim.world.ActorState;
import etrava.sim.world.ChronicleEntry;
import etrava.sim.world.DialogueSession;
import etrava.sim.world.KnowledgeEntry;
import etrava.sim.world.World;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * A parte narrativa da simulação: condições e efeitos, quem sabe o quê, rumores
 * entre NPCs, diálogos e conversas ambientes. Trabalha sempre sobre o
 * {@link World}; não guarda estado próprio.
 */
public final class Story {
    /** Distância, em metros, para conversar com alguém. */
    public static final double TALK_RANGE = 2.2;
    /** Até onde o jogador escuta uma conversa alheia. */
    public static final double HEAR_RANGE = 9.0;
    /** NPCs a esta distância uns dos outros estão "juntos": conversam e trocam rumores. */
    public static final double GROUP_RANGE = 6.0;
    /** Chance de um NPC passar um rumor a outro a cada hora juntos, e de ele sair distorcido. */
    public static final double RUMOR_CHANCE = 0.5, DISTORT_CHANCE = 0.15;
    private static final int LINE_TICKS = 80, AMBIENT_CHECK_TICKS = 25;
    private static final String PLAYER = "player", THIS_NPC = "npc", SLEEPING = "dormir";

    /** Como o {@link Story} pede à simulação para agendar efeitos. */
    public interface Scheduler {
        void schedule(long atTick, boolean interrupts, List<Effect> effects);
    }

    private final ContentDb content;
    private final World world;
    private final Scheduler scheduler;

    public Story(ContentDb content, World world, Scheduler scheduler) {
        this.content = content;
        this.world = world;
        this.scheduler = scheduler;
    }

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
            case Condition.Chance ch -> world.rng.chance(ch.probability());
        };
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
        switch (effect) {
            case Effect.SetFlag f -> world.flags.put(f.name(), f.value());
            case Effect.SetVar v -> world.vars.put(v.name(), v.value());
            case Effect.AddVar v -> world.vars.merge(v.name(), v.value(), Double::sum);
            case Effect.Learn l -> learn(resolve(l.who(), npc), l.fact(), l.version(), l.certainty(), source, events);
            case Effect.Opinion o -> {
                String id = resolve(o.npc(), npc);
                world.opinions.put(id, Math.max(-100, Math.min(100, world.opinion(id) + o.add())));
            }
            case Effect.Chronicle c ->
                    world.chronicle.add(new ChronicleEntry(world.clock.tick(), c.type(), c.ref(), c.text()));
            case Effect.Schedule s -> scheduler.schedule(
                    world.clock.tick() + Math.round(s.hours() * world.clock.ticksPerHour()), s.interrupts(), s.effects());
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
        return world.npcKnowledge.computeIfAbsent(who, k -> new LinkedHashMap<>()).putIfAbsent(factId, entry) == null;
    }

    /** O que um NPC já sabe e pensa ao começar o jogo. */
    public void initNpc(NpcDef def) {
        world.opinions.put(def.id(), def.opinion());
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
                if (!together(a, b) || SLEEPING.equals(a.activity) || SLEEPING.equals(b.activity)) continue;
                tell(a, b);
                tell(b, a);
            }
        }
    }

    private static boolean together(ActorState a, ActorState b) {
        return a.areaId != null && a.areaId.equals(b.areaId) && Math.hypot(a.x - b.x, a.y - b.y) <= GROUP_RANGE;
    }

    private void tell(ActorState speaker, ActorState listener) {
        if (!world.rng.chance(RUMOR_CHANCE)) return;
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
        session.nodeId = nodeId;
        String speaker = node.speaker() != null ? node.speaker() : session.npcId;
        applyAll(node.effects(), session.npcId, "npc:" + speaker, events);
        NpcDef speakerDef = content.npcs().get(speaker);
        session.speakerName = speakerDef != null ? speakerDef.displayName() : session.npcName;
        session.text = node.text();
        session.history.add(new DialogueView.Line(session.speakerName, node.text()));
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
        if (choice.target() == null) close(events);
        else enter(s, content.dialogues().get(s.dialogueId), choice.target(), events);
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
        return first != null && Math.hypot(first.x - world.player.x, first.y - world.player.y) <= HEAR_RANGE;
    }
}
