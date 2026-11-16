package etrava.tools.art.ui;

/**
 * Silhueta desenhada por máscara (cursores e ícones): o contorno escuro e o sombreado do lado
 * de baixo/direita saem sozinhos, então toda forma tem o mesmo acabamento.
 */
final class Forma {
    final int w, h;
    final boolean[] m;
    final int[] cor;   // 0 = cor padrão da forma
    int fill, shade, outline = K.Q;

    Forma(int w, int h, int fill, int shade) {
        this.w = w;
        this.h = h;
        this.m = new boolean[w * h];
        this.cor = new int[w * h];
        this.fill = fill;
        this.shade = shade;
    }

    boolean tem(int x, int y) { return x >= 0 && y >= 0 && x < w && y < h && m[y * w + x]; }

    void px(int x, int y) { if (x >= 0 && y >= 0 && x < w && y < h) m[y * w + x] = true; }

    void px(int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < w && y < h) {
            m[y * w + x] = true;
            cor[y * w + x] = c;
        }
    }

    /** Só muda a cor de um pixel que já existe. */
    void pinta(int x, int y, int c) { if (tem(x, y)) cor[y * w + x] = c; }

    void rect(int x0, int y0, int x1, int y1) { for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) px(x, y); }

    void rect(int x0, int y0, int x1, int y1, int c) { for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) px(x, y, c); }

    void tira(int x0, int y0, int x1, int y1) { for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) if (x >= 0 && y >= 0 && x < w && y < h) { m[y * w + x] = false; cor[y * w + x] = 0; } }

    void elipse(double cx, double cy, double rx, double ry) {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double dx = (x - cx) / rx, dy = (y - cy) / ry;
                if (dx * dx + dy * dy <= 1) m[y * w + x] = true;
            }
    }

    /** Desenho em texto: 'o' = preenchido, outro = vazio; origem em (ox, oy). */
    void ascii(int ox, int oy, String... linhas) {
        for (int y = 0; y < linhas.length; y++)
            for (int x = 0; x < linhas[y].length(); x++) if (linhas[y].charAt(x) == 'o') px(ox + x, oy + y);
    }

    Pix renderizar() {
        Pix r = new Pix(w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (tem(x, y)) {
                    int c = cor[y * w + x];
                    if (c == 0) c = (!tem(x + 1, y) || !tem(x, y + 1)) ? shade : fill;
                    r.p[y * w + x] = c;
                } else {
                    boolean borda = false;
                    for (int dy = -1; dy <= 1 && !borda; dy++)
                        for (int dx = -1; dx <= 1; dx++) if ((dx != 0 || dy != 0) && tem(x + dx, y + dy)) { borda = true; break; }
                    if (borda) r.p[y * w + x] = outline;
                }
            }
        return r;
    }
}
