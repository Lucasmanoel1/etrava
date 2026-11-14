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
 *   renderArt  [--so=id,id] [--force] [--processos=N] [--dispositivo=HIP]   renderiza o que mudou (cache por hash) e pixeliza
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

    /** Quantos Blender ao mesmo tempo (0 = metade dos núcleos). */
    static int processos = 0;
    /** --dispositivo=HIP (sobrepõe blender.dispositivo do local.properties). */
    static String dispositivo = null;

    public static void main(String[] args) throws Exception {
        Path raiz = Path.of(args[0]).toAbsolutePath();
        String modo = args[1];
        Set<String> so = new TreeSet<>();
        boolean force = false;
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("--so=")) {
                for (String s : args[i].substring(5).split(",")) if (!s.isBlank()) so.add(s.trim());
            } else if (args[i].equals("--force")) force = true;
            else if (args[i].startsWith("--processos=")) processos = Integer.parseInt(args[i].substring(12).trim());
            else if (args[i].startsWith("--dispositivo=")) dispositivo = args[i].substring(14).trim();
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
                // ficha ainda sem kit para ela (V2): só por nome, para não trocar a folha em uso
                if (d.path("rascunho").asBoolean(false) && !so.contains(id)) continue;
                lista.add(new Objeto(id, p, d));
            }
        }
        return lista;
    }

    // ------------------------------------------------------------------ Blender

    /** Configuração desta máquina (local.properties, fora do git): blender.path, blender.dispositivo, blender.processos. */
    String local(String chave) throws IOException {
        Path lp = raiz.resolve("local.properties");
        if (!Files.exists(lp)) return null;
        Properties pr = new Properties();
        try (var in = Files.newBufferedReader(lp)) {
            pr.load(in);
        }
        String v = pr.getProperty(chave);
        return v == null || v.isBlank() ? null : v.trim();
    }

    Path blender() throws IOException {
        String env = System.getenv("BLENDER");
        if (env != null && !env.isBlank()) return Path.of(env);
        if (local("blender.path") != null) return Path.of(local("blender.path"));
        return Path.of("C:/Program Files/Blender Foundation/Blender 5.3/blender.exe");
    }

    /**
     * Dispositivo do Cycles (D-106): CPU (padrão), HIP (AMD), CUDA/OPTIX (NVIDIA)... Vai na linha de
     * comando (depois do "--", onde o Cycles o lê) e não entra na chave do cache. Numa mesma
     * máquina o resultado se repete (o ruído de 1/65535 entre rodadas é o mesmo da CPU).
     */
    String dispositivo() throws IOException {
        if (dispositivo != null) return dispositivo;
        return local("blender.dispositivo");
    }

    /**
     * Chave do cache: o YAML do objeto, só os scripts que ele de fato usa (entrada.py, a tarefa do
     * tipo e o que ela importa, transitivamente), as texturas e o caminho do Blender. Antes eram
     * todos os .py da pasta, e mexer no kit humano refazia as árvores (D-105).
     */
    String hash(Objeto o) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(Files.readAllBytes(o.yaml()));
        java.util.SortedSet<Path> usados = new TreeSet<>(scripts(o.desc().path("tipo").asText()));
        try (Stream<Path> s = Files.walk(blenderDir)) {
            s.filter(Files::isRegularFile).filter(f -> f.toString().endsWith(".png")).forEach(usados::add);
        }
        for (Path p : usados) {
            md.update(blenderDir.relativize(p).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
            md.update(Files.readAllBytes(p));
        }
        md.update(blender().toString().getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(md.digest());
    }

    /** Chave do esquema antigo (todos os .py), só para migrar as marcas sem renderizar de novo. */
    String hashAntigo(Objeto o) throws Exception {
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

    private static final java.util.regex.Pattern IMPORT = java.util.regex.Pattern.compile(
            "^\\s*(?:from\\s+([\\w.]+)\\s+import\\s+([\\w, ]+)|import\\s+([\\w.]+))", java.util.regex.Pattern.MULTILINE);

    /** entrada.py + tarefas/<tipo>.py + os módulos locais que eles importam, transitivamente. */
    Set<Path> scripts(String tipo) throws IOException {
        Set<Path> vistos = new TreeSet<>();
        java.util.ArrayDeque<Path> fila = new java.util.ArrayDeque<>();
        fila.add(blenderDir.resolve("entrada.py"));
        fila.add(blenderDir.resolve("tarefas/" + tipo + ".py"));
        while (!fila.isEmpty()) {
            Path p = fila.poll();
            if (!Files.isRegularFile(p) || !vistos.add(p)) continue;
            Path pacote = p.getParent();
            if (Files.isRegularFile(pacote.resolve("__init__.py"))) fila.add(pacote.resolve("__init__.py"));
            var m = IMPORT.matcher(Files.readString(p));
            while (m.find()) {
                if (m.group(1) != null) {
                    String mod = m.group(1);
                    // entrada.py despacha por tipo: as outras tarefas não entram na chave
                    if (p.getFileName().toString().equals("entrada.py") && mod.equals("tarefas")) continue;
                    Path base = mod.equals(".") ? pacote : blenderDir.resolve(mod.replace('.', '/'));
                    if (mod.startsWith(".") && !mod.equals(".")) base = pacote.resolve(mod.substring(1).replace('.', '/'));
                    fila.add(base.resolveSibling(base.getFileName() + ".py"));
                    fila.add(base.resolve("__init__.py"));
                    for (String nome : m.group(2).split(",")) {
                        nome = nome.trim().split("\\s+")[0];
                        if (!nome.isEmpty()) fila.add(base.resolve(nome + ".py"));
                    }
                } else {
                    Path base = blenderDir.resolve(m.group(3).replace('.', '/'));
                    fila.add(base.resolveSibling(base.getFileName() + ".py"));
                    fila.add(base.resolve("__init__.py"));
                }
            }
        }
        return vistos;
    }

    void renderizar(List<Objeto> objetos, boolean force) throws Exception {
        Path exe = blender();
        if (!Files.exists(exe)) throw new IllegalStateException("Blender não encontrado: " + exe
                + " (defina BLENDER ou blender.path em local.properties)");
        Files.createDirectories(renderDir);
        // cada Blender ocupa ~0,4 GB; com pouca memória, -Pprocessos=N limita quantos rodam juntos
        if (processos <= 0 && local("blender.processos") != null) processos = Integer.parseInt(local("blender.processos"));
        List<String> vagas = vagas();
        // cada objeto vai sempre para o mesmo dispositivo (pelo id): CPU e GPU dão ruído diferente
        // na oclusão, e refazer do zero tem de dar os mesmos bytes (VT-16)
        Map<String, ExecutorService> pools = new LinkedHashMap<>();
        for (String d : new java.util.LinkedHashSet<>(vagas))
            pools.put(d, Executors.newFixedThreadPool((int) vagas.stream().filter(d::equals).count()));
        System.out.println("Render: " + vagas.size() + " Blender ao mesmo tempo " + vagas);
        List<Future<String>> fut = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        for (Objeto o : objetos) {
            String d = vagas.get(Math.floorMod(o.id().hashCode(), vagas.size()));
            fut.add(pools.get(d).submit(() -> renderizarUm(exe, o, force, d)));
        }
        List<String> erros = new ArrayList<>();
        for (Future<String> f : fut) {
            String e = f.get();
            if (e != null) erros.add(e);
        }
        pools.values().forEach(ExecutorService::shutdown);
        System.out.printf("Render: %d objeto(s) em %.1f s%n", objetos.size(), (System.currentTimeMillis() - t0) / 1000.0);
        if (!erros.isEmpty()) throw new IllegalStateException("Falhou no Blender:\n" + String.join("\n", erros));
    }

    /**
     * Uma vaga por Blender simultâneo, cada uma com seu dispositivo (D-106). blender.dispositivo
     * pode ser um só ("HIP": todas as vagas na GPU) ou uma lista ("HIP,HIP,CPU": duas na GPU, uma na
     * CPU). Os dois juntos no mesmo Blender ("HIP+CPU") não ganham nada nos quadros pequenos: a CPU
     * segura a GPU.
     */
    List<String> vagas() throws IOException {
        String d = dispositivo();
        List<String> l = new ArrayList<>();
        if (d != null && d.contains(",")) {
            for (String x : d.split(",")) if (!x.isBlank()) l.add(x.trim().toUpperCase());
            if (processos > 0) while (l.size() > processos) l.remove(l.size() - 1);
            return l;
        }
        int n = processos > 0 ? processos : Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        for (int i = 0; i < n; i++) l.add(d == null ? "CPU" : d.trim().toUpperCase());
        return l;
    }

    private String renderizarUm(Path exe, Objeto o, boolean force, String disp) throws Exception {
        Path pasta = renderDir.resolve(o.id());
        Path marca = pasta.resolve("hash.txt");
        String h = hash(o);
        if (!force && Files.exists(marca) && Files.exists(pasta.resolve("render.json"))
                && !Files.readString(marca).equals(h) && Files.readString(marca).equals(hashAntigo(o))) {
            Files.writeString(marca, h);  // migração da chave (D-105): o render é o mesmo
            System.out.println("  " + o.id() + ": chave do cache migrada");
        }
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
        List<String> cmd = new ArrayList<>(List.of(exe.toString(), "-b", "--factory-startup", "-noaudio",
                "-P", blenderDir.resolve("entrada.py").toString(), "--", jobFile.toString()));
        if (!disp.equals("CPU")) cmd.addAll(List.of("--cycles-device", disp));
        ProcessBuilder pb = new ProcessBuilder(cmd)
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
        System.out.printf("  %s: renderizado em %.1f s (%s)%n", o.id(), (System.currentTimeMillis() - t0) / 1000.0, disp);
        return null;
    }

    // ------------------------------------------------------------------ pixelizador

    /** Entradas do manifesto geradas nesta rodada, por id de asset. */
    final Map<String, Map<String, Object>> manifesto = new LinkedHashMap<>();

    void pixelizarTudo(List<Objeto> objetos) throws IOException, InterruptedException {
        Pixelizador px = new Pixelizador(paleta, Pinceis.carregar(raiz.resolve("tools/art/pinceis/folhas.pxo")));
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
