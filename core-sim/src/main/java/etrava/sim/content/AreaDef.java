package etrava.sim.content;

import etrava.sim.nav.NavGrid;
import etrava.sim.nav.Pathfinder.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Área jogável, lida de um mapa do Tiled (.tmj). Grade em tiles de 1 m;
 * x cresce para leste e y para sul.
 */
public record AreaDef(
        String id,
        int width,
        int height,
        String[] terrain,
        boolean[] collision,
        List<PropPlacement> props,
        Map<String, Point> spots,
        Map<String, Point> spawns,
        List<ActorSpawn> actors) {

    public static final String PLAYER_SPAWN = "jogador";
    public static final String NO_TERRAIN = "vazio";

    public String terrainAt(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= width || ty >= height) return NO_TERRAIN;
        String t = terrain[ty * width + tx];
        return t == null ? NO_TERRAIN : t;
    }

    public boolean collisionAt(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= width || ty >= height) return true;
        return collision[ty * width + tx];
    }

    /** Tipos de terreno usados no mapa, em ordem alfabética. */
    public Set<String> terrainsUsed() {
        Set<String> used = new TreeSet<>();
        for (String t : terrain) if (t != null) used.add(t);
        return used;
    }

    /** Monta a grade de navegação: colisão do mapa + base dos props que bloqueiam. */
    public NavGrid buildNavGrid(Map<String, PropDef> propDefs) {
        NavGrid grid = new NavGrid(width * NavGrid.CELLS_PER_TILE, height * NavGrid.CELLS_PER_TILE);
        for (int ty = 0; ty < height; ty++)
            for (int tx = 0; tx < width; tx++)
                if (collision[ty * width + tx]) grid.blockRect(tx, ty, tx + 1, ty + 1);
        for (PropPlacement p : props) {
            PropDef def = propDefs.get(p.propId());
            if (def == null || !def.blocks()) continue;
            double hw = def.width() / 2, hd = def.depth() / 2;
            grid.blockRect(p.x() - hw, p.y() - hd, p.x() + hw, p.y() + hd);
        }
        return grid;
    }
}
