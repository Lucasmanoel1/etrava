package etrava.tools.maps;

import static etrava.tools.maps.MapBuilder.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Gera a região do MVP (PRD 6.3, D-067): Pedra-Mansa ({@code vila.tmj}), a estrada com o
 * Posto da Encruzilhada ({@code estrada.tmj}) e quatro interiores (oficina de Avel,
 * taverna, moinho e casa da curandeira). {@code gradlew generateRegion} sobrescreve os
 * arquivos; depois de gerados, podem ser editados no Tiled.
 *
 * <p>Convenções: construção centrada em (cx, cy) ocupa cx ± largura/2 e cy ± profundidade/2;
 * a porta da face sudoeste fica em x = cx − largura/2 + pos + 0,5. O ponto "_porta" fica
 * logo à frente dela; o ponto de chegada vindo do interior fica a mais de 1 m dali.
 */
public final class GenerateRegion {
    private record Box(double x0, double y0, double x1, double y1) {
        boolean near(double x, double y, double m) { return x > x0 - m && x < x1 + m && y > y0 - m && y < y1 + m; }
    }

    private final List<Box> occupied = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "content/areas");
        GenerateRegion g = new GenerateRegion();
        g.vila().write(dir.resolve("vila.tmj"));
        new GenerateRegion().estrada().write(dir.resolve("estrada.tmj"));
        oficina().write(dir.resolve("oficina.tmj"));
        taverna().write(dir.resolve("taverna.tmj"));
        moinho().write(dir.resolve("moinho.tmj"));
        curandeira().write(dir.resolve("curandeira.tmj"));
    }

    private void building(MapBuilder m, String id, double cx, double cy, double w, double d) {
        m.prop(id, cx, cy);
        occupied.add(new Box(cx - w / 2, cy - d / 2, cx + w / 2, cy + d / 2));
    }

    private void thing(MapBuilder m, String id, double x, double y) {
        m.prop(id, x, y);
        occupied.add(new Box(x - 0.6, y - 0.6, x + 0.6, y + 0.6));
    }

    private boolean free(double x, double y, double margin) {
        for (Box b : occupied) if (b.near(x, y, margin)) return false;
        return true;
    }

    /** Árvores onde há grama livre; {@code keepOut} protege ruas e quintais. */
    private void trees(MapBuilder m, Random rnd, int count, double minX, double minY, double maxX, double maxY,
            String... kinds) {
        for (int i = 0; i < count; i++) {
            double x = Math.floor(minX + rnd.nextDouble() * (maxX - minX)) + 0.5;
            double y = Math.floor(minY + rnd.nextDouble() * (maxY - minY)) + 0.5;
            if (x < 2 || y < 2 || x > m.w - 2 || y > m.h - 2) continue;
            if (m.terrainAt((int) x, (int) y) != GRAMA || !free(x, y, 1.6)) continue;
            if (m.marked.stream().anyMatch(p -> Math.hypot(p[0] - x, p[1] - y) < 2.2)) continue; // longe dos pontos
            if (nearRoad(m, x, y)) continue; // nem colada na rua
            String k = kinds[rnd.nextInt(kinds.length)];
            // clareiras: sorteia igual (as outras árvores não mudam de lugar), mas não planta
            if (CLAREIRAS.stream().anyMatch(c -> m == vilaBuilder && Math.hypot(c[0] - x, c[1] - y) < c[2])) continue;
            thing(m, k, x, y);
        }
    }

    /** Praça do poço sem árvore no meio (portão V1: as copas escondiam o poço). x, y, raio em tiles. */
    private static final java.util.List<double[]> CLAREIRAS = java.util.List.of(new double[] {34.5, 33.5, 6.5});
    private MapBuilder vilaBuilder;

    /** Tem rua, praça ou lavoura a menos de um passo? */
    private static boolean nearRoad(MapBuilder m, double x, double y) {
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                int t = m.terrainAt((int) x + dx, (int) y + dy);
                if (t == TERRA || t == PEDRA || t == PLANTIO) return true;
            }
        return false;
    }

    private void fenceX(MapBuilder m, int x0, int x1, double y) {
        for (int x = x0; x < x1; x++) thing(m, "cerca_x", x + 0.5, y);
    }

    private void fenceY(MapBuilder m, double x, int y0, int y1) {
        for (int y = y0; y < y1; y++) thing(m, "cerca_y", x, y + 0.5);
    }

    // ================================================================ Pedra-Mansa

    private MapBuilder vila() {
        MapBuilder m = new MapBuilder(64, 64, GRAMA);
        vilaBuilder = m;
        Random rnd = new Random(64);
        m.border();
        // ruas: a estrada atravessa de oeste a leste; uma rua corta de norte a sul; a praça no cruzamento
        m.road(0, 31, 64, 31, 3.2, TERRA);
        m.road(32, 7, 32, 60, 2.6, TERRA);
        m.road(22.5, 27, 22.5, 31, 1.8, TERRA);   // oficina
        m.road(42.5, 27, 42.5, 31, 1.8, TERRA);   // taverna
        m.road(13.5, 31, 13.5, 41, 1.6, TERRA);   // curandeira
        m.road(32, 44.5, 43.5, 44.5, 1.6, TERRA); // moinho
        m.road(32, 37, 27.5, 39.5, 1.4, TERRA);   // Celi
        m.road(32, 18, 23, 11.5, 1.4, TERRA);     // Firmino e cemitério
        m.road(32, 20, 28, 19.5, 1.4, TERRA);     // Anselmo
        m.road(32, 18, 37, 17.5, 1.4, TERRA);     // Lauro
        m.road(56, 27, 56, 31, 1.4, TERRA);       // Gil
        m.road(55, 31, 55, 41.5, 1.4, TERRA);     // Bento e celeiro
        m.road(42, 44.5, 42, 50, 1.4, TERRA);     // campo de Bento
        m.disc(32, 31, 5.6, PEDRA);
        // lavouras: Bento a oeste, Gil a leste; a faixa entre os dois é a divisa (H2)
        m.rect(36, 50, 49, 61, PLANTIO);
        m.rect(50, 50, 62, 61, PLANTIO);
        m.rect(9, 42, 18, 46, PLANTIO);            // horta da curandeira

        // ---- construções (15)
        building(m, "casa_oficina", 22.5, 24.5, 7, 5);
        building(m, "casa_taverna", 42.5, 24.0, 9, 6);
        building(m, "casa_pequena", 9.5, 20.5, 5, 5);      // Doran
        building(m, "forja", 13.0, 26.2, 3, 2.6);
        thing(m, "bigorna", 15.4, 27.6);
        building(m, "casa_estreita", 28.0, 16.0, 4, 6);    // Anselmo
        building(m, "casa_media", 37.0, 14.5, 6, 5);       // Lauro
        building(m, "casa_pequena", 48.5, 15.5, 5, 5);     // Ciro
        building(m, "casa_estreita", 23.0, 8.0, 4, 6);     // Firmino
        building(m, "casa_media", 56.0, 24.0, 6, 5);       // Gil
        building(m, "casa_curandeira", 13.5, 38.0, 5, 4);
        building(m, "casa_pequena", 24.5, 39.5, 5, 5);     // Celi
        building(m, "casa_moinho", 43.5, 41.0, 5, 6);
        building(m, "casa_media", 55.0, 38.0, 6, 5);       // Bento
        building(m, "celeiro", 58.5, 45.5, 8, 6);
        building(m, "casa_pequena", 7.5, 51.0, 5, 5);      // casa vazia, na saída do pomar
        m.prop("poco", 34.5, 33.5);
        occupied.add(new Box(33.5, 32.5, 35.5, 34.5));

        // ---- cemitério, a noroeste
        fenceX(m, 8, 19, 4.6);
        fenceX(m, 8, 13, 13.4);
        fenceX(m, 15, 19, 13.4);
        fenceY(m, 7.6, 5, 13);
        fenceY(m, 19.4, 5, 13);
        int g = 0;
        for (int y = 6; y <= 11; y += 2)
            for (int x = 9; x <= 17; x += 2) {
                if (rnd.nextDouble() < 0.25) continue;
                thing(m, g++ % 3 == 0 ? "lapide_b" : "lapide", x + 0.5 + (rnd.nextDouble() - 0.5) * 0.3, y + 0.5);
            }
        // estacas que podem cortar o cemitério (H5: "a ferrovia vai passar pelo cemitério")
        for (int x = 9; x <= 18; x += 3) m.keyed("estaca_comitiva", "estacas_cemiterio", x + 0.2, 9.3);

        // ---- quintal do moinho e a árvore de Nalia (A1)
        m.keyed("arvore_velha", "arvore_nalia", 48.5, 45.5);
        occupied.add(new Box(47.8, 44.8, 49.2, 46.2));
        fenceX(m, 46, 52, 48.6);
        fenceY(m, 51.6, 42, 48);
        thing(m, "sacos", 46.8, 42.6);

        // ---- pomar de Celi (H1)
        for (int y = 45; y <= 51; y += 3)
            for (int x = 20; x <= 29; x += 3)
                thing(m, (x + y) % 2 == 0 ? "arvore_fruto" : "arvore_fruto_b", x + 0.5 + (rnd.nextDouble() - 0.5) * 0.4, y + 0.5);
        fenceX(m, 18, 31, 43.6);
        fenceY(m, 18.6, 44, 55);
        thing(m, "caixote", 30.0, 52.8);
        thing(m, "feno", 28.6, 54.4); // atrás do monte de feno é que a coisa se esconde

        // ---- lavouras, divisa e o buraco
        thing(m, "espantalho", 42.5, 56.5);
        thing(m, "espantalho", 56.5, 55.0);
        thing(m, "marco", 49.5, 50.3);
        thing(m, "marco", 49.5, 60.5);
        for (int y = 52; y <= 58; y += 3) m.keyed("estaca_comitiva", "estacas_divisa", 49.9, y + 0.5);
        m.keyed("buraco_campo", "buraco_campo", 40.5, 56.0);
        occupied.add(new Box(39.5, 55, 41.5, 57));
        thing(m, "feno", 38.0, 50.6);
        thing(m, "carroca", 46.0, 50.2);

        // ---- miudezas
        thing(m, "barril", 47.6, 27.6);
        thing(m, "barril", 48.4, 28.1);
        thing(m, "banco", 45.5, 28.6);
        thing(m, "caixote", 18.4, 23.0);
        thing(m, "caixote", 18.4, 24.1);
        thing(m, "barril", 18.6, 25.3);
        thing(m, "banco", 28.0, 34.6);
        thing(m, "caixote", 50.6, 29.0);
        thing(m, "feno", 61.0, 41.0);
        thing(m, "feno", 52.2, 47.8);
        thing(m, "pedras", 4.0, 34.0);
        // a tenda da comitiva, no largo da entrada leste, enquanto ela está aqui (A2)
        m.keyed("tenda_comitiva", "tenda_comitiva", 60.0, 34.6);
        occupied.add(new Box(58.5, 33.5, 61.5, 35.8));
        // lampiões
        for (double[] l : new double[][] {{26.6, 27.6}, {38.6, 27.8}, {29.0, 26.0}, {35.6, 36.0}, {17.0, 33.0},
                {47.0, 33.0}, {41.0, 46.0}, {20.0, 13.0}, {58.0, 29.0}})
            thing(m, "lampiao", l[0], l[1]);


        // ---- pontos (agendas, trabalhos, saídas)
        m.spot("praca", 30.0, 33.6);
        m.spot("praca_banco", 28.5, 35.4);
        m.spot("praca_norte", 32.5, 27.6);
        m.spot("poco", 33.0, 35.6);
        m.spot("oficina_porta", 22.5, 27.6);
        m.spot("oficina_fora", 19.6, 27.2);
        m.spot("taverna_porta", 42.5, 27.6);
        m.spot("taverna_fora", 45.2, 29.2);
        m.spot("forja", 14.8, 28.8);
        m.spot("casa_doran", 12.8, 20.6);
        m.spot("casa_anselmo", 28.0, 19.6);
        m.spot("casa_lauro", 37.0, 17.6);
        m.spot("casa_ciro", 51.8, 15.6);
        m.spot("casa_firmino", 23.0, 11.6);
        m.spot("casa_gil", 56.0, 27.2);
        m.spot("curandeira_porta", 13.5, 40.6);
        m.spot("horta", 14.5, 43.2);
        m.spot("casa_celi", 27.8, 39.6);
        m.spot("pomar", 24.0, 44.6);
        m.spot("pomar_fundo", 27.0, 55.4);
        m.spot("moinho_porta", 43.5, 44.6);
        m.spot("quintal_moinho", 47.6, 46.6);
        m.spot("quintal_cerca", 45.6, 47.0);
        m.spot("casa_bento", 55.0, 41.2);
        m.spot("celeiro", 58.0, 49.2);
        m.spot("campo_bento", 42.0, 51.6);
        m.spot("campo_gil", 55.0, 51.6);
        m.spot("divisa", 49.5, 55.0);
        m.spot("buraco", 40.5, 56.0);
        m.spot("beira_buraco", 42.2, 54.6);
        m.spot("cemiterio", 14.0, 14.2);
        m.spot("cemiterio_dentro", 13.8, 10.2);
        m.spot("comitiva", 58.6, 37.0);
        m.spot("casa_vazia", 10.6, 51.2);
        m.spot("feno", 31.6, 36.0);
        m.spot("saida_estrada", 62.5, 31.0);
        m.spot("entrada", 61.5, 31.6);
        m.spawn("jogador", 30.0, 33.0);
        m.spawn("estrada", 61.0, 30.6);
        m.spawn("oficina", 22.5, 29.0);
        m.spawn("taverna", 42.5, 29.0);
        m.spawn("moinho", 43.5, 46.0);
        m.spawn("curandeira", 13.5, 42.0);
        m.spawn("buraco", 42.4, 54.2);
        // ---- árvores: anel em volta e bosquetes soltos (depois dos pontos, para não cair em cima deles)
        trees(m, rnd, 320, 1, 1, 63, 63, "arvore", "arvore_b", "arvore");
        thing(m, "arvore_morta", 4.5, 44.5);
        return m;
    }

    // ================================================================ estrada e Posto

    private MapBuilder estrada() {
        MapBuilder m = new MapBuilder(72, 44, GRAMA);
        Random rnd = new Random(72);
        m.border();
        m.road(0, 22, 18, 22, 3.0, TERRA);
        m.road(18, 22, 30, 27, 3.0, TERRA);
        m.road(30, 27, 42, 22, 3.0, TERRA);
        m.road(42, 22, 72, 22, 3.0, TERRA);
        m.road(55, 0, 55, 44, 2.6, TERRA);     // a estrada que cruza: norte (Caldris) e sul (os Cantões)
        m.disc(63, 21.5, 6.5, TERRA);          // o largo do Posto

        building(m, "casa_posto", 64.0, 14.5, 10, 6);
        thing(m, "banca", 61.0, 26.4);
        thing(m, "banca", 66.6, 26.8);
        thing(m, "carroca", 70.0, 25.0);
        thing(m, "barril", 58.8, 18.4);
        thing(m, "barril", 59.6, 18.9);
        thing(m, "caixote", 69.4, 18.6);
        thing(m, "caixote", 70.4, 18.6);
        thing(m, "caixote", 69.9, 19.6);
        thing(m, "feno", 68.4, 29.6);
        thing(m, "placa", 56.8, 20.0);
        thing(m, "lampiao", 58.5, 25.0);
        thing(m, "lampiao", 67.5, 19.0);
        thing(m, "lampiao", 52.0, 20.0);
        thing(m, "pedras", 26.0, 30.0);
        thing(m, "pedras", 12.0, 19.0);
        thing(m, "arvore_morta", 31.5, 29.6);   // a curva de má fama

        m.spot("saida_vila", 1.4, 22.0);
        m.spot("entrada", 2.0, 22.6);
        m.spot("beira_estrada", 40.0, 20.0);
        m.spot("curva_estrada", 30.0, 27.0);
        m.spot("bosque_norte", 24.0, 12.0);
        m.spot("bosque_oeste", 8.0, 32.0);
        m.spot("bosque_sul", 37.0, 36.0);
        m.spot("posto_banca", 61.0, 24.8);
        m.spot("posto_carga", 69.0, 22.4);
        m.spot("posto_porta", 64.0, 18.2);
        m.spot("posto_guarda", 57.6, 24.0);
        m.spot("posto_fogo", 62.6, 20.6);
        m.spot("encruzilhada", 55.0, 22.0);
        m.spot("estrada_norte", 55.0, 2.0);
        m.spot("estrada_sul", 55.0, 42.0);
        m.spawn("jogador", 60.0, 22.0);
        m.spawn("vila", 3.0, 22.0);
        // bosque ralo dos dois lados da estrada
        trees(m, rnd, 260, 1, 1, 50, 43, "arvore", "arvore_b", "arvore", "arvore_b", "arvore_morta");
        trees(m, rnd, 30, 50, 34, 71, 43, "arvore", "arvore_b"); // longe das bancas: de frente, a copa as tapava
        trees(m, rnd, 30, 50, 1, 71, 9, "arvore", "arvore_b");
        return m;
    }

    // ================================================================ interiores (D-068)

    /** Oficina e casa de Avel: onde o aprendiz dorme, trabalha e onde ficam os papéis (A2). */
    private static MapBuilder oficina() {
        MapBuilder m = new MapBuilder(12, 10, NONE);
        m.room(1, 1, 11, 9, TABUAS,
                new String[] {"lisa", "lisa", "janela", "porta", "lisa", "prateleira", "prateleira", "lisa", "janela", "lisa"},
                new String[] {"lisa", "prateleira", "lisa", "janela", "lisa", "lisa", "lisa", "lisa"});
        m.prop("lareira", 2.2, 1.6);
        m.prop("bancada", 7.5, 2.4);
        m.prop("caixote", 9.0, 1.6);
        m.prop("barril", 5.5, 1.6);
        m.prop("bau", 10.3, 2.4);
        m.prop("cama", 9.6, 8.2);
        m.prop("cama", 2.4, 8.2);
        m.prop("mesa", 5.5, 5.6);
        m.prop("banco", 5.5, 6.8);
        m.spot("porta", 4.5, 1.5);
        m.spot("bancada", 7.5, 3.6);
        m.spot("bau", 10.2, 3.4);
        m.spot("mesa", 5.5, 4.6);
        m.spot("cama_avel", 9.2, 7.0);
        m.spot("cama_aprendiz", 3.6, 7.2);
        m.spot("lareira", 2.6, 3.0);
        m.spawn("porta", 4.5, 2.8);
        m.spawn("jogador", 4.0, 6.0);
        return m;
    }

    private static MapBuilder taverna() {
        MapBuilder m = new MapBuilder(14, 11, NONE);
        m.room(1, 1, 13, 10, LAJOTA,
                new String[] {"janela", "lisa", "prateleira", "lisa", "lisa", "porta", "lisa", "janela", "lisa",
                        "prateleira", "prateleira", "lisa"},
                new String[] {"lisa", "janela", "lisa", "lisa", "janela", "lisa", "lisa", "lisa", "lisa"});
        m.prop("lareira", 2.4, 1.6);
        m.prop("balcao", 10.5, 2.6);
        m.prop("barril", 12.5, 1.5);
        m.prop("barril", 12.5, 2.4);
        m.prop("barril", 8.6, 1.6);
        m.prop("mesa", 3.5, 5.2);
        m.prop("banco", 3.5, 6.2);
        m.prop("mesa_longa", 8.0, 5.6);
        m.prop("banco", 8.0, 4.6);
        m.prop("banco", 8.0, 6.6);
        m.prop("mesa", 3.5, 8.6);
        m.prop("mesa", 10.5, 8.6);
        m.prop("banco", 10.5, 7.6);
        m.spot("porta", 6.5, 1.5);
        m.spot("balcao", 10.5, 3.6);
        m.spot("atras_balcao", 10.4, 1.6);
        m.spot("mesa_jogo", 8.0, 7.4);
        m.spot("mesa_jogo_b", 9.2, 7.3);
        m.spot("mesa_janela", 2.4, 5.4);
        m.spot("mesa_fundo", 3.5, 7.6);
        m.spot("mesa_canto", 11.6, 8.6);
        m.spot("lareira", 3.4, 2.8);
        m.spawn("porta", 6.5, 2.9);
        m.spawn("jogador", 6.5, 4.0);
        return m;
    }

    private static MapBuilder moinho() {
        MapBuilder m = new MapBuilder(10, 10, NONE);
        m.room(1, 1, 9, 9, LAJOTA,
                new String[] {"lisa", "lisa", "porta", "lisa", "janela", "lisa", "lisa", "lisa"},
                new String[] {"lisa", "janela", "lisa", "lisa", "prateleira", "lisa", "lisa", "lisa"});
        m.prop("lareira", 6.6, 1.6);
        m.prop("mo", 5.6, 5.2);
        m.prop("sacos", 8.2, 2.4);
        m.prop("sacos", 8.2, 7.6);
        m.prop("cama", 2.4, 8.2);
        m.prop("mesa", 2.6, 4.2);
        m.prop("banco", 2.6, 5.2);
        m.spot("porta", 3.5, 1.5);
        m.spot("mo", 5.6, 6.8);
        m.spot("cama_nalia", 3.6, 7.2);
        m.spot("mesa", 2.6, 3.2);
        m.spot("lareira", 6.6, 3.0);
        m.spawn("porta", 3.5, 2.8);
        m.spawn("jogador", 4.0, 4.0);
        return m;
    }

    private static MapBuilder curandeira() {
        MapBuilder m = new MapBuilder(9, 8, NONE);
        m.room(1, 1, 8, 7, TABUAS,
                new String[] {"prateleira", "lisa", "porta", "lisa", "janela", "prateleira", "lisa"},
                new String[] {"lisa", "prateleira", "lisa", "janela", "lisa", "lisa"});
        m.prop("lareira", 5.8, 1.6);
        m.prop("ervas", 7.2, 3.0);
        m.prop("mesa", 4.2, 4.0);
        m.prop("banco", 4.2, 5.0);
        m.prop("cama", 2.4, 6.2);
        m.prop("bau", 7.2, 5.6);
        m.spot("porta", 3.5, 1.5);
        m.spot("mesa", 4.2, 3.0);
        m.spot("ervas", 6.6, 3.8);
        m.spot("cama", 3.8, 6.0);
        m.spawn("porta", 3.5, 2.8);
        m.spawn("jogador", 2.6, 3.4);
        return m;
    }
}
