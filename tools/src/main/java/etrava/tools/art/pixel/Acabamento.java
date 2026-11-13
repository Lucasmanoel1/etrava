package etrava.tools.art.pixel;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/**
 * Acabamento no Pixelorama (D-100, D-101). A folha pixelizada vira um projeto do Pixelorama em
 * camadas, guardado em {@code tools/art/acabamento/<pasta>/<folha>.pxo}:
 * <ul>
 *   <li>{@code sombra} — a sombra projetada no chão;</li>
 *   <li>{@code tronco} (árvores) ou {@code corpo} (o resto) — tudo o que não é folha;</li>
 *   <li>{@code folhagem} — os tufos pintados com os pincéis (só árvores);</li>
 *   <li>{@code contorno} — o contorno seletivo;</li>
 *   <li>{@code retoque} — a mão do artista. É a única camada que o pipeline NÃO refaz: quem
 *       abrir o .pxo no Pixelorama e pintar nela tem o retoque preservado em toda nova geração
 *       (enquanto o tamanho e o número de quadros não mudarem).</li>
 * </ul>
 * Cada quadro da folha é um quadro do projeto, direção por direção; cada animação de cada
 * direção ganha uma tag ({@code andar_d3}), para tocar no próprio Pixelorama.
 * A folha final é exportada pelo Pixelorama (linha de comando, sem janela). Sem o Pixelorama
 * instalado, compõe as camadas aqui mesmo (mesmo resultado) e avisa.
 */
final class Acabamento {
    private Acabamento() {}

    static BufferedImage pelaPixelorama(RenderArt ra, String sub, String nome, BufferedImage folha, int[] matFolha,
            Map<Integer, Pixelizador.Material> mats, int fw, int fh, int colunas, int direcoes,
            Map<String, int[]> inicio, double fps) throws IOException, InterruptedException {
        int n = fw * fh, total = colunas * direcoes, W = folha.getWidth();
        boolean temFolha = mats.values().stream().anyMatch(m -> m.pincel() != null);
        List<int[]> sombra = new ArrayList<>(), corpo = new ArrayList<>(), folhagem = new ArrayList<>(),
                contorno = new ArrayList<>(), retoque = new ArrayList<>();
        for (int d = 0; d < direcoes; d++) {
            for (int f = 0; f < colunas; f++) {
                int[] s = new int[n], t = new int[n], fo = new int[n], c = new int[n];
                for (int y = 0; y < fh; y++) {
                    for (int x = 0; x < fw; x++) {
                        int sx = f * fw + x, sy = d * fh + y;
                        int argb = folha.getRGB(sx, sy);
                        if ((argb >>> 24) == 0) continue;
                        int m = matFolha[sy * W + sx], o = y * fw + x;
                        if (m == 0) {
                            if (argb == Pixelizador.COR_SOMBRA) s[o] = argb;
                            else c[o] = argb;
                        } else if (mats.get(m) != null && mats.get(m).pincel() != null) fo[o] = argb;
                        else t[o] = argb;
                    }
                }
                sombra.add(s);
                corpo.add(t);
                folhagem.add(fo);
                contorno.add(c);
                retoque.add(new int[n]);
            }
        }
        Path pxo = ra.raiz.resolve("tools/art/acabamento/" + sub + "/" + nome + ".pxo");
        int retocados = 0;
        if (Files.exists(pxo)) {
            Pxo.Projeto velho = Pxo.ler(pxo);
            Pxo.Camada r = velho.camada("retoque");
            if (r != null && velho.largura() == fw && velho.altura() == fh && velho.quadros() == total) {
                retoque = r.quadros();
                for (int[] q : retoque) for (int v : q) if ((v >>> 24) != 0) retocados++;
            } else if (r != null && temPixel(r)) {
                System.out.println("  AVISO " + nome + ": o retoque do .pxo não cabe mais (tamanho ou quadros mudaram); guardado em .retoque-antigo.pxo");
                Files.copy(pxo, pxo.resolveSibling(nome + ".retoque-antigo.pxo"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        List<Pxo.Camada> camadas = new ArrayList<>();
        if (temPixel(sombra)) camadas.add(new Pxo.Camada("sombra", true, sombra));
        camadas.add(new Pxo.Camada(temFolha ? "tronco" : "corpo", true, corpo));
        if (temFolha) camadas.add(new Pxo.Camada("folhagem", true, folhagem));
        camadas.add(new Pxo.Camada("contorno", true, contorno));
        camadas.add(new Pxo.Camada("retoque", true, retoque));
        Pxo.Projeto proj = new Pxo.Projeto(fw, fh, fps, camadas);
        List<Pxo.Tag> tags = new ArrayList<>();
        for (int d = 0; d < direcoes; d++) {
            for (Map.Entry<String, int[]> e : inicio.entrySet()) {
                int de = d * colunas + e.getValue()[0] + 1;
                tags.add(new Pxo.Tag(direcoes == 1 ? e.getKey() : e.getKey() + "_d" + d, de, de + e.getValue()[1] - 1));
            }
        }
        Pxo.escrever(pxo, proj, tags);

        // composição de referência (a mesma regra de alfa do Pixelorama), já na grade da folha
        BufferedImage ref = new BufferedImage(fw * colunas, fh * direcoes, BufferedImage.TYPE_INT_ARGB);
        for (int k = 0; k < total; k++) ref.setRGB((k % colunas) * fw, (k / colunas) * fh, fw, fh, proj.composto(k), 0, fw);

        Path exe = pixelorama(ra.raiz);
        if (exe == null) {
            System.out.println("  AVISO " + nome + ": Pixelorama não encontrado (PIXELORAMA ou pixelorama.path em local.properties); camadas compostas no Java");
            return ref;
        }
        Path saida = ra.raiz.resolve("build/acabamento/" + nome + ".png");
        Files.createDirectories(saida.getParent());
        Files.deleteIfExists(saida);
        Process pr = new ProcessBuilder(exe.toString(), "--headless", "--quit", "--", pxo.toAbsolutePath().toString(),
                "--export", "--spritesheet", "--output", saida.toAbsolutePath().toString())
                .redirectErrorStream(true).redirectOutput(ra.raiz.resolve("build/acabamento/" + nome + ".log").toFile()).start();
        if (!pr.waitFor(180, TimeUnit.SECONDS) || !Files.exists(saida)) {
            pr.destroyForcibly();
            throw new IllegalStateException(nome + ": o Pixelorama não exportou (veja build/acabamento/" + nome + ".log)");
        }
        // o Pixelorama arruma os quadros em grade (linha por linha); remonta na grade da folha
        BufferedImage px = ImageIO.read(saida.toFile());
        int porLinha = Math.max(1, px.getWidth() / fw);
        BufferedImage folhaFinal = new BufferedImage(fw * colunas, fh * direcoes, BufferedImage.TYPE_INT_ARGB);
        for (int k = 0; k < total; k++) {
            int sx = (k % porLinha) * fw, sy = (k / porLinha) * fh;
            if (sx + fw > px.getWidth() || sy + fh > px.getHeight())
                throw new IllegalStateException(nome + ": folha do Pixelorama menor que o esperado (" + px.getWidth() + "×" + px.getHeight() + ")");
            folhaFinal.setRGB((k % colunas) * fw, (k / colunas) * fh, fw, fh, px.getRGB(sx, sy, fw, fh, null, 0, fw), 0, fw);
        }
        int dif = 0;
        for (int y = 0; y < ref.getHeight(); y++)
            for (int x = 0; x < ref.getWidth(); x++)
                if (folhaFinal.getRGB(x, y) != ref.getRGB(x, y)) dif++;
        System.out.printf("  %s: Pixelorama → %s (%d quadros, %d px de retoque%s)%n", nome,
                ra.raiz.relativize(pxo).toString().replace('\\', '/'), total, retocados,
                dif == 0 ? "" : "; " + dif + " px diferem da composição de referência");
        return folhaFinal;
    }

    private static boolean temPixel(Pxo.Camada c) {
        return temPixel(c.quadros());
    }

    private static boolean temPixel(List<int[]> qs) {
        for (int[] q : qs) for (int v : q) if ((v >>> 24) != 0) return true;
        return false;
    }

    static Path pixelorama(Path raiz) throws IOException {
        String env = System.getenv("PIXELORAMA");
        if (env != null && !env.isBlank()) return Path.of(env);
        Path lp = raiz.resolve("local.properties");
        if (Files.exists(lp)) {
            Properties pr = new Properties();
            try (var in = Files.newBufferedReader(lp)) {
                pr.load(in);
            }
            if (pr.getProperty("pixelorama.path") != null) return Path.of(pr.getProperty("pixelorama.path"));
        }
        Path padrao = Path.of(System.getProperty("user.home"), "AppData/Local/Programs/Pixelorama/Pixelorama-Windows-64bit/Pixelorama.exe");
        return Files.exists(padrao) ? padrao : null;
    }
}
