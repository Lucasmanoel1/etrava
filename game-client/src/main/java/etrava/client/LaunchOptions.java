package etrava.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Opções de inicialização.
 * Propriedades: {@code -Detrava.home} (pasta com content/ e assets/), {@code -Detrava.dev=true}.
 * Argumentos: {@code --seed=N}, {@code --area=ID}, {@code --cmd="comando de console"} (repetível),
 * {@code --cmdat=QUADRO:comando} (roda o comando de console naquele quadro; repetível),
 * {@code --menu} (abre no menu mesmo com outras opções), {@code --screenshot=arquivo.png} e {@code --frames=N} (captura a tela após N quadros e sai).
 */
public record LaunchOptions(
        boolean dev, Path home, Long seed, String area, List<String> commands, List<String> timedCommands,
        Path screenshot, int screenshotFrames, boolean menu) {

    public static LaunchOptions parse(String[] args) {
        boolean dev = Boolean.getBoolean("etrava.dev");
        Path home = Path.of(System.getProperty("etrava.home", ".")).toAbsolutePath().normalize();
        Long seed = null;
        String area = null;
        List<String> commands = new ArrayList<>();
        List<String> timed = new ArrayList<>();
        Path screenshot = null;
        int frames = 90;
        boolean menu = false;
        for (String arg : args) {
            if (arg.startsWith("--seed=")) seed = Long.parseLong(value(arg));
            else if (arg.startsWith("--area=")) area = value(arg);
            else if (arg.startsWith("--cmd=")) commands.add(value(arg));
            else if (arg.startsWith("--cmdat=")) timed.add(value(arg));
            else if (arg.startsWith("--screenshot=")) screenshot = Path.of(value(arg)).toAbsolutePath();
            else if (arg.startsWith("--frames=")) frames = Integer.parseInt(value(arg));
            else if (arg.equals("--menu")) menu = true;
        }
        return new LaunchOptions(dev, home, seed, area, List.copyOf(commands), List.copyOf(timed), screenshot, frames, menu);
    }

    private static String value(String arg) {
        return arg.substring(arg.indexOf('=') + 1);
    }

    public Path contentDir() { return home.resolve("content"); }
    public Path assetsDir() { return home.resolve("assets"); }
}
