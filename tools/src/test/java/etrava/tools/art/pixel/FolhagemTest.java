package etrava.tools.art.pixel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Acabamento das árvores (D-100): projeto do Pixelorama, pincéis e pintor de folhagem. */
class FolhagemTest {
    static final Paleta PALETA = PixelizadorTest.PALETA;
    static final Map<Integer, Pixelizador.Material> MATS = Map.of(
            1, new Pixelizador.Material(1, "folha_carvalho_00", "folha_carvalho", 0, false, false, true, 0, 0.7, null,
                    null, false, "carvalho", "folha_carvalho"));

    /** Um tufo redondo de 40 px (material 1) deslocado de (ox, oy) px, luz caindo da esquerda. */
    static Passes tufo(int ox, int oy) {
        int ss = 4, w = 64 * ss, h = 64 * ss;
        Passes p = new Passes(w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double dx = (x + 0.5) / ss - 30 - ox, dy = (y + 0.5) / ss - 30 - oy;
                if (dx * dx + dy * dy > 400) continue;
                p.set(x, y, 1, 0.5 + dy * 0.0005, Math.max(0, Math.min(1, 0.55 - dx / 40 - dy / 50)), 0.9, 0.5);
            }
        return p;
    }

    static Map<String, Pinceis.Conjunto> pinceis(Path dir) throws Exception {
        return Pinceis.carregar(dir.resolve("folhas.pxo"));
    }

    @Test
    void pxoIdaEVoltaEBytesIguais(@TempDir Path dir) throws Exception {
        int[] a = {0, 0xFF112233, 0x5A000000, 0}, b = {0xFFFFFFFF, 0, 0, 0x80102030};
        Pxo.Projeto p = new Pxo.Projeto(2, 2, 5.0, List.of(
                new Pxo.Camada("baixo", true, List.of(a, b)), new Pxo.Camada("cima", true, List.of(b, a))));
        Pxo.escrever(dir.resolve("t.pxo"), p, "vento");
        Pxo.Projeto q = Pxo.ler(dir.resolve("t.pxo"));
        assertEquals(2, q.quadros());
        assertArrayEquals(a, q.camada("baixo").quadros().get(0));
        assertArrayEquals(a, q.camada("cima").quadros().get(1));
        byte[] um = Files.readAllBytes(dir.resolve("t.pxo"));
        Pxo.escrever(dir.resolve("t.pxo"), p, "vento");
        assertArrayEquals(um, Files.readAllBytes(dir.resolve("t.pxo")), "a mesma camada tem que dar os mesmos bytes");
        // composição: o opaco de cima vence; o vazio de cima deixa ver o de baixo
        assertEquals(0xFFFFFFFF, q.composto(0)[0]);
        assertEquals(0xFF112233, q.composto(0)[1]);
    }

    @Test
    void pinceisIniciaisTemTodasAsEspecies(@TempDir Path dir) throws Exception {
        Map<String, Pinceis.Conjunto> c = pinceis(dir);
        for (String e : List.of("carvalho", "betula", "macieira", "pereira")) {
            assertNotNull(c.get(e), e);
            assertTrue(c.get(e).pinceis().size() >= 4, e);
        }
    }

    @Test
    void folhagemDeterministicaENaPaleta(@TempDir Path dir) throws Exception {
        Pixelizador px = new Pixelizador(PALETA, pinceis(dir));
        var a = px.pixelizar(tufo(0, 0), MATS, Pixelizador.Parametros.padrao(), List.of(), Map.of(1, new int[] {30, 30}));
        var b = px.pixelizar(tufo(0, 0), MATS, Pixelizador.Parametros.padrao(), List.of(), Map.of(1, new int[] {30, 30}));
        int[] pa = PixelizadorTest.pixels(a.cor());
        assertArrayEquals(pa, PixelizadorTest.pixels(b.cor()));
        int tons = 0;
        java.util.Set<Integer> vistas = new java.util.HashSet<>();
        for (int c : pa) {
            if ((c >>> 24) == 0) continue;
            assertTrue(PALETA.contem(c), String.format("fora da paleta: %08X", c));
            if (vistas.add(c)) tons++;
        }
        assertTrue(tons >= 4, "a folhagem precisa de luz, corpo e sombra: " + tons + " cores");
    }

    @Test
    void desenhoAndaComOTufo(@TempDir Path dir) throws Exception {
        // tufo e âncora andam (3, 2) px: o miolo do desenho anda junto (sem cintilar no vento)
        Pixelizador px = new Pixelizador(PALETA, pinceis(dir));
        var a = px.pixelizar(tufo(0, 0), MATS, Pixelizador.Parametros.padrao(), List.of(), Map.of(1, new int[] {30, 30}));
        var b = px.pixelizar(tufo(3, 2), MATS, Pixelizador.Parametros.padrao(), List.of(), Map.of(1, new int[] {33, 32}));
        int iguais = 0, total = 0;
        for (int y = 18; y < 42; y++)
            for (int x = 18; x < 42; x++) {
                total++;
                if (a.cor().getRGB(x, y) == b.cor().getRGB(x + 3, y + 2)) iguais++;
            }
        assertTrue(iguais > total * 0.8, "só " + iguais + "/" + total + " px do miolo acompanharam o tufo");
    }
}
