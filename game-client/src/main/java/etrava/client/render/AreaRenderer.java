package etrava.client.render;

import static etrava.client.render.IsoProjection.sx;
import static etrava.client.render.IsoProjection.sy;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import etrava.sim.content.AreaDef;
import etrava.sim.content.PropDef;
import etrava.sim.content.PropPlacement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Desenha uma área só com placeholders (RF-421): chão colorido pelo tipo de
 * terreno, paredes e props como caixas coloridas pela categoria, jogador como
 * um boneco simples. A arte de verdade entra no M1 sem mudar quem usa esta classe.
 */
public final class AreaRenderer {
    private static final Logger LOG = LoggerFactory.getLogger(AreaRenderer.class);
    private static final float WALL_HEIGHT = 2.4f;
    private static final float PLAYER_HEIGHT_PX = 96f;
    private static final float OCCLUDED_ALPHA = 0.35f;

    private record Box(double x0, double y0, double x1, double y1, double h, float r, float g, float b, double key) {}

    private final AreaDef area;
    private final Box[] boxes;
    private final Set<String> warned = new HashSet<>();

    public AreaRenderer(AreaDef area, Map<String, PropDef> props) {
        this.area = area;
        List<Box> list = new ArrayList<>();
        for (int ty = 0; ty < area.height(); ty++)
            for (int tx = 0; tx < area.width(); tx++)
                if (area.collisionAt(tx, ty)) list.add(box(tx, ty, tx + 1, ty + 1, WALL_HEIGHT, 0.37f, 0.31f, 0.28f));
        for (PropPlacement p : area.props()) {
            PropDef def = props.get(p.propId());
            if (def == null) {
                warnOnce("prop sem definição: '" + p.propId() + "' — usando placeholder");
                list.add(box(p.x() - 0.3, p.y() - 0.3, p.x() + 0.3, p.y() + 0.3, 0.6, 0.85f, 0.1f, 0.75f));
                continue;
            }
            float[] c = categoryColor(def.category());
            double hw = def.width() / 2, hd = def.depth() / 2;
            list.add(box(p.x() - hw, p.y() - hd, p.x() + hw, p.y() + hd, def.height(), c[0], c[1], c[2]));
        }
        list.sort(Comparator.comparingDouble(Box::key));
        this.boxes = list.toArray(new Box[0]);
    }

    private static Box box(double x0, double y0, double x1, double y1, double h, float r, float g, float b) {
        return new Box(x0, y0, x1, y1, h, r, g, b, (x0 + x1) / 2 + (y0 + y1) / 2);
    }

    private void warnOnce(String message) {
        if (warned.add(message)) LOG.warn(message);
    }

    private float[] categoryColor(String category) {
        return switch (category == null ? "" : category) {
            case "objeto" -> new float[] {0.62f, 0.46f, 0.26f};
            case "vegetacao" -> new float[] {0.24f, 0.42f, 0.22f};
            case "construcao" -> new float[] {0.52f, 0.52f, 0.54f};
            case "mobilia" -> new float[] {0.46f, 0.33f, 0.21f};
            default -> {
                warnOnce("categoria de prop sem cor de placeholder: '" + category + "'");
                yield new float[] {0.85f, 0.1f, 0.75f};
            }
        };
    }

    private float[] terrainColor(String terrain) {
        return switch (terrain) {
            case "grama" -> new float[] {0.33f, 0.40f, 0.24f};
            case "terra" -> new float[] {0.50f, 0.39f, 0.27f};
            case "pedra" -> new float[] {0.45f, 0.44f, 0.41f};
            case AreaDef.NO_TERRAIN -> new float[] {0.10f, 0.10f, 0.11f};
            default -> {
                warnOnce("terreno sem cor de placeholder: '" + terrain + "'");
                yield new float[] {0.85f, 0.1f, 0.75f};
            }
        };
    }

    /**
     * @param sr     já iniciado em modo preenchido, com a matriz da câmera e blending ligado
     * @param bounds retângulo visível em pixels de mundo {minX, minY, maxX, maxY}
     */
    public void render(ShapeRenderer sr, float[] bounds, double px, double py, double facing,
            boolean hasDestination, double destX, double destY) {
        drawGround(sr, bounds);

        if (hasDestination) {
            sr.setColor(0.95f, 0.88f, 0.6f, 0.55f);
            sr.ellipse(sx(destX, destY) - 14, sy(destX, destY, 0) - 7, 28, 14);
        }

        double playerKey = px + py;
        float psx = sx(px, py), psy = sy(px, py, 0);
        boolean playerDrawn = false;
        for (Box b : boxes) {
            if (!playerDrawn && b.key() > playerKey) {
                drawPlayer(sr, px, py, facing);
                playerDrawn = true;
            }
            float minX = sx(b.x0(), b.y1()), maxX = sx(b.x1(), b.y0());
            float minY = sy(b.x1(), b.y1(), 0), maxY = sy(b.x0(), b.y0(), b.h());
            if (maxX < bounds[0] || minX > bounds[2] || maxY < bounds[1] || minY > bounds[3]) continue;
            // caixa na frente do jogador e cobrindo-o: fica translúcida (RF-023)
            boolean hides = playerDrawn && psx > minX - 12 && psx < maxX + 12
                    && psy + PLAYER_HEIGHT_PX > minY && psy < maxY;
            drawBox(sr, b, hides ? OCCLUDED_ALPHA : 1f);
        }
        if (!playerDrawn) drawPlayer(sr, px, py, facing);
    }

    private void drawGround(ShapeRenderer sr, float[] bounds) {
        for (int ty = 0; ty < area.height(); ty++) {
            for (int tx = 0; tx < area.width(); tx++) {
                float cx = sx(tx + 0.5, ty + 0.5), cy = sy(tx + 0.5, ty + 0.5, 0);
                if (cx + IsoProjection.HALF_W < bounds[0] || cx - IsoProjection.HALF_W > bounds[2]
                        || cy + IsoProjection.HALF_H < bounds[1] || cy - IsoProjection.HALF_H > bounds[3]) continue;
                float[] c = terrainColor(area.terrainAt(tx, ty));
                float shade = ((tx + ty) & 1) == 0 ? 1f : 0.95f;
                sr.setColor(c[0] * shade, c[1] * shade, c[2] * shade, 1f);
                quad(sr, tx, ty, 0, tx + 1, ty, 0, tx + 1, ty + 1, 0, tx, ty + 1, 0);
            }
        }
    }

    private void drawBox(ShapeRenderer sr, Box b, float alpha) {
        double x0 = b.x0(), y0 = b.y0(), x1 = b.x1(), y1 = b.y1(), h = b.h();
        sr.setColor(b.r() * 0.80f, b.g() * 0.80f, b.b() * 0.80f, alpha); // face sudoeste
        quad(sr, x0, y1, 0, x1, y1, 0, x1, y1, h, x0, y1, h);
        sr.setColor(b.r() * 0.62f, b.g() * 0.62f, b.b() * 0.62f, alpha); // face sudeste
        quad(sr, x1, y1, 0, x1, y0, 0, x1, y0, h, x1, y1, h);
        sr.setColor(b.r(), b.g(), b.b(), alpha); // topo
        quad(sr, x0, y0, h, x1, y0, h, x1, y1, h, x0, y1, h);
    }

    private void drawPlayer(ShapeRenderer sr, double px, double py, double facing) {
        float x = sx(px, py), y = sy(px, py, 0);
        sr.setColor(0f, 0f, 0f, 0.35f);
        sr.ellipse(x - 20, y - 10, 40, 20);
        // indicador de direção, no chão
        double fx = px + Math.cos(facing) * 0.55, fy = py + Math.sin(facing) * 0.55;
        sr.setColor(0.95f, 0.9f, 0.7f, 0.9f);
        sr.circle(sx(fx, fy), sy(fx, fy, 0), 4, 12);
        sr.setColor(0.72f, 0.52f, 0.24f, 1f);
        sr.rect(x - 11, y + 4, 22, 68);
        sr.circle(x, y + 72, 11, 16);
        sr.setColor(0.86f, 0.70f, 0.55f, 1f);
        sr.circle(x, y + PLAYER_HEIGHT_PX - 10, 11, 18);
    }

    private static void quad(ShapeRenderer sr, double ax, double ay, double az, double bx, double by, double bz,
            double cx, double cy, double cz, double dx, double dy, double dz) {
        float x1 = sx(ax, ay), y1 = sy(ax, ay, az);
        float x2 = sx(bx, by), y2 = sy(bx, by, bz);
        float x3 = sx(cx, cy), y3 = sy(cx, cy, cz);
        float x4 = sx(dx, dy), y4 = sy(dx, dy, dz);
        sr.triangle(x1, y1, x2, y2, x3, y3);
        sr.triangle(x1, y1, x3, y3, x4, y4);
    }
}
