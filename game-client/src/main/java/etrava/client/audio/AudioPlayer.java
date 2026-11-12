package etrava.client.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;
import etrava.client.Settings;
import etrava.sim.content.AssetManifest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Som do jogo (RF-400–405): uma faixa de música e um ambiente em loop, cada um com
 * transição suave, e efeitos avulsos. Cinco canais de volume (RF-403). Tudo por ID
 * lógico do manifesto (RF-420); ID ausente ou arquivo quebrado = silêncio e um aviso
 * no log, nunca uma queda (RF-421). Sem dispositivo de áudio, tudo vira silêncio.
 */
public final class AudioPlayer implements Disposable {
    private static final Logger LOG = LoggerFactory.getLogger(AudioPlayer.class);
    /** Segundos de transição entre faixas. */
    private static final float FADE = 2.5f;

    public enum Channel { MUSICA, AMBIENTE, EFEITOS, INTERFACE }

    private final Path root;
    private final AssetManifest manifest;
    private final Settings settings;
    private final Map<String, Music> streams = new HashMap<>();
    private final Map<String, List<Sound>> sounds = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private final Random rnd = new Random();
    private final Loop music = new Loop(Channel.MUSICA), ambience = new Loop(Channel.AMBIENTE);
    private final boolean enabled;

    public AudioPlayer(Path assetsDir, AssetManifest manifest, Settings settings) {
        this.root = assetsDir;
        this.manifest = manifest;
        this.settings = settings;
        this.enabled = Gdx.audio != null;
    }

    /** Toca esta música (com transição); null = silêncio. Pedir a mesma faixa não recomeça. */
    public void music(String id) { music.request(id); }

    /** Ambiente em loop; tenta {@code id.dia}/{@code id.noite} antes de {@code id}. */
    public void ambience(String id, boolean night) {
        if (id == null) {
            ambience.request(null);
            return;
        }
        String specific = id + (night ? ".noite" : ".dia");
        ambience.request(manifest.has(specific) ? specific : id);
    }

    public void sfx(String id) { play(id, Channel.EFEITOS, 1f, 1f); }

    public void sfx(String id, float volume, float pitch) { play(id, Channel.EFEITOS, volume, pitch); }

    public void ui(String id) { play(id, Channel.INTERFACE, 1f, 1f); }

    /** Chamar a cada quadro: anda as transições e aplica o volume dos canais. */
    public void update(float dt) {
        music.update(dt);
        ambience.update(dt);
    }

    public float volume(Channel c) {
        float v = switch (c) {
            case MUSICA -> settings.volumeMusica;
            case AMBIENTE -> settings.volumeAmbiente;
            case EFEITOS -> settings.volumeEfeitos;
            case INTERFACE -> settings.volumeInterface;
        };
        return v * settings.volumeGeral;
    }

    private void play(String id, Channel channel, float volume, float pitch) {
        if (!enabled || id == null) return;
        List<Sound> list = sounds.computeIfAbsent(id, this::loadSounds);
        if (list.isEmpty()) return;
        float v = volume * volume(channel);
        if (v <= 0.001f) return;
        list.get(rnd.nextInt(list.size())).play(v, pitch * (0.96f + rnd.nextFloat() * 0.08f), 0f);
    }

    private List<Sound> loadSounds(String id) {
        List<Sound> list = new ArrayList<>();
        AssetManifest.Entry e = manifest.get(id);
        if (e == null) {
            warn(id, "ID fora do manifesto");
            return list;
        }
        for (String file : files(e)) {
            Path p = existing(file);
            if (!Files.exists(p)) {
                warn(id, "arquivo ausente: " + file);
                continue;
            }
            try {
                list.add(Gdx.audio.newSound(Gdx.files.absolute(p.toString())));
            } catch (RuntimeException ex) {
                warn(id, "não carregou: " + ex.getMessage());
            }
        }
        return list;
    }

    private Music stream(String id) {
        if (!enabled || id == null) return null;
        if (streams.containsKey(id)) return streams.get(id);
        Music m = null;
        AssetManifest.Entry e = manifest.get(id);
        if (e == null) {
            warn(id, "ID fora do manifesto");
        } else {
            List<String> files = files(e);
            Path p = files.isEmpty() ? null : existing(files.get(0));
            if (p == null || !Files.exists(p)) {
                warn(id, "arquivo ausente");
            } else {
                try {
                    m = Gdx.audio.newMusic(Gdx.files.absolute(p.toString()));
                    m.setLooping(true);
                } catch (RuntimeException ex) {
                    warn(id, "não carregou: " + ex.getMessage());
                }
            }
        }
        streams.put(id, m);
        return m;
    }

    /** O arquivo do manifesto; se for um .ogg que ainda não foi convertido, vale o .wav de mesmo nome (D-080). */
    private Path existing(String file) {
        Path p = root.resolve(file);
        if (!Files.exists(p) && file.endsWith(".ogg")) {
            Path wav = root.resolve(file.substring(0, file.length() - 4) + ".wav");
            if (Files.exists(wav)) return wav;
        }
        return p;
    }

    private static List<String> files(AssetManifest.Entry e) {
        List<String> all = new ArrayList<>();
        if (e.file() != null) all.add(e.file());
        if (e.files() != null) all.addAll(e.files());
        return all;
    }

    private void warn(String id, String why) {
        if (warned.add(id)) LOG.warn("Áudio '{}' indisponível ({}); fica em silêncio", id, why);
    }

    @Override
    public void dispose() {
        for (Music m : streams.values()) if (m != null) m.dispose();
        for (List<Sound> list : sounds.values()) for (Sound s : list) s.dispose();
        streams.clear();
        sounds.clear();
    }

    /** Uma faixa em loop com transição: a que sai some enquanto a que entra cresce. */
    private final class Loop {
        private final Channel channel;
        private String currentId, outgoingId;
        private float in = 0f, out = 0f;

        Loop(Channel channel) { this.channel = channel; }

        void request(String id) {
            if (java.util.Objects.equals(id, currentId)) return;
            Music old = stream(outgoingId);
            if (old != null) old.stop(); // terceira troca seguida: a mais antiga para de vez
            outgoingId = currentId;
            out = in;
            currentId = id;
            in = 0f;
            Music next = stream(id);
            if (next != null) {
                next.setVolume(0f);
                next.play();
            }
        }

        void update(float dt) {
            in = Math.min(1f, in + dt / FADE);
            out = Math.max(0f, out - dt / FADE);
            float vol = volume(channel);
            Music cur = stream(currentId), old = stream(outgoingId);
            if (cur != null) cur.setVolume(in * vol);
            if (old != null) {
                old.setVolume(out * vol);
                if (out <= 0f) {
                    old.stop();
                    outgoingId = null;
                }
            }
        }
    }
}
