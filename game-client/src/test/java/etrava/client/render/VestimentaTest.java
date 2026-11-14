package etrava.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VestimentaTest {
    private static final String YAML = """
            corpo: {gibao_couro: personagem.aprendiz_gibao, avental_couro: personagem.aprendiz_avental}
            cabeca: {chapeu_feltro: personagem.aprendiz_chapeu}
            """;

    @Test
    void roupaEscolheAFolhaECabecaACamada() throws Exception {
        Vestimenta v = Vestimenta.de(YAML);
        Set<String> existem = Set.of("personagem.aprendiz_gibao", "personagem.aprendiz_chapeu");
        Map<String, String> eq = Map.of("body", "gibao_couro", "head", "chapeu_feltro");
        assertEquals("personagem.aprendiz_gibao", v.corpo(eq, "personagem.aprendiz", existem::contains));
        assertEquals("personagem.aprendiz_chapeu", v.cabeca(eq, existem::contains));
    }

    @Test
    void semFolhaFicaABase() throws Exception {
        Vestimenta v = Vestimenta.de(YAML);
        Set<String> existem = Set.of();
        Map<String, String> eq = Map.of("body", "avental_couro", "head", "capuz_la");
        assertEquals("personagem.aprendiz", v.corpo(eq, "personagem.aprendiz", existem::contains));
        assertNull(v.cabeca(eq, existem::contains));
        assertEquals("personagem.aprendiz", v.corpo(Map.of(), "personagem.aprendiz", existem::contains));
        assertNull(v.cabeca(null, existem::contains));
    }
}
