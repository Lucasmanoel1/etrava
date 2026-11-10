package etrava.sim.nav;

/**
 * Grade de navegação (D-011). Cada tile do mapa (1 m) vira
 * {@link #CELLS_PER_TILE}² células. Coordenadas de mundo em metros/tiles.
 */
public final class NavGrid {
    public static final int CELLS_PER_TILE = 2;
    public static final double CELL_SIZE = 1.0 / CELLS_PER_TILE;

    private final int width;
    private final int height;
    private final boolean[] blocked;

    public NavGrid(int widthCells, int heightCells) {
        this.width = widthCells;
        this.height = heightCells;
        this.blocked = new boolean[widthCells * heightCells];
    }

    public int width() { return width; }
    public int height() { return height; }

    public boolean inBounds(int cx, int cy) {
        return cx >= 0 && cy >= 0 && cx < width && cy < height;
    }

    public boolean isBlocked(int cx, int cy) {
        return !inBounds(cx, cy) || blocked[cy * width + cx];
    }

    public void setBlocked(int cx, int cy, boolean value) {
        if (inBounds(cx, cy)) blocked[cy * width + cx] = value;
    }

    /** Bloqueia todas as células tocadas pelo retângulo de mundo [x0,x1) × [y0,y1). */
    public void blockRect(double x0, double y0, double x1, double y1) {
        int cx0 = cell(x0), cy0 = cell(y0);
        int cx1 = cell(x1 - 1e-9), cy1 = cell(y1 - 1e-9);
        for (int cy = cy0; cy <= cy1; cy++)
            for (int cx = cx0; cx <= cx1; cx++) setBlocked(cx, cy, true);
    }

    public static int cell(double worldCoord) {
        return (int) Math.floor(worldCoord / CELL_SIZE);
    }

    public static double center(int cell) {
        return (cell + 0.5) * CELL_SIZE;
    }

    public boolean isBlockedAt(double x, double y) {
        return isBlocked(cell(x), cell(y));
    }

    /** Linha reta livre entre dois pontos de mundo (amostragem fina). */
    public boolean lineClear(double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        double dist = Math.sqrt(dx * dx + dy * dy);
        int steps = Math.max(1, (int) Math.ceil(dist / (CELL_SIZE * 0.25)));
        int prevCx = cell(x0), prevCy = cell(y0);
        if (isBlocked(prevCx, prevCy)) return false;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            int cx = cell(x0 + dx * t), cy = cell(y0 + dy * t);
            if (cx == prevCx && cy == prevCy) continue;
            if (isBlocked(cx, cy)) return false;
            // não deixa a linha "cortar" a quina entre duas células bloqueadas
            if (cx != prevCx && cy != prevCy && (isBlocked(prevCx, cy) || isBlocked(cx, prevCy))) return false;
            prevCx = cx;
            prevCy = cy;
        }
        return true;
    }
}
