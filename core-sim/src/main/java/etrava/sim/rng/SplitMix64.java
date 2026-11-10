package etrava.sim.rng;

/**
 * Gerador aleatório único da simulação (D-008). O estado é um {@code long},
 * o que torna salvar e restaurar trivial e garante determinismo (RF-144).
 */
public final class SplitMix64 {
    private long state;

    public SplitMix64(long seed) {
        this.state = seed;
    }

    public long state() {
        return state;
    }

    public void restore(long savedState) {
        this.state = savedState;
    }

    public long nextLong() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Inteiro uniforme em [0, bound). */
    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound deve ser positivo");
        return (int) Math.floorMod(nextLong(), (long) bound);
    }

    /** Real uniforme em [0, 1). */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public boolean chance(double probability) {
        return nextDouble() < probability;
    }
}
