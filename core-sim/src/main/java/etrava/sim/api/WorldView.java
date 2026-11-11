package etrava.sim.api;

import etrava.sim.time.GameClock;
import etrava.sim.world.ChronicleEntry;
import java.util.List;

/** Vista somente leitura do estado, para a apresentação. */
public interface WorldView {
    long seed();
    GameClock clock();
    String areaId();
    ActorView player();
    /** NPCs visíveis na área carregada. */
    List<? extends ActorView> actors();
    double playerX();
    double playerY();
    /** Direção para onde o jogador está virado, em radianos no plano do mundo. */
    double playerFacing();
    boolean playerMoving();
    boolean hasDestination();
    double destinationX();
    double destinationY();
    boolean flag(String name);
    double var(String name);
    /** Crônica interna do mundo — só para depuração e relatórios (RF-143). */
    List<ChronicleEntry> chronicle();
    /** Conversa com o jogador em andamento, ou null. */
    DialogueView dialogue();
    /** O caderno: tudo o que o personagem ouviu, viu ou leu, na ordem em que soube. */
    List<etrava.sim.world.KnowledgeEntry> knowledge();
    java.util.Set<String> markedFacts();
    boolean knows(String factId);
    /** Opinião do NPC sobre o jogador, de -100 a 100. */
    int opinion(String npcId);
    /** O que um NPC sabe (depuração). */
    java.util.Map<String, etrava.sim.world.KnowledgeEntry> npcKnowledge(String npcId);
    /** Qualquer NPC do mundo, esteja onde estiver; null se não existir. */
    ActorView npc(String id);
    /** Estado de cada arco de mundo (depuração e relatórios). */
    java.util.Map<String, etrava.sim.world.ArcState> arcs();
    /** Variante atual de um prop com chave; null = ainda no estado inicial. */
    String propState(String key);
    /** O que um NPC lembra (depuração). */
    List<etrava.sim.world.MemoryEntry> memories(String npcId);
    /** Texto do epílogo do capítulo; vazio enquanto ele não terminou. */
    List<String> epilogue();
    /** present | away | departed | dead; null se o NPC não existir. */
    String npcStatus(String id);
    /** Área em que o NPC está agora; null se não existir. */
    String npcArea(String id);
    int money();
    /** Mochila: item → quantidade (sem o que está equipado). */
    java.util.Map<String, Integer> inventory();
    /** Encaixe → item equipado. */
    java.util.Map<String, String> equipment();
    /** Habilidade → valor de 0 a 100. */
    java.util.Map<String, Double> skills();
    /** Fome de 0 a 100. */
    double hunger();
    /** Comércio ou contêiner aberto, ou null. Preços e listas: {@code Simulation.tradeView()}. */
    etrava.sim.world.TradeSession trade();
    /** Multiplicador de preço de uma categoria ou marca (1 = normal). */
    double priceMod(String tag);
    /** Vida do jogador; o máximo vem de {@code Simulation.combat().maxHealth()}. */
    double health();
    /** Fôlego do jogador. */
    double stamina();
    /** Inimigos na área carregada. */
    List<etrava.sim.world.EnemyState> enemies();
    /** Inimigo que o jogador está atacando, ou null. */
    String attackTarget();
    /** O personagem morreu (RF-341). */
    boolean dead();
}
