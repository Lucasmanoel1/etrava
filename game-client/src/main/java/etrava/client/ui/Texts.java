package etrava.client.ui;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Textos da interface, lidos de {@code content/i18n/ui_pt_BR.properties} (RF-382). */
public final class Texts {
    private static final Logger LOG = LoggerFactory.getLogger(Texts.class);
    private final Properties props = new Properties();

    public Texts(Path contentDir) {
        Path file = contentDir.resolve("i18n/ui_pt_BR.properties");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (Exception e) {
            LOG.warn("Textos de interface não carregados ({}); as chaves aparecerão no lugar", e.getMessage());
        }
    }

    /** Texto da chave, com {0}, {1}… trocados pelos argumentos. Chave ausente aparece como a própria chave. */
    public String get(String key, Object... args) {
        String pattern = props.getProperty(key);
        if (pattern == null) return key;
        return args.length == 0 ? pattern : MessageFormat.format(pattern.replace("'", "''"), args);
    }
}
