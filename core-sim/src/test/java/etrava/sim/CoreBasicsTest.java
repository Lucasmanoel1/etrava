package etrava.sim;

import static org.junit.jupiter.api.Assertions.*;

import etrava.sim.nav.NavGrid;
import etrava.sim.nav.Pathfinder;
import etrava.sim.nav.Pathfinder.Point;
import etrava.sim.rng.SplitMix64;
import etrava.sim.time.GameClock;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreBasicsTest {

    // ---------- RNG ----------

    @Test
    void rngComMesmaSementeGeraMesmaSequencia() {
        SplitMix64 a = new SplitMix64(42), b = new SplitMix64(42), c = new SplitMix64(43);
        boolean differs = false;
        for (int i = 0; i < 100; i++) {
            long va = a.nextLong();
            assertEquals(va, b.nextLong());
            if (va != c.nextLong()) differs = true;
        }
        assertTrue(differs, "sementes diferentes devem divergir");
    }

    @Test
    void rngRestauraEstadoSalvo() {
        SplitMix64 a = new SplitMix64(7);
        a.nextLong();
        long saved = a.state();
        long expected = a.nextLong();
        SplitMix64 b = new SplitMix64(0);
        b.restore(saved);
        assertEquals(expected, b.nextLong());
    }

    @Test
    void rngRespeitaLimites() {
        SplitMix64 r = new SplitMix64(1);
        for (int i = 0; i < 1000; i++) {
            int n = r.nextInt(6);
            assertTrue(n >= 0 && n < 6);
            double d = r.nextDouble();
            assertTrue(d >= 0 && d < 1);
        }
        assertThrows(IllegalArgumentException.class, () -> r.nextInt(0));
        assertFalse(r.chance(0));
        assertTrue(r.chance(1));
    }

    // ---------- relógio ----------

    @Test
    void relogioComecaNoDia1As6() {
        GameClock clock = new GameClock(GameClock.DEFAULT_TICKS_PER_DAY);
        assertEquals(1, clock.day());
        assertEquals(6, clock.hour());
        assertEquals(0, clock.minute());
        assertEquals("Dia 1, 06:00", clock.format());
    }

    @Test
    void relogioAvancaHorasEDias() {
        GameClock clock = new GameClock(GameClock.DEFAULT_TICKS_PER_DAY);
        clock.advance(clock.ticksPerHour() * 2 + 25 * 30); // 2 h 30 min
        assertEquals("Dia 1, 08:30", clock.format());
        clock.advance(clock.ticksPerDay());
        assertEquals(2, clock.day());
        assertEquals(8, clock.hour());
        assertThrows(IllegalArgumentException.class, () -> new GameClock(10));
    }

    // ---------- navegação ----------

    /** Grade 10×10 m com um muro vertical em x=5, de y=0 a y=7. */
    private static NavGrid gridWithWall() {
        NavGrid grid = new NavGrid(20, 20);
        grid.blockRect(5, 0, 6, 8);
        return grid;
    }

    @Test
    void caminhoRetoQuandoNaoHaObstaculo() {
        NavGrid grid = new NavGrid(20, 20);
        List<Point> path = Pathfinder.findPath(grid, 1.2, 1.2, 8.3, 6.7);
        assertEquals(1, path.size(), "sem obstáculos o caminho é uma reta");
        assertEquals(8.3, path.get(0).x(), 1e-9);
        assertEquals(6.7, path.get(0).y(), 1e-9);
    }

    @Test
    void caminhoContornaMuro() {
        NavGrid grid = gridWithWall();
        List<Point> path = Pathfinder.findPath(grid, 2, 2, 8, 2);
        assertFalse(path.isEmpty());
        Point end = path.get(path.size() - 1);
        assertEquals(8, end.x(), 1e-9);
        assertEquals(2, end.y(), 1e-9);
        assertTrue(path.size() >= 2, "precisa de pelo menos um desvio");
        double px = 2, py = 2;
        for (Point p : path) {
            assertTrue(grid.lineClear(px, py, p.x(), p.y()), "trecho atravessa o muro");
            px = p.x();
            py = p.y();
        }
    }

    @Test
    void destinoBloqueadoLevaAoPontoAlcancavelMaisProximo() {
        NavGrid grid = gridWithWall();
        List<Point> path = Pathfinder.findPath(grid, 2, 2, 5.5, 3); // dentro do muro
        assertFalse(path.isEmpty());
        Point end = path.get(path.size() - 1);
        assertFalse(grid.isBlockedAt(end.x(), end.y()));
        assertTrue(Math.hypot(end.x() - 5.5, end.y() - 3) < 1.0, "deve parar encostado no muro");
    }

    @Test
    void destinoIsoladoNaoTravaABusca() {
        NavGrid grid = new NavGrid(20, 20);
        // sala fechada de 3×3 m em volta de (7, 7)
        grid.blockRect(5, 5, 9, 5.5);
        grid.blockRect(5, 8.5, 9, 9);
        grid.blockRect(5, 5, 5.5, 9);
        grid.blockRect(8.5, 5, 9, 9);
        List<Point> path = Pathfinder.findPath(grid, 1, 1, 7, 7);
        assertFalse(path.isEmpty());
        Point end = path.get(path.size() - 1);
        assertFalse(grid.isBlockedAt(end.x(), end.y()));
        boolean inside = end.x() > 5.5 && end.x() < 8.5 && end.y() > 5.5 && end.y() < 8.5;
        assertFalse(inside, "deve parar do lado de fora da sala");
    }

    @Test
    void semMovimentoPossivelDevolveVazio() {
        NavGrid grid = new NavGrid(4, 4);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) grid.setBlocked(x, y, !(x == 0 && y == 0));
        assertTrue(Pathfinder.findPath(grid, 0.25, 0.25, 1.5, 1.5).isEmpty());
        assertTrue(Pathfinder.findPath(grid, -5, -5, 1, 1).isEmpty(), "início fora do mapa");
    }

    @Test
    void foraDaGradeEhBloqueado() {
        NavGrid grid = new NavGrid(4, 4);
        assertTrue(grid.isBlocked(-1, 0));
        assertTrue(grid.isBlocked(0, 4));
        assertFalse(grid.isBlocked(3, 3));
        assertFalse(grid.lineClear(0.2, 0.2, 5, 5));
    }
}
