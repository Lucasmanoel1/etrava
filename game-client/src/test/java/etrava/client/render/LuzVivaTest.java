package etrava.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Luz que tremula e feixe pela hora (V6, SPEC-visual §7.4): só a lógica, sem janela. */
class LuzVivaTest {
    private static final String YAML = """
            tremula:
              lareira: {intensidade: 0.16, raio: 0.07, velocidade: 1.6}
              vela: {intensidade: 0.13, raio: 0.05, velocidade: 2.6}
              vela_mesa: {intensidade: 0.30, raio: 0.1, velocidade: 3}
            """;

    @Test
    void presetPorIdOuPrefixo() {
        LuzViva l = LuzViva.de(YAML);
        assertNotNull(l.para("lareira"));
        assertEquals(0.13, l.para("velas").intensidade(), 1e-9);
        assertEquals(0.30, l.para("vela_mesa").intensidade(), 1e-9);   // a mais específica ganha
        assertNull(l.para("mesa"));
        assertNull(l.para(null));
    }

    @Test
    void semPresetALuzEFirme() {
        assertEquals(1.0, LuzViva.intensidade(null, 3, 4, 12.3), 0);
        assertEquals(1.0, LuzViva.raio(null, 3, 4, 12.3), 0);
    }

    @Test
    void oscilacaoDeterministicaELimitada() {
        LuzViva.Preset p = LuzViva.de(YAML).para("lareira");
        double min = 9, max = 0;
        for (int i = 0; i < 6000; i++) {
            double t = i / 60.0;
            double a = LuzViva.intensidade(p, 4.4, 4.0, t);
            assertEquals(a, LuzViva.intensidade(p, 4.4, 4.0, t), 0);   // mesma entrada, mesma saída
            assertTrue(a >= 1 - p.intensidade() - 1e-9 && a <= 1 + p.intensidade() + 1e-9, "fora da faixa: " + a);
            double r = LuzViva.raio(p, 4.4, 4.0, t);
            assertTrue(Math.abs(r - 1) <= p.raio() + 1e-9);
            min = Math.min(min, a);
            max = Math.max(max, a);
        }
        assertTrue(max - min > p.intensidade(), "a luz quase não varia: " + (max - min));
    }

    @Test
    void semPiscarDuro() {
        LuzViva.Preset p = LuzViva.de(YAML).para("vela");
        double anterior = LuzViva.intensidade(p, 2, 2, 0), maiorSalto = 0;
        for (int i = 1; i <= 3000; i++) {   // a 60 quadros por segundo
            double a = LuzViva.intensidade(p, 2, 2, i / 60.0);
            maiorSalto = Math.max(maiorSalto, Math.abs(a - anterior));
            anterior = a;
        }
        assertTrue(maiorSalto < 0.03, "salto entre quadros grande demais: " + maiorSalto);
    }

    @Test
    void posicoesDiferentesNaoTremulamJuntas() {
        LuzViva.Preset p = LuzViva.de(YAML).para("lareira");
        int iguais = 0;
        for (int i = 0; i < 200; i++) {
            if (Math.abs(LuzViva.intensidade(p, 4, 4, i * 0.1) - LuzViva.intensidade(p, 12, 9, i * 0.1)) < 1e-6) iguais++;
        }
        assertTrue(iguais < 5);
        assertNotEquals(LuzViva.semente(4, 4, 1), LuzViva.semente(12, 9, 1));
    }

    @Test
    void feixePelaHora() {
        assertEquals(0, FeixeJanela.intensidade(3, 6.5, 18.5), 0);
        assertEquals(0, FeixeJanela.intensidade(6.5, 6.5, 18.5), 0);
        assertEquals(0, FeixeJanela.intensidade(22.5, 6.5, 18.5), 0);   // nada à noite
        assertEquals(1, FeixeJanela.intensidade(12.5, 6.5, 18.5), 1e-9);
        assertTrue(FeixeJanela.intensidade(8, 6.5, 18.5) < FeixeJanela.intensidade(10, 6.5, 18.5));
        assertTrue(FeixeJanela.intensidade(16.5, 6.5, 18.5) < FeixeJanela.intensidade(14, 6.5, 18.5));
    }

    @Test
    void manchaEntraPeloLadoDeDentroEDesliza() {
        FeixeJanela f = new FeixeJanela(LuzViva.de("""
                feixe: {janelas: {parede_x_janela: x}}
                """).feixe());
        FeixeJanela.Janela j = new FeixeJanela.Janela(7, 1.75, 'x', 1);
        double[][] meio = f.mancha(j, 12.5), manha = f.mancha(j, 8);
        for (double[] c : meio) assertTrue(c[1] > 1.75, "a mancha tem de cair dentro do cômodo");
        assertTrue(manha[2][1] > meio[2][1], "de manhã o sol é baixo: a mancha vai mais longe");
        assertNotEquals(manha[0][0], meio[0][0], "a mancha desliza ao longo da parede");
    }
}
