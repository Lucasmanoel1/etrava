package etrava.sim.validation;

import etrava.sim.content.AreaDef;
import etrava.sim.content.AssetManifest;
import etrava.sim.content.ContentDb;
import etrava.sim.content.NpcDef;
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
        return validate(db, new ArrayList<>());
    }

    private static List<String> validate(ContentDb db, List<String> warnings) {
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
        for (NpcDef npc : db.npcs().values()) checkNpc(problems, db, npc);
        NarrativeValidator.validate(db, problems, warnings);
        return problems;
    }

    /** Conteúdo + assets: todo ID de asset citado precisa existir no manifesto. */
    public static Report validate(Path contentDir, Path assetsDir) {
        ContentDb db = ContentDb.load(contentDir);
        AssetManifest manifest = AssetManifest.load(assetsDir);
        List<String> warnings = new ArrayList<>();
        List<String> errors = validate(db, warnings);
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
        }
        for (NpcDef npc : db.npcs().values()) {
            String file = "npcs/" + npc.id() + ".yaml";
            if (npc.sprite() == null) errors.add(file + ": NPC sem sprite");
            else requireAsset(errors, manifest, file, "NPC '" + npc.id() + "'", npc.sprite());
            if (npc.dialogue() != null) {
                requireAsset(errors, manifest, file, "retrato do NPC '" + npc.id() + "'", npc.portraitId());
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

    /** Agenda: blocos bem formados, apontando para pontos que existem, cobrindo as 24 horas. */
    private static void checkNpc(List<String> problems, ContentDb db, NpcDef npc) {
        String file = "npcs/" + npc.id() + ".yaml";
        if (npc.blocks().isEmpty()) {
            problems.add(file + ": NPC sem agenda");
            return;
        }
        boolean wellFormed = true;
        for (NpcDef.Block b : npc.blocks()) {
            if (!b.wellFormed()) {
                problems.add(file + ": bloco de agenda inválido (" + b.from() + "–" + b.to() + " em '" + b.at() + "')");
                wellFormed = false;
                continue;
            }
            AreaDef area = db.area(b.area());
            if (area == null) problems.add(file + ": agenda usa área inexistente '" + b.area() + "'");
            else if (!area.spots().containsKey(b.spot()))
                problems.add(file + ": agenda usa ponto inexistente '" + b.at() + "'");
        }
        if (!wellFormed) return;
        for (int minute = 0; minute < 1440; minute += 10) {
            if (npc.blockAt(minute) == null) {
                problems.add(file + String.format(": agenda sem bloco às %02d:%02d", minute / 60, minute % 60));
                return;
            }
        }
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
