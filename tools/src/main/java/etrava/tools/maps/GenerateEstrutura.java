package etrava.tools.maps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

/**
 * Gera {@code content/areas/estrutura.tmj}: a estrutura enterrada (PRD 6, gancho 03),
 * a única área "dungeon" do MVP. Uma sala de entrada sob o buraco, um corredor com
 * fibras petrificadas nas paredes, um nicho e a câmara do fundo. Fora do chão
 * escavado é tudo rocha (colisão, sem tile: no escuro, o preto é a parede).
 * Rodar de novo sobrescreve o arquivo; depois de gerado, pode ser editado no Tiled.
 */
public final class GenerateEstrutura {
    private static final int W = 26, H = 26, TW = 128, TH = 64;
    private static final int PEDRA = 3, BLOQUEIO = 4;
    private static final String[] TERRAINS = {"grama", "terra", "pedra", "parede"};
    private static final ObjectMapper JSON = new ObjectMapper();

    private final int[] terrain = new int[W * H];
    private final int[] collision = new int[W * H];
    private final ArrayNode props = JSON.createArrayNode();
    private final ArrayNode spots = JSON.createArrayNode();
    private final ArrayNode spawns = JSON.createArrayNode();
    private int nextId = 1;

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "content/areas/estrutura.tmj");
        new GenerateEstrutura().build(out);
        System.out.println("Mapa gerado: " + out.toAbsolutePath());
    }

    private void build(Path out) throws IOException {
        Random rnd = new Random(3);
        java.util.Arrays.fill(collision, BLOQUEIO);
        // sala de entrada, sob o buraco
        dig(3, 3, 9, 9);
        // corredor para leste, depois para o sul
        dig(9, 5, 17, 7);
        dig(15, 7, 17, 15);
        // nicho no fim do corredor de cima
        dig(17, 4, 20, 8);
        // câmara do fundo, mais larga, de borda irregular
        for (int y = 14; y < 23; y++) {
            for (int x = 10; x < 23; x++) {
                double d = Math.hypot((x + 0.5 - 16) / 6.2, (y + 0.5 - 18.5) / 4.4);
                if (d < 1 + Math.sin(x * 1.7 + y) * 0.08) open(x, y);
            }
        }
        // fibras petrificadas nas paredes do corredor e da câmara: parecem raízes, mas são pedra
        int[][] fibers = {{10, 5}, {12, 7}, {14, 5}, {15, 10}, {17, 12}, {15, 13}, {11, 17}, {21, 17}, {13, 21},
                {19, 21}, {4, 8}, {8, 3}};
        for (int[] f : fibers) {
            double x = f[0] + 0.5 + (rnd.nextDouble() - 0.5) * 0.4, y = f[1] + 0.5 + (rnd.nextDouble() - 0.5) * 0.4;
            prop("fibra", x, y);
        }
        prop("entulho", 7.5, 4.5);
        prop("entulho", 4.2, 6.8);
        prop("entulho", 16.4, 8.6);
        prop("entulho", 12.0, 19.8);
        // a laje com os símbolos, no meio da câmara
        prop("laje_simbolos", 16.0, 18.5);
        // a luz que entra pelo buraco, sobre a escada
        prop("luz_buraco", 5.0, 5.0);

        spot("escada", 5.0, 5.0);
        spot("entrada", 6.5, 7.0);
        spot("corredor", 16.2, 11.6);
        spot("nicho", 19.0, 5.2);
        spot("camara", 16.5, 20.5);
        spot("camara_fundo", 19.5, 19.5);
        spawn("jogador", 6.8, 6.8);

        ObjectNode map = JSON.createObjectNode();
        map.put("type", "map").put("version", "1.10").put("tiledversion", "1.11.0")
                .put("orientation", "isometric").put("renderorder", "right-down")
                .put("infinite", false).put("compressionlevel", -1)
                .put("width", W).put("height", H).put("tilewidth", TW).put("tileheight", TH);
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
    }

    private void dig(int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) open(x, y);
    }

    private void open(int x, int y) {
        if (x < 1 || y < 1 || x >= W - 1 || y >= H - 1) return;
        terrain[y * W + x] = PEDRA;
        collision[y * W + x] = 0;
    }

    private void prop(String id, double x, double y) {
        props.add(point("", id, x, y));
    }

    private void spot(String name, double x, double y) {
        spots.add(point(name, "", x, y));
    }

    private void spawn(String name, double x, double y) {
        spawns.add(point(name, "", x, y));
    }

    /** Em mapas isométricos do Tiled, a posição dos objetos usa a altura do tile nos dois eixos. */
    private ObjectNode point(String name, String type, double x, double y) {
        return JSON.createObjectNode().put("id", nextId++).put("name", name).put("type", type)
                .put("x", x * TH).put("y", y * TH).put("width", 0).put("height", 0)
                .put("rotation", 0).put("visible", true).put("point", true);
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
        layer.put("id", id).put("name", name).put("type", "tilelayer").put("width", W).put("height", H)
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
