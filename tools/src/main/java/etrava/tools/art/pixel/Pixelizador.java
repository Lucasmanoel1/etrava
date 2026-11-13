package etrava.tools.art.pixel;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * O pixelizador do ETRAVA (SPEC-visual §5.2): transforma os passes de um render em pixel art.
 * Quem decide cada pixel é ele, não o Blender — é o que separa sprite desenhado de render reduzido.
 *
 * <ol>
 *   <li>redução por voto: cada pixel final fica com o material mais presente nos subpixels
 *       (nada de média de cores);</li>
 *   <li>valor: luz direta, oclusão e detalhe da textura;</li>
 *   <li>rampa: o valor escolhe um tom da rampa do material, em faixas fixas (pontilhado
 *       ordenado só em material marcado);</li>
 *   <li>linhas internas onde a profundidade salta (braço na frente do tronco);</li>
 *   <li>limpeza de pixel solto;</li>
 *   <li>contorno seletivo por fora da silhueta, no tom escuro do material vizinho;</li>
 *   <li>decalques (olhos, botões) em posição exata;</li>
 *   <li>brilho: os materiais emissivos vão também para a imagem que acende à noite.</li>
 * </ol>
 * Determinístico: a mesma entrada dá os mesmos bytes.
 */
public final class Pixelizador {
    /** Índice reservado ao chão que só recebe sombra; 254 marca o subpixel em sombra. */
    public static final int INDICE_SOMBRA = 255;
    private static final int SOMBRA = 254;
    /** Sombra projetada no chão: preto translúcido, uma cor só (como no M8). */
    public static final int COR_SOMBRA = 0x5A000000;
    private static final int[] BAYER4 = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    /**
     * @param rampa     nome da rampa da paleta
     * @param k         peso do detalhe (textura) no valor
     * @param tons      [de, até] dentro da rampa, ou null para a rampa inteira
     */
    public record Material(int indice, String nome, String rampa, double k, boolean pontilhar, boolean emissivo,
            boolean contorno, double luzMin, double oa, int[] tons, String rampaBrilho, boolean linhas,
            String pincel, String grupo) {
        public Material(int indice, String nome, String rampa, double k, boolean pontilhar, boolean emissivo,
                boolean contorno, double luzMin, double oa, int[] tons, String rampaBrilho, boolean linhas) {
            this(indice, nome, rampa, k, pontilhar, emissivo, contorno, luzMin, oa, tons, rampaBrilho, linhas, null, null);
        }
    }

    /**
     * @param ss            superamostragem do render (4 = cada pixel final vem de 4×4 subpixels)
     * @param ambiente      luz que chega ao lado da sombra (céu)
     * @param difusa        peso da luz direta (ambiente + difusa = valor de uma face de frente para o sol)
     * @param limiarProf    salto de profundidade, em unidades do passe, que vira linha interna
     * @param limparSoltos  troca pixel isolado pela cor dos vizinhos
     * @param contornoBase  cor do contorno na base e no lado da sombra (0 = tom escuro do material)
     */
    public record Parametros(int ss, double ambiente, double difusa, double limiarProf, boolean limparSoltos,
            int contornoBase) {
        public static Parametros padrao() {
            return new Parametros(4, 0.36, 0.56, 0.25 / 40.0, true, 0xFF14101A);
        }
    }

    /** Um pixel carimbado depois de tudo (olho, boca, botão). */
    public record Decalque(int x, int y, int argb) {}

    /** Imagem final, imagem de brilho (null se nada brilha) e o índice do material de cada pixel. */
    public record Resultado(BufferedImage cor, BufferedImage brilho, int[] materialDoPixel) {}

    private final Paleta paleta;
    private final Map<String, Pinceis.Conjunto> pinceis;

    public Pixelizador(Paleta paleta) {
        this(paleta, Map.of());
    }

    public Pixelizador(Paleta paleta, Map<String, Pinceis.Conjunto> pinceis) {
        this.paleta = paleta;
        this.pinceis = pinceis;
    }

    public Resultado pixelizar(Passes p, Map<Integer, Material> mats, Parametros par, List<Decalque> decalques) {
        return pixelizar(p, mats, par, decalques, Map.of());
    }

    /**
     * @param ancoras posição na tela (px de arte) de cada material pintado com pincel, por índice:
     *                a grade de cachos fica presa a ela (sem âncora, a grade fica presa ao quadro)
     */
    public Resultado pixelizar(Passes p, Map<Integer, Material> mats, Parametros par, List<Decalque> decalques,
            Map<Integer, int[]> ancoras) {
        int ss = par.ss();
        int w = p.largura / ss, h = p.altura / ss, n = w * h;
        int[] id = new int[n];
        float[] luz = new float[n], oa = new float[n], det = new float[n], prof = new float[n];
        int[] conta = new int[256];
        double[] somaProf = new double[256];
        // índices válidos: os materiais do render e o chão de sombra. Um subpixel cuja amostra caiu
        // exatamente numa aresta mistura dois índices e vira lixo; ele conta como vazio no voto.
        boolean[] valido = new boolean[256];
        for (int k : mats.keySet()) if (k > 0 && k < 256) valido[k] = true;
        valido[INDICE_SOMBRA] = true;

        // 1. redução por voto
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                java.util.Arrays.fill(conta, 0);
                java.util.Arrays.fill(somaProf, 0);
                for (int sy = 0; sy < ss; sy++) {
                    for (int sx = 0; sx < ss; sx++) {
                        int i = (y * ss + sy) * p.largura + x * ss + sx;
                        int k = subId(p, i, valido);
                        conta[k]++;
                        somaProf[k] += p.profundidade[i];
                    }
                }
                int total = ss * ss, vencedor = 0;
                if (conta[0] * 2 < total) {
                    int melhor = -1;
                    for (int k = 1; k < 256; k++) {
                        if (conta[k] == 0) continue;
                        // empate: fica o mais perto da câmera
                        if (conta[k] > melhor || (conta[k] == melhor && somaProf[k] / conta[k] < somaProf[vencedor] / conta[vencedor])) {
                            melhor = conta[k];
                            vencedor = k;
                        }
                    }
                }
                int o = y * w + x;
                id[o] = vencedor;
                if (vencedor == 0 || vencedor == SOMBRA) continue;
                double l = 0, a = 0, d = 0, z = 0;
                int c = 0;
                for (int sy = 0; sy < ss; sy++) {
                    for (int sx = 0; sx < ss; sx++) {
                        int i = (y * ss + sy) * p.largura + x * ss + sx;
                        if (subId(p, i, valido) != vencedor) continue;
                        l += p.luz[i];
                        a += p.oa[i];
                        d += p.detalhe[i];
                        z += p.profundidade[i];
                        c++;
                    }
                }
                luz[o] = (float) (l / c);
                oa[o] = (float) (a / c);
                det[o] = (float) (d / c);
                prof[o] = (float) (z / c);
            }
        }

        // 2 e 3. valor → tom da rampa
        int[] cor = new int[n], tom = new int[n];
        double[] valores = new double[n];
        int[] brilho = null;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int o = y * w + x;
                if (id[o] == 0) continue;
                if (id[o] == SOMBRA) {
                    cor[o] = COR_SOMBRA;
                    continue;
                }
                Material m = material(mats, id[o]);
                int[] r = tons(m);
                double v = valor(m, par, luz[o], oa[o], det[o]);
                valores[o] = v;
                int t = quantizar(v, r.length, m.pontilhar(), x, y);
                tom[o] = t;
                cor[o] = r[t];
                if (m.emissivo()) {
                    if (brilho == null) brilho = new int[n];
                    int[] rb = paleta.rampa(m.rampaBrilho() != null ? m.rampaBrilho() : "fogo");
                    brilho[o] = rb[Math.min(rb.length - 1, quantizar(0.35 + 0.65 * v, rb.length, false, x, y))];
                }
            }
        }

        // 3b. folhagem: os materiais com pincel são redesenhados em cachos (D-100)
        Map<Integer, Folhagem.Tufo> tufos = new java.util.TreeMap<>();
        for (Material m : mats.values()) {
            if (m.pincel() == null) continue;
            Pinceis.Conjunto cj = pinceis.get(m.pincel());
            if (cj == null) throw new IllegalStateException("pincel desconhecido: " + m.pincel() + " (tools/art/pinceis/folhas.pxo)");
            int[] a = ancoras.getOrDefault(m.indice(), new int[] {0, 0});
            tufos.put(m.indice(), new Folhagem.Tufo(tons(m), cj, a[0], a[1], m.indice() * 7919 + 17));
        }
        Folhagem.pintar(w, h, id, prof, valores, cor, tufos, 0, SOMBRA);

        // 4. linhas internas: o pixel mais longe, onde a profundidade salta, escurece
        int[] linhas = cor.clone();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int o = y * w + x;
                if (!solido(id[o])) continue;
                Material m = material(mats, id[o]);
                if (!m.linhas()) continue;
                boolean salta = false;
                for (int[] dd : VIZ4) {
                    int nx = x + dd[0], ny = y + dd[1];
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int q = ny * w + nx;
                    if (solido(id[q]) && prof[o] - prof[q] > par.limiarProf()) salta = true;
                }
                if (salta) linhas[o] = tons(m)[0];
            }
        }
        cor = linhas;

        // 5. limpeza: pixel cercado pelos quatro lados por outra cor do mesmo material
        if (par.limparSoltos()) {
            int[] limpo = cor.clone();
            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    int o = y * w + x;
                    if (!solido(id[o])) continue;
                    int c0 = cor[o - 1];
                    if (c0 == cor[o] || cor[o + 1] != c0 || cor[o - w] != c0 || cor[o + w] != c0) continue;
                    if (id[o - 1] != id[o] || id[o + 1] != id[o] || id[o - w] != id[o] || id[o + w] != id[o]) continue;
                    limpo[o] = c0;
                }
            }
            cor = limpo;
        }

        // 6. contorno por fora: o pixel vazio encostado na silhueta pega o tom escuro do vizinho;
        // na base e no lado da sombra (à direita), o contorno de base
        int[] contorno = cor.clone();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int o = y * w + x;
                if (solido(id[o])) continue;
                int escolhido = -1;
                boolean base = false;
                // ordem: vizinho de cima (este pixel está na base), da esquerda (lado da sombra), de baixo, da direita
                int[][] ordem = {{0, -1}, {-1, 0}, {0, 1}, {1, 0}};
                for (int k = 0; k < 4 && escolhido < 0; k++) {
                    int nx = x + ordem[k][0], ny = y + ordem[k][1];
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int q = ny * w + nx;
                    if (!solido(id[q]) || !material(mats, id[q]).contorno()) continue;
                    escolhido = q;
                    base = k <= 1;
                }
                if (escolhido < 0) continue;
                int escuro = tons(material(mats, id[escolhido]))[0];
                contorno[o] = base && par.contornoBase() != 0 ? maisEscura(escuro, par.contornoBase()) : escuro;
            }
        }
        cor = contorno;

        // 7. decalques
        if (decalques != null) {
            for (Decalque d : decalques) {
                if (d.x() < 0 || d.y() < 0 || d.x() >= w || d.y() >= h) continue;
                int o = d.y() * w + d.x();
                if (solido(id[o])) cor[o] = d.argb();
            }
        }

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, cor, 0, w);
        BufferedImage glow = null;
        if (brilho != null) {
            glow = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            glow.setRGB(0, 0, w, h, brilho, 0, w);
        }
        int[] porPixel = new int[n];
        for (int i = 0; i < n; i++) porPixel[i] = solido(id[i]) ? id[i] : 0;
        return new Resultado(img, glow, porPixel);
    }

    private static final int[][] VIZ4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static int subId(Passes p, int i, boolean[] valido) {
        if (p.cobertura[i] < 0.5f) return 0;
        int k = p.indice[i];
        if (k < 0 || k > 255 || !valido[k]) return 0;
        if (k == INDICE_SOMBRA) return p.luz[i] > 0.5f ? 0 : SOMBRA;
        return k == SOMBRA ? 0 : k;
    }

    private static boolean solido(int id) {
        return id != 0 && id != SOMBRA;
    }

    private Material material(Map<Integer, Material> mats, int id) {
        Material m = mats.get(id);
        if (m == null) throw new IllegalStateException("material sem definição: índice " + id);
        return m;
    }

    private int[] tons(Material m) {
        int[] r = paleta.rampa(m.rampa());
        if (m.tons() == null) return r;
        return java.util.Arrays.copyOfRange(r, Math.max(0, m.tons()[0]), Math.min(r.length, m.tons()[1] + 1));
    }

    /** Valor do pixel, 0 (sombra funda) a 1 (luz plena). */
    static double valor(Material m, Parametros par, double luz, double oa, double detalhe) {
        double v = par.ambiente() + par.difusa() * luz;
        v *= 1 - m.oa() * (1 - oa);
        v += m.k() * (detalhe - 0.5);
        return Math.max(m.luzMin(), Math.min(1, v));
    }

    /**
     * Tom da rampa para o valor v. O tom 0 (o mais escuro) fica para contorno, dobras e linhas;
     * o valor se distribui pelos outros em faixas iguais.
     */
    static int quantizar(double v, int n, boolean pontilhar, int x, int y) {
        if (n <= 1) return 0;
        int usaveis = n >= 4 ? n - 1 : n, base = n - usaveis;
        double q = Math.max(0, Math.min(0.9999, v)) * usaveis;
        int t = (int) Math.floor(q);
        if (pontilhar) {
            double frac = q - t, limiar = (BAYER4[(y & 3) * 4 + (x & 3)] + 0.5) / 16.0;
            // pontilha só perto da fronteira entre duas faixas
            if (frac > 0.70 && (frac - 0.70) / 0.30 > limiar && t + 1 < usaveis) t++;
        }
        return base + Math.min(usaveis - 1, t);
    }

    private static int maisEscura(int a, int b) {
        return Paleta.luminancia(a) <= Paleta.luminancia(b) ? a : b;
    }
}
