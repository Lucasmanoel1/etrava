package etrava.sim.world;

/**
 * Algo que alguém sabe — ou acha que sabe (RF-121).
 *
 * @param version   como o fato foi contado a esta pessoa
 * @param certainty rumor (ouvi dizer) | claim (me afirmaram) | witnessed (vi) | read (li) | verified (conferi)
 * @param source    de onde veio: "npc:id", "conversa:id", "dialogo:id", "inicio"…
 * @param day       dia de jogo em que soube
 * @param place     área onde estava
 */
public record KnowledgeEntry(String fact, String version, String certainty, String source, int day, String place) {
    public static final java.util.List<String> CERTAINTIES =
            java.util.List.of("rumor", "claim", "witnessed", "read", "verified");
}
