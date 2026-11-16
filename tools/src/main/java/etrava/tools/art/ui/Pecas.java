package etrava.tools.art.ui;

import static etrava.tools.art.ui.K.*;

import java.util.ArrayList;
import java.util.List;

/**
 * As peças do tema (V7, SPEC-visual §8.3), desenhadas pixel a pixel: madeira escura, ferro, pergaminho
 * e couro, sempre na paleta v3. Nos 9-patches, o miolo e as faixas das bordas só variam no sentido
 * perpendicular à borda — por isso esticam sem emenda. Tudo em 1×.
 */
final class Pecas {
    private Pecas() {}

    /** Uma peça: imagem, split e recuo do 9-patch (null = imagem simples) e ponto ativo (cursores). */
    record Peca(String id, Pix img, int[] split, int[] pad, int[] ponto) {}

    static List<Peca> todas() {
        List<Peca> l = new ArrayList<>();
        // painéis
        l.add(nove("painel_madeira", madeira(48, 48, 10, true, 15), 16, 18));
        l.add(nove("pergaminho", pergaminho(40, 40), 9, 10));
        l.add(nove("pergaminho_escuro", pergaminhoEscuro(24, 24), 4, 5));
        l.add(nove("couro", couro(32, 32), 8, 8));
        l.add(nove("bilhete", bilhete(40, 28), 7, 8));
        l.add(new Peca("ui.balao", balao(48, 32), new int[] {20, 8, 8, 12}, new int[] {11, 11, 9, 12}, null));
        l.add(new Peca("ui.balao_dir", espelhar(balao(48, 32)), new int[] {8, 20, 8, 12}, new int[] {11, 11, 9, 12}, null));
        l.add(nove("dica", dica(24, 20), 5, 6));
        l.add(new Peca("ui.livro_esq", livro(56, 56, true), new int[] {14, 8, 12, 12}, new int[] {17, 10, 14, 14}, null));
        l.add(new Peca("ui.livro_dir", livro(56, 56, false), new int[] {8, 14, 12, 12}, new int[] {10, 17, 14, 14}, null));
        l.add(nove("documento", documento(48, 56), 10, 14));
        l.add(simples("papel_trama", papelTrama(128, P2, P3, G8, 31)));
        l.add(simples("papel_trama_escuro", papelTrama(128, P1, G8, P0, 57)));
        // botões
        l.add(new Peca("ui.botao", botao(32, 24, 0), new int[] {6, 6, 6, 6}, new int[] {8, 8, 4, 5}, null));
        l.add(new Peca("ui.botao_sobre", botao(32, 24, 1), new int[] {6, 6, 6, 6}, new int[] {8, 8, 4, 5}, null));
        l.add(new Peca("ui.botao_press", botao(32, 24, 2), new int[] {6, 6, 6, 6}, new int[] {8, 8, 5, 4}, null));
        l.add(new Peca("ui.botao_desativado", botao(32, 24, 3), new int[] {6, 6, 6, 6}, new int[] {8, 8, 4, 5}, null));
        // abas-fita
        l.add(new Peca("ui.aba", aba(48, 22, false), new int[] {4, 10, 10, 10}, new int[] {6, 15, 3, 3}, null));
        l.add(new Peca("ui.aba_ativa", aba(50, 22, true), new int[] {4, 10, 10, 10}, new int[] {6, 15, 3, 3}, null));
        // rolagem
        l.add(new Peca("ui.rolagem_trilho", trilho(10, 20), new int[] {0, 0, 5, 5}, new int[] {0, 0, 0, 0}, null));
        l.add(new Peca("ui.rolagem_pegador", pegador(10, 24), new int[] {0, 0, 5, 5}, new int[] {0, 0, 0, 0}, null));
        // divisórias
        l.add(new Peca("ui.divisoria", divisoria(48, 5), new int[] {6, 6, 0, 0}, new int[] {0, 0, 0, 0}, null));
        l.add(new Peca("ui.divisoria_v", divisoria(48, 5).transpor(), new int[] {0, 0, 6, 6}, new int[] {0, 0, 0, 0}, null));
        // molduras
        l.add(nove("moldura_retrato", madeira(64, 64, 7, false, 9), 10, 8));
        l.add(nove("moldura_icone", molduraIcone(40, false), 5, 4));
        l.add(nove("moldura_icone_sel", molduraIcone(40, true), 5, 4));
        l.add(nove("encaixe", encaixe(40), 5, 4));
        l.add(nove("foco", foco(24), 3, 0));
        // barras
        l.add(nove("barra_fundo", barraFundo(32, 10), 4, 3));
        l.add(nove("barra_vida", barra(10, 6, new int[] {R3, R2, R2, R1, R1, R0}, R0), 2, 0));
        l.add(nove("barra_folego", barra(10, 6, new int[] {O5, O4, O4, O3, O3, O2}, O2), 2, 0));
        // opções
        l.add(new Peca("ui.caixa", caixa(14, false), null, null, null));
        l.add(new Peca("ui.caixa_marcada", caixa(14, true), null, null, null));
        l.add(new Peca("ui.deslizante_trilho", deslizTrilho(24, 6), new int[] {3, 3, 0, 0}, new int[] {0, 0, 0, 0}, null));
        l.add(new Peca("ui.deslizante_pino", pino(12, 14), null, null, null));
        // ícones de HUD
        l.add(simples("sol", sol(18)));
        l.add(simples("lua", lua(18)));
        l.add(simples("bolsa", bolsa(18)));
        l.add(simples("vida", vida(12)));
        l.add(simples("folego", folego(12)));
        // silhuetas dos encaixes de equipamento vazios (boneco da mochila)
        l.add(simples("encaixe_cabeca", silhueta(0)));
        l.add(simples("encaixe_corpo", silhueta(1)));
        l.add(simples("encaixe_mao", silhueta(2)));
        l.add(simples("encaixe_mao2", silhueta(3)));
        l.add(simples("encaixe_acessorio", silhueta(4)));
        // cursores
        l.add(cursor("apontar", cursorApontar(), 1, 1));
        l.add(cursor("falar", cursorFalar(), 16, 16));
        l.add(cursor("entrar", cursorEntrar(), 16, 16));
        l.add(cursor("pegar", cursorPegar(), 16, 16));
        l.add(cursor("atacar", cursorAtacar(), 16, 16));
        l.add(cursor("esperar", cursorEsperar(), 16, 16));
        return l;
    }

    private static Peca nove(String nome, Pix img, int split, int pad) {
        return new Peca("ui." + nome, img, new int[] {split, split, split, split}, new int[] {pad, pad, pad, pad}, null);
    }

    private static Peca simples(String nome, Pix img) {
        return new Peca("ui." + nome, img, null, null, null);
    }

    private static Peca cursor(String nome, Pix img, int px, int py) {
        return new Peca("ui.cursor_" + nome, img, null, null, new int[] {px, py});
    }

    /** A mesma imagem espelhada na horizontal (balão com a cauda à direita). */
    private static Pix espelhar(Pix o) {
        return Pix.de(o.w, o.h, (x, y) -> o.get(o.w - 1 - x, y));
    }

    private static int hash(int x, int y, int s) {
        int h = x * 374761393 + y * 668265263 + s * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return h & 0xFFFF;
    }

    // ------------------------------------------------------------------ madeira e ferro

    /** Veios da madeira por lado (0 cima, 1 esquerda, 2 baixo, 3 direita) e camada; luz vem de cima à esquerda. */
    private static final int[][] VEIO = {
            {M6, M5, M4, M5, M4, M3, M3},
            {M6, M5, M5, M4, M5, M4, M3},
            {M4, M4, M3, M4, M3, M3, M2},
            {M5, M4, M4, M3, M4, M3, M2},
    };

    /**
     * Moldura de madeira com cantoneiras de ferro. {@code esp} = espessura total (contorno + madeira + linha
     * escura de dentro); {@code plaq} = tamanho da cantoneira (0 = sem). Com {@code miolo}, o centro é pergaminho.
     */
    static Pix madeira(int w, int h, int esp, boolean miolo, int plaq) {
        return Pix.de(w, h, (x, y) -> {
            int dt = y, dl = x, db = h - 1 - y, dr = w - 1 - x;
            int d = Math.min(Math.min(dt, db), Math.min(dl, dr));
            if (plaq > 0) {
                int cx = Math.min(x, w - 1 - x), cy = Math.min(y, h - 1 - y);
                if (cx <= plaq - 1 && cy <= plaq - 1 && cx + cy <= plaq + 2) return plaqueta(x, y, w, h, cx, cy, plaq);
            }
            if (d < esp) {
                if (d == 0) return Q;
                if (d == esp - 1) return Q1;
                int lado = dt == d ? 0 : dl == d ? 1 : db == d ? 2 : 3;
                int idx = d == esp - 2 ? 6 : Math.min(d - 1, 6);
                return VEIO[lado][idx];
            }
            if (!miolo) return 0;
            // sombra que a moldura joga no papel: forte em cima e à esquerda
            int nivel = 0;
            nivel = Math.max(nivel, 3 - (dt - esp));
            nivel = Math.max(nivel, 3 - (dl - esp));
            nivel = Math.max(nivel, 2 - (db - esp));
            nivel = Math.max(nivel, 2 - (dr - esp));
            return switch (Math.min(nivel, 3)) {
                case 3 -> P0;
                case 2 -> P1;
                case 1 -> G8;
                default -> P2;
            };
        });
    }

    private static int plaqueta(int x, int y, int w, int h, int cx, int cy, int n) {
        if (cx == 0 || cy == 0) return Q;
        if (cx == n - 1 || cy == n - 1 || cx + cy == n + 2) return Q2;
        if (cx == 3 && cy == 3) return F4;
        if (cx == 4 && cy == 4) return Q2;
        if (n >= 12 && ((cx == 3 && cy == n - 4) || (cx == n - 4 && cy == 3))) return F4;
        boolean topo = y < h / 2, esq = x < w / 2;
        return (topo && cy == 1) || (esq && cx == 1) ? F3 : F2;
    }

    // ------------------------------------------------------------------ pergaminhos

    private static Pix pergaminho(int w, int h) {
        int z = 9;
        return Pix.de(w, h, (x, y) -> {
            int cx = Math.min(x, w - 1 - x), cy = Math.min(y, h - 1 - y), d = Math.min(cx, cy);
            boolean canto = (x < z || x >= w - z) && (y < z || y >= h - z);
            int hs = hash(x, y, 7);
            if (cx + cy <= 1) return 0;
            if (canto && d == 0 && cx + cy > 2 && hs % 5 == 0) return 0;
            if (d == 0) return G5;
            if (d == 1) return P0;
            if (d == 2) return P1;
            if (d == 3) return G8;
            if (canto && hs % 19 == 0) return G8;
            if (canto && hs % 47 == 1) return P1;
            return P2;
        });
    }

    private static Pix pergaminhoEscuro(int w, int h) {
        return Pix.de(w, h, (x, y) -> {
            int dt = y, dl = x, db = h - 1 - y, dr = w - 1 - x, d = Math.min(Math.min(dt, db), Math.min(dl, dr));
            if (Math.min(x, w - 1 - x) + Math.min(y, h - 1 - y) == 0) return 0;
            if (d == 0) return G5;
            if (d == 1) return (dt == 1 || dl == 1) ? P0 : G8;
            return P1;
        });
    }

    private static Pix bilhete(int w, int h) {
        return Pix.de(w, h, (x, y) -> {
            int dt = y, dl = x, db = h - 1 - y, dr = w - 1 - x, d = Math.min(Math.min(dt, db), Math.min(dl, dr));
            if (dr + dt <= 2) return 0;
            if (dr + dt == 3) return Q2;
            if (dr + dt <= 6 && dr < 7 && dt < 7) return P1;   // a dobra do canto
            if (d == 0) return G5;
            if (d == 1) return (db == 1 || dr == 1) ? P1 : PA5;
            boolean canto = (x < 7 || x >= w - 7) && (y < 7 || y >= h - 7);
            int hs = hash(x, y, 13);
            if (canto && hs % 11 == 0) return P2;     // manchas discretas nos cantos
            if (canto && hs % 23 == 1) return G8;
            return P3;
        });
    }

    private static Pix balao(int w, int h) {
        int bb = h - 7;                       // última linha do corpo
        int[] larg = {8, 6, 5, 3, 2, 1};      // cauda: linhas bb+1 .. bb+6, a partir de x = 11
        return Pix.de(w, h, (x, y) -> {
            int t = y - bb;
            if (t >= 1) {
                if (t > 6) return 0;
                int a = 11, b = 11 + larg[t - 1] - 1;
                if (x >= a && x <= b) return P2;
                if (x == a - 1 || x == b + 1) return Q2;
                return 0;
            }
            int dt = y, dl = x, db = bb - y, dr = w - 1 - x, d = Math.min(Math.min(dt, db), Math.min(dl, dr));
            int cx = Math.min(dl, dr), cy = Math.min(dt, db);
            if (cx + cy == 0) return 0;
            if (d == 0) {
                if (y == bb && x >= 11 && x <= 11 + larg[0] - 1) return P2;   // boca da cauda aberta
                return Q2;
            }
            if (cx + cy == 1) return Q2;
            if (d == 1) return (db == 1 || dr == 1) ? P1 : G8;
            boolean canto = (x < 8 || x >= w - 8) && (y < 8 || y >= bb - 7) && !(x >= 11 && x <= 18 && y >= bb - 2);
            int hs = hash(x, y, 17);
            if (canto && hs % 13 == 0) return G8;
            if (canto && hs % 29 == 1) return P1;
            return P2;
        });
    }

    private static Pix dica(int w, int h) {
        return Pix.de(w, h, (x, y) -> {
            int dt = y, dl = x, db = h - 1 - y, dr = w - 1 - x, d = Math.min(Math.min(dt, db), Math.min(dl, dr));
            int cx = Math.min(dl, dr), cy = Math.min(dt, db);
            if (cx + cy == 0) return 0;
            if (d == 0 || cx + cy == 1) return Q;
            if (d == 1) return (dt == 1 || dl == 1) ? M5 : M4;
            return M2;
        });
    }

    /** Documento para ler: folha clara com dobra no canto de baixo à direita. */
    private static Pix documento(int w, int h) {
        return Pix.de(w, h, (x, y) -> {
            int dt = y, dl = x, db = h - 1 - y, dr = w - 1 - x, d = Math.min(Math.min(dt, db), Math.min(dl, dr));
            if (dr + db <= 2) return 0;
            if (dr + db == 3) return G4;
            if (dr + db <= 8 && dr < 10 && db < 10) return dr + db <= 4 ? P0 : G8;   // a dobra
            if (dl + dt == 0) return 0;
            if (d == 0) return G4;
            if (d == 1) return P0;
            if (d == 2) return P1;
            boolean canto = (x < 10 || x >= w - 10) && (y < 10 || y >= h - 10);
            int hs = hash(x, y, 21);
            if (canto && hs % 9 == 0) return P2;      // manchas discretas nos cantos
            if (canto && hs % 31 == 1) return G8;
            return (d == 3 || d == 4) ? P3 : P2;
        });
    }

    /** Ruído de valor que se repete a cada n px (n múltiplo de cel): sem emenda ao ladrilhar. */
    private static double ruido(int x, int y, int n, int cel, int seed) {
        double fx = (double) x * cel / n, fy = (double) y * cel / n;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double tx = fx - x0, ty = fy - y0;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        double a = hash(Math.floorMod(x0, cel), Math.floorMod(y0, cel), seed) / 65535.0;
        double b = hash(Math.floorMod(x0 + 1, cel), Math.floorMod(y0, cel), seed) / 65535.0;
        double c = hash(Math.floorMod(x0, cel), Math.floorMod(y0 + 1, cel), seed) / 65535.0;
        double d = hash(Math.floorMod(x0 + 1, cel), Math.floorMod(y0 + 1, cel), seed) / 65535.0;
        return a + (b - a) * tx + (c - a) * ty + (a - b - c + d) * tx * ty;
    }

    /**
     * Trama de papel que ladrilha (n×n, sem emenda): manchas largas e fibras curtas a 1 degrau da rampa da
     * cor-base ({@code claro}/{@code escuro}), para forrar o miolo sem esticar e sem a repetição aparecer.
     */
    private static Pix papelTrama(int n, int base, int claro, int escuro, int seed) {
        return Pix.de(n, n, (x, y) -> {
            double v = ruido(x, y, n, 4, seed) * 0.6 + ruido(x, y, n, 8, seed + 1) * 0.4;
            if (v > 0.72 && (x + y) % 3 == 0) return claro;
            if (v > 0.64 && (x + y) % 2 == 0 && x % 2 == 0) return claro;
            if (v < 0.28 && (x + y) % 3 == 0) return escuro;
            if (v < 0.36 && (x + y) % 2 == 0 && x % 2 == 0) return escuro;
            int seg = x - x % 8, k = x % 8;                       // fibras: trechos de até 7 px dentro de um segmento de 8
            int hf = hash(seg / 8, y, seed + 5);
            if (hf % 31 == 0 && k >= hf % 3 && k < hf % 3 + 3 + hf % 4) return claro;
            int hg = hash(seg / 8, y, seed + 9);
            if (hg % 61 == 0 && k >= hg % 4 && k < hg % 4 + 2 + hg % 3) return escuro;
            return base;
        });
    }

    private static Pix couro(int w, int h) {
        return Pix.de(w, h, (x, y) -> {
            int dt = y, dl = x, db = h - 1 - y, dr = w - 1 - x, d = Math.min(Math.min(dt, db), Math.min(dl, dr));
            int cx = Math.min(dl, dr), cy = Math.min(dt, db);
            if (cx + cy == 0) return 0;
            if (d == 0 || cx + cy == 1) return Q;
            if (d == 1) return (dt == 1 || dl == 1) ? L3 : Q1;
            boolean canto = (x < 8 || x >= w - 8) && (y < 8 || y >= h - 8);
            if (canto && (d == 3 || d == 4)) {        // pesponto tracejado só nos cantos; o trecho que estica fica liso
                int t = (dt == 3 || db == 3 || dt == 4 || db == 4) && Math.min(dt, db) <= Math.min(dl, dr) ? x : y;
                boolean fio = (t / 2) % 2 == 0;
                if (d == 3) return fio ? L4 : M3;
                return fio ? Q1 : M3;
            }
            return M3;
        });
    }

    /** Livro aberto: capa de couro por fora, pilha de folhas, dobra da lombada por dentro. */
    private static Pix livro(int w, int h, boolean esq) {
        return Pix.de(w, h, (xr, y) -> {
            int x = esq ? xr : w - 1 - xr;               // 0 = lado de fora
            int yy = Math.min(y, h - 1 - y);
            if (x + yy == 0) return 0;
            int g = w - 1 - x;                           // 0 = linha da dobra
            boolean capa = x <= 8 || yy <= 5;
            if (capa) {
                int dm = Math.min(x, yy);
                if (dm == 0) return Q;
                if (x <= 6 && yy <= 4 && x + yy <= 7) {  // cantoneira pequena de latão fosco
                    if (x + yy == 7 || x == 6 || yy == 4) return LA1;
                    return (x == 1 || yy == 1) ? LA3 : LA2;
                }
                if (x == 8 || yy == 5) return Q1;
                if (dm == 1) return R1;
                boolean canto = x <= 14 && (y < 12 || y >= h - 12);
                if (dm == 3 && canto) {                  // pesponto tracejado junto à borda, só nos cantos
                    int t = x < yy ? y : x;
                    return (t / 2) % 2 == 0 ? R3 : Q1;
                }
                return R0;                               // couro liso, sem veio
            }
            if (g == 0) return G5;
            int de = Math.min(x - 9, yy - 6);
            if (de < 5) return new int[] {P0, P3, G7, P3, G7}[de];
            if (g <= 7) return new int[] {G5, P0, P0, P1, P1, G8, G8, G8}[g];   // sombra em degraus entrando na página
            return P2;
        });
    }

    // ------------------------------------------------------------------ botões e abas

    /**
     * estado: 0 normal, 1 sobre, 2 pressionado, 3 desativado. O pressionado desce 1 px e perde a sombra.
     * Acabamento: chanfro de 1 px mais claro em cima, dois veios discretos (linhas inteiras, que esticam sem
     * emenda) na faixa fixa de cima e de baixo e um rebite de ferro em cada canto, dentro do split.
     */
    private static Pix botao(int w, int h, int estado) {
        int corpo = switch (estado) { case 0 -> M3; case 1 -> L3; case 2 -> T2; default -> G3; };
        int luz = switch (estado) { case 0 -> L3; case 1 -> L4; case 2 -> M3; default -> G4; };
        int chanfro = switch (estado) { case 0 -> M6; case 1 -> T5; case 2 -> M4; default -> G5; };
        int veio = switch (estado) { case 0 -> M2; case 1 -> M4; case 2 -> M2; default -> Q2; };
        int rebite = estado == 3 ? G5 : F4, rebiteSombra = estado == 3 ? G4 : F2;
        int baixo = estado == 3 ? Q2 : Q1;
        int borda = estado == 3 ? Q2 : Q;
        int top = estado == 2 ? 1 : 0;          // linha de cima do corpo
        int bot = estado == 2 ? h - 1 : h - 2;  // linha de baixo do corpo
        return Pix.de(w, h, (x, y) -> {
            if (y == h - 1 && estado != 2) return x < 1 || x > w - 2 ? 0 : borda;      // sombra projetada
            if (y < top || y > bot) return 0;
            int dt = y - top, db = bot - y, dl = x, dr = w - 1 - x, d = Math.min(Math.min(dt, db), Math.min(dl, dr));
            int cx = Math.min(dl, dr), cy = Math.min(dt, db);
            if (cx + cy == 0) return 0;
            if (d == 0 || cx + cy == 1) return borda;
            if (cx >= 2 && cx <= 3 && cy >= 2 && cy <= 3) return cx == 2 && cy == 2 ? rebite : rebiteSombra;   // rebites
            if (d == 1) return dt == 1 ? (cx >= 2 ? chanfro : luz) : (db == 1 ? baixo : (dl == 1 ? luz : corpo));
            if (estado == 1 && d == 2 && dt == 2) return luz;
            if ((dt == 4 || db == 4) && cx >= 5) return veio;
            return corpo;
        });
    }

    /** Aba-fita horizontal; a ponta em V (entalhe) fica à direita. */
    private static Pix aba(int w, int h, boolean ativa) {
        int[] tom = ativa ? new int[] {R0, R4, R3, R2, R1} : new int[] {R0, R2, R1, R1, R0};
        int meio = h / 2;
        return Pix.de(w, h, (x, y) -> {
            int dr = w - 1 - x;
            // entalhe: V de 45°, 9 px de fundo no meio
            int prof = Math.max(0, 9 - Math.abs(y - meio));
            if (dr < prof) return 0;
            int dt = y, db = h - 1 - y, dl = x;
            int dEnt = dr - prof;                // distância ao corte
            boolean corte = dEnt == 0 && prof > 0;
            if (dt == 0 || db == 0 || dl == 0 || corte) return Q;
            if (dl <= 2) return R0;              // a fita entra atrás da página: sombra e dobra à esquerda
            if (dl == 3) return ativa ? R1 : R0;
            if (dt == 1) return tom[1];
            if (db == 1) return tom[4];
            if (dEnt == 1 && prof > 0) return tom[2];
            if (ativa && dt == 2) return tom[2];
            return tom[3];
        });
    }

    private static Pix trilho(int w, int h) {
        return Pix.de(w, h, (x, y) -> {
            int dt = y, db = h - 1 - y, dl = x, dr = w - 1 - x, d = Math.min(Math.min(dl, dr), Math.min(dt, db));
            int cx = Math.min(dl, dr), cy = Math.min(dt, db);
            if (cx + cy == 0) return 0;
            if (d == 0) return Q;
            if (dl == 1) return Q1;
            if (dr == 1) return M3;
            return M2;
        });
    }

    private static Pix pegador(int w, int h) {
        return Pix.de(w, h, (x, y) -> {
            int dt = y, db = h - 1 - y, dl = x, dr = w - 1 - x, d = Math.min(Math.min(dl, dr), Math.min(dt, db));
            int cx = Math.min(dl, dr), cy = Math.min(dt, db);
            if (cx + cy == 0) return 0;
            if (d == 0 || cx + cy == 1) return Q;
            if (dl == 1) return L4;
            if (dr == 1) return M3;
            if (dt == 1) return L4;
            if (db == 1) return M3;
            return L3;
        });
    }

    /** Fita de couro fina com pontas aparadas e rebites. */
    private static Pix divisoria(int w, int h) {
        return Pix.de(w, h, (x, y) -> {
            int e = Math.min(x, w - 1 - x);
            int c = h / 2;
            if (Math.abs(y - c) > Math.min(c, e)) return 0;
            if (x == 3 && y == c || x == w - 4 && y == c) return F4;
            boolean borda = y == 0 || y == h - 1 || Math.abs(y - c) == Math.min(c, e);
            if (borda) return Q;
            return y == 1 ? L4 : y == c ? L3 : M3;
        });
    }

    // ------------------------------------------------------------------ molduras

    private static Pix molduraIcone(int n, boolean sel) {
        int[] tom = sel ? new int[] {Q, R4, R3, R1} : new int[] {Q, M5, M4, M2};
        return Pix.de(n, n, (x, y) -> {
            int d = Math.min(Math.min(x, y), Math.min(n - 1 - x, n - 1 - y));
            int cx = Math.min(x, n - 1 - x), cy = Math.min(y, n - 1 - y);
            if (d > 3) return 0;
            if (cx + cy == 0) return 0;
            if (cx + cy == 1) return Q;
            if (d == 1 && !sel) return (x == 1 || y == 1) ? M6 : M4;
            return tom[d];
        });
    }

    private static Pix encaixe(int n) {
        return Pix.de(n, n, (x, y) -> {
            int d = Math.min(Math.min(x, y), Math.min(n - 1 - x, n - 1 - y));
            int cx = Math.min(x, n - 1 - x), cy = Math.min(y, n - 1 - y);
            if (cx + cy == 0) return 0;
            if (d == 0) return G5;
            boolean tl = Math.min(x, y) <= Math.min(n - 1 - x, n - 1 - y);
            if (d == 1) return tl ? P0 : P2;
            if (d == 2) return tl ? P0 : G8;
            if (d == 3) return tl ? P1 : G8;
            return P1;
        });
    }

    /** Contorno de foco do teclado: fio claro por fora e fio de ferrugem escura por dentro; o miolo é transparente. */
    private static Pix foco(int n) {
        return Pix.de(n, n, (x, y) -> {
            int d = Math.min(Math.min(x, y), Math.min(n - 1 - x, n - 1 - y));
            int cx = Math.min(x, n - 1 - x), cy = Math.min(y, n - 1 - y);
            if (d > 2) return 0;
            if (cx == 0 && cy == 0) return 0;
            if (d == 0) return P3;
            if (d == 1) return R1;
            return cx + cy <= 3 ? R1 : 0;     // só o canto engrossa
        });
    }

    // ------------------------------------------------------------------ barras e opções

    private static Pix barraFundo(int w, int h) {
        return Pix.de(w, h, (x, y) -> {
            int dt = y, db = h - 1 - y, dl = x, dr = w - 1 - x, d = Math.min(Math.min(dl, dr), Math.min(dt, db));
            int cx = Math.min(dl, dr), cy = Math.min(dt, db);
            if (cx + cy == 0) return 0;
            if (d == 0 || cx + cy == 1) return Q;
            if (d == 1) return (dt == 1 || dl == 1) ? Q1 : M3;
            return M2;
        });
    }

    private static Pix barra(int w, int h, int[] linhas, int ponta) {
        return Pix.de(w, h, (x, y) -> (x == 0 || x == w - 1) ? ponta : linhas[y]);
    }

    private static Pix caixa(int n, boolean marcada) {
        Pix p = Pix.de(n, n, (x, y) -> {
            int d = Math.min(Math.min(x, y), Math.min(n - 1 - x, n - 1 - y));
            int cx = Math.min(x, n - 1 - x), cy = Math.min(y, n - 1 - y);
            if (cx + cy == 0) return 0;
            if (d == 0) return Q2;
            if (d == 1) return (x == 1 || y == 1) ? P0 : P2;
            return P3;
        });
        if (marcada) {
            int[][] v = {{3, 6}, {4, 7}, {5, 8}, {6, 7}, {7, 6}, {8, 5}, {9, 4}, {10, 3}};
            for (int[] a : v) {
                p.set(a[0], a[1], Q2);
                p.set(a[0], a[1] + 1, Q2);
            }
        }
        return p;
    }

    private static Pix deslizTrilho(int w, int h) {
        int[] linha = {Q, Q1, M2, M2, M3, Q};
        return Pix.de(w, h, (x, y) -> {
            if ((x == 0 || x == w - 1) && (y == 0 || y == h - 1)) return 0;
            return linha[y];
        });
    }

    private static Pix pino(int w, int h) {
        double cx = (w - 1) / 2.0, cy = (h - 1) / 2.0, r = Math.min(w, h) / 2.0;
        return Pix.de(w, h, (x, y) -> {
            double dx = x - cx, dy = y - cy, d = Math.sqrt(dx * dx + dy * dy);
            if (d > r) return 0;
            if (d > r - 1) return Q;
            if (Math.abs(dx) < 1 && Math.abs(dy) < 1) return Q2;
            double luz = -(dx + dy);
            return luz > 3 ? F4 : luz > -2.5 ? F3 : F2;
        });
    }

    // ------------------------------------------------------------------ ícones de HUD

    private static Pix sol(int n) {
        Forma f = new Forma(n, n, PA, LA3);
        f.outline = LA1;
        f.elipse(8.5, 8.5, 4.6, 4.6);
        for (int[] a : new int[][] {{8, 1}, {9, 1}, {8, 2}, {9, 2}, {8, 15}, {9, 15}, {8, 16}, {9, 16}, {1, 8}, {1, 9}, {2, 8}, {2, 9},
                {15, 8}, {15, 9}, {16, 8}, {16, 9}, {3, 3}, {4, 4}, {14, 3}, {13, 4}, {3, 14}, {4, 13}, {14, 14}, {13, 13}})
            f.px(a[0], a[1], LA3);
        Pix p = f.renderizar();
        for (int y = 5; y < 13; y++) for (int x = 5; x < 13; x++) if (f.tem(x, y) && f.cor[y * n + x] == 0 && p.get(x, y) == PA && x + y < 12) p.set(x, y, PA5);
        return p;
    }

    private static Pix lua(int n) {
        Forma f = new Forma(n, n, G8, G6);
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                double a = Math.hypot(x - 8.5, y - 8.5), b = Math.hypot(x - 11.5, y - 6.0);
                if (a <= 7.3 && b > 6.0) f.px(x, y);
            }
        Pix p = f.renderizar();
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) if (f.tem(x, y) && f.tem(x - 1, y) && f.tem(x, y - 1) && p.get(x, y) == G8 && x < 7 && y > 3) p.set(x, y, P2);
        return p;
    }

    private static Pix bolsa(int n) {
        Forma f = new Forma(n, n, L3, M3);
        f.elipse(8.5, 11.2, 6.3, 5.3);
        f.rect(7, 5, 10, 7);      // pescoço
        f.rect(5, 1, 12, 3);      // gola
        f.tira(8, 1, 9, 1);
        Pix p = f.renderizar();
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) if (f.tem(x, y) && f.tem(x - 1, y) && f.tem(x, y - 1) && p.get(x, y) == L3 && x + y < 14) p.set(x, y, L4);
        for (int x = 6; x <= 11; x++) p.set(x, 5, T5);          // cordão
        p.set(6, 6, T5);
        p.set(11, 6, T5);
        for (int y = 10; y <= 12; y++) for (int x = 7; x <= 10; x++) p.set(x, y, LA2);   // moeda na frente
        p.set(8, 11, LA4);
        p.set(9, 11, LA3);
        return p;
    }

    /** Coração sóbrio (vida), 12×12, na altura da barra. */
    private static Pix vida(int n) {
        Forma f = new Forma(n, n, R3, R1);
        f.outline = Q;
        f.ascii(1, 2,
                ".oo...oo.",
                "ooooooooo",
                "ooooooooo",
                "ooooooooo",
                ".ooooooo.",
                "..ooooo..",
                "...ooo...",
                "....o....");
        Pix p = f.renderizar();
        p.set(2, 3, R4);
        p.set(3, 3, R4);
        p.set(2, 4, R4);
        return p;
    }

    /** Pulmões e traqueia (fôlego), 12×12. */
    private static Pix folego(int n) {
        Forma f = new Forma(n, n, O5, O3);
        f.outline = Q;
        f.ascii(1, 1,
                "....oo....",
                "....oo....",
                ".oooooooo.",
                "oooo..oooo",
                "oooo..oooo",
                "oooo..oooo",
                "oooo..oooo",
                ".ooo..ooo.",
                "..oo..oo..");
        Pix p = f.renderizar();
        p.set(2, 4, PA5);
        p.set(2, 5, PA5);
        p.set(8, 4, PA5);
        return p;
    }

    /** Silhueta discreta (um degrau abaixo do encaixe, sem contorno) do que o encaixe do boneco guarda. */
    private static Pix silhueta(int quem) {
        Forma f = new Forma(32, 32, P0, P0);
        f.outline = 0;
        switch (quem) {
            case 0 -> {   // cabeça: elmo com a fresta dos olhos
                f.elipse(15.5, 14, 8.5, 9.5);
                f.rect(7, 14, 24, 24);
                f.tira(7, 24, 8, 24);
                f.tira(23, 24, 24, 24);
                f.tira(9, 15, 22, 17);
                f.rect(15, 15, 16, 23);
                f.tira(14, 25, 17, 31);
            }
            case 1 -> {   // corpo: túnica de mangas curtas
                f.rect(7, 7, 24, 27);
                f.rect(3, 8, 7, 17);
                f.rect(24, 8, 28, 17);
                f.tira(3, 8, 3, 8);
                f.tira(28, 8, 28, 8);
                f.tira(12, 7, 19, 10);
                f.tira(14, 11, 17, 11);
            }
            case 2 -> {   // mão principal: espada na diagonal
                for (int i = 0; i <= 14; i++) {
                    f.px(23 - i, 4 + i);
                    f.px(24 - i, 4 + i);
                    f.px(22 - i, 4 + i);
                }
                for (int t = -5; t <= 5; t++) {
                    f.px(10 + t, 17 + t);
                    f.px(11 + t, 17 + t);
                }
                for (int i = 0; i <= 5; i++) {
                    f.px(9 - i, 19 + i);
                    f.px(8 - i, 19 + i);
                }
                f.rect(2, 25, 4, 27);
            }
            case 3 -> {   // mão secundária: escudo
                f.rect(6, 5, 25, 16);
                for (int y = 17; y <= 28; y++) {
                    int meia = (int) Math.round(10 - (y - 16) * 0.8);
                    f.rect(16 - meia, y, 15 + meia, y);
                }
                f.tira(15, 9, 16, 22);
                f.tira(10, 13, 21, 14);
            }
            default -> {  // acessório: pingente num cordão
                for (int i = 0; i <= 12; i++) {
                    f.px(7 + i, 3 + i);
                    f.px(24 - i, 3 + i);
                }
                f.elipse(15.5, 22, 5, 5.5);
                f.tira(15, 21, 16, 22);
            }
        }
        return f.renderizar();
    }

    // ------------------------------------------------------------------ cursores (32×32)

    private static Pix cursorApontar() {
        Forma f = new Forma(32, 32, P2, G7);
        f.ascii(1, 1,
                "o",
                "oo",
                "ooo",
                "oooo",
                "ooooo",
                "oooooo",
                "ooooooo",
                "oooooooo",
                "ooooooooo",
                "oooooo",
                "oooo.oo",
                "oo...ooo",
                "o....ooo",
                ".....oo");
        return f.renderizar();
    }

    private static Pix cursorFalar() {
        Forma f = new Forma(32, 32, P3, G8);
        f.rect(5, 7, 26, 19);
        f.tira(5, 7, 5, 7);
        f.tira(26, 7, 26, 7);
        f.tira(5, 19, 5, 19);
        f.tira(26, 19, 26, 19);
        f.rect(9, 20, 12, 20);
        f.rect(9, 21, 11, 21);
        f.rect(9, 22, 10, 22);
        f.rect(9, 23, 9, 23);
        for (int x : new int[] {10, 15, 20}) f.rect(x, 12, x + 1, 13, Q2);
        return f.renderizar();
    }

    private static Pix cursorEntrar() {
        Forma f = new Forma(32, 32, M6, M5);
        for (int y = 5; y <= 25; y++)
            for (int x = 4; x <= 15; x++) {
                double dx = x - 9.5, dy = y - 11.0;
                if (y >= 11 || dx * dx + dy * dy <= 6.2 * 6.2) f.px(x, y);
            }
        // vão escuro da porta
        for (int y = 9; y <= 24; y++)
            for (int x = 6; x <= 13; x++) if (y >= 12 || Math.hypot(x - 9.5, y - 12.0) <= 3.8) f.pinta(x, y, Q1);
        // folha entreaberta, presa à dobradiça da esquerda: mais larga embaixo
        for (int y = 11; y <= 24; y++) {
            int larg = 2 + (y - 11) / 4;
            for (int i = 0; i < larg; i++) f.pinta(6 + i, y, i == 0 ? M6 : M5);
            f.pinta(6 + larg - 1, y, M4);
            if (y == 19) f.pinta(6 + larg - 2, y, PA);       // maçaneta
        }
        // seta clara apontando para dentro
        f.rect(23, 15, 28, 16, P3);
        for (int xi = 0; xi <= 4; xi++) {
            double meia = 0.5 + xi * 0.95;
            for (int r = 10; r <= 21; r++) if (Math.abs(r - 15.5) <= meia) f.px(18 + xi, r, P3);
        }
        for (int x = 18; x <= 28; x++) {                       // sombra sob a seta
            if (f.tem(x, 17) && !f.tem(x, 18)) f.pinta(x, 17, G8);
        }
        return f.renderizar();
    }

    private static Pix cursorPegar() {
        Forma f = new Forma(32, 32, PELE4, PELE3);
        int ox = 6, oy = 5;
        int[][] dedos = {{4, 3}, {8, 1}, {12, 3}, {16, 5}};   // x inicial e topo; cada dedo tem 3 px, com 1 px de vão
        for (int[] d : dedos) {
            f.rect(ox + d[0], oy + d[1], ox + d[0] + 2, oy + 10);
            f.tira(ox + d[0], oy + d[1], ox + d[0], oy + d[1]);            // ponta arredondada
            f.tira(ox + d[0] + 2, oy + d[1], ox + d[0] + 2, oy + d[1]);
        }
        f.rect(ox + 4, oy + 11, ox + 18, oy + 16);    // palma
        f.tira(ox + 4, oy + 16, ox + 4, oy + 16);
        f.tira(ox + 18, oy + 16, ox + 18, oy + 16);
        f.rect(ox + 0, oy + 9, ox + 2, oy + 14);      // polegar, separado do indicador
        f.rect(ox + 1, oy + 13, ox + 5, oy + 15);     // base do polegar ligando à palma
        f.tira(ox + 0, oy + 9, ox + 0, oy + 9);
        f.tira(ox + 2, oy + 9, ox + 2, oy + 9);
        f.rect(ox + 7, oy + 17, ox + 15, oy + 19);    // punho
        for (int y = oy + 11; y <= oy + 16; y += 5)    // dobras da palma
            for (int x = ox + 8; x <= ox + 14; x += 4) f.pinta(x, y, PELE3);
        return f.renderizar();
    }

    private static Pix cursorAtacar() {
        Forma f = new Forma(32, 32, G8, G6);
        for (int i = 4; i <= 19; i++) {              // lâminas
            f.px(i, i, G8);
            f.px(i + 1, i, G6);
            f.px(31 - i, i, G8);
            f.px(30 - i, i, G6);
        }
        for (int t = -5; t <= 5; t++) {              // guardas
            f.px(21 + t, 21 - t, R3);
            f.px(21 + t + 1, 21 - t, R2);
            f.px(10 - t, 21 - t, R3);
            f.px(10 - t + 1, 21 - t, R2);
        }
        for (int i = 22; i <= 26; i++) {             // empunhaduras
            f.px(i, i, M5);
            f.px(i + 1, i, M3);
            f.px(31 - i, i, M5);
            f.px(30 - i, i, M3);
        }
        f.rect(27, 27, 28, 28, LA3);
        f.rect(3, 27, 4, 28, LA3);
        return f.renderizar();
    }

    private static Pix cursorEsperar() {
        Forma f = new Forma(32, 32, P2, G7);
        for (int y = 5; y <= 26; y++) {
            int hw = Math.min(7, 1 + Math.abs(y - 15) * 7 / 10);
            for (int x = 16 - hw; x <= 15 + hw; x++) f.px(x, y);
        }
        f.rect(8, 4, 23, 5, M6);                     // tampas de madeira
        f.rect(8, 26, 23, 27, M6);
        for (int y = 20; y <= 25; y++) {             // areia embaixo
            int hw = Math.min(7, 1 + Math.abs(y - 15) * 7 / 10) - 1;
            for (int x = 16 - hw; x <= 15 + hw; x++) if (y >= 22 || Math.abs(x - 15.5) < 3) f.pinta(x, y, PA);
        }
        for (int y = 8; y <= 10; y++) {              // areia em cima
            int hw = Math.min(7, 1 + Math.abs(y - 15) * 7 / 10) - 1;
            for (int x = 16 - hw; x <= 15 + hw; x++) f.pinta(x, y, PA);
        }
        f.pinta(15, 15, PA);
        f.pinta(16, 15, PA);
        f.pinta(15, 17, PA);
        return f.renderizar();
    }
}
