package etrava.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pastas do jogo no perfil do usuário: %APPDATA%\Etrava (D-012). Nunca a pasta de instalação. */
public final class UserDirs {
    private UserDirs() {}

    public static Path base() {
        String appData = System.getenv("APPDATA");
        return appData != null && !appData.isBlank()
                ? Path.of(appData, "Etrava")
                : Path.of(System.getProperty("user.home"), ".etrava");
    }

    public static Path logs() { return ensure(base().resolve("logs")); }
    public static Path saves() { return ensure(base().resolve("saves")); }
    public static Path screenshots() { return ensure(base().resolve("screenshots")); }

    private static Path ensure(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            // sem pasta de usuário o jogo ainda roda; só não grava ali
        }
        return dir;
    }
}
