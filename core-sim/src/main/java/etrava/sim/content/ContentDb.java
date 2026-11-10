package etrava.sim.content;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Definições imutáveis carregadas de {@code content/}. O carregamento é tolerante:
 * o que falha vira uma entrada em {@link #problems()} e o jogo segue; quem decide
 * se isso quebra o build é o validador.
 */
public final class ContentDb {
    private static final Logger LOG = LoggerFactory.getLogger(ContentDb.class);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    /** Entrada de areas/areas.yaml. */
    public record AreaEntry(String id, String name, String map) {}

    private final Map<String, PropDef> props = new LinkedHashMap<>();
    private final Map<String, AreaDef> areas = new LinkedHashMap<>();
    private final Map<String, AreaEntry> areaEntries = new LinkedHashMap<>();
    private final Map<String, NpcDef> npcs = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    public static ContentDb load(Path contentDir) {
        ContentDb db = new ContentDb();
        Path propsFile = contentDir.resolve("props.yaml");
        try {
            for (PropDef p : YAML.readValue(propsFile.toFile(), new TypeReference<List<PropDef>>() {})) {
                if (db.props.put(p.id(), p) != null) db.problem("props.yaml", "prop duplicado: " + p.id());
            }
        } catch (Exception e) {
            db.problem("props.yaml", "não foi possível ler: " + e.getMessage());
        }

        Path areasFile = contentDir.resolve("areas/areas.yaml");
        List<AreaEntry> entries = List.of();
        try {
            entries = YAML.readValue(areasFile.toFile(), new TypeReference<List<AreaEntry>>() {});
        } catch (Exception e) {
            db.problem("areas/areas.yaml", "não foi possível ler: " + e.getMessage());
        }
        for (AreaEntry entry : entries) {
            db.areaEntries.put(entry.id(), entry);
            Path map = contentDir.resolve("areas").resolve(entry.map());
            if (!Files.exists(map)) {
                db.problem("areas/areas.yaml", "área '" + entry.id() + "': mapa não encontrado: " + entry.map());
                continue;
            }
            try {
                db.areas.put(entry.id(), TiledMapLoader.load(entry.id(), map));
            } catch (Exception e) {
                db.problem("areas/" + entry.map(), "não foi possível ler: " + e.getMessage());
            }
        }
        db.loadNpcs(contentDir.resolve("npcs"));
        for (String p : db.problems) LOG.warn("Conteúdo: {}", p);
        return db;
    }

    /** Um arquivo por NPC em content/npcs/, lidos em ordem alfabética (ordem estável = determinismo). */
    private void loadNpcs(Path dir) {
        if (!Files.isDirectory(dir)) return;
        List<Path> files;
        try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".yaml")).sorted().toList();
        } catch (java.io.IOException e) {
            problem("npcs", "não foi possível listar: " + e.getMessage());
            return;
        }
        for (Path file : files) {
            String name = "npcs/" + file.getFileName();
            try {
                NpcDef def = YAML.readValue(file.toFile(), NpcDef.class);
                if (def == null || def.id() == null) problem(name, "NPC sem id");
                else if (npcs.put(def.id(), def) != null) problem(name, "NPC duplicado: " + def.id());
            } catch (Exception e) {
                problem(name, "não foi possível ler: " + e.getMessage());
            }
        }
    }

    private void problem(String file, String message) {
        problems.add(file + ": " + message);
    }

    public Map<String, PropDef> props() { return props; }
    public Map<String, AreaDef> areas() { return areas; }
    public AreaEntry areaEntry(String id) { return areaEntries.get(id); }
    public AreaDef area(String id) { return areas.get(id); }
    public Map<String, NpcDef> npcs() { return npcs; }
    /** Problemas encontrados durante o carregamento (arquivo: mensagem). */
    public List<String> problems() { return problems; }
}
