package etrava.tools.art;

import etrava.tools.art.BuildingArt.Art;
import etrava.tools.art.BuildingArt.Opening;
import etrava.tools.art.BuildingArt.Spec;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Gera a arte PROVISÓRIA do jogo por código ({@code gradlew generateArt}) e o
 * manifesto correspondente ({@code assets/manifest/provisorio.yaml}).
 * Serve para o jogo ter clima e leitura antes da arte definitiva; cada imagem
 * pode ser substituída por um arquivo de mesmo nome, mantendo o ID (RF-423).
 */
public final class GenerateArt {
    private static final String SOURCE = "Gerado por código (tools: GenerateArt) — arte provisória";

    private final Path assets;
    private final StringBuilder manifest = new StringBuilder();

    private GenerateArt(Path assets) {
        this.assets = assets;
    }

    public static void main(String[] args) throws IOException {
        Path assets = Path.of(args.length > 0 ? args[0] : "assets");
        new GenerateArt(assets).run();
    }

    private void run() throws IOException {
        manifest.append("# GERADO por `gradlew generateArt` — não edite à mão.\n")
                .append("# Arte provisória feita por código, já em pixel art (1 px de arte = 4 px de mundo).\n")
                .append("# Medidas (anchor, frame) em pixels de arte. Para trocar uma imagem pela definitiva,\n")
                .append("# copie a entrada para outro arquivo de manifesto desta pasta (ele tem prioridade)\n")
                .append("# ou simplesmente substitua o PNG mantendo nome, tamanho e ponto de apoio.\n\n");

        tiles("grama", 3, 3, TileArt::grass);
        tiles("terra", 3, 2, TileArt::dirt);
        tiles("pedra", 2, 1, TileArt::cobble);

        sprite("prop.arvore", "props/arvore.png", PropArt.tree(1, false));
        sprite("prop.arvore_b", "props/arvore_b.png", PropArt.tree(2, false));
        sprite("prop.arvore_morta", "props/arvore_morta.png", PropArt.tree(3, true));
        sprite("prop.toco", "props/toco.png", PropArt.stump());
        sprite("prop.barril", "props/barril.png", PropArt.barrel());
        sprite("prop.caixote", "props/caixote.png", PropArt.crate());
        sprite("prop.poco", "props/poco.png", PropArt.well());
        sprite("prop.lampiao", "props/lampiao.png", PropArt.lampPost());
        sprite("prop.feno", "props/feno.png", PropArt.hay());

        sprite("prop.casa_oficina", "props/casa_oficina.png", BuildingArt.house(new Spec(7, 5, 2.7, 2.3, true, true, 11,
                new Opening('S', 3.0, true), new Opening('S', 1.0, false), new Opening('S', 5.3, false),
                new Opening('E', 2.1, false))));
        sprite("prop.casa_taverna", "props/casa_taverna.png", BuildingArt.house(new Spec(9, 6, 3.0, 2.7, true, true, 12,
                new Opening('S', 4.0, true), new Opening('S', 1.2, false), new Opening('S', 2.6, false),
                new Opening('S', 6.2, false), new Opening('S', 7.6, false), new Opening('E', 1.4, false),
                new Opening('E', 3.9, false))));
        sprite("prop.casa_pequena", "props/casa_pequena.png", BuildingArt.house(new Spec(5, 5, 2.5, 2.2, false, false, 13,
                new Opening('E', 2.0, true), new Opening('S', 2.1, false), new Opening('E', 3.9, false))));
        sprite("prop.casa_moinho", "props/casa_moinho.png", BuildingArt.house(new Spec(5, 6, 3.8, 2.6, false, true, 14,
                new Opening('S', 2.0, true), new Opening('E', 1.5, false), new Opening('E', 4.2, false))));

        character("personagem.aprendiz", "personagens/aprendiz.png", new CharacterArt.Look(
                new Color(0xC99A76), new Color(0x3B2A1E), new Color(0x7B6A45), new Color(0x4A4038),
                new Color(0x2E2420), false, 0.16));
        character("personagem.aldea", "personagens/aldea.png", new CharacterArt.Look(
                new Color(0xB98A6A), new Color(0x5A3A28), new Color(0x7A3F2C), new Color(0x57503F),
                new Color(0x2E2420), true, 0.42));

        character("personagem.ferreiro", "personagens/ferreiro.png", new CharacterArt.Look(
                new Color(0xB07F5E), new Color(0x2A2320), new Color(0x4F4A44), new Color(0x3A3430),
                new Color(0x241C18), false, 0.30));
        character("personagem.taberneira", "personagens/taberneira.png", new CharacterArt.Look(
                new Color(0xD0A283), new Color(0x7A4A2A), new Color(0x8C7A52), new Color(0x5C5240),
                new Color(0x2E2420), false, 0.46));
        character("personagem.moleiro", "personagens/moleiro.png", new CharacterArt.Look(
                new Color(0xC4967A), new Color(0x8E8A82), new Color(0xA69C84), new Color(0x4A4038),
                new Color(0x2E2420), false, 0.20));
        character("personagem.viajante", "personagens/viajante.png", new CharacterArt.Look(
                new Color(0xA87858), new Color(0x1E1A18), new Color(0x4E5A4A), new Color(0x3C3A34),
                new Color(0x241C18), true, 0.50));

        character("personagem.nalia", "personagens/nalia.png", new CharacterArt.Look(
                new Color(0xC9A084), new Color(0x4A2E20), new Color(0x6A6E58), new Color(0x4E463C),
                new Color(0x2E2420), false, 0.40));
        character("personagem.curandeira", "personagens/curandeira.png", new CharacterArt.Look(
                new Color(0xAE8468), new Color(0x8E8A82), new Color(0x4A3F4E), new Color(0x3A3430),
                new Color(0x241C18), true, 0.52));
        character("personagem.estudioso", "personagens/estudioso.png", new CharacterArt.Look(
                new Color(0xC2997C), new Color(0x2A2320), new Color(0x2E3440), new Color(0x2A2A2E),
                new Color(0x1E1A18), false, 0.48));
        character("personagem.comerciante", "personagens/comerciante.png", new CharacterArt.Look(
                new Color(0xB8896A), new Color(0x3A2C22), new Color(0x6B4A2E), new Color(0x3E3A32),
                new Color(0x2A201A), false, 0.44));

        Path file = assets.resolve("manifest/provisorio.yaml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, manifest.toString(), StandardCharsets.UTF_8);
        System.out.println("Arte provisória gerada em " + assets.toAbsolutePath());
    }

    private interface TilePainter { BufferedImage paint(int variant); }

    private void tiles(String terrain, int variants, int priority, TilePainter painter) throws IOException {
        StringBuilder files = new StringBuilder();
        for (int i = 1; i <= variants; i++) {
            String file = "tiles/" + terrain + "_" + i + ".png";
            write(file, PixelArt.pixelize(painter.paint(i), false));
            files.append(i > 1 ? ", " : "").append(file);
        }
        manifest.append("- id: tile.").append(terrain).append('\n')
                .append("  type: tile\n")
                .append("  files: [").append(files).append("]\n")
                .append("  priority: ").append(priority).append('\n');
        credits();
    }

    private void sprite(String id, String file, Art art) throws IOException {
        write(file, PixelArt.pixelize(art.image(), true));
        manifest.append("- id: ").append(id).append('\n')
                .append("  type: sprite\n")
                .append("  file: ").append(file).append('\n')
                .append(String.format(Locale.ROOT, "  anchor: [%.0f, %.0f]%n",
                        art.anchorX() / PixelArt.SCALE, art.anchorY() / PixelArt.SCALE));
        if (art.emissive() != null) {
            String glow = file.replace(".png", "_brilho.png");
            write(glow, PixelArt.pixelize(art.emissive(), false));
            manifest.append("  emissive: ").append(glow).append('\n');
        }
        credits();
    }

    private void character(String id, String file, CharacterArt.Look look) throws IOException {
        write(file, PixelArt.pixelize(CharacterArt.sheet(look), true));
        manifest.append("- id: ").append(id).append('\n')
                .append("  type: spritesheet\n")
                .append("  file: ").append(file).append('\n')
                .append("  frame: [").append(CharacterArt.FRAME_W / PixelArt.SCALE).append(", ")
                .append(CharacterArt.FRAME_H / PixelArt.SCALE).append("]\n")
                .append("  anchor: [").append(CharacterArt.ANCHOR_X / PixelArt.SCALE).append(", ")
                .append(CharacterArt.ANCHOR_Y / PixelArt.SCALE).append("]\n")
                .append("  directions: ").append(CharacterArt.DIRECTIONS).append('\n')
                .append("  animations:\n")
                .append("    idle: {start: 0, frames: ").append(CharacterArt.IDLE_FRAMES).append(", fps: 3}\n")
                .append("    walk: {start: ").append(CharacterArt.IDLE_FRAMES).append(", frames: ")
                .append(CharacterArt.WALK_FRAMES).append(", fps: 11}\n");
        credits();
    }

    private void credits() {
        manifest.append("  source: \"").append(SOURCE).append("\"\n")
                .append("  license: proprio\n\n");
    }

    private void write(String relative, BufferedImage image) throws IOException {
        Path file = assets.resolve(relative);
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }
}
