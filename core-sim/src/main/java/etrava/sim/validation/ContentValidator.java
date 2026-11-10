package etrava.sim.validation;

import etrava.sim.content.ActorSpawn;
import etrava.sim.content.AreaDef;
import etrava.sim.content.AssetManifest;
import etrava.sim.content.ContentDb;
import etrava.sim.content.PropDef;
import etrava.sim.content.PropPlacement;
import etrava.sim.nav.NavGrid;
import etrava.sim.nav.Pathfinder.Point;
import etrava.sim.world.World;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Validador de conteúdo (RF-442). Cada problema aponta arquivo e item.
 * Cobre props, áreas e referências a assets. As regras narrativas (fontes de
 * fatos, camadas de verdade, desfecho padrão de arcos) entram com os sistemas
 * correspondentes (M3/M4).
 */
public final class ContentValidator {
    /** Erros quebram o build; avisos não (ex.: asset no manifesto mas ainda sem arquivo). */
    public record Report(List<String> errors, List<String> warnings) {
        public boolean ok() { return errors.isEmpty(); }
    }

    private ContentValidator() {}

    /** Só o conteúdo, sem conferir assets. */
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
            for (ActorSpawn actor : area.actors()) {
                if (actor.id().isBlank()) problems.add(file + ": figurante sem nome");
                if (nav.isBlockedAt(actor.x(), actor.y()))
                    problems.add(file + ": figurante '" + actor.id() + "' está em local bloqueado");
                for (String stop : actor.patrol()) {
                    if (!area.spots().containsKey(stop))
                        problems.add(file + ": figurante '" + actor.id() + "' usa ponto inexistente '" + stop + "'");
                }
            }
        }
        return problems;
    }

    /** Conteúdo + assets: todo ID de asset citado precisa existir no manifesto. */
    public static Report validate(Path contentDir, Path assetsDir) {
        ContentDb db = ContentDb.load(contentDir);
        AssetManifest manifest = AssetManifest.load(assetsDir);
        List<String> errors = validate(db);
        List<String> warnings = new ArrayList<>();
        errors.addAll(manifest.problems());

        for (PropDef prop : db.props().values()) {
            requireAsset(errors, manifest, "props.yaml", "prop '" + prop.id() + "'", prop.spriteId());
        }
        requireAsset(errors, manifest, "(jogador)", "personagem do jogador", World.PLAYER_SPRITE);
        for (AreaDef area : db.areas().values()) {
            String file = "areas/" + db.areaEntry(area.id()).map();
            for (String terrain : area.terrainsUsed()) {
                requireAsset(errors, manifest, file, "terreno '" + terrain + "'", "tile." + terrain);
            }
            for (ActorSpawn actor : area.actors()) {
                if (actor.sprite() == null) errors.add(file + ": figurante '" + actor.id() + "' sem sprite");
                else requireAsset(errors, manifest, file, "figurante '" + actor.id() + "'", actor.sprite());
            }
        }
        for (AssetManifest.Entry entry : manifest.entries().values()) {
            for (String f : entry.allFiles()) {
                if (!Files.exists(assetsDir.resolve(f)))
                    warnings.add("asset '" + entry.id() + "': arquivo ausente (" + f + ") — o jogo usa placeholder");
            }
            boolean hasFile = entry.allFiles().stream().anyMatch(f -> Files.exists(assetsDir.resolve(f)));
            if (hasFile && (isBlank(entry.source()) || isBlank(entry.license())))
                warnings.add("asset '" + entry.id() + "': sem origem/licença registrada (RF-426)");
        }
        return new Report(errors, warnings);
    }

    private static void requireAsset(List<String> errors, AssetManifest manifest, String file, String what, String id) {
        if (!manifest.has(id)) errors.add(file + ": " + what + " usa asset inexistente '" + id + "'");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
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
