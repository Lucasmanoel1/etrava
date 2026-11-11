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
    public static final String EPILOGUE_FILE = "epilogues/cap1.yaml";
    /** A única origem do MVP (PRD 8.4). */
    public static final String ORIGIN_FILE = "origins/pedra_mansa.yaml";

    /** Entrada de areas/areas.yaml. */
    public record AreaEntry(String id, String name, String map) {}

    private final Map<String, PropDef> props = new LinkedHashMap<>();
    private final Map<String, AreaDef> areas = new LinkedHashMap<>();
    private final Map<String, AreaEntry> areaEntries = new LinkedHashMap<>();
    private final Map<String, NpcDef> npcs = new LinkedHashMap<>();
    private final Map<String, FactDef> facts = new LinkedHashMap<>();
    private final Map<String, DialogueDef> dialogues = new LinkedHashMap<>();
    private final Map<String, AmbientDef> ambients = new LinkedHashMap<>();
    private final Map<String, String> ambientFiles = new LinkedHashMap<>();
    private final Map<String, String> texts = new LinkedHashMap<>();
    private final Map<String, ArcDef> arcs = new LinkedHashMap<>();
    private final Map<String, String> arcFiles = new LinkedHashMap<>();
    private final Map<String, DeedDef> deeds = new LinkedHashMap<>();
    private EpilogueDef epilogue;
    private final Map<String, ItemDef> items = new LinkedHashMap<>();
    private final Map<String, MerchantDef> merchants = new LinkedHashMap<>();
    private final Map<String, JobDef> jobs = new LinkedHashMap<>();
    private final Map<String, ContainerDef> containers = new LinkedHashMap<>();
    private SkillsDef skills = SkillsDef.EMPTY;
    private OriginDef origin = OriginDef.EMPTY;
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
        db.loadNarrative(contentDir);
        db.loadWorld(contentDir);
        db.loadEconomy(contentDir);
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

    /** Fatos, diálogos e conversas ambientes. Pastas ausentes são só conteúdo que ainda não existe. */
    private void loadNarrative(Path contentDir) {
        Path factsFile = contentDir.resolve("facts.yaml");
        if (Files.exists(factsFile)) {
            try {
                List<FactDef> list = YAML.readValue(factsFile.toFile(), new TypeReference<List<FactDef>>() {});
                for (FactDef f : list == null ? List.<FactDef>of() : list) {
                    if (f.id() == null) problem("facts.yaml", "fato sem id");
                    else if (facts.put(f.id(), f) != null) problem("facts.yaml", "fato duplicado: " + f.id());
                }
            } catch (Exception e) {
                problem("facts.yaml", "não foi possível ler: " + e.getMessage());
            }
        }
        Path textsFile = contentDir.resolve("textos.yaml");
        if (Files.exists(textsFile)) {
            try {
                Map<String, String> map = YAML.readValue(textsFile.toFile(), new TypeReference<Map<String, String>>() {});
                if (map != null) texts.putAll(map);
            } catch (Exception e) {
                problem("textos.yaml", "não foi possível ler: " + e.getMessage());
            }
        }
        for (Path file : yamlFiles(contentDir.resolve("dialogues"), "dialogues")) {
            String name = "dialogues/" + file.getFileName();
            try {
                DialogueDef def = DialogueDef.parse(YAML.readTree(file.toFile()));
                if (dialogues.put(def.id(), def) != null) problem(name, "diálogo duplicado: " + def.id());
            } catch (Exception e) {
                problem(name, "não foi possível ler: " + e.getMessage());
            }
        }
        for (Path file : yamlFiles(contentDir.resolve("ambient"), "ambient")) {
            String name = "ambient/" + file.getFileName();
            try {
                for (com.fasterxml.jackson.databind.JsonNode n : YAML.readTree(file.toFile())) {
                    AmbientDef def = AmbientDef.parse(n);
                    if (ambients.put(def.id(), def) != null) problem(name, "conversa duplicada: " + def.id());
                    ambientFiles.put(def.id(), name);
                }
            } catch (Exception e) {
                problem(name, "não foi possível ler: " + e.getMessage());
            }
        }
    }

    /** Arcos de mundo, atos e epílogo. Um arquivo de arcos pode trazer um arco ou uma lista deles. */
    private void loadWorld(Path contentDir) {
        for (Path file : yamlFiles(contentDir.resolve("arcs"), "arcs")) {
            String name = "arcs/" + file.getFileName();
            try {
                com.fasterxml.jackson.databind.JsonNode root = YAML.readTree(file.toFile());
                for (com.fasterxml.jackson.databind.JsonNode n : root.isArray() ? root : List.of(root)) {
                    ArcDef def = ArcDef.parse(n);
                    if (arcs.put(def.id(), def) != null) problem(name, "arco duplicado: " + def.id());
                    arcFiles.put(def.id(), name);
                }
            } catch (Exception e) {
                problem(name, "não foi possível ler: " + e.getMessage());
            }
        }
        Path deedsFile = contentDir.resolve("deeds.yaml");
        if (Files.exists(deedsFile)) {
            try {
                List<DeedDef> list = YAML.readValue(deedsFile.toFile(), new TypeReference<List<DeedDef>>() {});
                for (DeedDef d : list == null ? List.<DeedDef>of() : list) {
                    if (d.id() == null) problem("deeds.yaml", "ato sem id");
                    else if (deeds.put(d.id(), d) != null) problem("deeds.yaml", "ato duplicado: " + d.id());
                }
            } catch (Exception e) {
                problem("deeds.yaml", "não foi possível ler: " + e.getMessage());
            }
        }
        Path epilogueFile = contentDir.resolve(EPILOGUE_FILE);
        if (Files.exists(epilogueFile)) {
            try {
                epilogue = EpilogueDef.parse(YAML.readTree(epilogueFile.toFile()));
            } catch (Exception e) {
                problem(EPILOGUE_FILE, "não foi possível ler: " + e.getMessage());
            }
        }
    }

    /** Itens, comerciantes, trabalhos, contêineres, habilidades e a origem (M5). Arquivo ausente = lista vazia. */
    private void loadEconomy(Path contentDir) {
        list(contentDir, "items.yaml", new TypeReference<List<ItemDef>>() {}, ItemDef::id, items, "item");
        list(contentDir, "merchants.yaml", new TypeReference<List<MerchantDef>>() {}, MerchantDef::id, merchants,
                "comerciante");
        list(contentDir, "jobs.yaml", new TypeReference<List<JobDef>>() {}, JobDef::id, jobs, "trabalho");
        list(contentDir, "containers.yaml", new TypeReference<List<ContainerDef>>() {}, ContainerDef::id, containers,
                "contêiner");
        Path skillsFile = contentDir.resolve("skills.yaml");
        if (Files.exists(skillsFile)) {
            try {
                skills = YAML.readValue(skillsFile.toFile(), SkillsDef.class);
            } catch (Exception e) {
                problem("skills.yaml", "não foi possível ler: " + e.getMessage());
            }
        }
        Path originFile = contentDir.resolve(ORIGIN_FILE);
        if (Files.exists(originFile)) {
            try {
                origin = YAML.readValue(originFile.toFile(), OriginDef.class);
            } catch (Exception e) {
                problem(ORIGIN_FILE, "não foi possível ler: " + e.getMessage());
            }
        }
    }

    private <T> void list(Path contentDir, String file, TypeReference<List<T>> type,
            java.util.function.Function<T, String> id, Map<String, T> into, String what) {
        Path path = contentDir.resolve(file);
        if (!Files.exists(path)) return;
        try {
            List<T> list = YAML.readValue(path.toFile(), type);
            for (T t : list == null ? List.<T>of() : list) {
                if (id.apply(t) == null) problem(file, what + " sem id");
                else if (into.put(id.apply(t), t) != null) problem(file, what + " duplicado: " + id.apply(t));
            }
        } catch (Exception e) {
            problem(file, "não foi possível ler: " + e.getMessage());
        }
    }

    private List<Path> yamlFiles(Path dir, String label) {
        if (!Files.isDirectory(dir)) return List.of();
        try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".yaml")).sorted().toList();
        } catch (java.io.IOException e) {
            problem(label, "não foi possível listar: " + e.getMessage());
            return List.of();
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
    public Map<String, FactDef> facts() { return facts; }
    /** Texto avulso de content/textos.yaml (falas genéricas, rótulos); usa o padrão se faltar. */
    public String text(String key, String fallback) { return texts.getOrDefault(key, fallback); }
    public Map<String, DialogueDef> dialogues() { return dialogues; }
    public Map<String, AmbientDef> ambients() { return ambients; }
    /** Arquivo em que uma conversa ambiente foi definida, para mensagens do validador. */
    public String ambientFile(String id) { return ambientFiles.getOrDefault(id, "ambient"); }
    public Map<String, ArcDef> arcs() { return arcs; }
    /** Arquivo em que um arco foi definido, para mensagens do validador. */
    public String arcFile(String id) { return arcFiles.getOrDefault(id, "arcs"); }
    public Map<String, DeedDef> deeds() { return deeds; }
    /** O ato cujo rumor é este fato, ou null. */
    public DeedDef deedByFact(String factId) {
        for (DeedDef d : deeds.values()) if (factId.equals(d.fact())) return d;
        return null;
    }
    /** Epílogo do Capítulo I, ou null se o conteúdo não tiver um. */
    public EpilogueDef epilogue() { return epilogue; }
    /** A definição do prop posicionado no mapa com esta chave, ou null se a chave não existe. */
    public PropDef keyedProp(String key) {
        for (AreaDef area : areas.values()) {
            for (PropPlacement p : area.props()) if (key.equals(p.key())) return props.get(p.propId());
        }
        return null;
    }
    public Map<String, ItemDef> items() { return items; }
    public Map<String, MerchantDef> merchants() { return merchants; }
    /** O comércio que este NPC atende, ou null. */
    public MerchantDef merchantOf(String npcId) {
        for (MerchantDef m : merchants.values()) if (npcId.equals(m.npc())) return m;
        return null;
    }
    public Map<String, JobDef> jobs() { return jobs; }
    public Map<String, ContainerDef> containers() { return containers; }
    public SkillsDef skills() { return skills; }
    /** Comunidades que têm ao menos um NPC (para relatórios de reputação). */
    public java.util.Set<String> factionsInUse() {
        java.util.Set<String> set = new java.util.TreeSet<>();
        for (NpcDef n : npcs.values()) if (n.faction() != null) set.add(n.faction());
        return set;
    }
    public OriginDef origin() { return origin; }
    /** Problemas encontrados durante o carregamento (arquivo: mensagem). */
    public List<String> problems() { return problems; }
}
