package etrava.sim.save;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import etrava.sim.nav.Pathfinder.Point;
import etrava.sim.rules.Effect;
import etrava.sim.world.ActorState;
import etrava.sim.world.ChronicleEntry;
import etrava.sim.world.Scheduled;
import etrava.sim.world.World;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Save do jogo (SPEC 5): um arquivo {@code .etrava}, que é um ZIP com
 * {@code meta.json} (resumo para a tela de carregar) e {@code state.json}
 * (o {@link World} inteiro). O esquema é versionado; saves antigos passam por
 * migrações em cadeia ou são recusados com mensagem clara (RF-363).
 */
public final class SaveGame {
    /** Aumenta a cada mudança incompatível no formato de state.json. */
    public static final int SCHEMA_VERSION = 1;
    public static final String EXTENSION = ".etrava";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper CANONICAL = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    /** Erro esperado ao salvar ou carregar; a mensagem pode ir direto para o jogador. */
    public static final class SaveException extends Exception {
        public SaveException(String message) { super(message); }
        public SaveException(String message, Throwable cause) { super(message, cause); }
    }

    /** Leva a árvore JSON da versão {@code from} para {@code from + 1}. */
    public interface Migration {
        int from();
        void apply(ObjectNode state);
    }

    /** Resumo de um save, para listar sem carregar tudo. */
    public record Meta(int schemaVersion, int day, String time, String areaId, long tick, String savedAt) {}

    public record Loaded(Meta meta, World world) {}

    /** Migrações em produção. Vazio enquanto só existir a versão 1. */
    private static final List<Migration> MIGRATIONS = List.of();

    private SaveGame() {}

    // ---------------------------------------------------------------- gravar

    public static void write(Path file, World world, String savedAt) throws SaveException {
        ObjectNode meta = JSON.createObjectNode();
        meta.put("schemaVersion", SCHEMA_VERSION);
        meta.put("day", world.clock.day());
        meta.put("time", String.format("%02d:%02d", world.clock.hour(), world.clock.minute()));
        meta.put("areaId", world.areaId);
        meta.put("tick", world.clock.tick());
        meta.put("savedAt", savedAt == null ? "" : savedAt);

        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry("meta.json"));
                zip.write(JSON.writeValueAsBytes(meta));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("state.json"));
                zip.write(JSON.writeValueAsBytes(toJson(world)));
                zip.closeEntry();
            }
            // escrita atômica: o arquivo antigo só some quando o novo está completo (RF-365)
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // nada a fazer
            }
            throw new SaveException("Não foi possível salvar o jogo: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- ler

    public static Loaded read(Path file) throws SaveException {
        return read(file, MIGRATIONS);
    }

    /** Lê só o resumo; devolve null se o arquivo não for um save legível. */
    public static Meta readMeta(Path file) {
        try {
            JsonNode meta = entry(file, "meta.json");
            return meta == null ? null : meta(meta);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static Loaded read(Path file, List<Migration> migrations) throws SaveException {
        if (!Files.exists(file)) throw new SaveException("Save não encontrado: " + file.getFileName());
        JsonNode metaNode, stateNode;
        try {
            metaNode = entry(file, "meta.json");
            stateNode = entry(file, "state.json");
        } catch (IOException | RuntimeException e) {
            throw new SaveException("O arquivo de save está corrompido ou não pôde ser lido.", e);
        }
        if (metaNode == null || !(stateNode instanceof ObjectNode state))
            throw new SaveException("O arquivo de save está corrompido (faltam partes).");

        int version = state.path("schemaVersion").asInt(-1);
        if (version > SCHEMA_VERSION)
            throw new SaveException("Este save foi criado por uma versão mais nova do jogo (formato "
                    + version + "; este jogo lê até o " + SCHEMA_VERSION + ").");
        while (version < SCHEMA_VERSION) {
            int from = version;
            Migration m = migrations.stream().filter(x -> x.from() == from).findFirst().orElse(null);
            if (m == null)
                throw new SaveException("Este save é de uma versão antiga do jogo (formato " + version
                        + ") e não pode mais ser carregado.");
            m.apply(state);
            version++;
            state.put("schemaVersion", version);
        }
        try {
            return new Loaded(meta(metaNode), fromJson(state));
        } catch (RuntimeException e) {
            throw new SaveException("O arquivo de save está corrompido (conteúdo inválido).", e);
        }
    }

    private static Meta meta(JsonNode n) {
        return new Meta(n.path("schemaVersion").asInt(), n.path("day").asInt(), n.path("time").asText(),
                n.path("areaId").asText(), n.path("tick").asLong(), n.path("savedAt").asText());
    }

    private static JsonNode entry(Path file, String name) throws IOException {
        try (InputStream in = Files.newInputStream(file); ZipInputStream zip = new ZipInputStream(in)) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (e.getName().equals(name)) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    zip.transferTo(bytes);
                    return JSON.readTree(bytes.toByteArray());
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- hash

    /** SHA-256 do estado em forma canônica: dois mundos iguais têm o mesmo hash (AT-03, AT-05). */
    public static String hash(World world) {
        try {
            Object tree = CANONICAL.treeToValue(toJson(world), Object.class);
            byte[] bytes = CANONICAL.writeValueAsBytes(tree);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("falha ao calcular o hash do estado", e);
        }
    }

    // ---------------------------------------------------------------- World ⇄ JSON

    public static ObjectNode toJson(World w) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        ObjectNode meta = root.putObject("meta");
        meta.put("seed", w.seed).put("rngState", w.rng.state()).put("tick", w.clock.tick())
                .put("ticksPerDay", w.clock.ticksPerDay()).put("area", w.areaId).put("nextSeq", w.nextSeq);

        ObjectNode flags = root.putObject("flags");
        w.flags.forEach(flags::put);
        ObjectNode vars = root.putObject("vars");
        w.vars.forEach(vars::put);

        ObjectNode player = actor(w.player);
        player.put("hasDestination", w.hasDestination).put("destX", w.destinationX).put("destY", w.destinationY)
                .put("dirX", w.dirX).put("dirY", w.dirY);
        root.set("player", player);

        ObjectNode npcs = root.putObject("npcs");
        for (Map.Entry<String, ActorState> e : w.npcs.entrySet()) {
            ObjectNode n = actor(e.getValue());
            n.put("sprite", e.getValue().sprite).put("target", e.getValue().target)
                    .put("activity", e.getValue().activity).put("hidden", e.getValue().hidden)
                    .put("leaving", e.getValue().leaving);
            npcs.set(e.getKey(), n);
        }

        ArrayNode scheduled = root.putArray("scheduled");
        for (Scheduled s : w.scheduled) {
            ObjectNode n = scheduled.addObject();
            n.put("tick", s.tick()).put("seq", s.seq()).put("interrupts", s.interrupts());
            ArrayNode effects = n.putArray("effects");
            for (Effect effect : s.effects()) effects.add(effect(effect));
        }

        ArrayNode chronicle = root.putArray("chronicle");
        for (ChronicleEntry c : w.chronicle) {
            chronicle.addObject().put("tick", c.tick()).put("type", c.type()).put("ref", c.ref()).put("text", c.text());
        }
        return root;
    }

    private static ObjectNode actor(ActorState a) {
        ObjectNode n = JSON.createObjectNode();
        n.put("area", a.areaId).put("x", a.x).put("y", a.y).put("facing", a.facing).put("moving", a.moving);
        ArrayNode path = n.putArray("path");
        for (Point p : a.path) path.addArray().add(p.x()).add(p.y());
        return n;
    }

    private static ObjectNode effect(Effect e) {
        ObjectNode n = JSON.createObjectNode();
        switch (e) {
            case Effect.SetFlag f -> n.put("type", "setFlag").put("name", f.name()).put("value", f.value());
            case Effect.SetVar v -> n.put("type", "setVar").put("name", v.name()).put("value", v.value());
            case Effect.Chronicle c -> n.put("type", "chronicle").put("kind", c.type()).put("ref", c.ref())
                    .put("text", c.text());
        }
        return n;
    }

    public static World fromJson(ObjectNode root) {
        JsonNode meta = root.path("meta");
        World w = new World(meta.path("seed").asLong(), meta.path("ticksPerDay").asLong());
        w.rng.restore(meta.path("rngState").asLong());
        w.clock.setTick(meta.path("tick").asLong());
        w.areaId = meta.path("area").asText();
        w.nextSeq = meta.path("nextSeq").asLong();

        root.path("flags").properties().forEach(e -> w.flags.put(e.getKey(), e.getValue().asBoolean()));
        root.path("vars").properties().forEach(e -> w.vars.put(e.getKey(), e.getValue().asDouble()));

        JsonNode player = root.path("player");
        readActor(player, w.player);
        w.hasDestination = player.path("hasDestination").asBoolean();
        w.destinationX = player.path("destX").asDouble();
        w.destinationY = player.path("destY").asDouble();
        w.dirX = player.path("dirX").asDouble();
        w.dirY = player.path("dirY").asDouble();

        root.path("npcs").properties().forEach(e -> {
            JsonNode n = e.getValue();
            ActorState npc = new ActorState(e.getKey(), text(n, "sprite"));
            readActor(n, npc);
            npc.target = text(n, "target");
            npc.activity = n.path("activity").asText("");
            npc.hidden = n.path("hidden").asBoolean();
            npc.leaving = n.path("leaving").asBoolean();
            w.npcs.put(e.getKey(), npc);
        });

        for (JsonNode n : root.path("scheduled")) {
            List<Effect> effects = new ArrayList<>();
            for (JsonNode e : n.path("effects")) effects.add(readEffect(e));
            w.scheduled.add(new Scheduled(n.path("tick").asLong(), n.path("seq").asLong(),
                    n.path("interrupts").asBoolean(), effects));
        }
        for (JsonNode n : root.path("chronicle")) {
            w.chronicle.add(new ChronicleEntry(n.path("tick").asLong(), n.path("type").asText(),
                    n.path("ref").asText(), n.path("text").asText()));
        }
        return w;
    }

    private static void readActor(JsonNode n, ActorState a) {
        a.areaId = text(n, "area");
        a.x = n.path("x").asDouble();
        a.y = n.path("y").asDouble();
        a.facing = n.path("facing").asDouble();
        a.moving = n.path("moving").asBoolean();
        for (JsonNode p : n.path("path")) a.path.add(new Point(p.get(0).asDouble(), p.get(1).asDouble()));
    }

    private static Effect readEffect(JsonNode n) {
        String type = n.path("type").asText();
        return switch (type) {
            case "setFlag" -> new Effect.SetFlag(n.path("name").asText(), n.path("value").asBoolean());
            case "setVar" -> new Effect.SetVar(n.path("name").asText(), n.path("value").asDouble());
            case "chronicle" -> new Effect.Chronicle(n.path("kind").asText(), n.path("ref").asText(),
                    n.path("text").asText());
            default -> throw new IllegalArgumentException("efeito desconhecido no save: " + type);
        };
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
