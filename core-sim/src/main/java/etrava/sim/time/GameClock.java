package etrava.sim.time;

/**
 * Relógio do jogo em ticks (D-007). A simulação nunca lê o relógio do sistema.
 * Padrão: 30 ticks por segundo real e 36.000 ticks por dia (1 dia = 20 min reais).
 */
public final class GameClock {
    public static final int TICKS_PER_SECOND = 30;
    public static final long DEFAULT_TICKS_PER_DAY = 36_000;
    /** O jogo começa no dia 1, às 06:00. */
    public static final int START_HOUR = 6;

    private final long ticksPerDay;
    private long tick;

    public GameClock(long ticksPerDay) {
        if (ticksPerDay < 1440) throw new IllegalArgumentException("ticksPerDay muito pequeno");
        this.ticksPerDay = ticksPerDay;
        this.tick = ticksPerDay * START_HOUR / 24;
    }

    public long tick() { return tick; }
    public long ticksPerDay() { return ticksPerDay; }
    public void setTick(long tick) { this.tick = tick; }
    public void advance(long ticks) { this.tick += ticks; }

    /** Dia de jogo, começando em 1. */
    public int day() { return (int) (tick / ticksPerDay) + 1; }

    public int minuteOfDay() { return (int) ((tick % ticksPerDay) * 1440 / ticksPerDay); }
    public int hour() { return minuteOfDay() / 60; }
    public int minute() { return minuteOfDay() % 60; }

    public long ticksPerHour() { return ticksPerDay / 24; }

    public String format() {
        return String.format("Dia %d, %02d:%02d", day(), hour(), minute());
    }
}
