package etrava.tools.art.pixel;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pixeliza os quadros de um render e monta as folhas (SPEC-visual §5.2.8): todos os quadros de um
 * objeto são cortados à menor caixa comum (a âncora acompanha), as linhas são as direções e as
 * colunas os quadros das animações, em sequência. Grava PNG (+ brilho) e JSON com os pontos, e
 * deixa a entrada do manifesto pronta.
 */
final class Montagem {
    private Montagem() {}

    record Quadro(String anim, int dir, int f, Pixelizador.Resultado r, JsonNode pontos) {}

    static void montar(RenderArt ra, Pixelizador px, RenderArt.Objeto o, JsonNode rj, Path pasta) throws IOException {
        JsonNode desc = o.desc();
        int ss = rj.path("ss").asInt(4);
        Map<Integer, Pixelizador.Material> mats = new HashMap<>();
        for (JsonNode m : rj.path("materiais")) {
            int[] tons = null;
            if (m.hasNonNull("tons")) tons = new int[] {m.get("tons").get(0).asInt(), m.get("tons").get(1).asInt()};
            mats.put(m.path("indice").asInt(), new Pixelizador.Material(m.path("indice").asInt(), m.path("nome").asText(),
                    m.path("rampa").asText(), m.path("k").asDouble(), m.path("pontilhar").asBoolean(),
                    m.path("emissivo").asBoolean(), m.path("contorno").asBoolean(true), m.path("luz_min").asDouble(),
                    m.path("oa").asDouble(1.0), tons, m.hasNonNull("rampa_brilho") ? m.get("rampa_brilho").asText() : null,
                    m.path("linhas").asBoolean(true)));
        }
        JsonNode pp = desc.path("pixelizador");
        Pixelizador.Parametros def = Pixelizador.Parametros.padrao();
        Pixelizador.Parametros par = new Pixelizador.Parametros(ss, pp.path("ambiente").asDouble(def.ambiente()),
                pp.path("difusa").asDouble(def.difusa()), pp.path("limiar_profundidade").asDouble(0.25) / 40.0, pp.path("limpar").asBoolean(true),
                pp.path("contorno_base").asBoolean(true) ? def.contornoBase() : 0);
        int olho = ra.paleta.rampa("olho")[0];
        // decalques: olhos sempre; outros pontos (óculos, fivela) com a cor pedida no YAML
        Map<String, Integer> decalques = new LinkedHashMap<>();
        decalques.put("olho_e", olho);
        decalques.put("olho_d", olho);
        pp.path("decalques").fields().forEachRemaining(e -> {
            int argb = 0xFF000000 | Integer.parseInt(e.getValue().asText(), 16);
            if (!ra.paleta.contem(argb)) throw new IllegalStateException(o.id() + ": decalque fora da paleta: " + e.getValue().asText());
            decalques.put(e.getKey(), argb);
        });

        // pixeliza cada quadro de cada folha
        Map<String, List<Quadro>> porFolha = new LinkedHashMap<>();
        for (JsonNode folha : rj.path("folhas")) {
            List<Quadro> qs = new ArrayList<>();
            for (JsonNode q : folha.path("quadros")) {
                String arq = q.path("arquivo").asText();
                Passes passes = Passes.ler(pasta.resolve(arq + "_a.png"), pasta.resolve(arq + "_b.png"));
                List<Pixelizador.Decalque> dec = new ArrayList<>();
                for (Map.Entry<String, Integer> d : decalques.entrySet()) {
                    JsonNode p = q.path("pontos").path(d.getKey());
                    if (p.isMissingNode() || !p.path("visivel").asBoolean()) continue;
                    dec.add(new Pixelizador.Decalque((int) Math.floor(p.path("x").asDouble()),
                            (int) Math.floor(p.path("y").asDouble()), d.getValue()));
                }
                qs.add(new Quadro(q.path("anim").asText(), q.path("dir").asInt(), q.path("f").asInt(),
                        px.pixelizar(passes, mats, par, dec), q.path("pontos")));
            }
            porFolha.put(folha.path("nome").asText(), qs);
        }

        // caixa comum a todos os quadros de todas as folhas do objeto
        int w = rj.path("largura").asInt(), h = rj.path("altura").asInt();
        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        boolean recortar = desc.path("recortar").asBoolean(true);
        if (recortar) {
            for (List<Quadro> qs : porFolha.values()) {
                for (Quadro q : qs) {
                    BufferedImage img = q.r().cor();
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            if ((img.getRGB(x, y) >>> 24) == 0) continue;
                            x0 = Math.min(x0, x);
                            y0 = Math.min(y0, y);
                            x1 = Math.max(x1, x);
                            y1 = Math.max(y1, y);
                        }
                    }
                }
            }
        }
        if (!recortar || x1 < 0) {
            x0 = 0;
            y0 = 0;
            x1 = w - 1;
            y1 = h - 1;
        }
        int fw = x1 - x0 + 1, fh = y1 - y0 + 1;
        double ax = rj.path("ancora").get(0).asDouble() - x0, ay = rj.path("ancora").get(1).asDouble() - y0;

        String tipo = desc.path("tipo").asText();
        String sub = switch (tipo) {
            case "personagem" -> "personagens";
            case "chao" -> "chao";
            case "decoracao" -> "decoracao";
            case "casa" -> "construcoes";
            case "spike_mpfb" -> "spike";
            default -> "natureza";
        };
        Map<String, String> arquivos = new LinkedHashMap<>(), brilhos = new LinkedHashMap<>();
        Map<String, Object> anims = new LinkedHashMap<>();
        Map<String, Map<String, Object>> animsPorFolha = new LinkedHashMap<>();
        int direcoes = 1;
        for (JsonNode folha : rj.path("folhas")) {
            String nome = folha.path("nome").asText();
            List<Quadro> qs = porFolha.get(nome);
            // colunas: animações em sequência; linhas: direções
            Map<String, int[]> inicio = new LinkedHashMap<>();
            int col = 0;
            direcoes = 1;
            anims.clear();
            for (JsonNode a : folha.path("animacoes")) {
                int nq = a.path("quadros").asInt(1);
                inicio.put(a.path("nome").asText(), new int[] {col, nq});
                direcoes = Math.max(direcoes, a.path("direcoes").asInt(1));
                Map<String, Object> am = new LinkedHashMap<>();
                am.put("start", col);
                am.put("frames", nq);
                am.put("fps", a.path("fps").asDouble(8));
                if (a.hasNonNull("passada")) am.put("stride", a.get("passada").asDouble());
                anims.put(a.path("nome").asText(), am);
                col += nq;
            }
            animsPorFolha.put(nome, new LinkedHashMap<>(anims));
            BufferedImage folhaImg = new BufferedImage(fw * col, fh * direcoes, BufferedImage.TYPE_INT_ARGB);
            BufferedImage brilhoImg = null;
            Map<String, Object> pontos = new LinkedHashMap<>();
            for (Quadro q : qs) {
                int[] ini = inicio.get(q.anim());
                int cx = (ini[0] + q.f()) * fw, cy = q.dir() * fh;
                copiar(q.r().cor(), x0, y0, fw, fh, folhaImg, cx, cy);
                if (q.r().brilho() != null) {
                    if (brilhoImg == null) brilhoImg = new BufferedImage(folhaImg.getWidth(), folhaImg.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    copiar(q.r().brilho(), x0, y0, fw, fh, brilhoImg, cx, cy);
                }
                if (q.pontos() != null && q.pontos().size() > 0) {
                    Map<String, Object> pq = new LinkedHashMap<>();
                    final int ox = x0, oy = y0;
                    q.pontos().fields().forEachRemaining(e -> pq.put(e.getKey(), List.of(
                            Math.round((e.getValue().path("x").asDouble() - ox) * 10) / 10.0,
                            Math.round((e.getValue().path("y").asDouble() - oy) * 10) / 10.0)));
                    pontos.put(q.anim() + "/" + q.dir() + "/" + q.f(), pq);
                }
            }
            String rel = "arte/" + sub + "/" + nome + ".png";
            RenderArt.escreverPng(folhaImg, ra.assetsDir.resolve(rel));
            arquivos.put(nome, rel);
            if (brilhoImg != null) {
                String relB = "arte/" + sub + "/" + nome + "_brilho.png";
                RenderArt.escreverPng(brilhoImg, ra.assetsDir.resolve(relB));
                brilhos.put(nome, relB);
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("frame", List.of(fw, fh));
            meta.put("anchor", List.of(ax, ay));
            meta.put("directions", direcoes);
            meta.put("animations", new LinkedHashMap<>(anims));
            meta.put("points", pontos);
            RenderArt.JSON.writeValue(ra.assetsDir.resolve("arte/" + sub + "/" + nome + ".json").toFile(), meta);
        }

        // entrada(s) do manifesto
        String fonte = "Blender (tools/blender/" + ra.blenderDir.relativize(o.yaml()).toString().replace('\\', '/')
                + ") + pixelizador";
        JsonNode man = desc.path("manifesto");
        List<JsonNode> entradas = new ArrayList<>();
        if (man.isArray()) man.forEach(entradas::add);
        else if (!man.isMissingNode()) entradas.add(man);
        for (JsonNode e : entradas) {
            Map<String, Object> m = new LinkedHashMap<>();
            String id = e.path("id").asText();
            m.put("id", id);
            List<String> folhas = new ArrayList<>();
            if (e.has("folhas")) e.get("folhas").forEach(f -> folhas.add(f.asText()));
            else folhas.addAll(arquivos.keySet());
            String primeira = arquivos.get(folhas.get(0));
            if (primeira == null) throw new IllegalStateException(o.id() + ": folha desconhecida no manifesto: " + folhas);
            anims = animsPorFolha.get(folhas.get(0));
            switch (tipo) {
                case "personagem" -> {
                    m.put("type", "spritesheet");
                    m.put("file", primeira);
                    m.put("meta", primeira.replace(".png", ".json"));
                    m.put("frame", List.of(fw, fh));
                    m.put("anchor", List.of(ax, ay));
                    m.put("directions", direcoes);
                    m.put("animations", new LinkedHashMap<>(anims));
                    if (e.has("papel")) m.put("role", e.get("papel").asText());
                }
                case "chao" -> {
                    m.put("type", "tile");
                    m.put("file", primeira);
                    if (e.has("prioridade")) m.put("priority", e.get("prioridade").asInt());
                }
                default -> {
                    m.put("type", "sprite");
                    m.put("file", primeira);
                    if (folhas.size() > 1) m.put("variants", folhas.stream().map(arquivos::get).toList());
                    m.put("anchor", List.of(ax, ay));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> primeiraAnim = anims.isEmpty() ? null : (Map<String, Object>) anims.values().iterator().next();
                    if (primeiraAnim != null && ((Integer) primeiraAnim.get("frames")) > 1) {
                        m.put("frames", primeiraAnim.get("frames"));
                        m.put("fps", primeiraAnim.get("fps"));
                    }
                    if (brilhos.containsKey(folhas.get(0))) m.put("emissive", brilhos.get(folhas.get(0)));
                    m.put("meta", primeira.replace(".png", ".json"));
                }
            }
            m.put("scale", 2);
            m.put("source", fonte);
            m.put("license", "proprio");
            ra.manifesto.put(id, m);
        }
        System.out.printf("  %s: %d folha(s), quadro %d×%d, âncora (%.0f, %.0f)%n", o.id(), arquivos.size(), fw, fh, ax, ay);
    }

    private static void copiar(BufferedImage src, int x0, int y0, int w, int h, BufferedImage dst, int dx, int dy) {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int sx = x0 + x, sy = y0 + y;
                if (sx < src.getWidth() && sy < src.getHeight()) dst.setRGB(dx + x, dy + y, src.getRGB(sx, sy));
            }
    }
}
