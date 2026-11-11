package etrava.sim.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lê condições e efeitos do formato dos arquivos de conteúdo (e grava efeitos
 * no mesmo formato, para o save). Exemplos:
 * <pre>
 *   when: {knows: {who: player, fact: arvore_voltou}}
 *   when: {all: [{flag: festa}, {not: {opinion: {npc: moleiro, max: -10}}}]}
 *   effects: [{learn: {who: player, fact: arvore_voltou, certainty: claim}}, {opinion: {npc: npc, add: -5}}]
 * </pre>
 * Erros de conteúdo viram {@link IllegalArgumentException} com mensagem clara.
 */
public final class Rules {
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private Rules() {}

    // ---------------------------------------------------------------- condições

    /** Nó ausente = sempre verdadeiro. Um objeto com várias chaves exige todas. */
    public static Condition condition(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return Condition.ALWAYS;
        if (!n.isObject()) throw new IllegalArgumentException("condição deve ser um objeto: " + n);
        List<Condition> all = new ArrayList<>();
        for (Map.Entry<String, JsonNode> e : n.properties()) all.add(condition(e.getKey(), e.getValue()));
        return all.size() == 1 ? all.get(0) : new Condition.All(all);
    }

    private static Condition condition(String key, JsonNode v) {
        return switch (key) {
            case "all" -> new Condition.All(conditions(v));
            case "any" -> new Condition.Any(conditions(v));
            case "not" -> new Condition.Not(condition(v));
            case "flag" -> v.isTextual() ? new Condition.Flag(v.asText(), true)
                    : new Condition.Flag(text(v, "name"), v.path("value").asBoolean(true));
            case "var" -> new Condition.Var(text(v, "name"), number(v, "min"), number(v, "max"));
            case "knows" -> new Condition.Knows(v.path("who").asText("player"), text(v, "fact"));
            case "opinion" -> new Condition.Opinion(v.path("npc").asText("npc"), integer(v, "min"), integer(v, "max"));
            case "time" -> new Condition.Time(integer(v, "dayMin"), integer(v, "dayMax"),
                    number(v, "hourFrom"), number(v, "hourTo"));
            case "npc" -> v.has("status") ? new Condition.NpcStatus(text(v, "id"), text(v, "status"))
                    : new Condition.NpcIn(text(v, "id"), text(v, "area"));
            case "arc" -> new Condition.Arc(text(v, "id"), strings(v.get("stage")), optional(v, "outcome"));
            case "prop" -> new Condition.Prop(text(v, "key"), text(v, "state"));
            case "remembers" -> new Condition.Remembers(v.path("npc").asText("npc"), text(v, "type"),
                    optional(v, "subject"));
            case "chance" -> new Condition.Chance(v.asDouble());
            default -> throw new IllegalArgumentException("condição desconhecida: '" + key + "'");
        };
    }

    private static List<Condition> conditions(JsonNode array) {
        List<Condition> list = new ArrayList<>();
        for (JsonNode n : array) list.add(condition(n));
        return list;
    }

    // ---------------------------------------------------------------- efeitos

    public static List<Effect> effects(JsonNode array) {
        List<Effect> list = new ArrayList<>();
        if (array == null || array.isNull() || array.isMissingNode()) return list;
        if (!array.isArray()) throw new IllegalArgumentException("efeitos devem ser uma lista: " + array);
        for (JsonNode n : array) list.add(effect(n));
        return list;
    }

    public static Effect effect(JsonNode n) {
        if (!n.isObject() || n.size() != 1)
            throw new IllegalArgumentException("cada efeito deve ser um objeto com uma chave: " + n);
        Map.Entry<String, JsonNode> e = n.properties().iterator().next();
        JsonNode v = e.getValue();
        return switch (e.getKey()) {
            case "setFlag" -> v.isTextual() ? new Effect.SetFlag(v.asText(), true)
                    : new Effect.SetFlag(text(v, "name"), v.path("value").asBoolean(true));
            case "setVar" -> new Effect.SetVar(text(v, "name"), v.path("value").asDouble());
            case "addVar" -> new Effect.AddVar(text(v, "name"), v.path("value").asDouble(1));
            case "learn" -> new Effect.Learn(v.path("who").asText("player"), text(v, "fact"),
                    optional(v, "version"), v.path("certainty").asText("claim"));
            case "opinion" -> new Effect.Opinion(v.path("npc").asText("npc"), v.path("add").asInt());
            case "chronicle" -> new Effect.Chronicle(v.path("type").asText("nota"), v.path("ref").asText(""),
                    text(v, "text"));
            case "schedule" -> new Effect.Schedule(v.path("hours").asDouble(), v.path("interrupts").asBoolean(false),
                    effects(v.path("effects")));
            case "setProp" -> new Effect.SetProp(text(v, "key"), text(v, "state"));
            case "arc" -> new Effect.Arc(text(v, "id"), text(v, "to"));
            case "npcStatus" -> new Effect.NpcStatus(text(v, "id"), text(v, "status"));
            case "remember" -> new Effect.Remember(v.path("npc").asText("npc"), text(v, "type"),
                    v.path("subject").asText("player"));
            case "deed" -> new Effect.Deed(v.isTextual() ? v.asText() : text(v, "id"));
            default -> throw new IllegalArgumentException("efeito desconhecido: '" + e.getKey() + "'");
        };
    }

    /** Grava um efeito no mesmo formato em que é lido. */
    public static ObjectNode toJson(Effect effect) {
        ObjectNode root = F.objectNode();
        switch (effect) {
            case Effect.SetFlag f -> root.putObject("setFlag").put("name", f.name()).put("value", f.value());
            case Effect.SetVar v -> root.putObject("setVar").put("name", v.name()).put("value", v.value());
            case Effect.AddVar v -> root.putObject("addVar").put("name", v.name()).put("value", v.value());
            case Effect.Learn l -> {
                ObjectNode n = root.putObject("learn").put("who", l.who()).put("fact", l.fact())
                        .put("certainty", l.certainty());
                if (l.version() != null) n.put("version", l.version());
            }
            case Effect.Opinion o -> root.putObject("opinion").put("npc", o.npc()).put("add", o.add());
            case Effect.Chronicle c -> root.putObject("chronicle").put("type", c.type()).put("ref", c.ref())
                    .put("text", c.text());
            case Effect.Schedule s -> {
                ObjectNode n = root.putObject("schedule").put("hours", s.hours()).put("interrupts", s.interrupts());
                ArrayNode list = n.putArray("effects");
                for (Effect inner : s.effects()) list.add(toJson(inner));
            }
            case Effect.SetProp p -> root.putObject("setProp").put("key", p.key()).put("state", p.state());
            case Effect.Arc a -> root.putObject("arc").put("id", a.id()).put("to", a.to());
            case Effect.NpcStatus n -> root.putObject("npcStatus").put("id", n.npc()).put("status", n.status());
            case Effect.Remember r -> root.putObject("remember").put("npc", r.npc()).put("type", r.type())
                    .put("subject", r.subject());
            case Effect.Deed d -> root.putObject("deed").put("id", d.id());
        }
        return root;
    }

    // ---------------------------------------------------------------- leitura de campos

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.asText().isBlank())
            throw new IllegalArgumentException("falta o campo '" + field + "' em " + n);
        return v.asText();
    }

    /** Um texto ou uma lista de textos; ausente = lista vazia. */
    private static List<String> strings(JsonNode n) {
        List<String> list = new ArrayList<>();
        if (n == null || n.isNull()) return list;
        if (n.isArray()) n.forEach(x -> list.add(x.asText()));
        else list.add(n.asText());
        return list;
    }

    private static String optional(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Double number(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asDouble();
    }

    private static Integer integer(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asInt();
    }
}
