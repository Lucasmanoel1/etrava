package etrava.tools.art.pixel;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Monta um GIF em laço a partir de uma pasta de quadros PNG (ordem alfabética), com o escritor do JDK, o mesmo
 * de {@code gradlew sheets}. Uso: {@code MontarGif <pasta de quadros> <saída.gif> [quadros por segundo]}.
 * No V7: {@code gradlew :tools:menuGif} refaz {@code docs/capturas/v7/menu.gif} a partir da sequência da cena
 * {@code menu_gif} ({@code gradlew :game-client:captures -Proteiro=v7_telas -Pso=menu_gif}).
 */
public final class MontarGif {
    private MontarGif() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 2) throw new IllegalArgumentException("uso: MontarGif <pasta de quadros> <saida.gif> [fps]");
        Path pasta = Path.of(args[0]), saida = Path.of(args[1]);
        double fps = args.length > 2 ? Double.parseDouble(args[2]) : 15;
        List<Path> arquivos;
        try (var s = Files.list(pasta)) {
            arquivos = s.filter(p -> p.getFileName().toString().endsWith(".png")).sorted().toList();
        }
        if (arquivos.isEmpty()) throw new IOException("sem quadros PNG em " + pasta);
        List<BufferedImage> quadros = new ArrayList<>();
        for (Path p : arquivos) {
            BufferedImage png = ImageIO.read(p.toFile());
            BufferedImage rgb = new BufferedImage(png.getWidth(), png.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(png, 0, 0, null);
            g.dispose();
            quadros.add(rgb);
        }
        Files.createDirectories(saida.toAbsolutePath().getParent());
        Revisao.escreverGif(quadros, (int) Math.round(100.0 / fps), saida);
        System.out.println("GIF: " + quadros.size() + " quadros em " + saida);
    }
}
