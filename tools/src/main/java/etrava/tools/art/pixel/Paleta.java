package etrava.tools.art.pixel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Paleta v3 (SPEC-visual §5.3): rampas com nome, do tom mais escuro ao mais claro, lidas de
 * {@code tools/art/paleta.yaml}. Toda a arte nova usa só estas cores (VT-04).
 */
public final class Paleta {
    private final Map<String, int[]> rampas = new LinkedHashMap<>();
    private final Set<Integer> cores = new LinkedHashSet<>();

    public static Paleta carregar(Path yaml) {
        try {
            JsonNode raiz = new ObjectMapper(new YAMLFactory()).readTree(yaml.toFile());
            Paleta p = new Paleta();
            for (Iterator<Map.Entry<String, JsonNode>> it = raiz.path("rampas").fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> e = it.next();
                int[] tons = new int[e.getValue().size()];
                for (int i = 0; i < tons.length; i++) {
                    tons[i] = 0xFF000000 | Integer.parseInt(e.getValue().get(i).asText(), 16);
                    p.cores.add(tons[i]);
                }
                p.rampas.put(e.getKey(), tons);
            }
            for (JsonNode c : raiz.path("soltas")) p.cores.add(0xFF000000 | Integer.parseInt(c.asText(), 16));
            return p;
        } catch (IOException e) {
            throw new UncheckedIOException("paleta: " + yaml, e);
        }
    }

    /** Tons da rampa (ARGB opaco), do escuro ao claro; erro claro se o nome não existe. */
    public int[] rampa(String nome) {
        int[] r = rampas.get(nome);
        if (r == null) throw new IllegalArgumentException("rampa desconhecida na paleta v3: " + nome);
        return r;
    }

    public boolean temRampa(String nome) { return rampas.containsKey(nome); }

    public Map<String, int[]> rampas() { return rampas; }

    /** A cor (ARGB opaco) pertence à paleta? */
    public boolean contem(int argb) { return cores.contains(argb | 0xFF000000); }

    public Set<Integer> cores() { return cores; }

    /** Luminância relativa (0..1) de uma cor ARGB. */
    public static double luminancia(int argb) {
        double r = ((argb >> 16) & 0xFF) / 255.0, g = ((argb >> 8) & 0xFF) / 255.0, b = (argb & 0xFF) / 255.0;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }
}
