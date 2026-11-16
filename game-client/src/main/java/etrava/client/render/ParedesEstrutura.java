package etrava.client.render;

import etrava.sim.content.AreaDef;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * V6: as paredes de uma área cavada na rocha (a estrutura enterrada), tiradas da colisão do mapa.
 * Em cada borda entre chão e rocha vai uma peça de 1 m, do lado da rocha (não invade o chão):
 * norte e oeste são o fundo (parede inteira), sul e leste a frente (cortada, para não esconder
 * quem anda). Pilares nos cantos de dentro fecham a emenda. Só desenho: não colide, não muda o
 * mapa nem a simulação.
 *
 * <p>Liga-se pelo manifesto: a área {@code A} ganha paredes se houver {@code A.parede_x}
 * (sprites {@code A.parede_x}, {@code A.parede_y}, {@code A.frente_x}, {@code A.frente_y},
 * {@code A.canto}, {@code A.canto_baixo}).
 */
public final class ParedesEstrutura {
    private ParedesEstrutura() {}

    /** Uma peça: sprite, posição (centro) e a caixa no chão, para a ordenação. */
    public record Peca(String sprite, double x, double y, double x0, double y0, double x1, double y1) {}

    private static final double E = 0.25;   // espessura

    public static List<Peca> pecas(AreaDef area, Predicate<String> temEntrada) {
        String p = area.id() + ".";
        List<Peca> out = new ArrayList<>();
        if (!temEntrada.test(p + "parede_x")) return out;
        for (int ty = 0; ty < area.height(); ty++) {
            for (int tx = 0; tx < area.width(); tx++) {
                if (!chao(area, tx, ty)) continue;
                boolean n = !chao(area, tx, ty - 1), s = !chao(area, tx, ty + 1);
                boolean w = !chao(area, tx - 1, ty), e = !chao(area, tx + 1, ty);
                // a parede do fundo só fica inteira se o que ela cobre na tela for rocha
                if (n) out.add(peca(p + (cobre(area, tx, ty - 1) ? "frente_x" : "parede_x"), tx + 0.5, ty - E / 2, 1.0, E));
                if (w) out.add(peca(p + (cobre(area, tx - 1, ty) ? "frente_y" : "parede_y"), tx - E / 2, ty + 0.5, E, 1.0));
                if (s) out.add(peca(p + "frente_x", tx + 0.5, ty + 1 + E / 2, 1.0, E));
                if (e) out.add(peca(p + "frente_y", tx + 1 + E / 2, ty + 0.5, E, 1.0));
                // cantos de dentro: a emenda de duas paredes do mesmo chão
                if (n && w) out.add(peca(p + canto(area, tx - 1, ty - 1), tx - E / 2, ty - E / 2, E, E));
                if (n && e) out.add(peca(p + canto(area, tx + 1, ty - 1), tx + 1 + E / 2, ty - E / 2, E, E));
                if (s && w) out.add(peca(p + canto(area, tx - 1, ty + 1), tx - E / 2, ty + 1 + E / 2, E, E));
                if (s && e) out.add(peca(p + "canto_baixo", tx + 1 + E / 2, ty + 1 + E / 2, E, E));
            }
        }
        return out;
    }

    /**
     * A parede alta na rocha (rx, ry) cobre, na tela, o que fica a noroeste dela (até 3 passos na
     * diagonal, ~2,8 m de altura). Se ali houver chão (outro trecho da área), ela fica cortada,
     * como a da frente: nunca esconde quem anda.
     */
    static boolean cobre(AreaDef a, int rx, int ry) {
        for (int k = 1; k <= 3; k++)
            if (chao(a, rx - k, ry - k) || chao(a, rx - k + 1, ry - k) || chao(a, rx - k, ry - k + 1)) return true;
        return false;
    }

    private static String canto(AreaDef a, int rx, int ry) {
        return cobre(a, rx, ry) ? "canto_baixo" : "canto";
    }

    private static boolean chao(AreaDef a, int tx, int ty) {
        return !a.collisionAt(tx, ty) && !AreaDef.NO_TERRAIN.equals(a.terrainAt(tx, ty));
    }

    private static Peca peca(String sprite, double x, double y, double w, double d) {
        return new Peca(sprite, x, y, x - w / 2, y - d / 2, x + w / 2, y + d / 2);
    }
}
