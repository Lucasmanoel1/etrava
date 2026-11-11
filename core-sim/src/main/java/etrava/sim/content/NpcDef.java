package etrava.sim.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Definição de um NPC (content/npcs/*.yaml).
 *
 * @param layer    A personagem | B morador | C figurante (PRD 9.1)
 * @param sprite   ID do asset de folha de sprites
 * @param portrait ID do asset de retrato; se omitido, vale {@code retrato.<id>}
 * @param dialogue ID do diálogo ao falar com o NPC; null = não puxa conversa
 * @param opinion  opinião inicial sobre o jogador, de -100 a 100
 * @param knows    fatos que já conhece no início: "fato" ou "fato:versão"
 * @param schedule agenda: blocos de horário que cobrem o dia inteiro
 */
public record NpcDef(String id, String name, String layer, String sprite, String portrait, String dialogue,
        int opinion, List<String> knows, List<Block> schedule) {

    /**
     * Um bloco da agenda.
     *
     * @param from     início, "HH:MM"
     * @param to       fim, "HH:MM" (pode passar da meia-noite)
     * @param activity o que o NPC faz (chave {@code do} no arquivo)
     * @param at       onde: "area/ponto"
     * @param hidden   true quando o NPC está dentro de uma construção sem interior jogável
     */
    public record Block(String from, String to, @JsonProperty("do") String activity, String at, boolean hidden) {

        public int fromMinute() { return minutes(from); }
        public int toMinute() { return minutes(to); }

        public boolean contains(int minuteOfDay) {
            int a = fromMinute(), b = toMinute();
            return a <= b ? minuteOfDay >= a && minuteOfDay < b : minuteOfDay >= a || minuteOfDay < b;
        }

        public String area() { return at.substring(0, at.indexOf('/')); }
        public String spot() { return at.substring(at.indexOf('/') + 1); }

        /** Formato válido: "HH:MM" e "area/ponto". */
        public boolean wellFormed() {
            try {
                return at != null && at.indexOf('/') > 0 && at.indexOf('/') < at.length() - 1
                        && fromMinute() >= 0 && toMinute() >= 0 && fromMinute() != toMinute();
            } catch (RuntimeException e) {
                return false;
            }
        }

        private static int minutes(String hhmm) {
            String[] p = hhmm.split(":");
            int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            if (h < 0 || h > 24 || m < 0 || m > 59) throw new IllegalArgumentException(hhmm);
            return (h * 60 + m) % 1440;
        }
    }

    public List<Block> blocks() { return schedule != null ? schedule : List.of(); }
    public List<String> knowsList() { return knows != null ? knows : List.of(); }
    public String portraitId() { return portrait != null ? portrait : "retrato." + id; }
    public String displayName() { return name != null ? name : id; }

    /** Bloco em vigor num minuto do dia, ou null se a agenda tiver um buraco. */
    public Block blockAt(int minuteOfDay) {
        for (Block b : blocks()) if (b.contains(minuteOfDay)) return b;
        return null;
    }
}
