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
 *   <li>VT-05: folha de personagem com as 8 direções, as animações do papel e o número de quadros
 *       da tabela (SPEC-visual §6.2), âncora dentro do quadro.</li>
 * </ul>
 * Aponta arquivo e posição do primeiro pixel errado de cada imagem.
 */
public final class ValidaArte {
    private ValidaArte() {}

    /** Quadros de cada animação (SPEC-visual §6.2). */
    static final java.util.Map<String, Integer> QUADROS = java.util.Map.ofEntries(
            java.util.Map.entry("parado", 8), java.util.Map.entry("andar", 8), java.util.Map.entry("correr", 8),
            java.util.Map.entry("sentar", 3), java.util.Map.entry("sentado", 4),
            java.util.Map.entry("trabalhar_bancada", 8), java.util.Map.entry("trabalhar_martelo", 8),
            java.util.Map.entry("trabalhar_chao", 8), java.util.Map.entry("conversar", 6), java.util.Map.entry("dormir", 4),
            // V3: combate
            java.util.Map.entry("golpe_corte", 6), java.util.Map.entry("golpe_estocada", 6), java.util.Map.entry("golpe_pesado", 6),
            java.util.Map.entry("dano", 3), java.util.Map.entry("cair", 6), java.util.Map.entry("caido", 1),
            java.util.Map.entry("levantar", 5),
            java.util.Map.entry("bote", 6), java.util.Map.entry("golpe", 6), java.util.Map.entry("morrer", 6),
            java.util.Map.entry("morto", 1), java.util.Map.entry("deitado", 4), java.util.Map.entry("desfazer", 6));

    /**
     * Animações exigidas por papel (SPEC-visual §6.2, até o V2): gente comum senta e conversa (o
     * trabalho depende do ofício e dormir só de quem dorme à vista); o jogador também trabalha nos
     * três jeitos e dorme. No V3 entram golpes, dano e queda (jogador e saqueador).
     */
    static List<String> exigidas(String papel) {
        return switch (papel == null ? "comum" : papel) {
            case "jogador" -> List.of("parado", "andar", "correr", "sentar", "sentado", "trabalhar_bancada",
                    "trabalhar_martelo", "trabalhar_chao", "dormir", "golpe_corte", "golpe_estocada", "golpe_pesado",
                    "dano", "cair", "caido", "levantar");
            case "saqueador" -> List.of("parado", "andar", "correr", "golpe_corte", "dano", "cair", "caido");
            case "camada" -> List.of("parado", "andar");
            case "lobo" -> List.of("parado", "andar", "correr", "bote", "dano", "morrer", "morto", "deitado");
            case "vulto" -> List.of("parado", "andar", "golpe", "dano", "desfazer");   // chapéu e capuz do jogador: a grade do corpo
            default -> List.of("parado", "andar", "sentar", "sentado", "conversar");
        };
    }

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
                for (String a : exigidas(e.role()))
                    if (e.animations() == null || !e.animations().containsKey(a)) erros.add(e.id() + ": falta a animação " + a + " (VT-05)");
                if (e.animations() != null)
                    e.animations().forEach((nome, an) -> {
                        Integer q = QUADROS.get(nome);
                        if (q != null && an.frames() != q)
                            erros.add(e.id() + ": " + nome + " com " + an.frames() + " quadros, a tabela pede " + q + " (VT-05)");
                    });
                String iso = isolados(assets.resolve(e.file()));
                if (iso != null) erros.add(e.id() + ": " + iso);
                if (e.meta() != null) erros.addAll(pes(assets.resolve(e.meta()), e).stream().map(s -> e.id() + ": " + s).toList());
                if (e.anchor() != null && e.frame() != null
                        && (e.anchor()[0] < 0 || e.anchor()[1] < 0 || e.anchor()[0] > e.frame()[0] || e.anchor()[1] > e.frame()[1]))
                    erros.add(e.id() + ": âncora fora do quadro (VT-05)");
            }
        }
        return erros;
    }

    /** Px de arte que o pé de apoio pode escorregar entre dois quadros (VT-15). */
    static final double DESLIZE_MAX = 1.0;

    /**
     * VT-15 (pé que desliza): nos ciclos {@code parado} e {@code andar}, o pé de apoio acompanha o
     * chão. No parado, o chão não anda: um dos pés fica onde está. No andar, o cliente avança o
     * quadro pela distância ({@code stride} m por ciclo, D-095), então entre dois quadros o chão
     * corre {@code stride/n} m para trás, projetado na tela (IsoProjection: 1 m em x = (32, 16) px
     * de arte, em y = (−32, 16)); o pé de apoio é o que mais se aproxima desse deslocamento, e a
     * diferença não passa de {@link #DESLIZE_MAX}. Usa os pontos {@code pe_e}/{@code pe_d} do JSON;
     * folha sem eles fica de fora.
     */
    static List<String> pes(Path meta, AssetManifest.Entry e) {
        List<String> erros = new ArrayList<>();
        if (!Files.exists(meta) || e.animations() == null) return erros;
        com.fasterxml.jackson.databind.JsonNode pts;
        try {
            pts = new com.fasterxml.jackson.databind.ObjectMapper().readTree(meta.toFile()).path("points");
        } catch (IOException ex) {
            return List.of(meta + ": " + ex.getMessage());
        }
        int dirs = e.directions() == null ? 8 : e.directions();
        for (String nome : List.of("parado", "andar")) {
            AssetManifest.Anim an = e.animations().get(nome);
            if (an == null) continue;
            int n = an.frames();
            double passo = nome.equals("andar") && an.stride() != null ? an.stride() / n : 0;
            double pior = 0;
            String onde = null;
            for (int d = 0; d < dirs; d++) {
                double a = d * 2 * Math.PI / dirs, cx = Math.cos(a), cy = Math.sin(a);
                // o chão corre para trás do passo: −(deslocamento do corpo) na tela, y para baixo
                double ex = -passo * 32 * (cx - cy), ey = -passo * 16 * (cx + cy);
                for (int f = 0; f < n; f++) {
                    var p0 = pts.path(nome + "/" + d + "/" + f);
                    var p1 = pts.path(nome + "/" + d + "/" + ((f + 1) % n));
                    if (!p0.has("pe_e") || !p1.has("pe_e")) return erros;
                    double melhor = Double.MAX_VALUE;
                    for (String pe : List.of("pe_e", "pe_d")) {
                        double dx = p1.path(pe).path(0).asDouble() - p0.path(pe).path(0).asDouble() - ex;
                        double dy = p1.path(pe).path(1).asDouble() - p0.path(pe).path(1).asDouble() - ey;
                        melhor = Math.min(melhor, Math.hypot(dx, dy));
                    }
                    if (melhor > pior) {
                        pior = melhor;
                        onde = d + "/" + f;
                    }
                }
            }
            if (pior > DESLIZE_MAX)
                erros.add(String.format(java.util.Locale.ROOT, "%s: o pé de apoio desliza %.1f px (direção/quadro %s) (VT-15)",
                        nome, pior, onde));
        }
        return erros;
    }

    /**
     * VT-15 (parte): pixel opaco sem nenhum vizinho opaco (nas 8 direções) numa folha de personagem;
     * no jogo ele pisca solto, longe do corpo. Aponta o primeiro.
     */
    static String isolados(Path p) {
        try {
            if (!Files.exists(p)) return null;
            BufferedImage img = ImageIO.read(p.toFile());
            int w = img.getWidth(), h = img.getHeight(), n = 0, px = -1, py = -1;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    if ((img.getRGB(x, y) >>> 24) == 0 || img.getRGB(x, y) == Pixelizador.COR_SOMBRA) continue;
                    boolean viz = false;
                    for (int dy = -1; dy <= 1 && !viz; dy++)
                        for (int dx = -1; dx <= 1 && !viz; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int xx = x + dx, yy = y + dy;
                            if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                            int c = img.getRGB(xx, yy);
                            viz = (c >>> 24) != 0 && c != Pixelizador.COR_SOMBRA;
                        }
                    if (!viz && n++ == 0) {
                        px = x;
                        py = y;
                    }
                }
            return n == 0 ? null : String.format("%d pixel(s) isolado(s), o primeiro em (%d, %d) (VT-15)", n, px, py);
        } catch (IOException ex) {
            return ex.getMessage();
        }
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
