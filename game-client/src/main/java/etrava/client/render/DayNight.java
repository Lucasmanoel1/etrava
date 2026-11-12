package etrava.client.render;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ciclo de luz do dia (RF-027), lido de {@code assets/config/daynight.yaml}. */
public final class DayNight {
    private static final Logger LOG = LoggerFactory.getLogger(DayNight.class);

    /**
     * Uma linha da tabela; também é o resultado da interpolação.
     * {@code actor}: luz de presença (D-090) — os personagens nunca recebem menos luz que
     * {@code ambiente × actor} (com teto); 0 ou ausente = 1, sem reforço.
     */
    public record Key(double hour, double[] ambient, double night, double saturation, double contrast,
            double vignette, double actor) {
        public double actorLift() {
            return actor <= 0 ? 1 : actor;
        }
    }

    private static final List<Key> FALLBACK = List.of(
            new Key(0, new double[] {1, 1, 1}, 0, 1, 1, 0.25, 1),
            new Key(24, new double[] {1, 1, 1}, 0, 1, 1, 0.25, 1));

    private final List<Key> keys;

    private DayNight(List<Key> keys) {
        this.keys = keys;
    }

    public static DayNight load(Path assetsDir) {
        Path file = assetsDir.resolve("config/daynight.yaml");
        if (Files.exists(file)) {
            try {
                List<Key> keys = new ObjectMapper(new YAMLFactory())
                        .readValue(file.toFile(), new TypeReference<List<Key>>() {});
                if (keys != null && keys.size() >= 2) {
                    return new DayNight(keys.stream().sorted(Comparator.comparingDouble(Key::hour)).toList());
                }
            } catch (Exception e) {
                LOG.warn("config/daynight.yaml inválido ({}) — usando luz fixa de dia", e.getMessage());
                return new DayNight(FALLBACK);
            }
        }
        LOG.warn("config/daynight.yaml ausente — usando luz fixa de dia");
        return new DayNight(FALLBACK);
    }

    /** Valores de luz para uma hora do dia (0 a 24, com fração). */
    public Key at(double hour) {
        Key a = keys.get(0), b = keys.get(keys.size() - 1);
        for (int i = 0; i < keys.size() - 1; i++) {
            if (hour >= keys.get(i).hour() && hour <= keys.get(i + 1).hour()) {
                a = keys.get(i);
                b = keys.get(i + 1);
                break;
            }
        }
        double span = b.hour() - a.hour();
        double t = span <= 0 ? 0 : Math.max(0, Math.min(1, (hour - a.hour()) / span));
        t = t * t * (3 - 2 * t);
        double[] ambient = new double[3];
        for (int i = 0; i < 3; i++) ambient[i] = lerp(a.ambient()[i], b.ambient()[i], t);
        return new Key(hour, ambient, lerp(a.night(), b.night(), t), lerp(a.saturation(), b.saturation(), t),
                lerp(a.contrast(), b.contrast(), t), lerp(a.vignette(), b.vignette(), t),
                lerp(a.actorLift(), b.actorLift(), t));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
