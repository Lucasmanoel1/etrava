package etrava.sim.world;

/**
 * Comércio ou contêiner aberto com o jogador. Enquanto existe, o relógio para
 * (RF-324). Não vai para o save: não se salva no meio de uma negociação.
 *
 * @param partnerId  ID do comerciante (merchants.yaml) ou do contêiner (containers.yaml)
 * @param container  true = contêiner (só pegar); false = comércio (comprar e vender)
 */
public record TradeSession(String partnerId, boolean container) {}
