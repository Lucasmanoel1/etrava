package etrava.sim.validation;

import etrava.sim.content.AmbientDef;
import etrava.sim.content.ArcDef;
import etrava.sim.content.AreaDef;
import etrava.sim.content.ContentDb;
import etrava.sim.content.DeedDef;
import etrava.sim.content.DialogueDef;
import etrava.sim.content.EpilogueDef;
import etrava.sim.content.FactDef;
import etrava.sim.content.NpcDef;
import etrava.sim.content.PropDef;
import etrava.sim.rules.Condition;
import etrava.sim.rules.Effect;
import etrava.sim.world.KnowledgeEntry;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Regras narrativas do validador (RF-442, PRD 20.3), conferidas sem rodar o jogo:
 * referências que não existem, nós de diálogo inalcançáveis, fato que destrava
 * ramificação com menos de 2 fontes (RF-123), fato de camada V2/V3 ao alcance do
 * jogador no Capítulo I, fato V1 entregue como certeza, arco que pode ficar sem
 * desfecho (RF-141) e epílogo sem texto garantido para os arcos maiores (RF-163).
 */
final class NarrativeValidator {
    /** Camada mais funda que o jogador pode alcançar no Capítulo I (PRD 8.2): V1, e só como pista. */
    private static final int MAX_LAYER_CHAPTER_1 = 1;
    private static final int MIN_EPILOGUE_FRAGMENTS = 15;
    private static final List<String> IMPERATIVES =
            List.of("Vá ", "Vai ", "Fale ", "Procure ", "Encontre ", "Volte ", "Leve ", "Descubra ", "Investigue ");

    private final ContentDb db;
    private final List<String> errors;
    private final List<String> warnings;
    /** Fato → de onde o jogador pode aprendê-lo. */
    private final Map<String, Set<String>> playerSources = new LinkedHashMap<>();
    /** Fato → onde ele é exigido do jogador para abrir um caminho. */
    private final Map<String, Set<String>> requiredBy = new LinkedHashMap<>();
    /** O epílogo só descreve o que houve: o que ele consulta não é ramificação a destravar. */
    private boolean trackRequirements = true;

    private NarrativeValidator(ContentDb db, List<String> errors, List<String> warnings) {
        this.db = db;
        this.errors = errors;
        this.warnings = warnings;
    }

    static void validate(ContentDb db, List<String> errors, List<String> warnings) {
        NarrativeValidator v = new NarrativeValidator(db, errors, warnings);
        v.facts();
        v.npcs();
        for (DialogueDef d : db.dialogues().values()) v.dialogue(d);
        for (AmbientDef a : db.ambients().values()) v.ambient(a);
        v.deeds();
        for (ArcDef a : db.arcs().values()) v.arc(a);
        v.epilogue();
        v.redundancy();
    }

    private void deeds() {
        for (DeedDef d : db.deeds().values()) {
            if (d.fact() != null) fact("deeds.yaml: ato '" + d.id() + "'", d.fact(), null);
        }
    }

    /**
     * Arco: estágios que existem, prazo em todo estágio não terminal e a garantia de
     * que, só pelos prazos, sempre se chega a um desfecho (RF-141, risco R6).
     */
    private void arc(ArcDef a) {
        String file = db.arcFile(a.id()), arc = "arco '" + a.id() + "'";
        if (!a.stages().containsKey(a.start()))
            errors.add(file + ": " + arc + " começa em estágio inexistente '" + a.start() + "'");
        boolean hasTerminal = false, hasKindEnding = false;
        for (ArcDef.Stage s : a.stages().values()) {
            String where = arc + ", estágio '" + s.id() + "'";
            effects(file, where, s.onEnter(), "arco:" + a.id());
            effects(file, where, s.onExit(), "arco:" + a.id());
            if (s.terminal()) {
                hasTerminal = true;
                hasKindEnding |= !"tragico".equals(s.outcome());
                if (!ArcDef.OUTCOMES.contains(s.outcome()))
                    errors.add(file + ": " + where + " é um desfecho sem 'outcome' válido (use "
                            + String.join(", ", ArcDef.OUTCOMES) + ")");
                continue;
            }
            for (ArcDef.Transition t : s.transitions()) {
                condition(file, where, t.when());
                stage(file, where, a, t.to());
            }
            if (s.timeout() == null || s.timeout().options().isEmpty()) {
                errors.add(file + ": " + where + " não tem prazo ('timeout'): o arco pode ficar sem desfecho (RF-141)");
                continue;
            }
            if (s.timeout().hours() <= 0) errors.add(file + ": " + where + " tem prazo sem duração ('after')");
            boolean fallback = false;
            for (ArcDef.Option o : s.timeout().options()) {
                condition(file, where, o.when());
                stage(file, where, a, o.to());
                fallback |= !o.conditional() && o.weight() > 0;
            }
            if (!fallback)
                errors.add(file + ": " + where + ": o prazo precisa de pelo menos uma saída sem condição (RF-141)");
        }
        if (!hasTerminal) errors.add(file + ": " + arc + " não tem nenhum desfecho (estágio 'terminal')");
        else if (!hasKindEnding) errors.add(file + ": " + arc + " só tem desfecho trágico (PRD 20.3)");
        // seguindo só os prazos a partir de qualquer estágio, não pode haver círculo
        for (ArcDef.Stage s : a.stages().values()) {
            if (loops(a, s.id(), new HashSet<>()))
                errors.add(file + ": " + arc + ": pelos prazos, o estágio '" + s.id()
                        + "' pode voltar a si mesmo e o arco nunca terminar (RF-141)");
        }
    }

    private boolean loops(ArcDef a, String stageId, Set<String> path) {
        ArcDef.Stage s = a.stage(stageId);
        if (s == null || s.terminal() || s.timeout() == null) return false;
        if (!path.add(stageId)) return true;
        for (ArcDef.Option o : s.timeout().options()) {
            if (loops(a, o.to(), path)) return true;
        }
        path.remove(stageId);
        return false;
    }

    private void stage(String file, String where, ArcDef a, String stageId) {
        if (!a.stages().containsKey(stageId))
            errors.add(file + ": " + where + " aponta para estágio inexistente '" + stageId + "'");
    }

    /** Epílogo: espaços e fragmentos coerentes; todo arco maior tem texto mesmo sem o jogador (RF-163). */
    private void epilogue() {
        EpilogueDef e = db.epilogue();
        String file = ContentDb.EPILOGUE_FILE;
        Set<String> covered = new HashSet<>();
        if (e != null) {
            if (e.fragments().size() < MIN_EPILOGUE_FRAGMENTS)
                warnings.add(file + ": o epílogo tem " + e.fragments().size() + " fragmentos; o MVP pede pelo menos "
                        + MIN_EPILOGUE_FRAGMENTS + " (RF-163)");
            Set<String> slots = new HashSet<>();
            for (EpilogueDef.Slot s : e.slots()) {
                if (!slots.add(s.id())) errors.add(file + ": espaço repetido '" + s.id() + "'");
                boolean any = false, guaranteed = false;
                for (EpilogueDef.Fragment f : e.fragments()) {
                    if (!f.slot().equals(s.id())) continue;
                    any = true;
                    guaranteed |= f.unconditional();
                }
                if (!any) errors.add(file + ": espaço '" + s.id() + "' sem nenhum fragmento");
                if (s.arc() == null) continue;
                if (!db.arcs().containsKey(s.arc())) errors.add(file + ": espaço '" + s.id() + "' cita arco inexistente '" + s.arc() + "'");
                else if (guaranteed) covered.add(s.arc());
                if (any && !guaranteed)
                    errors.add(file + ": espaço '" + s.id() + "' trata de um arco e precisa de um fragmento sem condição (RF-163)");
            }
            trackRequirements = false;
            for (EpilogueDef.Fragment f : e.fragments()) {
                String where = "fragmento do espaço '" + f.slot() + "'";
                if (!slots.contains(f.slot())) errors.add(file + ": fragmento em espaço inexistente '" + f.slot() + "'");
                condition(file, where, f.when());
            }
            trackRequirements = true;
        }
        for (ArcDef a : db.arcs().values()) {
            if (a.major() && !covered.contains(a.id()))
                errors.add(db.arcFile(a.id()) + ": arco maior '" + a.id()
                        + "' não tem espaço com fragmento garantido no epílogo (RF-163)");
        }
    }

    private void facts() {
        for (FactDef f : db.facts().values()) {
            String where = "facts.yaml: fato '" + f.id() + "'";
            if (f.layerIndex() < 0) errors.add(where + " com camada inválida '" + f.layer() + "' (use V0 a V3)");
            if (f.versionList().isEmpty()) errors.add(where + " sem nenhuma versão de texto");
            Set<String> seen = new HashSet<>();
            for (FactDef.Version v : f.versionList()) {
                if (v.id() == null || !seen.add(v.id())) errors.add(where + " com versão repetida ou sem id");
                if (v.text() == null || v.text().isBlank()) errors.add(where + " com versão sem texto");
                else for (String imp : IMPERATIVES) {
                    if (v.text().startsWith(imp))
                        warnings.add(where + ": o texto parece uma instrução (\"" + imp.trim()
                                + "…\"); o caderno registra o que se soube, não o que fazer");
                }
            }
        }
    }

    private void npcs() {
        for (NpcDef npc : db.npcs().values()) {
            String file = "npcs/" + npc.id() + ".yaml";
            if (npc.dialogue() != null && !db.dialogues().containsKey(npc.dialogue()))
                errors.add(file + ": diálogo inexistente '" + npc.dialogue() + "'");
            if (!NpcDef.STATUSES.contains(npc.initialStatus()))
                errors.add(file + ": situação inválida '" + npc.status() + "' (use " + String.join(", ", NpcDef.STATUSES) + ")");
            for (NpcDef.Relation r : npc.relationList()) {
                npc(file, "relação", r.npc());
                if (!NpcDef.RELATION_KINDS.contains(r.kind()))
                    errors.add(file + ": tipo de relação inválido '" + r.kind() + "' (use "
                            + String.join(", ", NpcDef.RELATION_KINDS) + ")");
            }
            for (NpcDef.Block b : npc.blocks()) {
                if (!b.conditional()) continue;
                try {
                    condition(file, "agenda", b.condition());
                } catch (IllegalArgumentException e) {
                    errors.add(file + ": condição inválida num bloco da agenda: " + e.getMessage());
                }
            }
            for (String ref : npc.knowsList()) fact(file, AmbientDef.factId(ref), AmbientDef.factVersion(ref));
        }
    }

    private void dialogue(DialogueDef d) {
        String file = "dialogues/" + d.id() + ".yaml";
        Deque<String> queue = new ArrayDeque<>();
        Set<String> reached = new LinkedHashSet<>();
        for (DialogueDef.Start s : d.start()) {
            condition(file, "start", s.when());
            target(file, "start", d, s.target(), queue);
        }
        for (DialogueDef.Node node : d.nodes().values()) {
            String where = "nó '" + node.id() + "'";
            if (node.speaker() != null) npc(file, where, node.speaker());
            if (node.gossip() != null) {
                DialogueDef.Node fallback = d.nodes().get(node.gossip());
                if (fallback == null) errors.add(file + ": " + where + ": 'gossip' aponta para nó inexistente '" + node.gossip() + "'");
                else if (fallback.gossip() != null)
                    errors.add(file + ": " + where + ": 'gossip' precisa apontar para um nó comum, não para outro nó de novidades");
            }
            effects(file, where, node.effects(), "dialogo:" + d.id());
            effects(file, where, node.onLeave(), "dialogo:" + d.id());
            for (DialogueDef.Choice c : node.choices()) {
                condition(file, where, c.when());
                effects(file, where, c.effects(), "dialogo:" + d.id());
                if (c.relevant() && new HashSet<>(c.channels()).size() < 2)
                    errors.add(file + ": " + where + ": escolha relevante \"" + c.text()
                            + "\" precisa declarar pelo menos 2 canais (RF-161)");
            }
        }
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!reached.add(id)) continue;
            DialogueDef.Node node = d.nodes().get(id);
            if (node == null) continue;
            String where = "nó '" + id + "'";
            if (node.next() != null) target(file, where, d, node.next(), queue);
            if (node.gossip() != null && d.nodes().containsKey(node.gossip())) queue.add(node.gossip());
            for (DialogueDef.Choice c : node.choices()) {
                if (c.target() != null) target(file, where, d, c.target(), queue);
            }
        }
        for (String id : d.nodes().keySet()) {
            if (!reached.contains(id)) errors.add(file + ": nó inalcançável '" + id + "'");
        }
    }

    private void target(String file, String where, DialogueDef d, String target, Deque<String> queue) {
        if (!d.nodes().containsKey(target)) errors.add(file + ": " + where + " aponta para nó inexistente '" + target + "'");
        else queue.add(target);
    }

    private void ambient(AmbientDef a) {
        String file = db.ambientFile(a.id()), where = "conversa '" + a.id() + "'";
        for (String p : a.participants()) npc(file, where, p);
        for (AmbientDef.Line line : a.lines()) {
            if (!a.participants().contains(line.speaker()))
                errors.add(file + ": " + where + ": '" + line.speaker() + "' fala sem estar entre os participantes");
        }
        condition(file, where, a.when());
        for (String ref : a.facts()) {
            String id = AmbientDef.factId(ref);
            if (fact(file + ": " + where, id, AmbientDef.factVersion(ref))) {
                layer(file + ": " + where, id, "rumor");
                playerSources.computeIfAbsent(id, k -> new TreeSet<>()).add("conversa:" + a.id());
            }
        }
    }

    private void condition(String file, String where, Condition c) {
        switch (c) {
            case Condition.All a -> a.of().forEach(x -> condition(file, where, x));
            case Condition.Any a -> a.of().forEach(x -> condition(file, where, x));
            case Condition.Not n -> condition(file, where, n.of());
            case Condition.Knows k -> {
                if (fact(file + ": " + where, k.fact(), null)) {
                    if (k.who().equals("player")) {
                        if (trackRequirements) requiredBy.computeIfAbsent(k.fact(), x -> new TreeSet<>()).add(file);
                    } else if (!k.who().equals("npc")) {
                        npc(file, where, k.who());
                    }
                }
            }
            case Condition.Opinion o -> {
                if (!o.npc().equals("npc")) npc(file, where, o.npc());
            }
            case Condition.NpcIn n -> {
                npc(file, where, n.npc());
                if (db.area(n.area()) == null) errors.add(file + ": " + where + ": área inexistente '" + n.area() + "'");
            }
            case Condition.NpcStatus n -> {
                if (!n.npc().equals("npc")) npc(file, where, n.npc());
                status(file, where, n.status());
            }
            case Condition.Arc a -> {
                ArcDef def = db.arcs().get(a.id());
                if (def == null) {
                    errors.add(file + ": " + where + ": arco inexistente '" + a.id() + "'");
                } else {
                    for (String s : a.stages()) stage(file, where, def, s);
                    if (a.outcome() != null && !ArcDef.OUTCOMES.contains(a.outcome()))
                        errors.add(file + ": " + where + ": desfecho inválido '" + a.outcome() + "'");
                }
            }
            case Condition.Prop p -> prop(file, where, p.key(), p.state());
            case Condition.Remembers r -> {
                if (!r.npc().equals("npc")) npc(file, where, r.npc());
            }
            default -> { /* sem referências a conferir */ }
        }
    }

    private void effects(String file, String where, List<Effect> effects, String source) {
        for (Effect e : effects) {
            switch (e) {
                case Effect.Learn l -> {
                    if (!KnowledgeEntry.CERTAINTIES.contains(l.certainty()))
                        errors.add(file + ": " + where + ": grau de certeza inválido '" + l.certainty() + "'");
                    if (!fact(file + ": " + where, l.fact(), l.version())) continue;
                    if (l.who().equals("player")) {
                        layer(file + ": " + where, l.fact(), l.certainty());
                        playerSources.computeIfAbsent(l.fact(), k -> new TreeSet<>()).add(source);
                    } else if (!l.who().equals("npc")) {
                        npc(file, where, l.who());
                    }
                }
                case Effect.Opinion o -> {
                    if (!o.npc().equals("npc")) npc(file, where, o.npc());
                }
                case Effect.Schedule s -> effects(file, where, s.effects(), source);
                case Effect.SetProp p -> prop(file, where, p.key(), p.state());
                case Effect.Arc a -> {
                    ArcDef def = db.arcs().get(a.id());
                    if (def == null) errors.add(file + ": " + where + ": arco inexistente '" + a.id() + "'");
                    else stage(file, where, def, a.to());
                }
                case Effect.NpcStatus n -> {
                    if (!n.npc().equals("npc")) npc(file, where, n.npc());
                    status(file, where, n.status());
                }
                case Effect.Remember r -> {
                    if (!r.npc().equals("npc")) npc(file, where, r.npc());
                }
                case Effect.Deed d -> {
                    if (!db.deeds().containsKey(d.id()))
                        errors.add(file + ": " + where + ": ato inexistente '" + d.id() + "' (ver deeds.yaml)");
                }
                default -> { /* sem referências a conferir */ }
            }
        }
    }

    /** O jogador não pode receber V2/V3 no Capítulo I, nem V1 como coisa certa (PRD 8.2). */
    private void layer(String where, String factId, String certainty) {
        FactDef f = db.facts().get(factId);
        if (f.layerIndex() > MAX_LAYER_CHAPTER_1)
            errors.add(where + ": fato '" + factId + "' é de camada " + f.layer()
                    + " e não pode chegar ao jogador no Capítulo I");
        else if (f.layerIndex() == 1 && certainty.equals("verified"))
            errors.add(where + ": fato '" + factId + "' é de camada V1 e só pode aparecer como pista, nunca como 'verified'");
    }

    private boolean fact(String where, String id, String version) {
        FactDef f = db.facts().get(id);
        if (f == null) {
            errors.add(where + ": fato inexistente '" + id + "'");
            return false;
        }
        if (version != null && !f.hasVersion(version))
            errors.add(where + ": o fato '" + id + "' não tem a versão '" + version + "'");
        return true;
    }

    private void status(String file, String where, String status) {
        if (!NpcDef.STATUSES.contains(status))
            errors.add(file + ": " + where + ": situação de NPC inválida '" + status + "'");
    }

    /** A chave precisa estar num prop do mapa, e o estado entre as variantes dele (RF-043). */
    private void prop(String file, String where, String key, String state) {
        PropDef def = db.keyedProp(key);
        boolean placed = false;
        for (AreaDef area : db.areas().values()) placed |= area.props().stream().anyMatch(p -> key.equals(p.key()));
        if (!placed) errors.add(file + ": " + where + ": nenhum prop do mapa tem a chave '" + key + "'");
        else if (def != null && !def.stateMap().containsKey(state))
            errors.add(file + ": " + where + ": o prop '" + key + "' não tem a variante '" + state + "'");
    }

    private void npc(String file, String where, String id) {
        if (!db.npcs().containsKey(id)) errors.add(file + ": " + where + ": NPC inexistente '" + id + "'");
    }

    /** RF-123: fato que abre caminho precisa estar ao alcance do jogador por 2 fontes diferentes. */
    private void redundancy() {
        for (Map.Entry<String, Set<String>> e : requiredBy.entrySet()) {
            Set<String> sources = playerSources.getOrDefault(e.getKey(), Set.of());
            if (sources.size() < 2)
                errors.add("facts.yaml: fato '" + e.getKey() + "' destrava ramificação em " + e.getValue()
                        + " mas o jogador só tem " + sources.size() + " fonte(s) para ele " + sources
                        + "; são exigidas 2 (RF-123)");
        }
    }
}
