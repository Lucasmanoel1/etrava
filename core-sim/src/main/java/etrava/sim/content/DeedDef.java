package etrava.sim.content;

/**
 * Um tipo de ato do jogador que os outros julgam (RF-089, content/deeds.yaml).
 * Só muda a opinião de quem viu ou de quem ficou sabendo pelo rumor (RF-281).
 *
 * @param fact    fato que passa a circular quando alguém testemunha o ato
 * @param witness quanto muda a opinião de quem viu
 * @param hearsay quanto muda a opinião de quem só ouviu falar
 */
public record DeedDef(String id, String name, String fact, int witness, int hearsay) {
    public String displayName() { return name != null ? name : id; }
}
