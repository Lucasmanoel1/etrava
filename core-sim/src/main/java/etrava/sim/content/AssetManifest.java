package etrava.sim.content;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Manifesto de assets: liga cada ID lógico a arquivo(s), origem e licença (RF-420).
 * Lê todos os {@code assets/manifest/*.yaml}. O arquivo {@code provisorio.yaml}
 * (arte gerada por código) é lido primeiro; qualquer outro arquivo que repita um
 * ID o substitui — é assim que a arte definitiva entra sem mexer em código.
 */
public final class AssetManifest {
    public static final String GENERATED_FILE = "provisorio.yaml";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    /** Trecho de animação numa folha de sprites: coluna inicial, nº de quadros e velocidade. */
    public record Anim(int start, int frames, double fps) {}

    /**
     * @param type       sprite | spritesheet | tile | portrait | icon | illustration | sound | music | font
     * @param file       arquivo, relativo a assets/ (ou {@code files}, para variações de um tile)
     * @param anchor     pixel da imagem (a partir do canto superior esquerdo) que fica no ponto de apoio no chão
     * @param emissive   imagem opcional, do mesmo tamanho, que acende à noite
     * @param frame      largura e altura de um quadro, em folhas de sprites
     * @param priority   em tiles: terrenos de prioridade maior avançam sobre os vizinhos
     */
    public record Entry(String id, String type, String file, List<String> files, double[] anchor, String emissive,
            int[] frame, Integer directions, Map<String, Anim> animations, Integer priority,
            String source, String license) {

        public List<String> allFiles() {
            List<String> all = new ArrayList<>();
            if (file != null) all.add(file);
            if (files != null) all.addAll(files);
            if (emissive != null) all.add(emissive);
            return all;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    public static AssetManifest load(Path assetsDir) {
        AssetManifest manifest = new AssetManifest();
        Path dir = assetsDir.resolve("manifest");
        if (!Files.isDirectory(dir)) {
            manifest.problems.add("assets/manifest: pasta não encontrada");
            return manifest;
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".yaml"))
                    .sorted(Comparator.comparing((Path p) -> !p.getFileName().toString().equals(GENERATED_FILE))
                            .thenComparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            manifest.problems.add("assets/manifest: não foi possível listar: " + e.getMessage());
            return manifest;
        }
        for (Path file : files) {
            String name = "assets/manifest/" + file.getFileName();
            try {
                List<Entry> list = YAML.readValue(file.toFile(), new TypeReference<List<Entry>>() {});
                if (list == null) continue;
                for (Entry e : list) {
                    if (e.id() == null || e.id().isBlank()) manifest.problems.add(name + ": entrada sem id");
                    else manifest.entries.put(e.id(), e);
                }
            } catch (Exception e) {
                manifest.problems.add(name + ": não foi possível ler: " + e.getMessage());
            }
        }
        return manifest;
    }

    public Entry get(String id) { return entries.get(id); }
    public boolean has(String id) { return entries.containsKey(id); }
    public Map<String, Entry> entries() { return entries; }
    public List<String> problems() { return problems; }
}
