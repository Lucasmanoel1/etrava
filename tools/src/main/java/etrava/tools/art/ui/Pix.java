package etrava.tools.art.ui;

import java.awt.image.BufferedImage;

/** Imagem de pixel art do tema: cada pixel é transparente (0) ou opaco, sempre numa cor da paleta v3. */
final class Pix {
    interface Fn { int cor(int x, int y); }

    final int w, h;
    final int[] p;

    Pix(int w, int h) {
        this.w = w;
        this.h = h;
        this.p = new int[w * h];
    }

    /** Pinta pixel a pixel pela função (0 = transparente). */
    static Pix de(int w, int h, Fn f) {
        Pix r = new Pix(w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) r.p[y * w + x] = f.cor(x, y);
        return r;
    }

    boolean dentro(int x, int y) { return x >= 0 && y >= 0 && x < w && y < h; }

    void set(int x, int y, int c) { if (dentro(x, y)) p[y * w + x] = c; }

    int get(int x, int y) { return dentro(x, y) ? p[y * w + x] : 0; }

    /** Retângulo preenchido, extremos incluídos. */
    void rect(int x0, int y0, int x1, int y1, int c) {
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) set(x, y, c);
    }

    /** Troca linhas por colunas. */
    Pix transpor() {
        Pix o = new Pix(h, w);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) o.p[x * h + y] = p[y * w + x];
        return o;
    }

    BufferedImage imagem() {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, p, 0, w);
        return img;
    }

    /** Estica como um 9-patch (vizinho mais próximo) para w×h. */
    Pix esticar(int[] split, int nw, int nh) {
        int l = split[0], r = split[1], t = split[2], b = split[3];
        Pix o = new Pix(nw, nh);
        for (int y = 0; y < nh; y++) {
            int sy = y < t ? y : y >= nh - b ? h - (nh - y) : t + (int) ((long) (y - t) * (h - t - b) / Math.max(1, nh - t - b));
            for (int x = 0; x < nw; x++) {
                int sx = x < l ? x : x >= nw - r ? w - (nw - x) : l + (int) ((long) (x - l) * (w - l - r) / Math.max(1, nw - l - r));
                o.p[y * nw + x] = get(sx, sy);
            }
        }
        return o;
    }
}
