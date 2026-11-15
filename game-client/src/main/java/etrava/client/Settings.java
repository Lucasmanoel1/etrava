package etrava.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opções do jogador (RF-383), gravadas em {@code %APPDATA%\Etrava\opcoes.json}. Campos públicos
 * para o Jackson ler e gravar sem cerimônia; valores inválidos voltam ao padrão.
 */
public final class Settings {
    private static final Logger LOG = LoggerFactory.getLogger(Settings.class);
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    // ---- áudio (RF-403): 0 a 1
    public float volumeGeral = 0.8f;
    public float volumeMusica = 0.55f;
    public float volumeAmbiente = 0.7f;
    public float volumeEfeitos = 0.8f;
    public float volumeInterface = 0.6f;
    // ---- vídeo
    public boolean telaCheia = false;
    public boolean vsync = true;
    /** 0 = sem limite. */
    public int limiteFps = 60;
    public boolean posProcessamento = true;
    public boolean luzDinamica = true;
    /** V4: "Detalhes do ambiente" — alto (true) mostra folhas, pássaros, vaga-lumes; baixo desliga. */
    public boolean detalhesAmbiente = true;
    // ---- texto (RNF-040): 1; 1,25; 1,5
    public float tamanhoTexto = 1f;
    /** Segundos por letra no balão das conversas ambientes (velocidade do texto). */
    public float velocidadeTexto = 1f;
    // ---- jogo
    public String nome = "";
    public boolean contemplativo = false;

    public static Path file() {
        return UserDirs.base().resolve("opcoes.json");
    }

    public static Settings load() {
        Path f = file();
        if (Files.exists(f)) {
            try {
                Settings s = JSON.readValue(f.toFile(), Settings.class);
                s.sanitize();
                return s;
            } catch (Exception e) {
                LOG.warn("opcoes.json ilegível ({}); usando o padrão", e.getMessage());
            }
        }
        return new Settings();
    }

    public void save() {
        try {
            Files.createDirectories(file().getParent());
            Path tmp = file().resolveSibling("opcoes.json.tmp");
            JSON.writeValue(tmp.toFile(), this);
            Files.move(tmp, file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOG.warn("Não foi possível gravar as opções: {}", e.getMessage());
        }
    }

    private void sanitize() {
        volumeGeral = clamp(volumeGeral);
        volumeMusica = clamp(volumeMusica);
        volumeAmbiente = clamp(volumeAmbiente);
        volumeEfeitos = clamp(volumeEfeitos);
        volumeInterface = clamp(volumeInterface);
        if (tamanhoTexto < 1f || tamanhoTexto > 1.5f) tamanhoTexto = 1f;
        if (velocidadeTexto < 0.5f || velocidadeTexto > 2f) velocidadeTexto = 1f;
        if (limiteFps < 0 || limiteFps > 360) limiteFps = 60;
        if (nome == null) nome = "";
    }

    private static float clamp(float v) {
        return Float.isNaN(v) ? 0.5f : Math.max(0, Math.min(1, v));
    }
}
