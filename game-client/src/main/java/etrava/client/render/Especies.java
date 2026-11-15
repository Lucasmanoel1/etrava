package etrava.client.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import etrava.sim.content.PropPlacement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Espécie de árvore por área e por região (V4, seção {@code especies} da
 * {@code assets/config/decoracao.yaml}). O mapa diz só "árvore" ({@code prop.arvore},
 * {@code prop.arvore_b}); aqui se decide se aquela árvore é carvalho, castanheiro, pinheiro ou
 * salgueiro — pela área, pela vizinhança (salgueiro perto do moinho) e pelo hash da posição.
 * Pura apresentação: a pegada e a colisão continuam as do prop.
 */
final class Especies {
    private static final Logger LOG = LoggerFactory.getLogger(Especies.class);

    /** Regra "perto de": dentro do raio de um prop do mapa, outra lista de sprites. */
    private record Perto(List<double[]> centros, double raio, Map<String, List<String>> troca) {}

    private final Map<String, List<String>> padrao = new HashMap<>();
    private final List<Perto> perto = new ArrayList<>();

    private Especies() {}

    static Especies vazia() {
        return new Especies();
    }

    /** @param existe se o sprite está no manifesto (ID ausente sai da lista, com aviso) */
    static Especies carregar(Path assetsDir, String areaId, List<PropPlacement> props, Predicate<String> existe) {
        Especies e = new Especies();
        Path file = assetsDir.resolve("config/decoracao.yaml");
        if (!Files.exists(file)) return e;
        JsonNode cfg;
        try {
            cfg = new ObjectMapper(new YAMLFactory()).readTree(file.toFile()).path("especies").path(areaId);
        } catch (Exception ex) {
            LOG.warn("decoracao.yaml ilegível ({}): árvores sem regra de espécie", ex.getMessage());
            return e;
        }
        if (cfg.isMissingNode()) return e;
        cfg.fields().forEachRemaining(f -> {
            if (f.getKey().equals("perto")) return;
            List<String> l = lista(f.getValue(), existe);
            if (!l.isEmpty()) e.padrao.put(f.getKey(), l);
        });
        for (JsonNode r : cfg.path("perto")) {
            String de = r.path("de").asText();
            List<double[]> centros = new ArrayList<>();
            for (PropPlacement p : props) if (p.propId().equals(de)) centros.add(new double[] {p.x(), p.y()});
            if (centros.isEmpty()) continue;
            Map<String, List<String>> troca = new HashMap<>();
            r.fields().forEachRemaining(f -> {
                if (f.getValue().isArray()) {
                    List<String> l = lista(f.getValue(), existe);
                    if (!l.isEmpty()) troca.put(f.getKey(), l);
                }
            });
            e.perto.add(new Perto(centros, r.path("raio").asDouble(6), troca));
        }
        return e;
    }

    private static List<String> lista(JsonNode n, Predicate<String> existe) {
        List<String> out = new ArrayList<>();
        for (JsonNode s : n) {
            if (existe.test(s.asText())) out.add(s.asText());
            else LOG.warn("decoracao.yaml: espécie '{}' não está no manifesto — ignorada", s.asText());
        }
        return out;
    }

    /** O sprite que vai no lugar de {@code id} nesta posição (o próprio id quando não há regra). */
    String escolher(String id, double x, double y, int hash) {
        for (Perto r : perto) {
            List<String> l = r.troca().get(id);
            if (l == null) continue;
            for (double[] c : r.centros()) {
                double dx = x - c[0], dy = y - c[1];
                if (dx * dx + dy * dy <= r.raio() * r.raio()) return l.get(Math.floorMod(hash, l.size()));
            }
        }
        List<String> l = padrao.get(id);
        return l == null ? id : l.get(Math.floorMod(hash, l.size()));
    }
}
