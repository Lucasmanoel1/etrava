package etrava.sim.content;

import com.fasterxml.jackson.databind.JsonNode;
import etrava.sim.rules.Condition;
import etrava.sim.rules.Rules;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversa ambiente (RF-088, content/ambient/*.yaml): NPCs conversam entre si,
 * sem pausar o jogo, quando estão juntos e o jogador está por perto. Quem ouve
 * até o fim fica sabendo dos fatos — como rumor, nunca como verdade.
 *
 * @param participants  NPCs que precisam estar juntos, parados e à vista
 * @param cooldownHours horas de jogo até poder repetir; 0 = só uma vez
 * @param facts         fatos que o jogador aprende se ouvir tudo: "fato" ou "fato:versão"
 */
public record AmbientDef(String id, List<String> participants, Condition when, double cooldownHours,
        List<Line> lines, List<String> facts) {

    public record Line(String speaker, String text) {}

    public static AmbientDef parse(JsonNode n) {
        JsonNode idNode = n.get("id");
        if (idNode == null || idNode.asText().isBlank()) throw new IllegalArgumentException("conversa ambiente sem id");
        String id = idNode.asText();
        List<String> participants = new ArrayList<>();
        for (JsonNode p : n.path("participants")) participants.add(p.asText());
        List<Line> lines = new ArrayList<>();
        for (JsonNode l : n.path("lines")) {
            if (!l.hasNonNull("speaker") || !l.hasNonNull("text"))
                throw new IllegalArgumentException("fala sem 'speaker' ou 'text' em " + id);
            lines.add(new Line(l.get("speaker").asText(), l.get("text").asText()));
        }
        List<String> facts = new ArrayList<>();
        for (JsonNode f : n.path("facts")) facts.add(f.asText());
        if (participants.isEmpty() || lines.isEmpty())
            throw new IllegalArgumentException("conversa '" + id + "' precisa de participantes e falas");
        return new AmbientDef(id, participants, Rules.condition(n.get("when")), n.path("cooldownHours").asDouble(0),
                lines, facts);
    }

    public static String factId(String ref) {
        int i = ref.indexOf(':');
        return i < 0 ? ref : ref.substring(0, i);
    }

    /** Versão indicada em "fato:versão", ou null. */
    public static String factVersion(String ref) {
        int i = ref.indexOf(':');
        return i < 0 ? null : ref.substring(i + 1);
    }
}
