package etrava.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Animação pela atividade da agenda (V2, SPEC-visual §6.3). */
class AnimacoesTest {
    private static final String YAML = """
            alternar: 5
            atividades:
              trabalhar: {padrao: trabalhar_bancada, por_npc: {ferreiro: trabalhar_martelo}}
              comer: sentado
              festejar: [conversar, parado]
            """;

    @Test
    void regraPadraoPorNpcESemRegra() throws Exception {
        Animacoes a = Animacoes.de(YAML);
        assertEquals("trabalhar_bancada", a.escolher("avel", "trabalhar", 0, 0));
        assertEquals("trabalhar_martelo", a.escolher("ferreiro", "trabalhar", 0, 0));
        assertEquals("sentado", a.escolher("anselmo", "comer", 0, 0));
        assertEquals("parado", a.escolher("anselmo", "olhar", 0, 0));
        assertEquals("parado", a.escolher("anselmo", "", 0, 0));
        assertEquals("parado", a.escolher("anselmo", null, 0, 0));
    }

    @Test
    void listaAlternaComFaseDoNpc() throws Exception {
        Animacoes a = Animacoes.de(YAML);
        assertEquals("conversar", a.escolher("x", "festejar", 0, 0));
        assertEquals("parado", a.escolher("x", "festejar", 5.1, 0));
        assertEquals("conversar", a.escolher("x", "festejar", 10.1, 0));
        // fase meia volta: começa no outro
        assertEquals("parado", a.escolher("x", "festejar", 0, 0.5));
    }

    @Test
    void faseEstavelEVariada() {
        assertEquals(Animacoes.fase("bento"), Animacoes.fase("bento"));
        assertNotEquals(Animacoes.fase("bento"), Animacoes.fase("gil"));
        for (String id : new String[] {"bento", "gil", "", "x"}) {
            double f = Animacoes.fase(id);
            assertTrue(f >= 0 && f < 1, id + " " + f);
        }
    }

    /** Toda atividade das agendas tem regra ou é das que ficam paradas de propósito. */
    @Test
    void agendasCobertas() throws Exception {
        Path raiz = Path.of("..").toAbsolutePath().normalize();
        JsonNode cfg = new ObjectMapper(new YAMLFactory()).readTree(raiz.resolve("assets/config/animacoes.yaml").toFile());
        Set<String> paradas = Set.of("olhar", "vigiar", "esperar", "andar", "viajar", "em_casa", "emburrar",
                "esconder", "visitar", "examinar", "buscar_agua", "brincar", "varrer");
        Set<String> faltam = new HashSet<>();
        ObjectMapper y = new ObjectMapper(new YAMLFactory());
        try (Stream<Path> s = Files.list(raiz.resolve("content/npcs"))) {
            for (Path p : s.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                for (JsonNode b : y.readTree(p.toFile()).path("schedule")) {
                    String act = b.path("do").asText();
                    if (!cfg.path("atividades").has(act) && !paradas.contains(act)) faltam.add(act);
                }
            }
        }
        assertTrue(faltam.isEmpty(), "atividades sem animação: " + faltam);
    }

    /** Trabalho, assento e cama só no lugar certo; parado e conversar valem em qualquer lugar. */
    @Test
    void lugaresDasAnimacoes() throws Exception {
        Animacoes a = Animacoes.carregar(Path.of("..").toAbsolutePath().normalize().resolve("assets"));
        assertTrue(a.lugar("trabalhar_martelo").props().contains("bigorna"));
        assertTrue(a.lugar("trabalhar_chao").terrenos().contains("plantio"));
        assertTrue(a.lugar("sentado").props().contains("banco"));
        assertTrue(a.lugar("dormir").props().contains("cama"));
        assertTrue(a.lugar("parado") == null && a.lugar("conversar") == null);
        Animacoes b = Animacoes.de("lugares: {trabalhar_bancada: {props: [bancada]}}");
        assertTrue(b.lugar("trabalhar_bancada").alcance() == 1.6, "alcance padrão");
    }
}
