package etrava.client.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * O que o jogador veste muda o desenho (SPEC-visual §6.4, V2), lido de {@code assets/config/jogador.yaml}:
 * a roupa do corpo escolhe a folha; o item de cabeça, uma camada desenhada por cima no mesmo
 * quadro. Pura apresentação: o equipamento continua na simulação.
 */
public final class Vestimenta {
    private static final Logger LOG = LoggerFactory.getLogger(Vestimenta.class);
    private final Map<String, String> corpo = new HashMap<>(), cabeca = new HashMap<>(), maos = new HashMap<>();

    public static Vestimenta carregar(Path assetsDir) {
        Vestimenta v = new Vestimenta();
        Path f = assetsDir.resolve("config/jogador.yaml");
        if (!Files.exists(f)) return v;
        try {
            v.ler(new ObjectMapper(new YAMLFactory()).readTree(f.toFile()));
        } catch (Exception e) {
            LOG.warn("jogador.yaml ilegível: {} — o jogador fica com a folha base", e.getMessage());
        }
        return v;
    }

    static Vestimenta de(String yaml) throws Exception {
        Vestimenta v = new Vestimenta();
        v.ler(new ObjectMapper(new YAMLFactory()).readTree(yaml));
        return v;
    }

    private void ler(JsonNode raiz) {
        raiz.path("corpo").properties().forEach(e -> corpo.put(e.getKey(), e.getValue().asText()));
        raiz.path("cabeca").properties().forEach(e -> cabeca.put(e.getKey(), e.getValue().asText()));
        raiz.path("maos").properties().forEach(e -> maos.put(e.getKey(), e.getValue().asText()));
    }

    /** A folha do corpo para o que está no slot {@code body}; a base se não houver folha para ele. */
    public String corpo(Map<String, String> equipamento, String base, Predicate<String> existe) {
        String id = equipamento == null ? null : corpo.get(equipamento.get("body"));
        return id != null && existe.test(id) ? id : base;
    }

    /** A camada de cabeça (ou null) para o que está no slot {@code head}. */
    public String cabeca(Map<String, String> equipamento, Predicate<String> existe) {
        String id = equipamento == null ? null : cabeca.get(equipamento.get("head"));
        return id != null && existe.test(id) ? id : null;
    }

    /**
     * As camadas desenhadas por cima do corpo, nesta ordem (V3, SPEC-visual §6.4): cabeça, mão
     * principal (arma ou ferramenta), mão secundária (lamparina). Só as que têm folha.
     */
    public java.util.List<String> camadas(Map<String, String> equipamento, Predicate<String> existe) {
        java.util.List<String> out = new java.util.ArrayList<>(3);
        String c = cabeca(equipamento, existe);
        if (c != null) out.add(c);
        if (equipamento != null)
            for (String slot : new String[] {"mainHand", "offHand"}) {
                String id = maos.get(equipamento.get(slot));
                if (id != null && existe.test(id)) out.add(id);
            }
        return out;
    }
}
