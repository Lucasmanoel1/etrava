package etrava.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import java.util.Random;

/**
 * As camadas vivas sobre a ilustração do menu (V7): névoa que deriva devagar, brasas que sobem e fios de
 * fumaça nas chaminés. Tudo discreto e em função do tempo (posição = f(semente, t)): a mesma semente e o
 * mesmo tempo dão o mesmo quadro, o que a captura precisa. Desenha em pixels de tela, depois da ilustração
 * e antes da interface.
 */
final class MenuFx implements Disposable {
    /** Tamanho da ilustração (640×360) e as chaminés nela, em pixels da ilustração. */
    private static final float ILU_W = 640, ILU_H = 360;
    private static final float[][] CHAMINES = {{316, 133}, {68, 183}, {372, 127}};

    private static final int NEVOA = 7, BRASAS = 64, FIOS = 3, PUFES = 9;

    private final SpriteBatch batch = new SpriteBatch();
    private final Texture mole, quadrado;
    private final float[][] nevoa = new float[NEVOA][6];
    private final float[][] brasa = new float[BRASAS][7];
    private final float[][] fio = new float[FIOS * PUFES][3];
    private float tempo;

    MenuFx(long semente) {
        Random r = new Random(semente);
        for (float[] n : nevoa) { // x0, altura (fração), velocidade (px/s por altura de 720), largura, altura da mancha, fase
            n[0] = r.nextFloat();
            n[1] = 0.08f + r.nextFloat() * 0.42f;
            n[2] = (r.nextBoolean() ? 1 : -1) * (5 + r.nextFloat() * 9);
            n[3] = 0.45f + r.nextFloat() * 0.35f;
            n[4] = 0.10f + r.nextFloat() * 0.10f;
            n[5] = r.nextFloat() * 6.28f;
        }
        for (float[] b : brasa) { // x0, fase, período, tamanho, balanço, fase do balanço, cintilação
            b[0] = 0.05f + r.nextFloat() * 1.0f;
            b[1] = r.nextFloat();
            b[2] = 7 + r.nextFloat() * 8;
            b[3] = r.nextFloat();
            b[4] = 8 + r.nextFloat() * 22;
            b[5] = r.nextFloat() * 6.28f;
            b[6] = 2 + r.nextFloat() * 4;
        }
        for (float[] p : fio) { // fase, balanço, fase do balanço
            p[0] = r.nextFloat();
            p[1] = 6 + r.nextFloat() * 10;
            p[2] = r.nextFloat() * 6.28f;
        }
        mole = new Texture(disco(64));
        Pixmap px = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        px.setColor(Color.WHITE);
        px.fill();
        quadrado = new Texture(px);
        px.dispose();
    }

    /** Mancha redonda e macia (alfa em queda suave do centro à borda). */
    private static Pixmap disco(int n) {
        Pixmap px = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        px.setBlending(Pixmap.Blending.None);
        float c = (n - 1) / 2f;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float d = (float) Math.hypot(x - c, y - c) / c;
                float a = Math.max(0, 1 - d);
                a = a * a * (3 - 2 * a);
                px.setColor(1, 1, 1, a);
                px.drawPixel(x, y);
            }
        }
        return px;
    }

    float tempo() { return tempo; }

    void render(float dt, int w, int h) {
        tempo += dt;
        float t = tempo, s = Math.max(1f, h / 720f);
        float cobre = Math.max(w / ILU_W, h / ILU_H);
        float ox = (w - ILU_W * cobre) / 2, oy = (h - ILU_H * cobre) / 2;
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        batch.begin();

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        // névoa: manchas largas e achatadas, atravessando a tela e voltando pelo outro lado
        for (float[] n : nevoa) {
            float larg = n[3] * w, alt = n[4] * h;
            float volta = w + 2 * larg;
            float x = ((n[0] * volta + n[2] * s * t) % volta + volta) % volta - larg;
            float y = n[1] * h + (float) Math.sin(t * 0.15f + n[5]) * 0.015f * h;
            float a = 0.17f + 0.05f * (float) Math.sin(t * 0.2f + n[5]);
            batch.setColor(0.70f, 0.72f, 0.80f, a);
            batch.draw(mole, x, y - alt / 2, larg, alt);
        }
        // fumaça: pufes que sobem das chaminés, crescem e se apagam
        for (int c = 0; c < FIOS; c++) {
            float cx = ox + CHAMINES[c][0] * cobre, cy = oy + (ILU_H - CHAMINES[c][1]) * cobre;
            for (int i = 0; i < PUFES; i++) {
                float[] p = fio[c * PUFES + i];
                float u = ((t / 9f + p[0] + (float) i / PUFES) % 1 + 1) % 1;
                float x = cx + (float) Math.sin(u * 5 + p[2]) * p[1] * s * u + u * 14 * s;
                float y = cy + u * 0.16f * h;
                float tam = (5 + 20 * u) * s;
                float a = 0.38f * (float) Math.sin(Math.PI * u) * (1 - u);
                batch.setColor(0.55f, 0.52f, 0.52f, a);
                batch.draw(mole, x - tam / 2, y - tam / 2, tam, tam);
            }
        }
        // brasas: quadrados pequenos que sobem balançando, com brilho aditivo
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (float[] b : brasa) {
            float u = ((t / b[2] + b[1]) % 1 + 1) % 1;
            float x = b[0] * w + (float) Math.sin(t * 0.7f + b[5]) * b[4] * s + u * 20 * s;
            float y = h * (0.03f + u * 0.78f);
            float tam = Math.max(1, Math.round((1.2f + b[3] * 1.8f) * s));
            float cint = 0.65f + 0.35f * (float) Math.sin(t * b[6] + b[5]);
            float a = (float) Math.sin(Math.PI * u) * cint * 1.0f;
            batch.setColor(1f, 0.45f + 0.35f * b[3], 0.12f, a);
            batch.draw(quadrado, Math.round(x), Math.round(y), tam, tam);
        }
        batch.setColor(Color.WHITE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        mole.dispose();
        quadrado.dispose();
    }
}
