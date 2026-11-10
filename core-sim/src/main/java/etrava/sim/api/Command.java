package etrava.sim.api;

/**
 * Toda entrada do jogador chega à simulação como um comando. Gravar os comandos
 * com o tick em que foram aplicados permite reexecutar uma partida (AT-03).
 */
public sealed interface Command {

    /** Andar até um ponto do mundo (clique no terreno). */
    record MoveTo(double x, double y) implements Command {}

    /** Andar numa direção do mundo (WASD). (0, 0) para parar. */
    record MoveDir(double dx, double dy) implements Command {}

    /** Esperar de 1 a 12 horas de jogo (RF-322). */
    record Wait(int hours) implements Command {}

    /** Dormir até a manhã seguinte, 06:00 (RF-322). */
    record Sleep() implements Command {}

    /** Ir para outra área, entrando pelo ponto de chegada indicado. */
    record EnterArea(String areaId, String spawn) implements Command {}

    /** Depuração: teletransporta o jogador. */
    record Teleport(double x, double y) implements Command {}

    /** Um comando e o tick em que foi aplicado. */
    record Timed(long tick, Command command) {}
}
