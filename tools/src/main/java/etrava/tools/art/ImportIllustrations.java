package etrava.tools.art;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Importa as ilustrações grandes (menu, mapa da região, painéis do epílogo), geradas por IA:
 * corta a marca d'água do canto mantendo 16:9, reduz para 640×360 na paleta do jogo (o cliente
 * amplia sem suavizar, então ficam em pixel art como o resto) e regenera {@code ilustracoes.yaml}.
 * Uso: {@code ImportIllustrations <pasta de entrada> <pasta assets>}.
 */
public final class ImportIllustrations {
    private static final int W = 640, H = 360;

    private ImportIllustrations() {}

    public static void main(String[] args) throws IOException {
        Path in = Path.of(args[0]), assets = Path.of(args[1]);
        Path outDir = assets.resolve("ilustracoes");
        Files.createDirectories(outDir);
        StringBuilder sb = new StringBuilder("# Gerado por `gradlew importIllustrations`. Não edite à mão.\n");
        List<Path> files;
        try (Stream<Path> s = Files.list(in)) {
            files = s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png")).sorted().toList();
        }
        for (Path f : files) {
            BufferedImage raw = ImageIO.read(f.toFile());
            if (raw == null) continue;
            int w = raw.getWidth(), h = raw.getHeight();
            // corta a marca "AI" (canto superior esquerdo) tirando 1/16 da largura e da altura: continua 16:9
            int x0 = w / 16, y0 = h / 16;
            BufferedImage cropped = raw.getSubimage(x0, y0, w - x0, h - y0);
            BufferedImage px = PixelArt.pixelize(cropped, W, H);
            String name = f.getFileName().toString();
            ImageIO.write(px, "png", outDir.resolve(name).toFile());
            String id = name.substring(0, name.length() - 4);
            sb.append("- id: ilustracao.").append(id).append('\n')
                    .append("  type: illustration\n")
                    .append("  file: ilustracoes/").append(name).append('\n')
                    .append("  source: \"Gerado por IA (Dreamina, Seedream 5.0 Pro) e convertido para a paleta do jogo\"\n")
                    .append("  license: \"Dreamina — conferir termos de uso comercial antes do release\"\n");
            System.out.println("Ilustração importada: " + name);
        }
        Files.writeString(assets.resolve("manifest/ilustracoes.yaml"), sb.toString(), StandardCharsets.UTF_8);
    }
}
