package etrava.client.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import etrava.sim.content.AreaDef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decoração do chão (SPEC-visual §7.2), lida de {@code assets/config/decoracao.yaml}: tufos,
 * flores, pedrinhas espalhados por terreno, em manchas, longe dos objetos do mapa. Determinística
 * pela área e pelo tile; não bloqueia, não é clicável, não entra no save.
 */
final class Decoration {
    private static final Logger LOG = LoggerFactory.getLogger(Decoration.class);

    /** Um elemento posto: sprite, posição em metros e se é decalque deitado. */
    record Piece(String sprite, double x, double y, boolean flat) {}

    /** Caixa de um objeto do mapa, para manter distância. */
    record Box(double x0, double y0, double x1, double y1) {}

    private Decoration() {}

    static List<Piece> place(Path assetsDir, String areaId, AreaDef area, List<Box> obstacles) {
        List<Piece> out = new ArrayList<>();
        Path file = assetsDir.resolve("config/decoracao.yaml");
        if (!Files.exists(file)) return out;
        JsonNode cfg;
        try {
            cfg = new ObjectMapper(new YAMLFactory()).readTree(file.toFile()).path("areas").path(areaId);
        } catch (Exception e) {
            LOG.warn("decoracao.yaml ilegível: {} — sem decoração", e.getMessage());
            return out;
        }
        if (cfg.isMissingNode()) return out;
        int seedArea = areaId.hashCode();
        for (int ty = 0; ty < area.height(); ty++) {
            for (int tx = 0; tx < area.width(); tx++) {
                JsonNode list = cfg.path(area.terrainAt(tx, ty));
                if (!list.isArray()) continue;
                int k = 0;
                for (JsonNode el : list) {
                    k++;
                    SplittableRandom rng = new SplittableRandom(((long) seedArea << 32) ^ (tx * 73856093L) ^ (ty * 19349663L) ^ (k * 83492791L));
                    double dens = el.path("densidade").asDouble(0.2);
                    double manchas = el.path("manchas").asDouble(0);
                    double n = noise(tx * 0.23 + k * 13.1, ty * 0.23 - k * 7.7);
                    dens *= (1 - manchas) + manchas * Math.max(0, n * 2 - 0.5) * 1.6;
                    int count = (int) Math.floor(dens + rng.nextDouble());
                    double folga = el.path("folga").asDouble(0.3);
                    for (int i = 0; i < count; i++) {
                        double x = tx + 0.08 + rng.nextDouble() * 0.84, y = ty + 0.08 + rng.nextDouble() * 0.84;
                        if (blocked(obstacles, x, y, folga)) continue;
                        out.add(new Piece(el.path("sprite").asText(), x, y, el.path("plano").asBoolean(false)));
                    }
                }
            }
        }
        return out;
    }

    private static boolean blocked(List<Box> obs, double x, double y, double m) {
        for (Box b : obs) if (x > b.x0() - m && x < b.x1() + m && y > b.y0() - m && y < b.y1() + m) return true;
        return false;
    }

    private static double noise(double x, double y) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(xi, yi), b = hash(xi + 1, yi), c = hash(xi, yi + 1), d = hash(xi + 1, yi + 1);
        double top = a + (b - a) * fx, bottom = c + (d - c) * fx;
        return top + (bottom - top) * fy;
    }

    private static double hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }
}
