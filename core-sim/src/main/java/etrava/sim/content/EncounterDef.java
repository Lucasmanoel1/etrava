package etrava.sim.content;

import com.fasterxml.jackson.databind.JsonNode;
import etrava.sim.rules.Condition;
import etrava.sim.rules.Rules;
import java.util.List;

/**
 * Um encontro (content/encounters.yaml, PRD 7.3): inimigos que podem aparecer
 * numa área. É sorteado quando o jogador chega à área e a cada hora em que fica
 * nela — inclusive dormindo ou esperando, o que interrompe o descanso (RF-323).
 * O efeito {@code encounter} força um encontro (RF-102: "iniciar combate").
 *
 * @param area     onde acontece
 * @param at       pontos do mapa onde os inimigos podem surgir (um é sorteado)
 * @param enemies  quem aparece e quantos
 * @param when     condição (hora, estado do mundo); ausente = sempre
 * @param chance   chance a cada sorteio
 * @param cooldown horas de jogo até poder acontecer de novo
 * @param once     acontece no máximo uma vez por partida
 */
public record EncounterDef(String id, String area, List<String> at, List<Group> enemies, JsonNode when,
        double chance, double cooldown, boolean once) {

    /** Um tipo de inimigo e quantos. */
    public record Group(String enemy, int qty) {
        public int amount() { return qty > 0 ? qty : 1; }
    }

    public List<String> spots() { return at != null ? at : List.of(); }
    public List<Group> groups() { return enemies != null ? enemies : List.of(); }
    public Condition condition() { return Rules.condition(when); }
}
