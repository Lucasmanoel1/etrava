package etrava.tools.art.pixel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Chave do cache por objeto (D-105): cada tipo depende só dos scripts que de fato usa. */
class RenderArtTest {
    private static final Path RAIZ = Path.of("..").toAbsolutePath().normalize();

    private static Set<String> scripts(String tipo) throws Exception {
        RenderArt r = new RenderArt(RAIZ);
        Path b = RAIZ.resolve("tools/blender");
        return r.scripts(tipo).stream().map(p -> b.relativize(p).toString().replace('\\', '/')).collect(Collectors.toSet());
    }

    @Test
    void arvoreNaoDependeDoKitHumano() throws Exception {
        Set<String> s = scripts("arvore");
        assertTrue(s.contains("entrada.py"), s.toString());
        assertTrue(s.contains("tarefas/arvore.py"), s.toString());
        assertTrue(s.contains("natureza/arvore.py"), s.toString());
        assertTrue(s.contains("nucleo/render.py"), s.toString());
        assertTrue(s.contains("nucleo/camera.py"), s.toString());
        assertFalse(s.stream().anyMatch(f -> f.startsWith("kit_humano/") || f.startsWith("anim/")), s.toString());
        assertFalse(s.contains("tarefas/personagem.py"), s.toString());
    }

    @Test
    void personagemDependeDoKitEDasAnimacoes() throws Exception {
        Set<String> s = scripts("personagem");
        assertTrue(s.contains("kit_humano/corpo.py"), s.toString());
        assertTrue(s.contains("kit_humano/pele.py"), s.toString());
        assertTrue(s.contains("anim/ciclos.py"), s.toString());
        assertTrue(s.contains("anim/correr_diretor.py"), s.toString());
        assertFalse(s.contains("natureza/arvore.py"), s.toString());
    }
}
