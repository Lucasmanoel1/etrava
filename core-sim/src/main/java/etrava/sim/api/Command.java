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

    /** Falar com um NPC: o personagem se aproxima e a conversa começa (RF-004). */
    record Talk(String npcId) implements Command {}

    /** Escolher uma das opções visíveis do diálogo (0 = a primeira). */
    record Choose(int index) implements Command {}

    /** Seguir adiante num trecho de diálogo sem escolhas. */
    record Advance() implements Command {}

    /** Abandonar a conversa. */
    record LeaveDialogue() implements Command {}

    /** Durante uma conversa, perguntar ao NPC sobre um fato do caderno (RF-126). */
    record AskAbout(String factId) implements Command {}

    /** Marcar ou desmarcar um fato do caderno como de interesse. */
    record MarkFact(String factId, boolean marked) implements Command {}

    /** Comer, beber ou usar um item da mochila. Vale a qualquer hora. */
    record UseItem(String itemId) implements Command {}

    /** Vestir ou empunhar um item da mochila. */
    record Equip(String itemId) implements Command {}

    /** Tirar o que está num encaixe (mainHand, offHand, body, head, accessory). */
    record Unequip(String slot) implements Command {}

    /** Trabalhar (RF-264). Normalmente vem de uma conversa; o comando direto serve ao console e à política headless. */
    record Work(String jobId) implements Command {}

    /** Abrir o comércio de um comerciante (normalmente pela conversa com ele). */
    record OpenTrade(String merchantId) implements Command {}

    /** Ir até um contêiner e abri-lo. */
    record OpenContainer(String containerId) implements Command {}

    /** Com o comércio aberto: comprar uma unidade. */
    record Buy(String itemId) implements Command {}

    /** Com o comércio aberto: vender uma unidade. */
    record Sell(String itemId) implements Command {}

    /** Com um contêiner aberto: pegar uma unidade. */
    record TakeItem(String itemId) implements Command {}

    record CloseTrade() implements Command {}

    // ---- combate (M6)

    /**
     * Atacar um inimigo (RF-220): o personagem vai até ele e golpeia. Com {@code hold},
     * continua golpeando até soltar ({@link ReleaseAttack}) ou o alvo cair, fugir ou se render.
     */
    record Attack(String enemyId, boolean hold) implements Command {}

    /** Soltou o botão: termina o golpe em curso e para de atacar. */
    record ReleaseAttack() implements Command {}

    /** Habilidade de combate (RF-222): golpe_pesado | empurrao, contra o alvo atual ou o inimigo mais perto ao alcance. */
    record Ability(String ability) implements Command {}

    /** Esquivar numa direção do mundo (gasta fôlego; durante a esquiva nada acerta). (0, 0) = para trás. */
    record Dodge(double dx, double dy) implements Command {}

    /** Diante de um inimigo rendido (RF-224): poupar | saquear | executar. */
    record Resolve(String enemyId, String action) implements Command {}

    /** O jogador se rende aos saqueadores: levam o dinheiro e vão embora. */
    record Surrender() implements Command {}

    /** Agredir um NPC (ato "agressao"): um empurrão, não uma luta. */
    record Strike(String npcId) implements Command {}

    /** Depuração: teletransporta o jogador. */
    record Teleport(double x, double y) implements Command {}

    /** Um comando e o tick em que foi aplicado. */
    record Timed(long tick, Command command) {}
}
