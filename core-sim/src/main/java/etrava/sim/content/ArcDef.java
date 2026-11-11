package etrava.sim.content;

import com.fasterxml.jackson.databind.JsonNode;
import etrava.sim.rules.Condition;
import etrava.sim.rules.Effect;
import etrava.sim.rules.Rules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arco de mundo (RF-140, content/arcs/*.yaml): uma máquina de estados que anda
 * sozinha. Os eventos externos usam o mesmo formato (D-009).
 *
 * @param major arco maior (A1–A3): precisa de um trecho garantido no epílogo
 * @param start estágio em que o arco começa, no início do jogo
 */
public record ArcDef(String id, String name, boolean major, String start, Map<String, Stage> stages) {

    public static final List<String> OUTCOMES = List.of("leve", "ambiguo", "tragico", "neutro");

    /**
     * @param chronicle   o que a crônica registra quando o arco chega aqui
     * @param transitions caminhos abertos pelo estado do mundo; vale o primeiro cuja condição passa
     * @param timeout     o que acontece se ninguém fizer nada; obrigatório em estágio não terminal
     * @param outcome     em estágio terminal: leve | ambiguo | tragico | neutro
     */
    public record Stage(String id, String chronicle, List<Effect> onEnter, List<Effect> onExit,
            List<Transition> transitions, Timeout timeout, boolean terminal, String outcome) {}

    public record Transition(Condition when, String to) {}

    /**
     * Prazo de um estágio, em horas de jogo, com folga sorteada pela semente.
     * Ao vencer: a primeira opção com condição (e sem peso) que passar; senão,
     * sorteio por peso entre as demais.
     */
    public record Timeout(double hours, double jitterHours, List<Option> options) {}

    /** @param weight 0 = opção de prioridade (exige condição); maior que 0 = entra no sorteio */
    public record Option(Condition when, boolean conditional, double weight, String to) {}

    public Stage stage(String stageId) { return stages.get(stageId); }

    public String displayName() { return name != null ? name : id; }

    public static ArcDef parse(JsonNode root) {
        String id = required(root, "id", "arco");
        Map<String, Stage> stages = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : root.path("stages").properties()) {
            JsonNode n = e.getValue();
            String where = id + "#" + e.getKey();
            List<Transition> transitions = new ArrayList<>();
            for (JsonNode t : n.path("transitions")) {
                transitions.add(new Transition(Rules.condition(t.get("when")), required(t, "to", "transição em " + where)));
            }
            stages.put(e.getKey(), new Stage(e.getKey(), text(n, "chronicle"), Rules.effects(n.get("onEnter")),
                    Rules.effects(n.get("onExit")), transitions, timeout(n.get("timeout"), where),
                    n.path("terminal").asBoolean(false), text(n, "outcome")));
        }
        if (stages.isEmpty()) throw new IllegalArgumentException("arco '" + id + "' sem estágios");
        return new ArcDef(id, text(root, "name"), root.path("major").asBoolean(false),
                required(root, "start", "arco " + id), stages);
    }

    private static Timeout timeout(JsonNode n, String where) {
        if (n == null || n.isNull()) return null;
        JsonNode after = n.path("after");
        double hours = after.path("days").asDouble(0) * 24 + after.path("hours").asDouble(0);
        List<Option> options = new ArrayList<>();
        if (n.hasNonNull("to")) options.add(new Option(Condition.ALWAYS, false, 1, n.get("to").asText()));
        for (JsonNode o : n.path("choose")) {
            boolean conditional = o.hasNonNull("when");
            double weight = o.has("weight") ? o.get("weight").asDouble() : conditional ? 0 : 1;
            options.add(new Option(Rules.condition(o.get("when")), conditional, weight,
                    required(o, "to", "prazo de " + where)));
        }
        return new Timeout(hours, after.path("jitterHours").asDouble(0), options);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String required(JsonNode n, String field, String where) {
        String v = text(n, field);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("falta '" + field + "' em " + where);
        return v;
    }
}
