package etrava.sim.nav;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A* em grade de 8 direções, sem cortar quinas. Se o destino for inalcançável,
 * devolve o caminho até o ponto alcançável mais próximo dele (RF-001).
 * Determinístico: empates são resolvidos pelo índice da célula.
 */
public final class Pathfinder {
    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DY = {0, 0, 1, -1, 1, -1, 1, -1};
    private static final double DIAGONAL = Math.sqrt(2);

    private Pathfinder() {}

    /** Ponto de mundo (x, y). */
    public record Point(double x, double y) {}

    /**
     * @return pontos de passagem do início (exclusivo) ao destino (inclusivo);
     *         vazio se o início já for o melhor ponto possível.
     */
    public static List<Point> findPath(NavGrid grid, double startX, double startY, double goalX, double goalY) {
        int w = grid.width(), h = grid.height();
        int sx = NavGrid.cell(startX), sy = NavGrid.cell(startY);
        int gx = clamp(NavGrid.cell(goalX), 0, w - 1), gy = clamp(NavGrid.cell(goalY), 0, h - 1);
        if (!grid.inBounds(sx, sy)) return List.of();

        int start = sy * w + sx, goal = gy * w + gx;
        double[] g = new double[w * h];
        Arrays.fill(g, Double.POSITIVE_INFINITY);
        int[] parent = new int[w * h];
        Arrays.fill(parent, -1);
        boolean[] closed = new boolean[w * h];
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> {
            int c = Double.compare(a[0], b[0]);
            return c != 0 ? c : Double.compare(a[1], b[1]);
        });

        g[start] = 0;
        open.add(new double[] {heuristic(sx, sy, gx, gy), start});
        int best = start;
        double bestH = heuristic(sx, sy, gx, gy);
        boolean reached = false;

        while (!open.isEmpty()) {
            int current = (int) open.poll()[1];
            if (closed[current]) continue;
            closed[current] = true;
            int cx = current % w, cy = current / w;
            double hc = heuristic(cx, cy, gx, gy);
            if (hc < bestH) {
                bestH = hc;
                best = current;
            }
            if (current == goal) {
                reached = true;
                break;
            }
            for (int d = 0; d < 8; d++) {
                int nx = cx + DX[d], ny = cy + DY[d];
                if (grid.isBlocked(nx, ny)) continue;
                if (d >= 4 && (grid.isBlocked(cx + DX[d], cy) || grid.isBlocked(cx, cy + DY[d]))) continue;
                int n = ny * w + nx;
                if (closed[n]) continue;
                double cost = g[current] + (d >= 4 ? DIAGONAL : 1);
                if (cost < g[n]) {
                    g[n] = cost;
                    parent[n] = current;
                    open.add(new double[] {cost + heuristic(nx, ny, gx, gy), n});
                }
            }
        }

        int end = reached ? goal : best;
        if (end == start && !reached) return List.of();

        List<Point> cells = new ArrayList<>();
        for (int c = end; c != start && c != -1; c = parent[c]) {
            cells.add(new Point(NavGrid.center(c % w), NavGrid.center(c / w)));
        }
        Collections.reverse(cells);
        // chega no ponto exato clicado, quando ele é o destino alcançado
        if (reached && !grid.isBlockedAt(goalX, goalY)) {
            if (cells.isEmpty()) cells.add(new Point(goalX, goalY));
            else cells.set(cells.size() - 1, new Point(goalX, goalY));
        }
        return smooth(grid, startX, startY, cells);
    }

    /** Remove pontos intermediários desnecessários (linha de visão direta). */
    private static List<Point> smooth(NavGrid grid, double startX, double startY, List<Point> path) {
        if (path.size() <= 1) return path;
        List<Point> out = new ArrayList<>();
        double ax = startX, ay = startY;
        int i = 0;
        while (i < path.size()) {
            int far = i;
            for (int j = path.size() - 1; j > i; j--) {
                if (grid.lineClear(ax, ay, path.get(j).x(), path.get(j).y())) {
                    far = j;
                    break;
                }
            }
            Point p = path.get(far);
            out.add(p);
            ax = p.x();
            ay = p.y();
            i = far + 1;
        }
        return out;
    }

    private static double heuristic(int x, int y, int gx, int gy) {
        int dx = Math.abs(x - gx), dy = Math.abs(y - gy);
        return Math.max(dx, dy) + (DIAGONAL - 1) * Math.min(dx, dy);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
