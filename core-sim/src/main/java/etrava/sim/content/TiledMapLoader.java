package etrava.sim.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import etrava.sim.nav.Pathfinder.Point;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê mapas do Tiled exportados em JSON (.tmj) — D-010.
 * Camadas reconhecidas: {@code terrain} e {@code collision} (tiles);
 * {@code props}, {@code spots} e {@code spawns} (objetos).
 * Exigências: tileset embutido no mapa e camadas de tile em formato CSV, sem compressão.
 */
public final class TiledMapLoader {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long GID_MASK = 0x1FFFFFFFL;

    private TiledMapLoader() {}

    public static AreaDef load(String id, Path file) throws IOException {
        JsonNode root = JSON.readTree(file.toFile());
        int width = root.path("width").asInt();
        int height = root.path("height").asInt();
        if (width <= 0 || height <= 0) throw new IOException("mapa sem largura/altura");

        // Em mapas isométricos do Tiled, objetos usam tileheight nos dois eixos.
        boolean iso = "isometric".equals(root.path("orientation").asText());
        double unitX = iso ? root.path("tileheight").asDouble(1) : root.path("tilewidth").asDouble(1);
        double unitY = root.path("tileheight").asDouble(1);

        Map<Long, String> terrainByGid = new HashMap<>();
        for (JsonNode ts : root.path("tilesets")) {
            if (ts.has("source")) throw new IOException("tileset externo não suportado: embuta o tileset no mapa");
            long first = ts.path("firstgid").asLong();
            for (JsonNode tile : ts.path("tiles")) {
                String terrain = property(tile, "terrain");
                if (terrain != null) terrainByGid.put(first + tile.path("id").asLong(), terrain);
            }
        }

        String[] terrain = new String[width * height];
        boolean[] collision = new boolean[width * height];
        List<PropPlacement> props = new ArrayList<>();
        Map<String, Point> spots = new LinkedHashMap<>();
        Map<String, Point> spawns = new LinkedHashMap<>();

        for (JsonNode layer : root.path("layers")) {
            String name = layer.path("name").asText();
            String type = layer.path("type").asText();
            if ("tilelayer".equals(type)) {
                JsonNode data = layer.path("data");
                if (!data.isArray() || data.size() != width * height)
                    throw new IOException("camada '" + name + "': use formato CSV sem compressão");
                for (int i = 0; i < data.size(); i++) {
                    long gid = data.get(i).asLong() & GID_MASK;
                    if (gid == 0) continue;
                    if ("terrain".equals(name)) terrain[i] = terrainByGid.getOrDefault(gid, "desconhecido");
                    else if ("collision".equals(name)) collision[i] = true;
                }
            } else if ("objectgroup".equals(type)) {
                for (JsonNode obj : layer.path("objects")) {
                    double x = obj.path("x").asDouble() / unitX;
                    double y = obj.path("y").asDouble() / unitY;
                    String objName = obj.path("name").asText("");
                    String objClass = obj.path("type").asText(obj.path("class").asText(""));
                    switch (name) {
                        case "props" -> {
                            String propId = !objClass.isEmpty() ? objClass : objName;
                            String key = property(obj, "key");
                            props.add(new PropPlacement(propId, key, x, y, obj.path("rotation").asDouble()));
                        }
                        case "spots" -> spots.put(objName, new Point(x, y));
                        case "spawns" -> spawns.put(objName, new Point(x, y));
                        default -> { /* camada de objetos ainda não usada */ }
                    }
                }
            }
        }
        return new AreaDef(id, width, height, terrain, collision, List.copyOf(props), spots, spawns);
    }

    private static String property(JsonNode node, String name) {
        for (JsonNode p : node.path("properties")) {
            if (name.equals(p.path("name").asText())) return p.path("value").asText();
        }
        return null;
    }
}
