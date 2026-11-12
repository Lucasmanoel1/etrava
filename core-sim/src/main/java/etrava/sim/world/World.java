package etrava.sim.world;

import etrava.sim.api.ActorView;
import etrava.sim.api.WorldView;
import etrava.sim.rng.SplitMix64;
import etrava.sim.time.GameClock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;

/**
 * Raiz do estado da simulação (SPEC 3.5). Tudo o que muda durante o jogo mora
 * aqui e é gravado no save; a {@code Simulation} só guarda o que dá para
 * recalcular (grade de navegação, listas de conveniência).
 */
public final class World implements WorldView {
    public static final String PLAYER_SPRITE = "personagem.aprendiz";

    public final long seed;
    public final SplitMix64 rng;
    public final GameClock clock;
    public final ActorState player = new ActorState("jogador", PLAYER_SPRITE);
    /** Todos os NPCs do mundo, na ordem do conteúdo. */
    public final Map<String, ActorState> npcs = new LinkedHashMap<>();
    public final Map<String, Boolean> flags = new TreeMap<>();
    public final Map<String, Double> vars = new TreeMap<>();
    /** Efeitos futuros, mantidos em ordem de (tick, seq). */
    public final List<Scheduled> scheduled = new ArrayList<>();
    public final List<ChronicleEntry> chronicle = new ArrayList<>();
    public long nextSeq;

    /** O que o jogador sabe; pode haver várias entradas do mesmo fato (fontes e versões diferentes). */
    public final List<KnowledgeEntry> playerKnowledge = new ArrayList<>();
    /** Fatos que o jogador marcou no caderno como de interesse. */
    public final Set<String> marked = new TreeSet<>();
    /** O que cada NPC sabe: NPC → fato → entrada (um NPC acredita numa versão só). */
    public final Map<String, Map<String, KnowledgeEntry>> npcKnowledge = new LinkedHashMap<>();
    /** Opinião de cada NPC sobre o jogador, de -100 a 100. */
    public final Map<String, Integer> opinions = new TreeMap<>();

    /** Estado dos arcos de mundo, na ordem do conteúdo. */
    public final Map<String, ArcState> arcs = new LinkedHashMap<>();
    /** Variante atual dos props com chave; só os que já mudaram (os demais estão no estado inicial). */
    public final Map<String, String> props = new TreeMap<>();
    /** Lembranças de cada NPC, da mais antiga para a mais nova (RF-084). */
    public final Map<String, List<MemoryEntry>> memories = new TreeMap<>();
    /** Multiplicador dos prazos dos arcos: 1 = Normal, 2 = Contemplativo (RF-145). */
    public double pace = 1.0;
    /** Nome que o jogador deu ao personagem no novo jogo; vazio = "o aprendiz". */
    public String playerName = "";
    /** Texto do epílogo do capítulo, depois de montado; vazio enquanto o capítulo corre. */
    public final List<String> epilogue = new ArrayList<>();

    // ---- vida do personagem (M5): dinheiro, mochila, corpo, habilidades, fome
    public int money;
    /** Item → quantidade na mochila (fora o que está equipado). */
    public final Map<String, Integer> inventory = new TreeMap<>();
    /** Encaixe (mainHand, offHand, body, head, accessory) → item. */
    public final Map<String, String> equipment = new TreeMap<>();
    /** Habilidade → valor de 0 a 100 (RF-180). */
    public final Map<String, Double> skills = new TreeMap<>();
    /** Fome de 0 (satisfeito) a 100 (faminto). Sobe com o tempo; cai ao comer. */
    public double hunger;
    /** Trabalho → último dia em que foi feito (um por dia). */
    public final Map<String, Integer> jobDay = new TreeMap<>();

    // ---- economia (M5)
    /** Categoria ou marca → multiplicador de preço (RF-262/263). Ausente = 1. */
    public final Map<String, Double> priceMods = new TreeMap<>();
    public final Map<String, MerchantState> merchants = new TreeMap<>();
    /** Contêiner → item → quantidade. */
    public final Map<String, Map<String, Integer>> containers = new TreeMap<>();
    /** Contêiner → último dia em que o dono repôs. */
    public final Map<String, Integer> containerRestock = new TreeMap<>();
    /** Contêiner até o qual o jogador está indo (null = nenhum). */
    public String pendingContainer;
    /** Trabalho ou comércio pedido por um diálogo; começa quando a conversa acaba. Não vai para o save. */
    public String pendingJob, pendingTrade;
    /** Comércio ou contêiner aberto; não vai para o save. */
    public TradeSession trade;

    // ---- combate e perigo (M6)
    /** Vida e fôlego do jogador (RF-221). Os máximos dependem do Vigor: ver {@code Combat}. */
    public double health = 100, stamina = 100;
    /** Inimigos na área carregada, na ordem em que apareceram. */
    public final List<EnemyState> enemies = new ArrayList<>();
    public long nextEnemySeq;
    /** Encontro → tick em que aconteceu pela última vez. */
    public final Map<String, Long> encounterLast = new TreeMap<>();
    /** Inimigo que o jogador está atacando (null = nenhum) e se continua atacando (botão segurado, RF-220). */
    public String attackTarget;
    public boolean attackRepeat;
    /** Tick a partir do qual o jogador pode golpear de novo. */
    public long attackReadyTick;
    /** Tick do último golpe ou esquiva: o fôlego só volta um pouco depois. */
    public long exertTick = -1_000_000;
    /** Habilidade de combate → tick em que a recarga termina (RF-222). */
    public final Map<String, Long> cooldowns = new TreeMap<>();
    /** Esquiva: ticks que faltam e direção. Esquivando, o jogador não é atingido. */
    public int dodgeTicks;
    public double dodgeX, dodgeY;
    /** O personagem morreu (RF-341): nada mais anda até carregar um save. */
    public boolean dead;

    /** Conversa ambiente em andamento (null = nenhuma), próxima fala e quando ela sai. */
    public String ambientId;
    public int ambientLine;
    public long ambientNextTick;
    /** Tick em que cada conversa ambiente tocou pela última vez. */
    public final Map<String, Long> ambientLast = new TreeMap<>();

    /** NPC que o jogador está indo agredir (null = ninguém). Não vai para o save. */
    public String pendingStrike;
    /** NPC com quem o jogador está indo falar (null = ninguém). */
    public String pendingTalk;
    /** Conversa com o jogador em andamento; não vai para o save. */
    public DialogueSession dialogue;

    public String areaId;
    public boolean hasDestination;
    public double destinationX;
    public double destinationY;
    /** Direção do movimento por teclado (WASD), em mundo; (0, 0) parado. */
    public double dirX, dirY;

    /** NPCs visíveis na área carregada; recalculado pela simulação. */
    public final List<ActorState> visible = new ArrayList<>();

    public World(long seed, long ticksPerDay) {
        this.seed = seed;
        this.rng = new SplitMix64(seed);
        this.clock = new GameClock(ticksPerDay);
    }

    @Override public long seed() { return seed; }
    @Override public GameClock clock() { return clock; }
    @Override public String areaId() { return areaId; }
    @Override public ActorView player() { return player; }
    @Override public List<ActorState> actors() { return visible; }
    @Override public double playerX() { return player.x; }
    @Override public double playerY() { return player.y; }
    @Override public double playerFacing() { return player.facing; }
    @Override public boolean playerMoving() { return player.moving; }
    @Override public boolean hasDestination() { return hasDestination; }
    @Override public double destinationX() { return destinationX; }
    @Override public double destinationY() { return destinationY; }
    @Override public boolean flag(String name) { return flags.getOrDefault(name, false); }
    @Override public double var(String name) { return vars.getOrDefault(name, 0.0); }
    @Override public List<ChronicleEntry> chronicle() { return chronicle; }
    @Override public ActorView npc(String id) { return npcs.get(id); }
    @Override public etrava.sim.api.DialogueView dialogue() { return dialogue; }
    @Override public List<KnowledgeEntry> knowledge() { return playerKnowledge; }
    @Override public Set<String> markedFacts() { return marked; }
    @Override public int opinion(String npcId) { return opinions.getOrDefault(npcId, 0); }
    @Override public Map<String, KnowledgeEntry> npcKnowledge(String npcId) {
        return npcKnowledge.getOrDefault(npcId, Map.of());
    }
    @Override public boolean knows(String factId) {
        return playerKnowledge.stream().anyMatch(k -> k.fact().equals(factId));
    }
    @Override public Map<String, ArcState> arcs() { return arcs; }
    @Override public String propState(String key) { return props.get(key); }
    @Override public List<MemoryEntry> memories(String npcId) { return memories.getOrDefault(npcId, List.of()); }
    @Override public List<String> epilogue() { return epilogue; }
    @Override public String npcStatus(String id) {
        ActorState n = npcs.get(id);
        return n == null ? null : n.status;
    }
    @Override public String npcArea(String id) {
        ActorState n = npcs.get(id);
        return n == null ? null : n.areaId;
    }
    @Override public int money() { return money; }
    @Override public Map<String, Integer> inventory() { return inventory; }
    @Override public Map<String, String> equipment() { return equipment; }
    @Override public Map<String, Double> skills() { return skills; }
    @Override public double hunger() { return hunger; }
    @Override public TradeSession trade() { return trade; }
    @Override public double priceMod(String tag) { return priceMods.getOrDefault(tag, 1.0); }
    @Override public double health() { return health; }
    @Override public double stamina() { return stamina; }
    @Override public List<EnemyState> enemies() { return enemies; }
    @Override public String attackTarget() { return attackTarget; }
    @Override public boolean dead() { return dead; }
}
