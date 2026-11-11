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
 * Diálogo em árvore (RF-100, content/dialogues/*.yaml): nós com fala, escolhas,
 * condições e efeitos. {@code start} é uma lista de entradas; vale a primeira
 * cuja condição passa.
 */
public record DialogueDef(String id, List<Start> start, Map<String, Node> nodes) {

    public record Start(Condition when, String target) {}

    /**
     * @param speaker quem fala: um NPC, ou null para o NPC da conversa
     * @param effects aplicados ao entrar no nó
     * @param next    próximo nó quando não há escolhas; null encerra a conversa
     * @param onLeave aplicados se o jogador abandonar a conversa neste nó (RF-107)
     * @param gossip  nó de novidades (RF-125): o NPC conta um fato que sabe e o jogador não; o texto do
     *                nó vira a introdução. O valor é o nó para onde ir quando ele não tem nada de novo
     * @param branch  desvios: ao entrar, vai para o primeiro cujo {@code when} passa (sorte, estado do mundo);
     *                se nenhum passa, o nó segue normalmente
     */
    public record Node(String id, String speaker, String text, List<Effect> effects, List<Choice> choices,
            String next, List<Effect> onLeave, String gossip, List<Start> branch) {}

    /**
     * @param tag      rótulo mostrado entre colchetes, ex.: "Lábia" (RF-103); nunca a consequência
     * @param relevant escolha que precisa mudar o mundo em pelo menos 2 canais (RF-161)
     * @param target   nó de destino; null encerra a conversa
     */
    public record Choice(String text, Condition when, String tag, boolean relevant, List<String> channels,
            List<Effect> effects, String target) {}

    /** Lê a definição de um arquivo YAML já convertido em árvore. */
    public static DialogueDef parse(JsonNode root) {
        String id = required(root, "id", "diálogo");
        List<Start> start = new ArrayList<>();
        for (JsonNode s : root.path("start")) {
            start.add(new Start(Rules.condition(s.get("when")), required(s, "goto", "start de " + id)));
        }
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : root.path("nodes").properties()) {
            JsonNode n = e.getValue();
            String where = id + "#" + e.getKey();
            List<Choice> choices = new ArrayList<>();
            for (JsonNode c : n.path("choices")) {
                List<String> channels = new ArrayList<>();
                for (JsonNode ch : c.path("channels")) channels.add(ch.asText());
                choices.add(new Choice(required(c, "text", "escolha em " + where), Rules.condition(c.get("when")),
                        text(c, "tag"), c.path("relevant").asBoolean(false), channels,
                        Rules.effects(c.get("effects")), text(c, "goto")));
            }
            String gossip = text(n, "gossip");
            List<Start> branch = new ArrayList<>();
            for (JsonNode b : n.path("branch")) {
                branch.add(new Start(Rules.condition(b.get("when")), required(b, "goto", "branch em " + where)));
            }
            boolean onlyBranch = !branch.isEmpty() && !n.has("text");
            nodes.put(e.getKey(), new Node(e.getKey(), text(n, "speaker"),
                    gossip != null || onlyBranch ? n.path("text").asText("") : required(n, "text", where),
                    Rules.effects(n.get("effects")), choices, text(n, "goto"), Rules.effects(n.get("onLeave")),
                    gossip, branch));
        }
        if (start.isEmpty()) throw new IllegalArgumentException("diálogo '" + id + "' sem 'start'");
        return new DialogueDef(id, start, nodes);
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
