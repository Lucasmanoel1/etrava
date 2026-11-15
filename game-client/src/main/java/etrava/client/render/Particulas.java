package etrava.client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Efeitos de combate em pixel de arte (V3, SPEC-visual §7.3): lampejo de impacto, gotas escuras
 * (acento {@code #9C2A2A}, com parcimônia), farelo de terra do vulto, poeira de corrida e de queda,
 * e o brilho que anuncia o golpe do inimigo. Cores só da paleta v3. Pura apresentação: nada aqui
 * entra no save nem mexe na simulação. Determinístico por semente fixa (as capturas se repetem).
 */
public final class Particulas {
    /** Teto global de partículas vivas (o mais velho sai primeiro). */
    static final int TETO = 400;

    static final Color LAMPEJO = Color.valueOf("FFE9A8"), LAMPEJO2 = Color.valueOf("D8CCB0"),
            SANGUE = Color.valueOf("9C2A2A"), SANGUE_ESCURO = Color.valueOf("6E2C22"),
            TERRA = Color.valueOf("3D2A1C"), RAIZ = Color.valueOf("7A5632"),
            POEIRA = Color.valueOf("8E6A44"), POEIRA_CLARA = Color.valueOf("A39A8A"),
            AVISO = Color.valueOf("F09A3C");

    private static final class P {
        double x, y, z, vx, vy, vz;
        float vida, dura;
        Color cor;
        int tam;
        boolean cai;          // gravidade: cai e para no chão
    }

    private final List<P> vivas = new ArrayList<>();
    private final Random rnd = new Random(7);

    public int vivas() {
        return vivas.size();
    }

    private P nova(double x, double y, double z, Color cor, float dura) {
        if (vivas.size() >= TETO) vivas.remove(0);
        P p = new P();
        p.x = x;
        p.y = y;
        p.z = z;
        p.cor = cor;
        p.dura = dura;
        p.tam = 1;
        vivas.add(p);
        return p;
    }

    /** Golpe que acertou: estrela de luz no ponto do golpe e, se sangra, duas ou três gotas. */
    public void impacto(double x, double y, boolean sangra, boolean terra, boolean pesado) {
        int n = pesado ? 9 : 6;
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n + rnd.nextDouble() * 0.4;
            double v = 2.2 + rnd.nextDouble() * (pesado ? 2.0 : 1.0);
            P p = nova(x, y, 1.0, i % 3 == 0 ? LAMPEJO2 : LAMPEJO, 0.14f);
            p.vx = Math.cos(a) * v;
            p.vy = Math.sin(a) * v;
            p.vz = (rnd.nextDouble() - 0.3) * v;
        }
        if (sangra || terra) {
            int g = pesado ? 4 : 2 + rnd.nextInt(2);
            for (int i = 0; i < g; i++) {
                Color c = terra ? (i % 2 == 0 ? TERRA : RAIZ) : (i % 2 == 0 ? SANGUE : SANGUE_ESCURO);
                P p = nova(x, y, 0.9 + rnd.nextDouble() * 0.3, c, 0.7f);
                double a = rnd.nextDouble() * 2 * Math.PI;
                p.vx = Math.cos(a) * 0.9;
                p.vy = Math.sin(a) * 0.9;
                p.vz = 1.2 + rnd.nextDouble();
                p.cai = true;
                p.tam = terra && i % 2 == 0 ? 2 : 1;
            }
        }
    }

    /** Poeira de um passo de corrida (um ou dois grãos atrás do pé). */
    public void passo(double x, double y, double facing) {
        for (int i = 0; i < 2; i++) {
            P p = nova(x - Math.cos(facing) * 0.2, y - Math.sin(facing) * 0.2, 0.02,
                    i == 0 ? POEIRA : POEIRA_CLARA, 0.45f);
            p.vx = -Math.cos(facing) * 0.4 + (rnd.nextDouble() - 0.5) * 0.4;
            p.vy = -Math.sin(facing) * 0.4 + (rnd.nextDouble() - 0.5) * 0.4;
            p.vz = 0.3 + rnd.nextDouble() * 0.3;
        }
    }

    /** Nuvem de poeira de quem caiu no chão. */
    public void queda(double x, double y) {
        for (int i = 0; i < 10; i++) {
            double a = 2 * Math.PI * i / 10;
            P p = nova(x, y, 0.05, i % 2 == 0 ? POEIRA : POEIRA_CLARA, 0.6f);
            p.vx = Math.cos(a) * 0.8;
            p.vy = Math.sin(a) * 0.8;
            p.vz = 0.2 + rnd.nextDouble() * 0.3;
        }
    }

    /** O inimigo começou a armar o golpe: um brilho curto acima dele (lê-se a zoom 1). */
    public void aviso(double x, double y, double altura) {
        for (int i = 0; i < 4; i++) {
            P p = nova(x, y, altura, AVISO, 0.22f);
            double a = Math.PI / 2 * i + Math.PI / 4;
            p.vx = Math.cos(a) * 0.9;
            p.vy = Math.sin(a) * 0.9;
            p.vz = 0.3;
        }
        nova(x, y, altura, LAMPEJO, 0.28f).tam = 2;
    }

    public void atualizar(float dt) {
        for (int i = vivas.size() - 1; i >= 0; i--) {
            P p = vivas.get(i);
            p.vida += dt;
            if (p.vida >= p.dura) {
                vivas.remove(i);
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            if (p.cai) {
                p.vz -= 9.0 * dt;
                if (p.z <= 0) {      // no chão, a gota para e fica até sumir
                    p.z = 0;
                    p.vx = p.vy = p.vz = 0;
                }
            } else {
                p.vx *= 1 - 3 * dt;
                p.vy *= 1 - 3 * dt;
                p.vz *= 1 - 3 * dt;
            }
        }
    }

    /** Desenha cada partícula como um quadrado de pixels de arte, alinhado à grade (não treme). */
    public void desenhar(SpriteBatch batch, Texture branco, float[] b) {
        int px = IsoProjection.ART_SCALE;
        for (P p : vivas) {
            float sx = IsoProjection.snap(IsoProjection.sx(p.x, p.y)), sy = IsoProjection.snap(IsoProjection.sy(p.x, p.y, p.z));
            if (sx < b[0] || sx > b[2] || sy < b[1] || sy > b[3]) continue;
            batch.setColor(p.cor);
            batch.draw(branco, sx, sy, p.tam * px, p.tam * px);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }
}
