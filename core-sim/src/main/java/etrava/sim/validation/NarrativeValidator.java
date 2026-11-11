package etrava.sim.validation;

import etrava.sim.content.AmbientDef;
import etrava.sim.content.ArcDef;
import etrava.sim.content.AreaDef;
import etrava.sim.content.ContainerDef;
import etrava.sim.content.ContentDb;
import etrava.sim.content.DeedDef;
import etrava.sim.content.DialogueDef;
import etrava.sim.content.EpilogueDef;
import etrava.sim.content.FactDef;
import etrava.sim.content.ItemDef;
import etrava.sim.content.JobDef;
import etrava.sim.content.MerchantDef;
import etrava.sim.content.NpcDef;
import etrava.sim.content.OriginDef;
import etrava.sim.content.PropDef;
import etrava.sim.content.SkillsDef;
import etrava.sim.api.Simulation;
import etrava.sim.combat.Combat;
import etrava.sim.content.EncounterDef;
import etrava.sim.content.EnemyDef;
import etrava.sim.economy.Economy;
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
        v.economy();
        v.combat();
        for (ArcDef a : db.arcs().values()) {
            v.arc(a);
            v.peaceful(a);
        }
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
            case Condition.Has h -> {
                if (h.item() != null) item(file, where, h.item());
            }
            case Condition.Skill sk -> skill(file, where, sk.name());
            case Condition.Reputation r -> {
                if (db.npcs().values().stream().noneMatch(n -> r.faction().equals(n.faction())))
                    errors.add(file + ": " + where + ": comunidade sem nenhum NPC '" + r.faction() + "'");
            }
            case Condition.Job j -> {
                if (!db.jobs().containsKey(j.id())) errors.add(file + ": " + where + ": trabalho inexistente '" + j.id() + "'");
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
                case Effect.Give g -> {
                    if (g.item() != null) item(file, where, g.item());
                }
                case Effect.Take t -> {
                    if (t.item() != null) item(file, where, t.item());
                }
                case Effect.PriceMod pm -> {
                    if (pm.factor() <= 0) errors.add(file + ": " + where + ": priceMod precisa de 'factor' maior que 0");
                    if (db.items().values().stream().noneMatch(i -> i.hasTag(pm.tag())))
                        warnings.add(file + ": " + where + ": priceMod em '" + pm.tag()
                                + "', mas nenhum item tem essa categoria ou marca");
                }
                case Effect.Train t -> skill(file, where, t.skill());
                case Effect.StartJob j -> {
                    if (!db.jobs().containsKey(j.id())) errors.add(file + ": " + where + ": trabalho inexistente '" + j.id() + "'");
                }
                case Effect.StartTrade t -> {
                    if (!db.merchants().containsKey(t.id()))
                        errors.add(file + ": " + where + ": comerciante inexistente '" + t.id() + "'");
                }
                case Effect.Encounter en -> {
                    if (!db.encounters().containsKey(en.id()))
                        errors.add(file + ": " + where + ": encontro inexistente '" + en.id() + "' (ver encounters.yaml)");
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

    private void item(String file, String where, String id) {
        if (!db.items().containsKey(id)) errors.add(file + ": " + where + ": item inexistente '" + id + "'");
    }

    private void skill(String file, String where, String id) {
        if (db.skills().skill(id) == null)
            errors.add(file + ": " + where + ": habilidade inexistente '" + id + "' (ver skills.yaml)");
    }

    /** "area/ponto" que existe num mapa. */
    private void place(String file, String where, String at) {
        int slash = at == null ? -1 : at.indexOf('/');
        if (slash <= 0) {
            errors.add(file + ": " + where + ": lugar mal escrito '" + at + "' (use area/ponto)");
            return;
        }
        AreaDef area = db.area(at.substring(0, slash));
        if (area == null) errors.add(file + ": " + where + ": área inexistente '" + at.substring(0, slash) + "'");
        else if (!area.spots().containsKey(at.substring(slash + 1)))
            errors.add(file + ": " + where + ": ponto inexistente '" + at + "'");
    }

    /** Itens, habilidades, origem, comerciantes, trabalhos e contêineres (M5). */
    private void economy() {
        Set<String> skillIds = new HashSet<>();
        for (SkillsDef.Skill s : db.skills().list()) {
            if (!skillIds.add(s.id())) errors.add("skills.yaml: habilidade duplicada '" + s.id() + "'");
        }
        for (ItemDef i : db.items().values()) {
            String where = "item '" + i.id() + "'";
            if (!ItemDef.CATEGORIES.contains(i.category()))
                errors.add("items.yaml: " + where + ": categoria inválida '" + i.category() + "' " + ItemDef.CATEGORIES);
            if (i.slot() != null && !ItemDef.SLOTS.contains(i.slot()))
                errors.add("items.yaml: " + where + ": encaixe inválido '" + i.slot() + "' " + ItemDef.SLOTS);
            if (i.weight() < 0 || i.value() < 0 || i.food() < 0)
                errors.add("items.yaml: " + where + ": peso, valor e comida não podem ser negativos");
            try {
                effects("items.yaml", where, i.useEffects(), "item:" + i.id());
            } catch (IllegalArgumentException e) {
                errors.add("items.yaml: " + where + ": " + e.getMessage());
            }
        }
        OriginDef o = db.origin();
        for (ItemDef.Stack s : o.itemList()) item(ContentDb.ORIGIN_FILE, "itens", s.item());
        for (Map.Entry<String, String> e : o.equipmentMap().entrySet()) {
            ItemDef i = db.items().get(e.getValue());
            if (i == null) item(ContentDb.ORIGIN_FILE, "equipamento", e.getValue());
            else if (!e.getKey().equals(i.slot()))
                errors.add(ContentDb.ORIGIN_FILE + ": equipamento: '" + i.id() + "' não vai no encaixe '" + e.getKey() + "'");
        }
        for (String s : o.skillMap().keySet()) skill(ContentDb.ORIGIN_FILE, "habilidades", s);
        for (MerchantDef m : db.merchants().values()) {
            String where = "comerciante '" + m.id() + "'";
            npc("merchants.yaml", where, m.npc());
            for (MerchantDef.Entry e : m.stockList()) {
                item("merchants.yaml", where, e.item());
                if (e.min() < 0 || e.min() > e.max())
                    errors.add("merchants.yaml: " + where + ": estoque de '" + e.item() + "' com mínimo/máximo inválidos");
            }
            for (String tag : m.buysList()) {
                if (db.items().values().stream().noneMatch(i -> i.hasTag(tag)))
                    warnings.add("merchants.yaml: " + where + ": compra '" + tag
                            + "', mas nenhum item tem essa categoria ou marca");
            }
            condition("merchants.yaml", where, m.condition());
        }
        for (JobDef j : db.jobs().values()) {
            String where = "trabalho '" + j.id() + "'";
            if (!j.wellFormed()) {
                errors.add("jobs.yaml: " + where + ": precisa de giver, at (area/ponto), hours > 0, pay >= 0 e horários HH:MM");
                continue;
            }
            npc("jobs.yaml", where, j.giver());
            place("jobs.yaml", where, j.at());
            if (j.paySkill() != null) skill("jobs.yaml", where, j.paySkill());
            for (String s : j.trainMap().keySet()) skill("jobs.yaml", where, s);
            condition("jobs.yaml", where, j.condition());
            effects("jobs.yaml", where, j.finishEffects(), "trabalho:" + j.id());
        }
        for (ContainerDef c : db.containers().values()) {
            String where = "contêiner '" + c.id() + "'";
            if (!c.wellFormed()) {
                errors.add("containers.yaml: " + where + ": 'at' precisa ser area/ponto");
                continue;
            }
            place("containers.yaml", where, c.at());
            if (c.owner() != null) {
                npc("containers.yaml", where, c.owner());
                if (!db.deeds().containsKey(Economy.THEFT_DEED))
                    errors.add("containers.yaml: " + where + ": tem dono, mas deeds.yaml não tem o ato '"
                            + Economy.THEFT_DEED + "'");
            }
            for (ItemDef.Stack s : c.itemList()) item("containers.yaml", where, s.item());
        }
    }

    /** Inimigos, encontros e o que as áreas dizem sobre perigo (M6). */
    private void combat() {
        boolean humans = false;
        for (EnemyDef e : db.enemies().values()) {
            String where = "inimigo '" + e.id() + "'";
            if (!EnemyDef.KINDS.contains(e.kind()))
                errors.add("enemies.yaml: " + where + ": tipo inválido '" + e.kind() + "' " + EnemyDef.KINDS);
            if (e.health() <= 0 || e.damage() < 0 || e.protection() < 0)
                errors.add("enemies.yaml: " + where + ": precisa de health > 0, damage e protection não negativos");
            if (e.flee() < 0 || e.flee() >= 1 || e.surrender() < 0 || e.surrender() > 1)
                errors.add("enemies.yaml: " + where + ": flee vai de 0 a 1 (exclusive) e surrender de 0 a 1");
            if (e.surrender() > 0 && !e.human())
                warnings.add("enemies.yaml: " + where + ": só humanos se rendem; 'surrender' não tem efeito");
            for (ItemDef.Stack s : e.lootList()) item("enemies.yaml", where, s.item());
            humans |= e.human();
        }
        if (humans && !db.deeds().containsKey(Combat.EXECUTION_DEED))
            errors.add("deeds.yaml: há inimigos humanos (que se rendem) mas falta o ato '" + Combat.EXECUTION_DEED + "'");
        if (!db.deeds().containsKey(Combat.AGGRESSION_DEED))
            errors.add("deeds.yaml: falta o ato '" + Combat.AGGRESSION_DEED + "' (empurrar um NPC)");
        for (EncounterDef enc : db.encounters().values()) {
            String where = "encontro '" + enc.id() + "'";
            AreaDef area = db.area(enc.area());
            if (area == null) errors.add("encounters.yaml: " + where + ": área inexistente '" + enc.area() + "'");
            for (String spot : enc.spots()) {
                if (area != null && !area.spots().containsKey(spot) && !area.spawns().containsKey(spot))
                    errors.add("encounters.yaml: " + where + ": ponto inexistente '" + enc.area() + "/" + spot + "'");
            }
            if (enc.groups().isEmpty()) errors.add("encounters.yaml: " + where + ": sem inimigos");
            for (EncounterDef.Group g : enc.groups()) {
                if (!db.enemies().containsKey(g.enemy()))
                    errors.add("encounters.yaml: " + where + ": inimigo inexistente '" + g.enemy() + "'");
            }
            if (enc.chance() <= 0 || enc.chance() > 1)
                errors.add("encounters.yaml: " + where + ": chance precisa estar entre 0 (exclusive) e 1");
            if (!enc.once() && enc.cooldown() <= 0)
                errors.add("encounters.yaml: " + where + ": sem 'cooldown' e sem 'once': aconteceria a toda hora");
            condition("encounters.yaml", where, enc.condition());
        }
        for (ContentDb.AreaEntry e : db.areaEntries().values()) {
            String where = "área '" + e.id() + "'";
            AreaDef area = db.area(e.id());
            for (ContentDb.Exit x : e.exitList()) {
                if (area != null && !area.spots().containsKey(x.spot()))
                    errors.add("areas/areas.yaml: " + where + ": saída num ponto inexistente '" + x.spot() + "'");
                AreaDef to = db.area(x.to());
                if (to == null) errors.add("areas/areas.yaml: " + where + ": saída para área inexistente '" + x.to() + "'");
                else if (x.spawn() != null && !to.spawns().containsKey(x.spawn()))
                    errors.add("areas/areas.yaml: " + where + ": saída chega a um ponto inexistente '" + x.to() + "/"
                            + x.spawn() + "' (camada spawns)");
            }
            if (e.wakeAt() != null) {
                place("areas/areas.yaml", where + " (wakeAt)", e.wakeAt());
                ContentDb.AreaEntry wake = db.areaEntry(e.wakeAt().substring(0, Math.max(0, e.wakeAt().indexOf('/'))));
                if (wake != null && wake.lethal())
                    errors.add("areas/areas.yaml: " + where + ": acordar numa área letal não faz sentido");
            }
            if (e.finder() != null) {
                npc("areas/areas.yaml", where, e.finder());
                if (!db.facts().containsKey(Simulation.FOUND_FACT))
                    errors.add("facts.yaml: falta o fato '" + Simulation.FOUND_FACT + "' (quem acha o jogador desacordado)");
            }
        }
    }

    /**
     * RF-225 / AT-13: nenhum desfecho de arco pode exigir vencer um combate. Cada desfecho
     * precisa ser alcançável do estágio inicial por caminhos cujas condições não dependam
     * de abater alguém ({@code abatidos.*}) nem de flags de combate ({@code combate*}).
     */
    private void peaceful(ArcDef a) {
        if (a.stage(a.start()) == null) return;
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(a.start()));
        while (!queue.isEmpty()) {
            ArcDef.Stage s = a.stage(queue.poll());
            if (s == null || !seen.add(s.id()) || s.terminal()) continue;
            for (ArcDef.Transition t : s.transitions()) if (!needsCombat(t.when())) queue.add(t.to());
            if (s.timeout() != null)
                for (ArcDef.Option o : s.timeout().options()) if (!needsCombat(o.when())) queue.add(o.to());
        }
        for (ArcDef.Stage s : a.stages().values()) {
            if (s.terminal() && !seen.contains(s.id()))
                errors.add(db.arcFile(a.id()) + ": arco '" + a.id() + "': o desfecho '" + s.id()
                        + "' só se alcança vencendo um combate (RF-225)");
        }
    }

    /** A condição só é verdadeira depois de uma vitória em combate? ({@code any} basta que uma saída seja pacífica.) */
    static boolean needsCombat(Condition c) {
        return switch (c) {
            case Condition.All all -> all.of().stream().anyMatch(NarrativeValidator::needsCombat);
            case Condition.Any any -> !any.of().isEmpty() && any.of().stream().allMatch(NarrativeValidator::needsCombat);
            case Condition.Var v -> v.name().startsWith(Combat.KILLS_VAR) && v.min() != null && v.min() > 0;
            case Condition.Flag f -> f.name().startsWith("combate") && f.value();
            default -> false;
        };
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
