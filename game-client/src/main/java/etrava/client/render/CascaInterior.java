package etrava.client.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import etrava.sim.content.AreaDef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Casca dos cômodos (V6, SPEC-visual §7.4), pura apresentação: o mapa e a simulação não mudam.
 * <ul>
 *   <li>as paredes de fundo do mapa ({@code prop.parede_*}) trocam de sprite pelo conjunto da casa
 *   ({@code prop.parede_x_lisa} → {@code prop.parede_x_lisa_taverna}), se existir;</li>
 *   <li>em volta do piso entram as paredes da frente cortadas a 0,4 m (sul e leste), o canto da
 *   frente e as pontas das paredes de fundo — na faixa de colisão da borda do mapa, onde ninguém
 *   pisa; baixas, nunca escondem o jogador;</li>
 *   <li>o piso troca de textura pelo cômodo e é desenhado com o losango exato, sem a sangria
 *   irregular do chão de fora: a borda fica reta e se apoia no pé das paredes.</li>
 * </ul>
 * Só vale para as áreas de {@code assets/config/interiores.yaml}; nas outras {@link #carregar} dá null.
 */
final class CascaInterior implements Disposable {
    private static final Logger LOG = LoggerFactory.getLogger(CascaInterior.class);
    /** Meia espessura das paredes (a pegada do props.yaml é 0,25 m). */
    private static final double E = 0.125;

    /** Uma peça a pôr na cena: sprite, posição (âncora) e caixa da base, em metros. */
    record Peca(String sprite, double x, double y, double x0, double y0, double x1, double y1) {}

    private final String casa;
    private final Map<String, String> pisos;
    private final Predicate<String> existe;
    private final List<Peca> frente = new ArrayList<>();
    private Texture mascara;
    private TextureRegion regiao;

    private CascaInterior(String casa, Map<String, String> pisos, Predicate<String> existe) {
        this.casa = casa;
        this.pisos = pisos;
        this.existe = existe;
    }

    /** @param existe se um ID está no manifesto */
    static CascaInterior carregar(Path assetsDir, AreaDef area, Predicate<String> existe) {
        Path file = assetsDir.resolve("config/interiores.yaml");
        if (!Files.exists(file)) return null;
        JsonNode c;
        try {
            c = new ObjectMapper(new YAMLFactory()).readTree(file.toFile()).path(area.id());
        } catch (Exception ex) {
            LOG.warn("interiores.yaml ilegível ({}): cômodo com a casca antiga", ex.getMessage());
            return null;
        }
        if (c.isMissingNode() || c.isNull()) return null;
        Map<String, String> pisos = new HashMap<>();
        c.path("piso").fields().forEachRemaining(f -> pisos.put(f.getKey(), f.getValue().asText()));
        CascaInterior ci = new CascaInterior(c.path("paredes").asText(""), pisos, existe);
        ci.montarFrente(area);
        return ci;
    }

    /** Sprite da parede no conjunto da casa; qualquer outro ID volta igual. */
    String sprite(String id) {
        if (id == null || casa.isEmpty() || !id.startsWith("prop.parede_")) return id;
        String daCasa = id + "_" + casa;
        return existe.test(daCasa) ? daCasa : id;
    }

    /** Terreno a desenhar no lugar do terreno do mapa (o som dos passos segue o do mapa). */
    String piso(String terreno) {
        String p = pisos.get(terreno);
        return p != null && existe.test("tile." + p) ? p : terreno;
    }

    /** Paredes da frente, canto da frente e pontas, já com o sprite da casa. */
    List<Peca> frente() {
        return frente;
    }

    /**
     * Retângulo do piso (tiles com terreno) → paredes nas bordas sul e leste, logo além do piso
     * (a mesma distância das paredes de fundo do mapa, que ficam a 0,125 m da borda norte e oeste).
     */
    private void montarFrente(AreaDef area) {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
        for (int ty = 0; ty < area.height(); ty++)
            for (int tx = 0; tx < area.width(); tx++) {
                if (AreaDef.NO_TERRAIN.equals(area.terrainAt(tx, ty))) continue;
                x0 = Math.min(x0, tx);
                y0 = Math.min(y0, ty);
                x1 = Math.max(x1, tx);
                y1 = Math.max(y1, ty);
            }
        if (x1 < 0) return;
        double sul = y1 + 1 + E, leste = x1 + 1 + E;
        for (int tx = x0; tx <= x1; tx++)
            frente.add(new Peca(sprite("prop.parede_frente_x"), tx + 0.5, sul, tx, sul - E, tx + 1, sul + E));
        for (int ty = y0; ty <= y1; ty++)
            frente.add(new Peca(sprite("prop.parede_frente_y"), leste, ty + 0.5, leste - E, ty, leste + E, ty + 1));
        frente.add(caixa(sprite("prop.parede_canto_frente"), leste, sul));
        frente.add(caixa(sprite("prop.parede_ponta"), x0 - E, sul));     // fim da parede de fundo oeste
        frente.add(caixa(sprite("prop.parede_ponta"), leste, y0 - E));   // fim da parede de fundo norte
    }

    private static Peca caixa(String sprite, double x, double y) {
        return new Peca(sprite, x, y, x - E, y - E, x + E, y + E);
    }

    /**
     * Máscara do piso: o losango exato de 64×32 px de arte (no quadro de 128×64 das máscaras do
     * chão), sem anel nem sangria. Os losangos vizinhos se encaixam sem sobra nem falta (nenhum
     * centro de pixel cai na divisa), e a borda do cômodo sai reta.
     */
    TextureRegion mascara() {
        if (regiao == null) {
            int w = GroundMasks.W, h = GroundMasks.H;
            Pixmap px = new Pixmap(w, h, Pixmap.Format.RGBA8888);
            px.setBlending(Pixmap.Blending.None);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    double dx = (x + 0.5 - w / 2.0) / 32.0, dy = (y + 0.5 - h / 2.0) / 16.0;
                    px.drawPixel(x, y, Math.abs(dx) + Math.abs(dy) <= 1.0 ? 0xFFFFFFFF : 0);
                }
            mascara = new Texture(px);
            mascara.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            px.dispose();
            regiao = new TextureRegion(mascara);
        }
        return regiao;
    }

    @Override
    public void dispose() {
        if (mascara != null) mascara.dispose();
    }
}
