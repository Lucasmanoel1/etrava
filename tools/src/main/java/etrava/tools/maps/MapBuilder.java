package etrava.tools.maps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Monta um mapa do Tiled (.tmj, isométrico) por código: terreno, colisão, props,
 * pontos ({@code spots}) e pontos de chegada ({@code spawns}). O resultado abre e
 * edita no Tiled normalmente (D-010); rodar o gerador de novo sobrescreve.
 */
final class MapBuilder {
    static final int TW = 128, TH = 64;
    /** Terrenos, na ordem do tileset (gid = índice + 1). */
    static final String[] TERRAINS = {"grama", "terra", "pedra", "parede", "tabuas", "lajota", "plantio"};
    static final int NONE = 0, GRAMA = 1, TERRA = 2, PEDRA = 3, BLOQUEIO = 4, TABUAS = 5, LAJOTA = 6, PLANTIO = 7;
    private static final ObjectMapper JSON = new ObjectMapper();

    final int w, h;
    final int[] terrain, collision;
    private final ArrayNode props = JSON.createArrayNode();
    private final ArrayNode spots = JSON.createArrayNode();
    private final ArrayNode spawns = JSON.createArrayNode();
    private int nextId = 1;

    MapBuilder(int w, int h, int fill) {
        this.w = w;
        this.h = h;
        this.terrain = new int[w * h];
        this.collision = new int[w * h];
        Arrays.fill(terrain, fill);
    }

    boolean inside(int x, int y) { return x >= 0 && y >= 0 && x < w && y < h; }
    int terrainAt(int x, int y) { return inside(x, y) ? terrain[y * w + x] : NONE; }
    void set(int x, int y, int t) { if (inside(x, y)) terrain[y * w + x] = t; }
    void block(int x, int y) { if (inside(x, y)) collision[y * w + x] = BLOQUEIO; }
    void unblock(int x, int y) { if (inside(x, y)) collision[y * w + x] = 0; }

    /** Retângulo de terreno [x0, x1) × [y0, y1). */
    void rect(int x0, int y0, int x1, int y1, int t) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) set(x, y, t);
    }

    /** Borda invisível em volta do mapa. */
    void border() {
        for (int i = 0; i < w; i++) { block(i, 0); block(i, h - 1); }
        for (int i = 0; i < h; i++) { block(0, i); block(w - 1, i); }
    }

    /** Caminho de terra entre dois pontos, com a largura dada e a beirada levemente irregular. */
    void road(double x0, double y0, double x1, double y1, double width, int t) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double px = x + 0.5, py = y + 0.5;
                double u = ((px - x0) * (x1 - x0) + (py - y0) * (y1 - y0)) / (len * len);
                if (u < -0.02 || u > 1.02) continue;
                double cx = x0 + (x1 - x0) * u, cy = y0 + (y1 - y0) * u;
                double wobble = Math.sin(x * 0.7) * 0.3 + Math.sin(y * 0.9 + 1) * 0.3;
                if (Math.hypot(px - cx, py - cy) < width / 2 + wobble) set(x, y, t);
            }
        }
    }

    void disc(double cx, double cy, double r, int t) {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double wobble = Math.sin(x * 0.7) * 0.25 + Math.sin(y * 0.9 + 1) * 0.25;
                if (Math.hypot(x + 0.5 - cx, y + 0.5 - cy) < r + wobble) set(x, y, t);
            }
    }

    void prop(String id, double x, double y) {
        props.add(point("", id, x, y));
    }

    /** Prop com chave: o estado (variante) fica no mundo e muda pelo efeito {@code setProp}. */
    void keyed(String id, String key, double x, double y) {
        ObjectNode obj = point("", id, x, y);
        obj.set("properties", JSON.createArrayNode().add(JSON.createObjectNode()
                .put("name", "key").put("type", "string").put("value", key)));
        props.add(obj);
    }

    /** Pontos já marcados (spots e spawns): nada de árvore em cima deles. */
    final java.util.List<double[]> marked = new java.util.ArrayList<>();

    void spot(String name, double x, double y) {
        spots.add(point(name, "", x, y));
        marked.add(new double[] {x, y});
    }
    void spawn(String name, double x, double y) {
        spawns.add(point(name, "", x, y));
        marked.add(new double[] {x, y});
    }

    /** Em mapas isométricos do Tiled, a posição dos objetos usa a altura do tile nos dois eixos. */
    private ObjectNode point(String name, String type, double x, double y) {
        return JSON.createObjectNode().put("id", nextId++).put("name", name).put("type", type)
                .put("x", x * TH).put("y", y * TH).put("width", 0).put("height", 0)
                .put("rotation", 0).put("visible", true).put("point", true);
    }

    /**
     * Um cômodo visto em corte (D-068): piso em [x0, x1) × [y0, y1), paredes de fundo ao
     * norte (eixo x) e a oeste (eixo y), e o resto do mapa bloqueado. {@code northWall}
     * diz o tipo de cada metro da parede norte ("lisa", "janela", "porta", "prateleira"),
     * da esquerda para a direita; {@code westWall}, de cima para baixo.
     */
    void room(int x0, int y0, int x1, int y1, int floor, String[] northWall, String[] westWall) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) block(x, y);
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                set(x, y, floor);
                unblock(x, y);
            }
        double t = 0.125;
        for (int i = 0; i < x1 - x0; i++) {
            String kind = i < northWall.length ? northWall[i] : "lisa";
            prop("parede_x_" + kind, x0 + i + 0.5, y0 - t);
        }
        for (int i = 0; i < y1 - y0; i++) {
            String kind = i < westWall.length ? westWall[i] : "lisa";
            prop("parede_y_" + kind, x0 - t, y0 + i + 0.5);
        }
        prop("parede_canto", x0 - t, y0 - t);
    }

    void write(Path out) throws IOException {
        ObjectNode map = JSON.createObjectNode();
        map.put("type", "map").put("version", "1.10").put("tiledversion", "1.11.0")
                .put("orientation", "isometric").put("renderorder", "right-down")
                .put("infinite", false).put("compressionlevel", -1)
                .put("width", w).put("height", h).put("tilewidth", TW).put("tileheight", TH);
        map.set("tilesets", JSON.createArrayNode().add(tileset()));
        ArrayNode layers = JSON.createArrayNode();
        layers.add(tileLayer(1, "terrain", terrain));
        layers.add(tileLayer(2, "collision", collision));
        layers.add(objectLayer(3, "props", props));
        layers.add(objectLayer(4, "spots", spots));
        layers.add(objectLayer(5, "spawns", spawns));
        map.set("layers", layers);
        map.put("nextlayerid", 6).put("nextobjectid", nextId);
        JSON.writeValue(out.toFile(), map);
        System.out.println("Mapa gerado: " + out.toAbsolutePath());
    }

    private ObjectNode tileset() {
        ObjectNode ts = JSON.createObjectNode();
        ts.put("firstgid", 1).put("name", "placeholder").put("image", "tiles_placeholder.png")
                .put("imagewidth", TW * TERRAINS.length).put("imageheight", TH)
                .put("tilewidth", TW).put("tileheight", TH).put("tilecount", TERRAINS.length)
                .put("columns", TERRAINS.length).put("margin", 0).put("spacing", 0);
        ArrayNode tiles = JSON.createArrayNode();
        for (int i = 0; i < TERRAINS.length; i++) {
            ObjectNode tile = JSON.createObjectNode().put("id", i);
            tile.set("properties", JSON.createArrayNode().add(JSON.createObjectNode()
                    .put("name", "terrain").put("type", "string").put("value", TERRAINS[i])));
            tiles.add(tile);
        }
        ts.set("tiles", tiles);
        return ts;
    }

    private ObjectNode tileLayer(int id, String name, int[] data) {
        ObjectNode layer = JSON.createObjectNode();
        layer.put("id", id).put("name", name).put("type", "tilelayer").put("width", w).put("height", h)
                .put("x", 0).put("y", 0).put("opacity", 1).put("visible", true);
        ArrayNode array = JSON.createArrayNode();
        for (int v : data) array.add(v);
        layer.set("data", array);
        return layer;
    }

    private ObjectNode objectLayer(int id, String name, ArrayNode objects) {
        ObjectNode layer = JSON.createObjectNode();
        layer.put("id", id).put("name", name).put("type", "objectgroup").put("draworder", "topdown")
                .put("x", 0).put("y", 0).put("opacity", 1).put("visible", true);
        layer.set("objects", objects);
        return layer;
    }
}
