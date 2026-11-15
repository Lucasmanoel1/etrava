package etrava.client.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Animação pelo que o NPC está fazendo (SPEC-visual §6.3), lida de {@code assets/config/animacoes.yaml}.
 * Pura apresentação: a simulação só diz a atividade da agenda ({@code ActorView.activity()}); aqui
 * ela vira o nome de uma animação da folha. Atividade sem regra, ou animação que a folha não tem,
 * fica em {@code parado}.
 *
 * <pre>
 * atividades:
 *   trabalhar: {padrao: trabalhar_bancada, por_npc: {ferreiro: trabalhar_martelo}}
 *   comer: sentado
 *   festejar: [conversar, parado]   # alterna a cada {@code alternar} segundos
 * </pre>
 */
public final class Animacoes {
    private static final Logger LOG = LoggerFactory.getLogger(Animacoes.class);

    /** Cada regra: as animações (uma ou várias, que se alternam), por NPC ou padrão. */
    private record Regra(List<String> padrao, Map<String, List<String>> porNpc) {}

    private final Map<String, Regra> regras = new HashMap<>();

    /**
     * Onde uma animação pode acontecer: perto (até {@code alcance} m) de um destes props ou sobre um
     * destes terrenos. Sem o lugar, o NPC fica em {@code parado}: ninguém martela no meio da praça
     * nem senta no ar (V2).
     */
    public record Lugar(java.util.Set<String> props, java.util.Set<String> terrenos, double alcance) {}

    private final Map<String, Lugar> lugares = new HashMap<>();
    private double alternar = 7.0;

    public static Animacoes carregar(Path assetsDir) {
        Animacoes a = new Animacoes();
        Path file = assetsDir.resolve("config/animacoes.yaml");
        if (!Files.exists(file)) {
            LOG.warn("animacoes.yaml não existe: NPC parado faz sempre 'parado'");
            return a;
        }
        try {
            a.ler(new ObjectMapper(new YAMLFactory()).readTree(file.toFile()));
        } catch (Exception e) {
            LOG.warn("animacoes.yaml ilegível: {} — NPC parado faz sempre 'parado'", e.getMessage());
        }
        return a;
    }

    static Animacoes de(String yaml) throws Exception {
        Animacoes a = new Animacoes();
        a.ler(new ObjectMapper(new YAMLFactory()).readTree(yaml));
        return a;
    }

    private void ler(JsonNode raiz) {
        alternar = raiz.path("alternar").asDouble(alternar);
        raiz.path("lugares").fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            lugares.put(e.getKey(), new Lugar(new java.util.HashSet<>(lista(v.path("props"))),
                    new java.util.HashSet<>(lista(v.path("terrenos"))), v.path("alcance").asDouble(1.6)));
        });
        raiz.path("atividades").fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            Map<String, List<String>> por = new HashMap<>();
            List<String> padrao;
            if (v.isObject()) {
                padrao = lista(v.path("padrao"));
                v.path("por_npc").fields().forEachRemaining(n -> por.put(n.getKey(), lista(n.getValue())));
            } else {
                padrao = lista(v);
            }
            regras.put(e.getKey(), new Regra(padrao, por));
        });
    }

    private static List<String> lista(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n.isArray()) n.forEach(x -> out.add(x.asText()));
        else if (n.isTextual()) out.add(n.asText());
        return out;
    }

    /**
     * A animação de um NPC parado nesta atividade.
     *
     * @param tempo segundos de jogo (para alternar entre várias)
     * @param fase  0..1, própria do NPC, para o bando não trocar junto
     * @return nome da animação, ou {@code "parado"} se não houver regra
     */
    public String escolher(String npc, String atividade, double tempo, double fase) {
        Regra r = atividade == null ? null : regras.get(atividade);
        if (r == null) return "parado";
        List<String> l = r.porNpc().getOrDefault(npc, r.padrao());
        if (l.isEmpty()) return "parado";
        if (l.size() == 1) return l.get(0);
        int i = (int) Math.floor(tempo / alternar + fase * l.size());
        return l.get(Math.floorMod(i, l.size()));
    }

    /** O lugar que a animação exige, ou null se ela vale em qualquer lugar (parado, conversar). */
    public Lugar lugar(String animacao) {
        return lugares.get(animacao);
    }

    /** Fase 0..1 estável por id (o mesmo NPC sempre com a mesma). */
    public static double fase(String id) {
        int h = id == null ? 0 : id.hashCode() * 0x9E3779B1;
        return ((h >>> 8) & 0xFFFF) / 65536.0;
    }
}
