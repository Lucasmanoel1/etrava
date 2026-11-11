package etrava.sim.validation;

import etrava.sim.content.AmbientDef;
import etrava.sim.content.ContentDb;
import etrava.sim.content.DialogueDef;
import etrava.sim.content.FactDef;
import etrava.sim.content.NpcDef;
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
 * jogador no Capítulo I, e fato V1 entregue como certeza.
 */
final class NarrativeValidator {
    /** Camada mais funda que o jogador pode alcançar no Capítulo I (PRD 8.2): V1, e só como pista. */
    private static final int MAX_LAYER_CHAPTER_1 = 1;
    private static final List<String> IMPERATIVES =
            List.of("Vá ", "Vai ", "Fale ", "Procure ", "Encontre ", "Volte ", "Leve ", "Descubra ", "Investigue ");

    private final ContentDb db;
    private final List<String> errors;
    private final List<String> warnings;
    /** Fato → de onde o jogador pode aprendê-lo. */
    private final Map<String, Set<String>> playerSources = new LinkedHashMap<>();
    /** Fato → onde ele é exigido do jogador para abrir um caminho. */
    private final Map<String, Set<String>> requiredBy = new LinkedHashMap<>();

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
        v.redundancy();
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
                    if (k.who().equals("player"))
                        requiredBy.computeIfAbsent(k.fact(), x -> new TreeSet<>()).add(file);
                    else if (!k.who().equals("npc")) npc(file, where, k.who());
                }
            }
            case Condition.Opinion o -> {
                if (!o.npc().equals("npc")) npc(file, where, o.npc());
            }
            case Condition.NpcIn n -> {
                npc(file, where, n.npc());
                if (db.area(n.area()) == null) errors.add(file + ": " + where + ": área inexistente '" + n.area() + "'");
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
