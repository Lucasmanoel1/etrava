package etrava.sim.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import etrava.sim.rules.Condition;
import etrava.sim.rules.Effect;
import etrava.sim.rules.Rules;
import java.util.List;
import java.util.Map;

/**
 * Um trabalho remunerado (content/jobs.yaml, RF-264): uma ação que consome horas
 * de jogo, paga, treina habilidades e é também onde se ouvem coisas (PRD 7.2).
 *
 * @param giver     quem dá o trabalho; precisa estar presente, acordado e na área do jogador
 * @param at        onde se trabalha ("area/ponto"); o jogador termina ali
 * @param from      a partir de que hora dá para começar, "HH:MM"
 * @param to        até que hora dá para começar, "HH:MM"
 * @param hours     duração em horas de jogo
 * @param pay       pagamento-base pelo trabalho inteiro
 * @param paySkill  habilidade que melhora o pagamento (até +50% com 100); null = nenhuma
 * @param train     habilidade → ganho-base por hora trabalhada
 * @param when      condição extra para o trabalho ser oferecido (opinião de quem dá, por exemplo)
 * @param onFinish  efeitos ao terminar
 * @param gossip    chance de, durante o trabalho, quem dá o trabalho contar uma novidade
 */
public record JobDef(String id, String name, String giver, String at, String from, String to, double hours, int pay,
        String paySkill, Map<String, Double> train, JsonNode when, JsonNode onFinish,
        @JsonProperty("gossip") double gossipChance) {

    public String displayName() { return name != null ? name : id; }
    public Map<String, Double> trainMap() { return train != null ? train : Map.of(); }
    public Condition condition() { return Rules.condition(when); }
    public List<Effect> finishEffects() { return Rules.effects(onFinish); }
    public String area() { return at.substring(0, at.indexOf('/')); }
    public String spot() { return at.substring(at.indexOf('/') + 1); }

    /** Dá para começar neste minuto do dia? A janela pode atravessar a meia-noite. */
    public boolean opensAt(int minuteOfDay) {
        int a = minutes(from, 0), b = minutes(to, 1440);
        return a <= b ? minuteOfDay >= a && minuteOfDay < b : minuteOfDay >= a || minuteOfDay < b;
    }

    public boolean wellFormed() {
        try {
            minutes(from, 0);
            minutes(to, 1440);
            return giver != null && at != null && at.indexOf('/') > 0 && hours > 0 && pay >= 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int minutes(String hhmm, int fallback) {
        if (hhmm == null) return fallback;
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }
}
