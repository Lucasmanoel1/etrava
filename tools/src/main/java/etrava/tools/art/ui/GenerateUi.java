package etrava.tools.art.ui;

import etrava.tools.art.ui.Pecas.Peca;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Gera o tema da interface (V7, SPEC-visual §8.3): os PNG em {@code assets/ui/}, o manifesto
 * {@code assets/manifest/ui.yaml}, os metadados dos 9-patches em {@code assets/config/ui.yaml} e a prancha
 * de revisão. Determinístico: mesma entrada, mesmos bytes.
 *
 * <p>Uso: {@code GenerateUi <pasta assets> <prancha.png>}.
 */
public final class GenerateUi {
    private GenerateUi() {}

    public static void main(String[] args) throws IOException {
        Path assets = Path.of(args.length > 0 ? args[0] : "assets");
        Path prancha = Path.of(args.length > 1 ? args[1] : "docs/capturas/v7/prancha_tema.png");
        List<Peca> pecas = Pecas.todas();

        Path ui = assets.resolve("ui");
        Files.createDirectories(ui);
        StringBuilder man = new StringBuilder();
        man.append("# GERADO por `gradlew :tools:generateUi` — não edite à mão.\n");
        man.append("# Tema da interface (V7): 9-patches, botões, molduras, ícones de HUD e cursores, desenhados por código\n");
        man.append("# na paleta v3. Os splits e recuos dos 9-patches ficam em assets/config/ui.yaml.\n\n");
        StringBuilder cfg = new StringBuilder();
        cfg.append("# GERADO por `gradlew :tools:generateUi` — não edite à mão.\n");
        cfg.append("# Metadados do tema da interface (V7). split = bordas do 9-patch em px de arte (esq, dir, cima, baixo);\n");
        cfg.append("# pad = recuo do conteúdo (esq, dir, cima, baixo); ponto = ponto ativo do cursor; sem split = imagem simples.\n");
        cfg.append("pecas:\n");
        StringBuilder cur = new StringBuilder("cursores:\n");
        for (Peca p : pecas) {
            String nome = p.id().substring(3);
            ImageIO.write(p.img().imagem(), "png", ui.resolve(nome + ".png").toFile());
            man.append("- id: \"").append(p.id()).append("\"\n  type: \"ui\"\n  file: \"ui/").append(nome).append(".png\"\n")
                    .append("  scale: 2\n  source: \"gerado por código (GenerateUi)\"\n  license: \"proprio\"\n");
            cfg.append("  ").append(p.id()).append(": {");
            if (p.split() != null)
                cfg.append("split: ").append(Arrays.toString(p.split())).append(", pad: ").append(Arrays.toString(p.pad()));
            cfg.append("}\n");
            if (p.ponto() != null) cur.append("  ").append(p.id()).append(": {ponto: ").append(Arrays.toString(p.ponto())).append("}\n");
        }
        cfg.append(cur);
        cfg.append("cores:\n");
        cfg.append("  tinta: \"241C18\"\n  tinta_suave: \"5A4232\"\n  ferrugem: \"6E2C22\"\n  tinta_clara: \"EEE6D2\"\n  desativado: \"857D70\"\n");
        Files.createDirectories(assets.resolve("manifest"));
        Files.writeString(assets.resolve("manifest/ui.yaml"), man.toString(), StandardCharsets.UTF_8);
        Files.createDirectories(assets.resolve("config"));
        Files.writeString(assets.resolve("config/ui.yaml"), cfg.toString(), StandardCharsets.UTF_8);

        Files.createDirectories(prancha.toAbsolutePath().getParent());
        ImageIO.write(new Prancha(pecas).gerar(), "png", prancha.toFile());
        System.out.println("Tema da interface: " + pecas.size() + " peças em " + ui + "; prancha em " + prancha);
    }
}
