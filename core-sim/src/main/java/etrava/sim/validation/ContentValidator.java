package etrava.sim.validation;

import etrava.sim.content.AreaDef;
import etrava.sim.content.ContentDb;
import etrava.sim.content.PropPlacement;
import etrava.sim.nav.NavGrid;
import etrava.sim.nav.Pathfinder.Point;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Validador de conteúdo (RF-442). Cada problema aponta arquivo e item.
 * M0: cobre props e áreas. As regras narrativas (fontes de fatos, camadas de
 * verdade, desfecho padrão de arcos) entram com os sistemas correspondentes (M3/M4).
 */
public final class ContentValidator {
    private ContentValidator() {}

    public static List<String> validate(Path contentDir) {
        return validate(ContentDb.load(contentDir));
    }

    public static List<String> validate(ContentDb db) {
        List<String> problems = new ArrayList<>(db.problems());
        if (db.areas().isEmpty()) problems.add("areas/areas.yaml: nenhuma área carregada");

        for (AreaDef area : db.areas().values()) {
            String file = "areas/" + db.areaEntry(area.id()).map();
            for (PropPlacement p : area.props()) {
                if (!db.props().containsKey(p.propId()))
                    problems.add(file + ": prop inexistente '" + p.propId() + "' em ("
                            + fmt(p.x()) + ", " + fmt(p.y()) + ")");
            }
            NavGrid nav = area.buildNavGrid(db.props());
            checkPoints(problems, file, "spawn", area.spawns(), nav);
            checkPoints(problems, file, "ponto", area.spots(), nav);
        }
        return problems;
    }

    private static void checkPoints(List<String> problems, String file, String kind,
            Map<String, Point> points, NavGrid nav) {
        for (Map.Entry<String, Point> e : points.entrySet()) {
            if (e.getKey().isBlank()) problems.add(file + ": " + kind + " sem nome");
            if (nav.isBlockedAt(e.getValue().x(), e.getValue().y()))
                problems.add(file + ": " + kind + " '" + e.getKey() + "' está em local bloqueado");
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
