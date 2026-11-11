package etrava.sim.content;

import com.fasterxml.jackson.databind.JsonNode;
import etrava.sim.rules.Condition;
import etrava.sim.rules.Rules;
import java.util.ArrayList;
import java.util.List;

/**
 * Epílogo combinatório de um capítulo (RF-163, content/epilogues/*.yaml): uma
 * sequência de espaços (slots); em cada um entra o fragmento de maior
 * prioridade cuja condição passa.
 *
 * @param endDay último dia do capítulo; o epílogo aparece quando ele termina
 */
public record EpilogueDef(int endDay, List<Slot> slots, List<Fragment> fragments) {

    /** @param arc arco de que o espaço trata; se houver, o espaço precisa de um fragmento sem condição */
    public record Slot(String id, String arc) {}

    /** @param unconditional true quando o conteúdo não declarou {@code when} */
    public record Fragment(String slot, int priority, Condition when, boolean unconditional, String text) {}

    public static EpilogueDef parse(JsonNode root) {
        List<Slot> slots = new ArrayList<>();
        for (JsonNode s : root.path("slots")) {
            if (s.isTextual()) slots.add(new Slot(s.asText(), null));
            else slots.add(new Slot(s.path("id").asText(), s.hasNonNull("arc") ? s.get("arc").asText() : null));
        }
        List<Fragment> fragments = new ArrayList<>();
        for (JsonNode f : root.path("fragments")) {
            if (!f.hasNonNull("slot") || !f.hasNonNull("text"))
                throw new IllegalArgumentException("fragmento de epílogo sem 'slot' ou 'text'");
            fragments.add(new Fragment(f.get("slot").asText(), f.path("priority").asInt(0),
                    Rules.condition(f.get("when")), !f.hasNonNull("when"), f.get("text").asText().strip()));
        }
        return new EpilogueDef(root.path("endDay").asInt(30), slots, fragments);
    }
}
