package etrava.tools.art.pixel;

import etrava.sim.content.AssetManifest;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Validação da arte da Fase V (SPEC-visual §9.4), parte do {@code validateContent}:
 * <ul>
 *   <li>VT-04: todo pixel opaco de toda imagem da arte nova (entradas com {@code scale: 2}) está
 *       na paleta v3; o único translúcido aceito é a sombra no chão;</li>
 *   <li>VT-05 (parte do V1): folha de personagem com as 8 direções e as animações do papel, âncora
 *       dentro do quadro.</li>
 * </ul>
 * Aponta arquivo e posição do primeiro pixel errado de cada imagem.
 */
public final class ValidaArte {
    private ValidaArte() {}

    public static List<String> validar(Path assets, Path paletaYaml) {
        List<String> erros = new ArrayList<>();
        if (!Files.exists(paletaYaml)) return erros;
        Paleta paleta = Paleta.carregar(paletaYaml);
        AssetManifest man = AssetManifest.load(assets);
        for (AssetManifest.Entry e : man.entries().values()) {
            if (e.scaleOr() != 2) continue;
            for (String f : e.allFiles()) {
                if (!f.endsWith(".png")) continue;
                Path p = assets.resolve(f);
                if (!Files.exists(p)) continue;
                String erro = paleta(paleta, p, f, f.endsWith("_brilho.png"));
                if (erro != null) erros.add(e.id() + ": " + erro);
            }
            if ("spritesheet".equals(e.type())) {
                if (e.directions() == null || e.directions() != 8) erros.add(e.id() + ": folha de personagem sem 8 direções (VT-05)");
                List<String> exigidas = "jogador".equals(e.role()) ? List.of("parado", "andar", "correr") : List.of("parado", "andar");
                for (String a : exigidas)
                    if (e.animations() == null || !e.animations().containsKey(a)) erros.add(e.id() + ": falta a animação " + a + " (VT-05)");
                if (e.anchor() != null && e.frame() != null
                        && (e.anchor()[0] < 0 || e.anchor()[1] < 0 || e.anchor()[0] > e.frame()[0] || e.anchor()[1] > e.frame()[1]))
                    erros.add(e.id() + ": âncora fora do quadro (VT-05)");
            }
        }
        return erros;
    }

    static String paleta(Paleta paleta, Path p, String nome, boolean brilho) {
        try {
            BufferedImage img = ImageIO.read(p.toFile());
            if (img == null) return nome + ": PNG ilegível";
            int fora = 0, px = -1, py = -1;
            for (int y = 0; y < img.getHeight(); y++)
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getRGB(x, y), a = argb >>> 24;
                    if (a == 0 || argb == Pixelizador.COR_SOMBRA) continue;
                    if (a == 255 && paleta.contem(argb)) continue;
                    if (fora++ == 0) {
                        px = x;
                        py = y;
                    }
                }
            if (fora == 0) return null;
            return String.format("%s: %d pixel(s) fora da paleta v3 (o primeiro em %d, %d) (VT-04)", nome, fora, px, py);
        } catch (IOException e) {
            return nome + ": " + e.getMessage();
        }
    }
}
