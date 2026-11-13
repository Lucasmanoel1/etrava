package etrava.tools.art.pixel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Projeto do Pixelorama (.pxo, v1.x): ZIP com {@code data.json} e cada cel em RGBA8 cru,
 * {@code image_data/frames/<quadro+1>/layer_<camada+1>} (D-100). É o formato-fonte do acabamento:
 * a arte sai do pipeline em camadas (sombra, tronco, folhagem, contorno, retoque) e o Pixelorama
 * abre, edita e exporta. Escrita determinística (datas zeradas): o mesmo conteúdo dá os mesmos bytes.
 */
public final class Pxo {
    /** Versão do formato gravada: a partir da 8 o Pixelorama não aplica os caminhos legados. */
    static final int VERSAO = 8;
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Uma camada: um quadro ARGB (largura × altura) por quadro do projeto. */
    public record Camada(String nome, boolean visivel, List<int[]> quadros) {}

    public record Projeto(int largura, int altura, double fps, List<Camada> camadas) {
        public int quadros() { return camadas.isEmpty() ? 0 : camadas.get(0).quadros().size(); }

        public Camada camada(String nome) {
            for (Camada c : camadas) if (c.nome().equals(nome)) return c;
            return null;
        }

        /** As camadas visíveis compostas (alfa por cima, de baixo para cima) no quadro f. */
        public int[] composto(int f) {
            int[] out = new int[largura * altura];
            for (Camada c : camadas) {
                if (!c.visivel()) continue;
                int[] src = c.quadros().get(f);
                for (int i = 0; i < out.length; i++) out[i] = sobre(src[i], out[i]);
            }
            return out;
        }
    }

    private Pxo() {}

    /** "a sobre b", alfa direto (como o Pixelorama compõe camadas normais). */
    static int sobre(int a, int b) {
        int aa = a >>> 24;
        if (aa == 255 || (b >>> 24) == 0) return aa == 0 ? b : a;
        if (aa == 0) return b;
        double fa = aa / 255.0, fb = (b >>> 24) / 255.0, fo = fa + fb * (1 - fa);
        int r = (int) Math.round((((a >> 16) & 255) * fa + ((b >> 16) & 255) * fb * (1 - fa)) / fo);
        int g = (int) Math.round((((a >> 8) & 255) * fa + ((b >> 8) & 255) * fb * (1 - fa)) / fo);
        int bl = (int) Math.round(((a & 255) * fa + (b & 255) * fb * (1 - fa)) / fo);
        return ((int) Math.round(fo * 255) << 24) | (r << 16) | (g << 8) | bl;
    }

    /** Marca de animação no Pixelorama: quadros [de, ate], contados a partir de 1. */
    public record Tag(String nome, int de, int ate) {}

    public static void escrever(Path destino, Projeto p, String tag) throws IOException {
        escrever(destino, p, tag == null || p.quadros() == 0 ? List.of() : List.of(new Tag(tag, 1, p.quadros())));
    }

    public static void escrever(Path destino, Projeto p, List<Tag> marcas) throws IOException {
        ObjectNode d = JSON.createObjectNode();
        d.put("pixelorama_version", "v1.2.4");
        d.put("pxo_version", VERSAO);
        d.put("size_x", p.largura());
        d.put("size_y", p.altura());
        d.put("color_mode", 5);                      // Image.FORMAT_RGBA8
        ArrayNode camadas = d.putArray("layers");
        for (Camada c : p.camadas()) {
            ObjectNode l = camadas.addObject();
            l.put("name", c.nome());
            l.put("type", 0);
            l.put("visible", c.visivel());
            l.put("locked", false);
            l.put("blend_mode", 0);
            l.put("clipping_mask", false);
            l.put("opacity", 1.0);
            l.put("parent", -1);
            l.putArray("effects");
            l.put("new_cels_linked", false);
        }
        ArrayNode quadros = d.putArray("frames");
        for (int f = 0; f < p.quadros(); f++) {
            ObjectNode q = quadros.addObject();
            ArrayNode cels = q.putArray("cels");
            for (int k = 0; k < p.camadas().size(); k++) {
                ObjectNode cel = cels.addObject();
                cel.put("opacity", 1.0);
                cel.put("z_index", 0);
                cel.put("offset", "(0, 0)");
                cel.put("image_size", "Vector2i(" + p.largura() + ", " + p.altura() + ")");
                cel.putObject("metadata");
            }
            q.put("duration", 1.0);
            q.putObject("metadata");
        }
        ArrayNode tags = d.putArray("tags");
        for (Tag m : marcas) {
            ObjectNode t = tags.addObject();
            t.put("name", m.nome());
            t.put("color", "ffffff");
            t.put("from", m.de());
            t.put("to", m.ate());
        }
        d.put("fps", p.fps());
        d.put("current_frame", 0);
        d.put("current_layer", Math.max(0, p.camadas().size() - 1));
        Files.createDirectories(destino.toAbsolutePath().getParent());
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(destino))) {
            entrada(z, "mimetype", "application/x-pixelorama".getBytes(StandardCharsets.US_ASCII));
            entrada(z, "data.json", JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(d));
            for (int f = 0; f < p.quadros(); f++) {
                for (int k = 0; k < p.camadas().size(); k++) {
                    int[] px = p.camadas().get(k).quadros().get(f);
                    byte[] b = new byte[px.length * 4];
                    for (int i = 0; i < px.length; i++) {
                        int c = px[i];
                        b[i * 4] = (byte) (c >> 16);
                        b[i * 4 + 1] = (byte) (c >> 8);
                        b[i * 4 + 2] = (byte) c;
                        b[i * 4 + 3] = (byte) (c >>> 24);
                    }
                    entrada(z, "image_data/frames/" + (f + 1) + "/layer_" + (k + 1), b);
                }
            }
        }
    }

    private static void entrada(ZipOutputStream z, String nome, byte[] b) throws IOException {
        ZipEntry e = new ZipEntry(nome);
        e.setTime(315532800000L);   // 1980-01-01: bytes iguais a cada geração
        z.putNextEntry(e);
        z.write(b);
        z.closeEntry();
    }

    private static final Pattern PAR = Pattern.compile("\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)");

    /** Lê um .pxo (gravado por este código ou pelo próprio Pixelorama). Só camadas de pixel. */
    public static Projeto ler(Path arq) throws IOException {
        Map<String, byte[]> arquivos = new HashMap<>();
        try (InputStream in = Files.newInputStream(arq); ZipInputStream z = new ZipInputStream(in)) {
            for (ZipEntry e; (e = z.getNextEntry()) != null;) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                z.transferTo(bo);
                arquivos.put(e.getName(), bo.toByteArray());
            }
        }
        byte[] dj = arquivos.get("data.json");
        if (dj == null) throw new IOException(arq + ": não é um .pxo v1 (sem data.json)");
        JsonNode d = JSON.readTree(dj);
        int w = d.path("size_x").asInt(64), h = d.path("size_y").asInt(64);
        List<Camada> camadas = new ArrayList<>();
        JsonNode ls = d.path("layers"), fs = d.path("frames");
        for (int k = 0; k < ls.size(); k++) {
            List<int[]> qs = new ArrayList<>();
            for (int f = 0; f < fs.size(); f++) {
                int[] px = new int[w * h];
                byte[] b = arquivos.get("image_data/frames/" + (f + 1) + "/layer_" + (k + 1));
                if (b != null) {
                    JsonNode cel = fs.get(f).path("cels").path(k);
                    int cw = w, ch = h, ox = 0, oy = 0;
                    Matcher m = PAR.matcher(cel.path("image_size").asText(""));
                    if (m.find()) { cw = Integer.parseInt(m.group(1)); ch = Integer.parseInt(m.group(2)); }
                    m = PAR.matcher(cel.path("offset").asText(""));
                    if (m.find()) { ox = Integer.parseInt(m.group(1)); oy = Integer.parseInt(m.group(2)); }
                    double op = cel.path("opacity").asDouble(1.0) * ls.get(k).path("opacity").asDouble(1.0);
                    for (int y = 0; y < ch; y++) {
                        for (int x = 0; x < cw; x++) {
                            int i = (y * cw + x) * 4;
                            if (i + 3 >= b.length) continue;
                            int tx = x + ox, ty = y + oy;
                            if (tx < 0 || ty < 0 || tx >= w || ty >= h) continue;
                            int a = (int) Math.round((b[i + 3] & 255) * op);
                            px[ty * w + tx] = a == 0 ? 0 : (a << 24) | ((b[i] & 255) << 16) | ((b[i + 1] & 255) << 8) | (b[i + 2] & 255);
                        }
                    }
                }
                qs.add(px);
            }
            camadas.add(new Camada(ls.get(k).path("name").asText("camada " + (k + 1)), ls.get(k).path("visible").asBoolean(true), qs));
        }
        return new Projeto(w, h, d.path("fps").asDouble(6.0), camadas);
    }
}
