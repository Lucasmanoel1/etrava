package etrava.sim.api;

/** Vista somente leitura de um personagem, para a apresentação. */
public interface ActorView {
    String id();
    /** ID do asset de folha de sprites. */
    String sprite();
    double x();
    double y();
    /** Direção para onde está virado, em radianos no plano do mundo. */
    double facing();
    boolean moving();
    /** O que está fazendo, segundo a agenda (vazio para o jogador). */
    String activity();
}
