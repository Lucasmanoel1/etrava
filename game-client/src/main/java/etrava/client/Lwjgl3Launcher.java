package etrava.client;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ponto de entrada do jogo no desktop. */
public final class Lwjgl3Launcher {

    public static void main(String[] args) {
        // precisa vir antes do primeiro uso do log: o logback.xml lê esta propriedade
        System.setProperty("etrava.logdir", UserDirs.logs().toString());
        Logger log = LoggerFactory.getLogger(Lwjgl3Launcher.class);

        LaunchOptions options = LaunchOptions.parse(args);
        log.info("ETRAVA iniciando (dev={}, home={}, java={})", options.dev(), options.home(),
                System.getProperty("java.version"));

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("ETRAVA");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        config.setForegroundFPS(60);
        if (options.hidden()) config.setInitialVisible(false);

        try {
            new Lwjgl3Application(new EtravaGame(options), config);
        } catch (Throwable t) {
            Path report = writeCrashReport(t);
            log.error("Erro fatal. Relatório: {}", report, t);
            System.exit(1);
        }
        log.info("ETRAVA encerrado.");
    }

    /** Relatório de crash com stack trace, semente e dia de jogo (RNF-032). */
    private static Path writeCrashReport(Throwable t) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = UserDirs.logs().resolve("crash-" + stamp + ".txt");
        StringWriter text = new StringWriter();
        text.write("ETRAVA — relatório de erro " + stamp + System.lineSeparator());
        text.write("Contexto: " + EtravaGame.crashContext() + System.lineSeparator());
        text.write("Java: " + System.getProperty("java.version") + " · SO: " + System.getProperty("os.name")
                + System.lineSeparator() + System.lineSeparator());
        t.printStackTrace(new PrintWriter(text, true));
        try {
            Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // o log em arquivo já registrou o erro
        }
        return file;
    }
}
