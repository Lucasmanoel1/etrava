package etrava.tools.maps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

/**
 * Gera {@code content/areas/vila_ref.tmj}: a cena de referência do M1 (praça de
 * um vilarejo), no formato do Tiled. Depois de gerado, o mapa pode ser editado
 * no Tiled normalmente; rodar este gerador de novo sobrescreve o arquivo.
 */
public final class GenerateRefMap {
    private static final int W = 48, H = 48, TW = 128, TH = 64;
    private static final int GRAMA = 1, TERRA = 2, PEDRA = 3, BLOQUEIO = 4;
    private static final String[] TERRAINS = {"grama", "terra", "pedra", "parede"};
    private static final ObjectMapper JSON = new ObjectMapper();

    private final int[] terrain = new int[W * H];
    private final int[] collision = new int[W * H];
    private final ArrayNode props = JSON.createArrayNode();
    private final ArrayNode spots = JSON.createArrayNode();
    private final ArrayNode spawns = JSON.createArrayNode();
    private int nextId = 1;

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "content/areas/vila_ref.tmj");
        new GenerateRefMap().build(out);
        System.out.println("Mapa gerado: " + out.toAbsolutePath());
    }

    private void build(Path out) throws IOException {
        Random rnd = new Random(7);
        java.util.Arrays.fill(terrain, GRAMA);
        for (int i = 0; i < W; i++) { // borda invisível
            collision[i] = collision[(H - 1) * W + i] = BLOQUEIO;
            collision[i * W] = collision[i * W + W - 1] = BLOQUEIO;
        }
        // estradas de terra com beirada irregular, e a praça calçada em volta do poço
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double wobble = Math.sin(x * 0.7) * 0.6 + Math.sin(y * 0.9 + 1) * 0.6;
                boolean roadNS = Math.abs(x - 24 + wobble * 0.5) < 1.9 && y > 22;
                boolean roadEW = Math.abs(y - 23.5 + wobble * 0.5) < 1.7;
                boolean toOficina = Math.abs(x - 14.5) < 1.1 && y >= 18 && y < 23;
                boolean toTaverna = Math.abs(x - 33.5) < 1.3 && y >= 18 && y < 23;
                if (roadNS || roadEW || toOficina || toTaverna) terrain[y * W + x] = TERRA;
                double d = Math.hypot(x + 0.5 - 24, y + 0.5 - 24);
                if (d < 5.2 + wobble * 0.4) terrain[y * W + x] = PEDRA;
            }
        }

        prop("casa_oficina", 14.5, 15.5);
        prop("casa_taverna", 33.5, 15.0);
        prop("casa_pequena", 13.5, 32.5);
        prop("casa_moinho", 35.5, 33.0);
        prop("poco", 24.0, 24.0);

        prop("lampiao", 20.8, 21.2);
        prop("lampiao", 31.6, 18.8);
        prop("lampiao", 16.8, 18.8);
        prop("lampiao", 26.6, 33.4);
        prop("lampiao", 17.2, 30.6);

        prop("barril", 28.4, 18.6);
        prop("barril", 29.3, 18.9);
        prop("barril", 28.9, 19.6);
        prop("caixote", 36.6, 18.7);
        prop("caixote", 37.6, 18.6);
        prop("caixote", 19.0, 14.5);
        prop("caixote", 19.0, 15.6);
        prop("barril", 19.1, 16.7);
        prop("feno", 18.4, 33.6);
        prop("feno", 19.6, 35.4);
        prop("feno", 31.4, 35.2);
        prop("caixote", 31.6, 31.4);

        // árvores: um anel irregular em volta do vilarejo e algumas soltas
        for (int i = 0; i < 64; i++) {
            double angle = i * Math.PI * 2 / 64 + rnd.nextDouble() * 0.08;
            double radius = 18.5 + rnd.nextDouble() * 4.5;
            double x = 24 + Math.cos(angle) * radius, y = 24 + Math.sin(angle) * radius;
            tree(rnd, x, y);
        }
        tree(rnd, 8.5, 22.5);
        tree(rnd, 20.5, 9.5);
        tree(rnd, 27.5, 8.5);
        tree(rnd, 40.5, 24.5);
        tree(rnd, 28.5, 30.5);
        tree(rnd, 9.5, 27.5);
        prop("arvore_morta", 21.5, 39.5);

        spot("praca", 26.5, 27.0);
        spot("poco", 22.2, 25.6);
        spot("oficina_porta", 14.5, 19.2);
        spot("taverna_porta", 33.5, 19.2);
        spot("estrada_sul", 24.5, 38.5);
        spot("moinho_porta", 35.0, 37.0);
        spot("casa_porta", 17.0, 32.5);
        spawn("jogador", "", 24.5, 29.5, null, null);
        spawn("aldea", "figurante", 30.5, 22.5, "personagem.aldea",
                "praca,poco,taverna_porta,estrada_sul,moinho_porta,casa_porta,oficina_porta");

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

    private void tree(Random rnd, double x, double y) {
        if (x < 2 || y < 2 || x > W - 2 || y > H - 2) return;
        // não plantar árvore em cima de estrada ou praça
        if (terrain[(int) y * W + (int) x] != GRAMA) return;
        prop(rnd.nextDouble() < 0.5 ? "arvore" : "arvore_b", Math.floor(x) + 0.5, Math.floor(y) + 0.5);
    }

    private void prop(String id, double x, double y) {
        props.add(point("", id, x, y));
    }

    private void spot(String name, double x, double y) {
        spots.add(point(name, "", x, y));
    }

    private void spawn(String name, String type, double x, double y, String sprite, String patrol) {
        ObjectNode obj = point(name, type, x, y);
        if (sprite != null) {
            ArrayNode properties = JSON.createArrayNode();
            properties.add(JSON.createObjectNode().put("name", "sprite").put("type", "string").put("value", sprite));
            properties.add(JSON.createObjectNode().put("name", "patrol").put("type", "string").put("value", patrol));
            obj.set("properties", properties);
        }
        spawns.add(obj);
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
