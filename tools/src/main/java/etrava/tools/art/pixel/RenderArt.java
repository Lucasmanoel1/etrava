package etrava.tools.art.pixel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Pipeline de arte da Fase V (SPEC-visual §1, §2.3): YAML do objeto → Blender (passes) →
 * pixelizador → folha PNG + JSON em {@code assets/arte/} + manifesto gerado
 * {@code assets/manifest/arte.yaml}.
 *
 * <pre>
 *   renderArt  [--so=id,id] [--force]   renderiza o que mudou (cache por hash) e pixeliza
 *   pixelize   [--so=id,id]             só pixeliza passes já renderizados
 * </pre>
 * O Blender fica fora do build e da execução do jogo (RNF-006): os PNG gerados vão para o git.
 */
public final class RenderArt {
    static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    final Path raiz, blenderDir, renderDir, assetsDir;
    final Paleta paleta;

    RenderArt(Path raiz) {
        this.raiz = raiz;
        this.blenderDir = raiz.resolve("tools/blender");
        this.renderDir = raiz.resolve("build/render");
        this.assetsDir = raiz.resolve("assets");
        this.paleta = Paleta.carregar(raiz.resolve("tools/art/paleta.yaml"));
    }

    public static void main(String[] args) throws Exception {
        Path raiz = Path.of(args[0]).toAbsolutePath();
        String modo = args[1];
        Set<String> so = new TreeSet<>();
        boolean force = false;
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("--so=")) {
                for (String s : args[i].substring(5).split(",")) if (!s.isBlank()) so.add(s.trim());
            } else if (args[i].equals("--force")) force = true;
        }
        RenderArt r = new RenderArt(raiz);
        List<Objeto> objetos = r.objetos(so);
        if (objetos.isEmpty()) {
            System.out.println("Nada a fazer (nenhum objeto em tools/blender/**/*.yaml" + (so.isEmpty() ? "" : " com " + so) + ").");
            return;
        }
        if (modo.equals("render")) r.renderizar(objetos, force);
        r.pixelizarTudo(objetos);
        r.escreverManifesto();
    }

    /** Um objeto descrito em tools/blender/<pasta>/<id>.yaml. */
    record Objeto(String id, Path yaml, JsonNode desc) {}

    List<Objeto> objetos(Set<String> so) throws IOException {
        List<Objeto> lista = new ArrayList<>();
        try (Stream<Path> s = Files.walk(blenderDir)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".yaml")).sorted().toList()) {
                JsonNode d = YAML.readTree(p.toFile());
                if (d == null || !d.has("tipo")) continue;
                String id = d.path("id").asText(p.getFileName().toString().replace(".yaml", ""));
                if (!so.isEmpty() && !so.contains(id)) continue;
                if (d.path("spike").asBoolean(false) && !so.contains(id)) continue; // spikes só por nome
                lista.add(new Objeto(id, p, d));
            }
        }
        return lista;
    }

    // ------------------------------------------------------------------ Blender

    Path blender() throws IOException {
        String env = System.getenv("BLENDER");
        if (env != null && !env.isBlank()) return Path.of(env);
        Path lp = raiz.resolve("local.properties");
        if (Files.exists(lp)) {
            Properties pr = new Properties();
            try (var in = Files.newBufferedReader(lp)) {
                pr.load(in);
            }
            if (pr.getProperty("blender.path") != null) return Path.of(pr.getProperty("blender.path"));
        }
        return Path.of("C:/Program Files/Blender Foundation/Blender 5.3/blender.exe");
    }

    String hash(Objeto o) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(Files.readAllBytes(o.yaml()));
        try (Stream<Path> s = Files.walk(blenderDir)) {
            for (Path p : s.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".py") || f.toString().endsWith(".png"))
                    .sorted().toList()) {
                md.update(blenderDir.relativize(p).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                md.update(Files.readAllBytes(p));
            }
        }
        md.update(blender().toString().getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(md.digest());
    }

    void renderizar(List<Objeto> objetos, boolean force) throws Exception {
        Path exe = blender();
        if (!Files.exists(exe)) throw new IllegalStateException("Blender não encontrado: " + exe
                + " (defina BLENDER ou blender.path em local.properties)");
        Files.createDirectories(renderDir);
        int n = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Future<String>> fut = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        for (Objeto o : objetos) {
            fut.add(pool.submit(() -> renderizarUm(exe, o, force)));
        }
        List<String> erros = new ArrayList<>();
        for (Future<String> f : fut) {
            String e = f.get();
            if (e != null) erros.add(e);
        }
        pool.shutdown();
        System.out.printf("Render: %d objeto(s) em %.1f s%n", objetos.size(), (System.currentTimeMillis() - t0) / 1000.0);
        if (!erros.isEmpty()) throw new IllegalStateException("Falhou no Blender:\n" + String.join("\n", erros));
    }

    private String renderizarUm(Path exe, Objeto o, boolean force) throws Exception {
        Path pasta = renderDir.resolve(o.id());
        Path marca = pasta.resolve("hash.txt");
        String h = hash(o);
        if (!force && Files.exists(marca) && Files.readString(marca).equals(h) && Files.exists(pasta.resolve("render.json"))) {
            System.out.println("  " + o.id() + ": sem mudança (cache)");
            return null;
        }
        if (Files.exists(pasta)) {
            try (Stream<Path> s = Files.walk(pasta)) {
                for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
        Files.createDirectories(pasta);
        ObjectNode job = (ObjectNode) o.desc().deepCopy();
        job.put("id", o.id());
        job.put("saida", pasta.toString().replace('\\', '/'));
        job.put("texturas", blenderDir.resolve("texturas").toString().replace('\\', '/'));
        Path jobFile = renderDir.resolve(o.id() + ".json");
        JSON.writeValue(jobFile.toFile(), job);
        long t0 = System.currentTimeMillis();
        ProcessBuilder pb = new ProcessBuilder(exe.toString(), "-b", "--factory-startup", "-noaudio",
                "-P", blenderDir.resolve("entrada.py").toString(), "--", jobFile.toString())
                .redirectErrorStream(true).redirectOutput(pasta.resolve("blender.log").toFile());
        // spikes com extensão (MPFB2): extensões numa pasta isolada, nunca nas preferências do usuário
        if (o.desc().path("spike").asBoolean(false)) {
            pb.environment().put("BLENDER_USER_RESOURCES", raiz.resolve("build/blender-ext").toString());
        }
        Process pr = pb.start();
        int code = pr.waitFor();
        if (code != 0 || !Files.exists(pasta.resolve("render.json"))) {
            List<String> log = Files.readAllLines(pasta.resolve("blender.log"));
            return o.id() + " (código " + code + "):\n    "
                    + String.join("\n    ", log.subList(Math.max(0, log.size() - 25), log.size()));
        }
        Files.writeString(marca, h);
        System.out.printf("  %s: renderizado em %.1f s%n", o.id(), (System.currentTimeMillis() - t0) / 1000.0);
        return null;
    }

    // ------------------------------------------------------------------ pixelizador

    /** Entradas do manifesto geradas nesta rodada, por id de asset. */
    final Map<String, Map<String, Object>> manifesto = new LinkedHashMap<>();

    void pixelizarTudo(List<Objeto> objetos) throws IOException {
        Pixelizador px = new Pixelizador(paleta);
        for (Objeto o : objetos) {
            Path pasta = renderDir.resolve(o.id());
            Path rj = pasta.resolve("render.json");
            if (!Files.exists(rj)) {
                System.out.println("  " + o.id() + ": sem render (rode renderArt)");
                continue;
            }
            Montagem.montar(this, px, o, JSON.readTree(rj.toFile()), pasta);
        }
    }

    /** Lê o manifesto antigo para manter entradas de objetos que não foram tocados nesta rodada. */
    void escreverManifesto() throws IOException {
        Path arq = assetsDir.resolve("manifest/arte.yaml");
        Map<String, Object> todas = new LinkedHashMap<>();
        if (Files.exists(arq)) {
            JsonNode velho = YAML.readTree(arq.toFile());
            if (velho != null) for (JsonNode e : velho) todas.put(e.path("id").asText(), YAML.convertValue(e, Map.class));
        }
        todas.putAll(manifesto);
        StringBuilder sb = new StringBuilder();
        sb.append("# GERADO por `gradlew renderArt` / `gradlew pixelize` — não edite à mão.\n");
        sb.append("# Arte da Fase V: Blender (tools/blender) + pixelizador, densidade dobrada (D-086):\n");
        sb.append("# 1 px de arte = 2×2 px de mundo (scale: 2). Tem prioridade sobre provisorio.yaml.\n\n");
        String corpo = YAML.writeValueAsString(new ArrayList<>(new java.util.TreeMap<>(todas).values()));
        sb.append(corpo.startsWith("---") ? corpo.substring(corpo.indexOf('\n') + 1) : corpo);
        Files.writeString(arq, sb.toString());
        System.out.println("Manifesto: " + todas.size() + " entrada(s) em assets/manifest/arte.yaml");
    }

    static void escreverPng(BufferedImage img, Path destino) {
        try {
            Files.createDirectories(destino.getParent());
            ImageIO.write(img, "png", destino.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
