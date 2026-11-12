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
                new Color(0xC99A76), new Color(0x3B2A1E), new Color(0xB9A888), new Color(0x4A4038),
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

        // M6: inimigos e a estrutura enterrada
        character("personagem.saqueador", "personagens/saqueador.png", new CharacterArt.Look(
                new Color(0xA67A5C), new Color(0x2A201A), new Color(0x5A4632), new Color(0x3A342C),
                new Color(0x231B16), true, 0.24));
        // o vulto: fibra escura e terra; sem rosto (a "pele" é da cor do capuz)
        character("personagem.vulto", "personagens/vulto.png", new CharacterArt.Look(
                new Color(0x2E2B26), new Color(0x1A1815), new Color(0x2B2824), new Color(0x221F1B),
                new Color(0x1A1815), true, 0.62));
        quadruped("personagem.lobo", "personagens/lobo.png", new Color(0x6E6658), new Color(0x9A9080));
        sprite("prop.fibra", "props/fibra.png", RuinArt.fiber(1));
        sprite("prop.entulho", "props/entulho.png", RuinArt.rubble());
        sprite("prop.laje_simbolos", "props/laje_simbolos.png", RuinArt.slab());
        sprite("prop.luz_buraco", "props/luz_buraco.png", RuinArt.ladder());
        sprite("prop.buraco_terra", "props/buraco_terra.png", RuinArt.pit());

        m7();
        icons();

        Path file = assets.resolve("manifest/provisorio.yaml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, manifest.toString(), StandardCharsets.UTF_8);
        System.out.println("Arte provisória gerada em " + assets.toAbsolutePath());
    }

    /** M7: a região inteira — interiores, casas novas, lavoura, cemitério, estrada, Posto e o elenco. */
    private void m7() throws IOException {
        tiles("tabuas", 2, 6, TileArt::planks);
        tiles("lajota", 2, 4, TileArt::flagstone);
        tiles("plantio", 3, 8, TileArt::crops);

        // paredes de fundo dos cômodos: um metro cada, no eixo x (norte) ou y (oeste)
        for (String kind : new String[] {"lisa", "janela", "porta", "prateleira"}) {
            sprite("prop.parede_x_" + kind, "props/interior/parede_x_" + kind + ".png", FurnitureArt.wall(true, kind, kind.length()));
            sprite("prop.parede_y_" + kind, "props/interior/parede_y_" + kind + ".png", FurnitureArt.wall(false, kind, kind.length() + 7));
        }
        sprite("prop.parede_canto", "props/interior/parede_canto.png", FurnitureArt.wallCorner());
        sprite("prop.mesa", "props/interior/mesa.png", FurnitureArt.table(false));
        sprite("prop.mesa_longa", "props/interior/mesa_longa.png", FurnitureArt.table(true));
        sprite("prop.banco", "props/interior/banco.png", FurnitureArt.bench());
        sprite("prop.cama", "props/interior/cama.png", FurnitureArt.bed());
        sprite("prop.balcao", "props/interior/balcao.png", FurnitureArt.counter());
        sprite("prop.lareira", "props/interior/lareira.png", FurnitureArt.hearth());
        sprite("prop.bancada", "props/interior/bancada.png", FurnitureArt.workbench());
        sprite("prop.bau", "props/interior/bau.png", FurnitureArt.chest());
        sprite("prop.sacos", "props/interior/sacos.png", FurnitureArt.sacks());
        sprite("prop.mo", "props/interior/mo.png", FurnitureArt.millstone());
        sprite("prop.ervas", "props/interior/ervas.png", FurnitureArt.herbRack());
        sprite("prop.bigorna", "props/bigorna.png", FurnitureArt.anvil());
        sprite("prop.forja", "props/forja.png", FurnitureArt.forge());

        sprite("prop.cerca_x", "props/cerca_x.png", FurnitureArt.fence(true));
        sprite("prop.cerca_y", "props/cerca_y.png", FurnitureArt.fence(false));
        sprite("prop.lapide", "props/lapide.png", FurnitureArt.gravestone(0));
        sprite("prop.lapide_b", "props/lapide_b.png", FurnitureArt.gravestone(1));
        sprite("prop.estaca", "props/estaca.png", FurnitureArt.stake());
        sprite("prop.marco", "props/marco.png", FurnitureArt.marker());
        sprite("prop.tenda", "props/tenda.png", FurnitureArt.tent());
        sprite("prop.carroca", "props/carroca.png", FurnitureArt.cart());
        sprite("prop.banca", "props/banca.png", FurnitureArt.stall());
        sprite("prop.espantalho", "props/espantalho.png", FurnitureArt.scarecrow());
        sprite("prop.arvore_fruto", "props/arvore_fruto.png", FurnitureArt.fruitTree(1));
        sprite("prop.arvore_fruto_b", "props/arvore_fruto_b.png", FurnitureArt.fruitTree(2));
        sprite("prop.terra_remexida", "props/terra_remexida.png", FurnitureArt.filledPit());
        sprite("prop.vazio", "props/vazio.png", FurnitureArt.empty());
        sprite("prop.placa", "props/placa.png", FurnitureArt.signpost());
        sprite("prop.pedras", "props/pedras.png", FurnitureArt.rocks());

        // casas novas do vilarejo e o Posto
        sprite("prop.casa_curandeira", "props/casa_curandeira.png", BuildingArt.house(new Spec(5, 4, 2.4, 2.2, true, true, 21,
                new Opening('S', 2.0, true), new Opening('S', 0.6, false), new Opening('E', 1.6, false))));
        sprite("prop.casa_media", "props/casa_media.png", BuildingArt.house(new Spec(6, 5, 2.6, 2.4, true, true, 22,
                new Opening('S', 2.5, true), new Opening('S', 0.8, false), new Opening('S', 4.4, false),
                new Opening('E', 2.0, false))));
        sprite("prop.casa_estreita", "props/casa_estreita.png", BuildingArt.house(new Spec(4, 6, 2.5, 2.3, false, false, 23,
                new Opening('S', 1.5, true), new Opening('E', 1.2, false), new Opening('E', 3.8, false))));
        sprite("prop.celeiro", "props/celeiro.png", BuildingArt.house(new Spec(8, 6, 3.0, 3.0, true, false, 24,
                new Opening('S', 3.0, true), new Opening('S', 4.2, true), new Opening('E', 3.0, false))));
        sprite("prop.casa_posto", "props/casa_posto.png", BuildingArt.house(new Spec(10, 6, 3.0, 2.8, true, true, 25,
                new Opening('S', 4.5, true), new Opening('S', 1.2, false), new Opening('S', 2.8, false),
                new Opening('S', 7.0, false), new Opening('S', 8.6, false), new Opening('E', 1.6, false),
                new Opening('E', 4.2, false))));

        // o elenco do Capítulo I (D-067): o mesmo boneco, com roupa e cores de cada um
        look("personagem.avel", "avel", 0xB8896A, 0x8E8A82, 0x6B4A2E, 0x3E3A32, 0x2A201A, false, 0.30, 1.0);
        look("personagem.estranho", "estranho", 0xB07F5E, 0x3A3634, 0x2A2428, 0x2E2A26, 0x1E1A18, false, 0.55, 1.0);
        look("personagem.registrador", "registrador", 0xD0A283, 0x2A2320, 0x3B4048, 0x2A2A2E, 0x1E1A18, false, 0.40, 1.0);
        look("personagem.agrimensora", "agrimensora", 0xC99A76, 0x2A2320, 0x5A3F26, 0x3E3A32, 0x2A201A, false, 0.36, 1.0);
        look("personagem.bento", "bento", 0xA06C50, 0x5A3F26, 0x5A6438, 0x4A4038, 0x2E2420, false, 0.18, 1.0);
        look("personagem.gil", "gil", 0xC08A68, 0x3D2A1C, 0x9C7A45, 0x524C46, 0x2E2420, false, 0.16, 1.0);
        look("personagem.pipo", "pipo", 0xD8A884, 0xB8975A, 0x868C52, 0x5A3F26, 0x3D2A1C, false, 0.20, 0.66);
        look("personagem.lauro", "lauro", 0xC08A68, 0x3D2A1C, 0x8A4E2E, 0x4A4038, 0x2E2420, false, 0.22, 1.0);
        look("personagem.ciro", "ciro", 0xA06C50, 0x524C46, 0x565C66, 0x3A3634, 0x2E2420, true, 0.20, 1.0);
        look("personagem.anselmo", "anselmo", 0xC4967A, 0xEEE6D2, 0x8C7E66, 0x524C46, 0x2E2420, false, 0.50, 0.96);
        look("personagem.lina", "lina", 0xD8A884, 0x8A4E2E, 0xB9A888, 0x5A3F26, 0x2E2420, false, 0.46, 0.97);
        look("personagem.tobias", "tobias", 0xA06C50, 0x2A2320, 0x7C5A40, 0x3E3A32, 0x2A201A, false, 0.18, 1.06);
        look("personagem.vessa", "vessa", 0xB07F5E, 0x2A2320, 0x4A3526, 0x3A3430, 0x241C18, false, 0.30, 1.0);
        look("personagem.firmino", "firmino", 0xC4967A, 0x8E8A82, 0x2A2428, 0x2A2A2E, 0x1E1A18, true, 0.46, 0.98);
    }

    /** M8: um ícone por item (RF-200), 20×20 px de arte. */
    private void icons() throws IOException {
        for (var e : ItemIcons.all().entrySet()) {
            String file = "icones/" + e.getKey() + ".png";
            write(file, PixelArt.pixelize(ItemIcons.draw(e.getValue()), true));
            manifest.append("- id: icone.").append(e.getKey()).append('\n')
                    .append("  type: icon\n")
                    .append("  file: ").append(file).append('\n');
            credits();
        }
    }

    private void look(String id, String file, int skin, int hair, int tunic, int pants, int boots, boolean hood,
            double skirt, double scale) throws IOException {
        character(id, "personagens/" + file + ".png", new CharacterArt.Look(new Color(skin), new Color(hair),
                new Color(tunic), new Color(pants), new Color(boots), hood, skirt, scale));
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
        appendSheet(id, file, CharacterArt.FRAME_W, CharacterArt.ANCHOR_X);
        credits();
    }

    private void quadruped(String id, String file, Color fur, Color belly) throws IOException {
        write(file, PixelArt.pixelize(CharacterArt.quadrupedSheet(fur, belly), true));
        appendSheet(id, file, CharacterArt.QUAD_FRAME_W, CharacterArt.QUAD_ANCHOR_X);
        credits();
    }

    private void appendSheet(String id, String file, int frameW, int anchorX) {
        manifest.append("- id: ").append(id).append('\n')
                .append("  type: spritesheet\n")
                .append("  file: ").append(file).append('\n')
                .append("  frame: [").append(frameW / PixelArt.SCALE).append(", ")
                .append(CharacterArt.FRAME_H / PixelArt.SCALE).append("]\n")
                .append("  anchor: [").append(anchorX / PixelArt.SCALE).append(", ")
                .append(CharacterArt.ANCHOR_Y / PixelArt.SCALE).append("]\n")
                .append("  directions: ").append(CharacterArt.DIRECTIONS).append('\n')
                .append("  animations:\n")
                .append("    idle: {start: 0, frames: ").append(CharacterArt.IDLE_FRAMES).append(", fps: 3}\n")
                .append("    walk: {start: ").append(CharacterArt.IDLE_FRAMES).append(", frames: ")
                .append(CharacterArt.WALK_FRAMES).append(", fps: 11}\n")
                .append("    attack: {start: ").append(CharacterArt.IDLE_FRAMES + CharacterArt.WALK_FRAMES)
                .append(", frames: ").append(CharacterArt.ATTACK_FRAMES).append(", fps: 12}\n");
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
