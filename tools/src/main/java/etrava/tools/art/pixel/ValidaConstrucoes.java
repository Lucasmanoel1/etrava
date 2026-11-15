package etrava.tools.art.pixel;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * VT-07 (SPEC-visual §9.4): a ficha de cada construção ({@code tools/blender/construcoes/*.yaml})
 * contra o jogo. A arte nunca muda a colisão:
 * <ul>
 *   <li>largura × profundidade da ficha = pegada do {@code content/props.yaml};</li>
 *   <li>o corpo (paredes) cabe dentro da pegada, em todas as variantes;</li>
 *   <li>em cada lugar do mapa onde a construção está, a porta desenhada cai a no máximo
 *       {@value #FOLGA} m de um ponto do mapa (a porta do jogo: {@code *_porta}, {@code casa_*}).</li>
 * </ul>
 */
public final class ValidaConstrucoes {
    private ValidaConstrucoes() {}

    /** Distância máxima, em metros, entre a porta desenhada (na borda da pegada) e o ponto do mapa. */
    static final double FOLGA = 1.3;

    public static List<String> validar(Path content, Path fichas) {
        List<String> erros = new ArrayList<>();
        if (!Files.isDirectory(fichas)) return erros;
        try {
            Map<String, double[]> pegadas = new HashMap<>();
            for (JsonNode p : RenderArt.YAML.readTree(content.resolve("props.yaml").toFile()))
                if (p.has("footprint")) pegadas.put(p.path("id").asText(),
                        new double[] {p.path("footprint").get(0).asDouble(), p.path("footprint").get(1).asDouble()});
            List<JsonNode> mapas = new ArrayList<>();
            try (Stream<Path> s = Files.list(content.resolve("areas"))) {
                for (Path m : s.filter(f -> f.toString().endsWith(".tmj")).sorted().toList())
                    mapas.add(RenderArt.JSON.readTree(m.toFile()));
            }
            try (Stream<Path> s = Files.list(fichas)) {
                for (Path f : s.filter(x -> x.toString().endsWith(".yaml")).sorted().toList()) {
                    JsonNode d = RenderArt.YAML.readTree(f.toFile());
                    if (!"casa".equals(d.path("tipo").asText())) continue;
                    ficha(d, pegadas, mapas, erros);
                }
            }
        } catch (IOException e) {
            erros.add("VT-07: não deu para ler fichas ou mapas: " + e);
        }
        return erros;
    }

    static void ficha(JsonNode d, Map<String, double[]> pegadas, List<JsonNode> mapas, List<String> erros) {
        String id = d.path("id").asText();
        double w = d.path("largura").asDouble(), dp = d.path("profundidade").asDouble();
        double[] pg = pegadas.get(id);
        if (pg == null) {
            erros.add(id + ": construção sem prop em content/props.yaml (VT-07)");
            return;
        }
        if (Math.abs(pg[0] - w) > 1e-6 || Math.abs(pg[1] - dp) > 1e-6)
            erros.add(String.format("%s: ficha %.1f × %.1f m, pegada do mapa %.1f × %.1f m (VT-07)", id, w, dp, pg[0], pg[1]));
        List<JsonNode> variantes = new ArrayList<>();
        variantes.add(d);
        d.path("variantes").forEach(variantes::add);
        for (JsonNode v : variantes) {
            String nome = id + (v.has("nome") && v != d ? "_" + v.path("nome").asText() : "");
            JsonNode c = v.has("corpo") ? v.get("corpo") : d.path("corpo");
            double cw = c.size() > 0 ? c.get(0).asDouble() : w, cd = c.size() > 1 ? c.get(1).asDouble() : dp;
            double cx = c.size() > 2 ? c.get(2).asDouble() : 0, cy = c.size() > 3 ? c.get(3).asDouble() : 0;
            if (cx - cw / 2 < -w / 2 - 1e-6 || cx + cw / 2 > w / 2 + 1e-6 || cy - cd / 2 < -dp / 2 - 1e-6
                    || cy + cd / 2 > dp / 2 + 1e-6)
                erros.add(nome + ": o corpo sai da pegada (VT-07)");
            JsonNode abs = v.has("aberturas") ? v.get("aberturas") : d.path("aberturas");
            double[] porta = null;
            for (JsonNode a : abs) {
                if (!a.path("porta").asBoolean(false)) continue;
                double pos = a.path("pos").asDouble();
                // a porta projetada na borda da pegada (o alpendre fica entre a parede e a borda)
                porta = "S".equals(a.path("face").asText()) ? new double[] {cx - cw / 2 + pos, dp / 2}
                        : new double[] {w / 2, cy + cd / 2 - pos};
                break;
            }
            if (porta == null) {
                erros.add(nome + ": construção sem porta (VT-07)");
                continue;
            }
            for (JsonNode m : mapas) {
                double tile = m.path("tileheight").asDouble(64);
                List<double[]> pontos = new ArrayList<>();
                List<double[]> lugares = new ArrayList<>();
                for (JsonNode l : m.path("layers")) {
                    for (JsonNode o : l.path("objects")) {
                        double[] xy = {o.path("x").asDouble() / tile, o.path("y").asDouble() / tile};
                        if ("spots".equals(l.path("name").asText())) pontos.add(xy);
                        else if ("props".equals(l.path("name").asText()) && id.equals(o.path("name").asText())) lugares.add(xy);
                    }
                }
                for (double[] lg : lugares) {
                    double melhor = Double.MAX_VALUE;
                    for (double[] p : pontos) melhor = Math.min(melhor, Math.hypot(p[0] - lg[0] - porta[0], p[1] - lg[1] - porta[1]));
                    if (melhor > FOLGA)
                        erros.add(String.format("%s em (%.1f, %.1f): a porta desenhada fica a %.1f m do ponto de porta mais próximo do mapa (VT-07)",
                                nome, lg[0], lg[1], melhor));
                }
            }
        }
    }
}
