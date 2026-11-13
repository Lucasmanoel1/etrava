package etrava.tools.art.pixel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pincéis do pintor de folhagem (D-100): cachos de folha desenhados em pixel, um conjunto por
 * espécie, guardados em {@code tools/art/pinceis/folhas.pxo} — um projeto do Pixelorama, uma
 * camada por conjunto, cada pincel numa célula de 8×8 px. Quem quiser redesenhar a folha de uma
 * espécie abre o .pxo no Pixelorama e pinta; o pipeline lê o arquivo como está.
 *
 * <p>O pincel é em cinza, por nível (não por cor): a cor sai da rampa da espécie e da luz do
 * lugar onde o cacho cai.
 * <ul>
 *   <li>escuro (luminância &lt; 0,38): sombra do cacho, um tom abaixo;</li>
 *   <li>médio (&lt; 0,63): corpo, o tom da luz local;</li>
 *   <li>claro (&lt; 0,88): luz, um tom acima;</li>
 *   <li>branco: brilho, dois tons acima (só onde já é claro).</li>
 * </ul>
 */
public final class Pinceis {
    public static final int CELULA = 12;
    public static final int SOMBRA = -1, CORPO = 0, LUZ = 1, BRILHO = 2;

    /** Um pincel: deslocamentos (dx, dy) a partir do centro e o nível de cada pixel. */
    public record Pincel(int[] dx, int[] dy, int[] nivel) {}

    /** Conjunto de uma espécie: os pincéis e o passo da grade de carimbos (px). */
    public record Conjunto(String nome, List<Pincel> pinceis, int passoX, int passoY, int borda) {}

    private Pinceis() {}

    /** Passo da grade e borda (quantos px o cacho pode passar da silhueta) por espécie. */
    private static final Map<String, int[]> PASSO = Map.of(
            "carvalho", new int[] {6, 4, 2},
            "betula", new int[] {3, 3, 2},
            "macieira", new int[] {3, 3, 1},
            "pereira", new int[] {3, 3, 1});

    public static Map<String, Conjunto> carregar(Path pxo) throws IOException {
        if (!Files.exists(pxo)) gerarIniciais(pxo);
        Pxo.Projeto p = Pxo.ler(pxo);
        Map<String, Conjunto> out = new LinkedHashMap<>();
        for (Pxo.Camada c : p.camadas()) {
            int[] px = c.quadros().get(0);
            List<Pincel> lista = new ArrayList<>();
            for (int cy = 0; cy + CELULA <= p.altura(); cy += CELULA) {
                for (int cx = 0; cx + CELULA <= p.largura(); cx += CELULA) {
                    Pincel pi = celula(px, p.largura(), cx, cy);
                    if (pi != null) lista.add(pi);
                }
            }
            if (lista.isEmpty()) continue;
            int[] ps = PASSO.getOrDefault(c.nome(), new int[] {3, 3, 1});
            out.put(c.nome(), new Conjunto(c.nome(), List.copyOf(lista), ps[0], ps[1], ps[2]));
        }
        return out;
    }

    private static Pincel celula(int[] px, int w, int cx, int cy) {
        List<int[]> pts = new ArrayList<>();
        int x0 = 99, y0 = 99, x1 = -1, y1 = -1;
        for (int y = 0; y < CELULA; y++) {
            for (int x = 0; x < CELULA; x++) {
                int c = px[(cy + y) * w + cx + x];
                if ((c >>> 24) < 128) continue;
                double l = Paleta.luminancia(c);
                int nivel = l < 0.38 ? SOMBRA : l < 0.63 ? CORPO : l < 0.88 ? LUZ : BRILHO;
                pts.add(new int[] {x, y, nivel});
                x0 = Math.min(x0, x);
                y0 = Math.min(y0, y);
                x1 = Math.max(x1, x);
                y1 = Math.max(y1, y);
            }
        }
        if (pts.isEmpty()) return null;
        int mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
        int[] dx = new int[pts.size()], dy = new int[pts.size()], nv = new int[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            dx[i] = pts.get(i)[0] - mx;
            dy[i] = pts.get(i)[1] - my;
            nv[i] = pts.get(i)[2];
        }
        return new Pincel(dx, dy, nv);
    }

    // ---------------------------------------------------------------- pincéis iniciais

    /**
     * Desenho inicial dos pincéis ('.' vazio, s sombra, b corpo, l luz, r brilho). Só serve para
     * criar o .pxo na primeira vez; depois a fonte é o .pxo (edite no Pixelorama).
     */
    static final Map<String, String[][]> INICIAIS = new LinkedHashMap<>();

    static {
        INICIAIS.put("carvalho", new String[][] {
                {"...rll....", "..lbbbbl..", ".lbbbbbbb.", "lbbbbbbbbb", "bbbbbbbbbs", ".sbbbbbbs.", "..ssssss.."},
                {".rl...ll..", "lbbl.lbbl.", "bbbbbbbbb.", "bbbbbbbbbs", "sbbbbbbbs.", ".sssssss.."},
                {".rl..", "lbbb.", "bbbbb", "sbbbs", ".sss."},
                {"..rl..", ".lbbl.", "lbbbbb", "bbbbbb", "bbbbbs", "sbbbs.", ".sss.."},
                {".r..l..l.", "lbllbllbl", "bbbbbbbbb", "sbbbbbbbs", ".sssssss."},
                {"..rl....", ".lbbll..", "lbbbbbl.", "bbbbbbbb", "sbbbbbbs", ".ssbbss.", "...ss..."},
        });
        INICIAIS.put("betula", new String[][] {
                {".r.", "lbl", "bbs", ".bs", ".s."},
                {".rl.", "lbbl", "bbbs", ".bs.", ".s.."},
                {"r.", "bl", "bs", "s."},
                {"..r", ".lb", "lbs", "bs.", "s.."},
                {".rl", "lbb", "bbs", "bs.", ".s."},
        });
        INICIAIS.put("macieira", new String[][] {
                {".rl.", "lbbl", "bbbs", "sbbs", ".ss."},
                {"..rl.", ".lbbl", "lbbbs", "bbbs.", ".ss.."},
                {".rl..", "lbbbl", "bbbbs", ".sbs.", "..s.."},
                {"rl.", "bbl", "bbs", ".s."},
        });
        INICIAIS.put("pereira", new String[][] {
                {".r.", "lbl", "bbb", "bbs", ".s."},
                {"..r.", ".lbl", "lbbs", "bbs.", ".s.."},
                {".r.", "lbl", "bbs", "bs.", "s.."},
                {".rl", "lbb", "bbs", ".s."},
        });
    }

    static void gerarIniciais(Path destino) throws IOException {
        int colunas = 8, w = colunas * CELULA;
        int linhas = 1;
        for (String[][] v : INICIAIS.values()) linhas = Math.max(linhas, (v.length + colunas - 1) / colunas);
        int h = linhas * CELULA;
        List<Pxo.Camada> camadas = new ArrayList<>();
        for (Map.Entry<String, String[][]> e : INICIAIS.entrySet()) {
            int[] px = new int[w * h];
            String[][] ps = e.getValue();
            for (int k = 0; k < ps.length; k++) {
                int cx = (k % colunas) * CELULA, cy = (k / colunas) * CELULA;
                for (int y = 0; y < ps[k].length; y++) {
                    for (int x = 0; x < ps[k][y].length(); x++) {
                        int cor = switch (ps[k][y].charAt(x)) {
                            case 's' -> 0xFF404040;
                            case 'b' -> 0xFF808080;
                            case 'l' -> 0xFFC0C0C0;
                            case 'r' -> 0xFFFFFFFF;
                            default -> 0;
                        };
                        px[(cy + 1 + y) * w + cx + 1 + x] = cor;
                    }
                }
            }
            camadas.add(new Pxo.Camada(e.getKey(), true, List.of(px)));
        }
        Pxo.escrever(destino, new Pxo.Projeto(w, h, 1.0, camadas), java.util.List.of());
    }
}
