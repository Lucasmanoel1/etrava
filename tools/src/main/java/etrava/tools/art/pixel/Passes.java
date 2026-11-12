package etrava.tools.art.pixel;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Os dois passes de um quadro renderizado pelo Blender (SPEC-visual §5.1), já superamostrados,
 * em valores 0..1. Passe "a": índice do material, profundidade, emissivo, cobertura.
 * Passe "b": luz direta com sombra, oclusão ambiente, detalhe da textura.
 */
public final class Passes {
    public final int largura, altura;
    final int[] indice;
    final float[] profundidade, luz, oa, detalhe, cobertura;

    public Passes(int largura, int altura) {
        this.largura = largura;
        this.altura = altura;
        int n = largura * altura;
        indice = new int[n];
        profundidade = new float[n];
        luz = new float[n];
        oa = new float[n];
        detalhe = new float[n];
        cobertura = new float[n];
    }

    public static Passes ler(Path a, Path b) {
        try {
            BufferedImage ia = ImageIO.read(a.toFile()), ib = ImageIO.read(b.toFile());
            if (ia == null || ib == null) throw new IOException("PNG ilegível: " + a + " / " + b);
            if (ia.getWidth() != ib.getWidth() || ia.getHeight() != ib.getHeight())
                throw new IOException("passes de tamanhos diferentes: " + a);
            Passes p = new Passes(ia.getWidth(), ia.getHeight());
            Raster ra = ia.getRaster(), rb = ib.getRaster();
            double maxA = (1 << ia.getColorModel().getComponentSize(0)) - 1;
            double maxB = (1 << ib.getColorModel().getComponentSize(0)) - 1;
            boolean alfaA = ra.getNumBands() > 3;
            for (int y = 0; y < p.altura; y++) {
                for (int x = 0; x < p.largura; x++) {
                    int i = y * p.largura + x;
                    float cob = alfaA ? (float) (ra.getSample(x, y, 3) / maxA) : 1f;
                    p.cobertura[i] = cob;
                    if (cob < 0.5f) continue;
                    p.indice[i] = (int) Math.round(ra.getSample(x, y, 0) / maxA * 255.0);
                    p.profundidade[i] = (float) (ra.getSample(x, y, 1) / maxA);
                    p.luz[i] = (float) (rb.getSample(x, y, 0) / maxB);
                    p.oa[i] = (float) (rb.getSample(x, y, 1) / maxB);
                    p.detalhe[i] = (float) (rb.getSample(x, y, 2) / maxB);
                }
            }
            return p;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Para testes: define um subpixel. */
    public void set(int x, int y, int indice, double profundidade, double luz, double oa, double detalhe) {
        int i = y * largura + x;
        this.indice[i] = indice;
        this.profundidade[i] = (float) profundidade;
        this.luz[i] = (float) luz;
        this.oa[i] = (float) oa;
        this.detalhe[i] = (float) detalhe;
        this.cobertura[i] = indice == 0 ? 0f : 1f;
    }
}
