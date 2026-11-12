package etrava.tools.art.pixel;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Funil de textura (SPEC-visual §3): original da Dreamina em {@code arte-bruta/texturas/} →
 * recorte central → repetível (deslocamento de meio quadro + mescla nas costuras) → redução por
 * média de área → só luminância, normalizada (média 0,5) → {@code tools/blender/texturas/}.
 * A textura entra no render só como variação de valor; a cor sempre sai da rampa da paleta.
 */
public final class ImportTextures {
    static final int TAM = 256;

    public static void main(String[] args) throws IOException {
        Path origem = Path.of(args[0]), destino = Path.of(args[1]);
        Files.createDirectories(destino);
        int n = 0;
        try (Stream<Path> s = Files.list(origem)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".png")).sorted().toList()) {
                BufferedImage img = ImageIO.read(p.toFile());
                if (img == null) continue;
                ImageIO.write(funil(img), "png", destino.resolve(p.getFileName()).toFile());
                n++;
            }
        }
        System.out.println("Texturas: " + n + " em " + destino);
    }

    static BufferedImage funil(BufferedImage img) {
        // 1. recorte central quadrado (80 % do lado menor: as bordas da IA costumam ter vinheta)
        int lado = (int) (Math.min(img.getWidth(), img.getHeight()) * 0.8);
        int x0 = (img.getWidth() - lado) / 2, y0 = (img.getHeight() - lado) / 2;
        // 2. luminância reduzida por média de área
        double[] lum = new double[TAM * TAM];
        double f = (double) lado / TAM;
        for (int y = 0; y < TAM; y++)
            for (int x = 0; x < TAM; x++) {
                double soma = 0;
                int c = 0;
                for (int sy = (int) (y * f); sy < (int) ((y + 1) * f); sy++)
                    for (int sx = (int) (x * f); sx < (int) ((x + 1) * f); sx++) {
                        int rgb = img.getRGB(x0 + sx, y0 + sy);
                        soma += Paleta.luminancia(rgb);
                        c++;
                    }
                lum[y * TAM + x] = soma / Math.max(1, c);
            }
        // 3. repetível: mistura a imagem com ela mesma deslocada de meio quadro, pesando pela
        // distância à borda (no meio vale a original; nas bordas, a deslocada, que é contínua lá)
        double[] rep = new double[TAM * TAM];
        int h = TAM / 2;
        for (int y = 0; y < TAM; y++)
            for (int x = 0; x < TAM; x++) {
                double wx = Math.min(x, TAM - 1 - x) / (double) h, wy = Math.min(y, TAM - 1 - y) / (double) h;
                double w = Math.min(1, Math.min(wx, wy) * 2.2);
                double a = lum[y * TAM + x], b = lum[((y + h) % TAM) * TAM + (x + h) % TAM];
                rep[y * TAM + x] = a * w + b * (1 - w);
            }
        // 4. normaliza: média 0,5, desvio 0,18
        double m = 0, v = 0;
        for (double d : rep) m += d;
        m /= rep.length;
        for (double d : rep) v += (d - m) * (d - m);
        double dp = Math.sqrt(v / rep.length) + 1e-6;
        BufferedImage out = new BufferedImage(TAM, TAM, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < TAM; y++)
            for (int x = 0; x < TAM; x++) {
                double val = 0.5 + (rep[y * TAM + x] - m) / dp * 0.18;
                int g = (int) Math.round(Math.max(0, Math.min(1, val)) * 255);
                out.getRaster().setSample(x, y, 0, g);
            }
        return out;
    }
}
