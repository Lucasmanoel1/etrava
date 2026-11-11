package etrava.sim.api;

import java.util.List;

/**
 * O comércio (ou contêiner) aberto, já com os preços calculados, para a tela de
 * comércio (PRD 10.2): duas colunas, preço, dinheiro dos dois lados e o efeito da opinião.
 *
 * @param partnerCash    caixa do comerciante; -1 num contêiner
 * @param opinionPercent quanto a opinião do comerciante mexe no preço de compra, em % (negativo = desconto)
 * @param offers         o que o outro lado tem (preço 0 num contêiner)
 * @param wants          o que o jogador tem e o comerciante compra
 * @param owner          dono do contêiner (nome), ou null
 */
public record TradeView(String partnerId, String name, boolean container, int partnerCash, int opinionPercent,
        List<Offer> offers, List<Offer> wants, String owner) {

    /** @param possible dá para fechar o negócio agora (dinheiro, caixa) */
    public record Offer(String item, String name, int qty, int price, boolean possible) {}
}
