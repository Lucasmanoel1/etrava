package etrava.sim.content;

import java.util.List;
import java.util.Map;

/**
 * Um tipo de inimigo (content/enemies.yaml, RF-223). Tempos em segundos reais
 * de combate; distâncias em metros.
 *
 * @param kind       animal | humano | estranho. Só humanos se rendem (RF-224)
 * @param health     vida máxima
 * @param damage     dano-base de um golpe (antes da proteção do jogador)
 * @param protection quanto tira de cada golpe recebido
 * @param skill      "Luta" do inimigo, 0–100: mexe na chance de acertar e de ser acertado
 * @param speed      velocidade correndo, m/s
 * @param reach      alcance do golpe
 * @param attackTime intervalo entre golpes
 * @param windup     quanto o golpe demora a sair depois de anunciado (dá tempo de esquivar)
 * @param aggro      a que distância percebe o jogador e parte para cima
 * @param leash      quanto se afasta de onde apareceu antes de desistir
 * @param flee       fração da vida em que foge (0 = luta até o fim)
 * @param surrender  chance de, na hora de fugir, se render em vez disso (só humanos)
 * @param lethal     derrotar o jogador mata de verdade (RF-341); o padrão é a derrota não letal
 * @param robs       quando vence (ou o jogador se rende), leva o dinheiro
 * @param fearsLight hesita na luz de uma lamparina: mais lento, golpeia menos, apanha mais
 * @param loot       o que leva consigo; vai para o jogador se ele o saquear ou abater
 * @param money      moedas que leva consigo
 * @param lines      falas curtas: ataque, rende, foge, vence, poupado
 */
public record EnemyDef(String id, String name, String kind, String sprite, double health, double damage,
        double protection, double skill, double speed, double reach, double attackTime, double windup, double aggro,
        double leash, double flee, double surrender, boolean lethal, boolean robs, boolean fearsLight,
        List<ItemDef.Stack> loot, int money, Map<String, String> lines) {

    public static final List<String> KINDS = List.of("animal", "humano", "estranho");

    public String displayName() { return name != null ? name : id; }
    public String spriteId() { return sprite != null ? sprite : "personagem." + id; }
    public boolean human() { return "humano".equals(kind); }
    public List<ItemDef.Stack> lootList() { return loot != null ? loot : List.of(); }
    public double reachOr() { return reach > 0 ? reach : 1.4; }
    public double attackTimeOr() { return attackTime > 0 ? attackTime : 1.5; }
    public double windupOr() { return windup > 0 ? windup : 0.45; }
    public double speedOr() { return speed > 0 ? speed : 3.0; }
    public double aggroOr() { return aggro > 0 ? aggro : 8; }
    public double leashOr() { return leash > 0 ? leash : 18; }

    /** Uma fala do inimigo, ou null se ele não tem. */
    public String line(String key) { return lines != null ? lines.get(key) : null; }
}
