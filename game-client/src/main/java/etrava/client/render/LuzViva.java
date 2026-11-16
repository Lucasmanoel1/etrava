package etrava.client.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Luz que tremula (V6, SPEC-visual §7.4): lareira, velas, forja e lampião respiram com um ruído
 * suave, determinístico pela posição (duas lareiras nunca batem juntas) e sem piscar duro.
 * Os parâmetros vêm de {@code assets/config/luz_viva.yaml}, chave {@code tremula}.
 * Pura apresentação: nada entra no save nem na simulação.
 */
final class LuzViva {
    private static final Logger LOG = LoggerFactory.getLogger(LuzViva.class);

    /** Quanto a luz varia: frações de ±, e viradas do ruído por segundo. */
    record Preset(double intensidade, double raio, double velocidade) {}

    private final Map<String, Preset> presets = new TreeMap<>();
    private final JsonNode feixe;

    private LuzViva(JsonNode raiz) {
        raiz.path("tremula").fields().forEachRemaining(f -> presets.put(f.getKey(), new Preset(
                f.getValue().path("intensidade").asDouble(0), f.getValue().path("raio").asDouble(0),
                f.getValue().path("velocidade").asDouble(1.5))));
        this.feixe = raiz.path("feixe");
    }

    static LuzViva carregar(Path assetsDir) {
        Path file = assetsDir.resolve("config/luz_viva.yaml");
        if (Files.exists(file)) {
            try {
                return new LuzViva(new ObjectMapper(new YAMLFactory()).readTree(file.toFile()));
            } catch (Exception e) {
                LOG.warn("config/luz_viva.yaml ilegível ({}): luz firme e sem feixe", e.getMessage());
            }
        } else {
            LOG.warn("config/luz_viva.yaml ausente: luz firme e sem feixe");
        }
        return de("{}");
    }

    static LuzViva de(String yaml) {
        try {
            return new LuzViva(new ObjectMapper(new YAMLFactory()).readTree(yaml));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** A seção {@code feixe} do arquivo (vazia se não houver). */
    JsonNode feixe() {
        return feixe;
    }

    /** Preset do prop: a chave igual ao id ou, senão, a chave mais longa que é começo dele; null = luz firme. */
    Preset para(String propId) {
        if (propId == null) return null;
        Preset exato = presets.get(propId);
        if (exato != null) return exato;
        String melhor = null;
        for (String k : presets.keySet()) {
            if (propId.startsWith(k) && (melhor == null || k.length() > melhor.length())) melhor = k;
        }
        return melhor == null ? null : presets.get(melhor);
    }

    /** Multiplicador do brilho (em torno de 1) da luz na posição, no instante {@code t} (segundos). */
    static double intensidade(Preset p, double x, double y, double t) {
        if (p == null || p.intensidade() == 0) return 1;
        return 1 + p.intensidade() * fogo(t * p.velocidade(), semente(x, y, 1));
    }

    /** Multiplicador do raio (em torno de 1), com ruído próprio: o raio não acompanha o brilho. */
    static double raio(Preset p, double x, double y, double t) {
        if (p == null || p.raio() == 0) return 1;
        return 1 + p.raio() * fogo(t * p.velocidade(), semente(x, y, 2));
    }

    /** Ruído de fogo em [-1, 1]: duas oitavas de ruído de valor, contínuas (sem saltos). */
    static double fogo(double u, int semente) {
        double v = 0.65 * ruido(u, semente) + 0.35 * ruido(u * 2.7 + 17.3, semente + 101);
        return Math.max(-1, Math.min(1, v));
    }

    /** Ruído de valor 1D com interpolação em cosseno, em [-1, 1]. */
    static double ruido(double u, int semente) {
        double f = Math.floor(u);
        int i = (int) f;
        double a = lattice(i, semente), b = lattice(i + 1, semente);
        double s = (1 - Math.cos((u - f) * Math.PI)) / 2;
        return a + (b - a) * s;
    }

    private static double lattice(int i, int semente) {
        int h = i * 0x27d4eb2d ^ semente * 0x165667b1;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return (h & 0xffff) / 32767.5 - 1;
    }

    static int semente(double x, double y, int canal) {
        int h = (int) Math.floor(x * 16) * 73856093 ^ (int) Math.floor(y * 16) * 19349663 ^ canal * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return h ^ (h >>> 15);
    }
}
