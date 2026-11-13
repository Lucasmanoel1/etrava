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
 * Acabamento no Pixelorama (D-100). A folha pixelizada vira um projeto do Pixelorama em camadas,
 * guardado em {@code tools/art/acabamento/<pasta>/<folha>.pxo}:
 * <ul>
 *   <li>{@code sombra} — a sombra projetada no chão;</li>
 *   <li>{@code tronco} — tronco, galhos, raízes, frutos (o que não é folha);</li>
 *   <li>{@code folhagem} — os tufos pintados com os pincéis;</li>
 *   <li>{@code contorno} — o contorno seletivo;</li>
 *   <li>{@code retoque} — a mão do artista. É a única camada que o pipeline NÃO refaz: quem
 *       abrir o .pxo no Pixelorama e pintar nela tem o retoque preservado em toda nova geração
 *       (enquanto o tamanho e o número de quadros não mudarem).</li>
 * </ul>
 * A folha final é exportada pelo próprio Pixelorama (linha de comando, sem janela). Sem o
 * Pixelorama instalado, compõe as camadas aqui mesmo (mesmo resultado) e avisa.
 */
final class Acabamento {
    private Acabamento() {}

    static final String[] CAMADAS = {"sombra", "tronco", "folhagem", "contorno", "retoque"};

    static BufferedImage pelaPixelorama(RenderArt ra, String sub, String nome, List<Montagem.Quadro> qs,
            Map<Integer, Pixelizador.Material> mats, int x0, int y0, int fw, int fh, int colunas, double fps)
            throws IOException, InterruptedException {
        int n = fw * fh;
        List<int[]> sombra = new ArrayList<>(), tronco = new ArrayList<>(), folha = new ArrayList<>(),
                contorno = new ArrayList<>(), retoque = new ArrayList<>();
        // quadros na ordem das colunas da folha (uma direção só)
        Montagem.Quadro[] porColuna = new Montagem.Quadro[colunas];
        for (Montagem.Quadro q : qs) {
            if (q.dir() != 0) throw new IllegalStateException(nome + ": acabamento no Pixelorama só para folhas de uma direção");
            porColuna[q.f()] = q;
        }
        for (Montagem.Quadro q : porColuna) {
            int[] s = new int[n], t = new int[n], f = new int[n], c = new int[n];
            BufferedImage img = q.r().cor();
            int[] mp = q.r().materialDoPixel();
            for (int y = 0; y < fh; y++) {
                for (int x = 0; x < fw; x++) {
                    int sx = x0 + x, sy = y0 + y;
                    if (sx >= img.getWidth() || sy >= img.getHeight()) continue;
                    int argb = img.getRGB(sx, sy);
                    if ((argb >>> 24) == 0) continue;
                    int m = mp[sy * img.getWidth() + sx], o = y * fw + x;
                    if (m == 0) {
                        if (argb == Pixelizador.COR_SOMBRA) s[o] = argb;
                        else c[o] = argb;
                    } else if (mats.get(m) != null && mats.get(m).pincel() != null) f[o] = argb;
                    else t[o] = argb;
                }
            }
            sombra.add(s);
            tronco.add(t);
            folha.add(f);
            contorno.add(c);
            retoque.add(new int[n]);
        }
        Path pxo = ra.raiz.resolve("tools/art/acabamento/" + sub + "/" + nome + ".pxo");
        int retocados = 0;
        if (Files.exists(pxo)) {
            Pxo.Projeto velho = Pxo.ler(pxo);
            Pxo.Camada r = velho.camada("retoque");
            if (r != null && velho.largura() == fw && velho.altura() == fh && velho.quadros() == colunas) {
                retoque = r.quadros();
                for (int[] q : retoque) for (int v : q) if ((v >>> 24) != 0) retocados++;
            } else if (r != null && temPixel(r)) {
                System.out.println("  AVISO " + nome + ": o retoque do .pxo não cabe mais (tamanho ou quadros mudaram); guardado em .retoque-antigo.pxo");
                Files.copy(pxo, pxo.resolveSibling(nome + ".retoque-antigo.pxo"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Pxo.Projeto proj = new Pxo.Projeto(fw, fh, fps, List.of(
                new Pxo.Camada("sombra", true, sombra), new Pxo.Camada("tronco", true, tronco),
                new Pxo.Camada("folhagem", true, folha), new Pxo.Camada("contorno", true, contorno),
                new Pxo.Camada("retoque", true, retoque)));
        Pxo.escrever(pxo, proj, "vento");

        // composição de referência (a mesma regra de alfa do Pixelorama)
        BufferedImage ref = new BufferedImage(fw * colunas, fh, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < colunas; f++) ref.setRGB(f * fw, 0, fw, fh, proj.composto(f), 0, fw);

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
        if (!pr.waitFor(120, TimeUnit.SECONDS) || !Files.exists(saida)) {
            pr.destroyForcibly();
            throw new IllegalStateException(nome + ": o Pixelorama não exportou (veja build/acabamento/" + nome + ".log)");
        }
        BufferedImage px = ImageIO.read(saida.toFile());
        BufferedImage folhaFinal = new BufferedImage(fw * colunas, fh, BufferedImage.TYPE_INT_ARGB);
        if (px.getWidth() == fw * colunas && px.getHeight() == fh) {
            folhaFinal.getGraphics().drawImage(px, 0, 0, null);
        } else {
            // o Pixelorama arruma a folha em grade quando há muitos quadros: lê quadro a quadro
            int porLinha = px.getWidth() / fw;
            for (int f = 0; f < colunas; f++) {
                int sx = (f % porLinha) * fw, sy = (f / porLinha) * fh;
                folhaFinal.setRGB(f * fw, 0, fw, fh, px.getRGB(sx, sy, fw, fh, null, 0, fw), 0, fw);
            }
        }
        int dif = 0;
        for (int y = 0; y < fh; y++)
            for (int x = 0; x < fw * colunas; x++)
                if (folhaFinal.getRGB(x, y) != ref.getRGB(x, y)) dif++;
        System.out.printf("  %s: Pixelorama → %s (%d px de retoque%s)%n", nome, ra.raiz.relativize(pxo).toString().replace('\\', '/'),
                retocados, dif == 0 ? "" : "; " + dif + " px diferem da composição de referência");
        return folhaFinal;
    }

    private static boolean temPixel(Pxo.Camada c) {
        for (int[] q : c.quadros()) for (int v : q) if ((v >>> 24) != 0) return true;
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
