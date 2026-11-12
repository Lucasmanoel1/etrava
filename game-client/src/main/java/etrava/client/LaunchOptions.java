package etrava.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Opções de inicialização.
 * Propriedades: {@code -Detrava.home} (pasta com content/ e assets/), {@code -Detrava.dev=true}.
 * Argumentos: {@code --seed=N}, {@code --area=ID}, {@code --cmd="comando de console"} (repetível),
 * {@code --cmdat=QUADRO:comando} (roda o comando de console naquele quadro; repetível),
 * {@code --menu} (abre no menu mesmo com outras opções), {@code --screenshot=arquivo.png} e {@code --frames=N} (captura a tela após N quadros e sai),
 * {@code --bench=SEGUNDOS} (mede o desempenho sem limite de quadros, grava o resultado no log e sai; AT-17),
 * {@code --bot=SEGUNDOS} (um jogador automático aperta de tudo por esse tempo e sai; teste de resistência),
 * {@code --vel=FATOR} (velocidade do tempo de jogo desde o início),
 * {@code --hidden} (janela invisível: para testes automáticos longos sem ocupar a tela),
 * {@code --paineis} (com {@code --bot}: abre, rola e fecha painéis sem parar e confere o zoom depois de cada um; VT-01),
 * {@code --debug} (modo de depuração mesmo no pacote de release; o mesmo que {@code ETRAVA_DEBUG=1}).
 */
public record LaunchOptions(
        boolean dev, Path home, Long seed, String area, List<String> commands, List<String> timedCommands,
        Path screenshot, int screenshotFrames, boolean menu, int bench, int bot, float speed,
        boolean hidden, boolean panels) {

    public static LaunchOptions parse(String[] args) {
        boolean dev = Boolean.getBoolean("etrava.dev") || "1".equals(System.getenv("ETRAVA_DEBUG"));
        Path home = Path.of(System.getProperty("etrava.home", ".")).toAbsolutePath().normalize();
        Long seed = null;
        String area = null;
        List<String> commands = new ArrayList<>();
        List<String> timed = new ArrayList<>();
        Path screenshot = null;
        int frames = 90;
        boolean menu = false;
        int bench = 0, bot = 0;
        float speed = 1f;
        boolean hidden = false, panels = false;
        for (String arg : args) {
            if (arg.startsWith("--seed=")) seed = Long.parseLong(value(arg));
            else if (arg.startsWith("--area=")) area = value(arg);
            else if (arg.startsWith("--cmd=")) commands.add(value(arg));
            else if (arg.startsWith("--cmdat=")) timed.add(value(arg));
            else if (arg.startsWith("--screenshot=")) screenshot = Path.of(value(arg)).toAbsolutePath();
            else if (arg.startsWith("--frames=")) frames = Integer.parseInt(value(arg));
            else if (arg.equals("--menu")) menu = true;
            else if (arg.startsWith("--bench=")) bench = Integer.parseInt(value(arg));
            else if (arg.startsWith("--bot=")) bot = Integer.parseInt(value(arg));
            else if (arg.startsWith("--vel=")) speed = Float.parseFloat(value(arg));
            else if (arg.equals("--hidden")) hidden = true;
            else if (arg.equals("--paineis")) panels = true;
            else if (arg.equals("--debug")) dev = true;
        }
        return new LaunchOptions(dev, home, seed, area, List.copyOf(commands), List.copyOf(timed), screenshot, frames, menu, bench,
                bot, speed, hidden, panels);
    }

    private static String value(String arg) {
        return arg.substring(arg.indexOf('=') + 1);
    }

    public Path contentDir() { return home.resolve("content"); }
    public Path assetsDir() { return home.resolve("assets"); }
}
