package etrava.tools.art;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Importa retratos brutos (gerados por IA) para o jogo: recorta a marca d'água,
 * reduz para 96×128 na paleta do jogo e regenera o manifesto {@code retratos.yaml}.
 * Uso: {@code ImportPortraits <pasta de entrada> <pasta assets>}.
 */
public final class ImportPortraits {
    private static final int W = 96, H = 128;

    private ImportPortraits() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Uso: ImportPortraits <pasta com PNGs brutos> <pasta assets>");
            System.exit(1);
        }
        Path in = Path.of(args[0]);
        Path assets = Path.of(args[1]);
        if (!Files.isDirectory(in)) {
            System.err.println("Pasta de entrada não existe: " + in);
            System.exit(1);
        }
        List<Path> files = listPngs(in);
        if (files.isEmpty()) {
            System.err.println("Nenhum PNG encontrado em: " + in);
            System.exit(1);
        }

        Path outDir = assets.resolve("retratos");
        Files.createDirectories(outDir);
        int count = 0;
        for (Path f : files) {
            String name = f.getFileName().toString();
            BufferedImage raw = ImageIO.read(f.toFile());
            if (raw == null) {
                System.err.println("Ignorado (não é uma imagem válida): " + name);
                continue;
            }
            // corta a marca d'água "AI" no canto superior esquerdo e mantém a proporção 3:4
            int w = raw.getWidth(), h = raw.getHeight();
            int x0 = (int) Math.round(w * 0.06), x1 = (int) Math.round(w * 0.94);
            int y0 = (int) Math.round(h * 0.08), y1 = (int) Math.round(h * 0.96);
            BufferedImage cropped = raw.getSubimage(x0, y0, x1 - x0, y1 - y0);
            BufferedImage px = PixelArt.pixelize(cropped, W, H);
            ImageIO.write(px, "png", outDir.resolve(name).toFile());
            System.out.println("Retrato importado: " + name + " (" + w + "x" + h + " -> " + W + "x" + H + ")");
            count++;
        }

        writeManifest(assets.resolve("manifest").resolve("retratos.yaml"), listPngs(outDir));
        System.out.println("Pronto: " + count + " retrato(s) importado(s); manifesto retratos.yaml atualizado.");
    }

    private static List<Path> listPngs(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".png"))
                    .sorted()
                    .toList();
        }
    }

    private static void writeManifest(Path manifest, List<Path> portraits) throws IOException {
        Files.createDirectories(manifest.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("# Gerado por `gradlew importPortraits`. Não edite à mão.\n");
        for (Path p : portraits) {
            String file = p.getFileName().toString();
            String id = file.substring(0, file.length() - 4);
            sb.append("- id: retrato.").append(id).append('\n');
            sb.append("  type: portrait\n");
            sb.append("  file: retratos/").append(file).append('\n');
            sb.append("  source: \"Gerado por IA (Dreamina, Seedream 5.5) e convertido para a paleta do jogo\"\n");
            sb.append("  license: \"Dreamina — conferir termos de uso comercial antes do release\"\n");
        }
        Files.writeString(manifest, sb.toString(), StandardCharsets.UTF_8);
    }
}
