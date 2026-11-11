package etrava.sim.content;

import java.util.List;
import java.util.Map;

/**
 * Fato: a unidade de conhecimento do jogo (RF-120, content/facts.yaml).
 *
 * @param layer           camada de verdade: V0 superfície, V1 suspeita, V2 estrutura, V3 fundação (PRD 8.2)
 * @param truth           oculto do jogador: true | false | partial | unknown
 * @param spread          o quanto vira rumor entre NPCs: none | local | wide
 * @param versions        como o fato é contado; a primeira é a versão padrão
 * @param interpretations versão preferida por facção (RF-064)
 */
public record FactDef(String id, String layer, String truth, List<String> tags, String spread,
        List<Version> versions, Map<String, String> interpretations) {

    public record Version(String id, String text) {}

    public static final List<String> LAYERS = List.of("V0", "V1", "V2", "V3");

    public List<Version> versionList() { return versions != null ? versions : List.of(); }

    public boolean spreads() { return spread != null && !spread.equals("none"); }

    public String defaultVersion() { return versionList().isEmpty() ? null : versionList().get(0).id(); }

    public boolean hasVersion(String versionId) {
        return versionList().stream().anyMatch(v -> v.id().equals(versionId));
    }

    /** Texto de uma versão; cai na versão padrão se a pedida não existir. */
    public String text(String versionId) {
        for (Version v : versionList()) if (v.id().equals(versionId)) return v.text();
        return versionList().isEmpty() ? "" : versionList().get(0).text();
    }

    /** Posição da camada (0 a 3); -1 se inválida. */
    public int layerIndex() { return LAYERS.indexOf(layer); }
}
