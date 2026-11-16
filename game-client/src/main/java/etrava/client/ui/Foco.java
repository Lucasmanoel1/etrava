package etrava.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Navegação por teclado de um painel (V7, SPEC-visual §8.1): as setas e o Tab andam entre os
 * atores "focáveis" (Shift+Tab e as setas para cima/esquerda voltam), Enter ou espaço ativam,
 * Esc fecha. O ator focado ganha o contorno {@code ui.foco} e a lista que o contém rola até ele.
 * <p>
 * O {@code Foco} é um ator: entra no painel (como último filho, para desenhar por cima) e o
 * {@link UiStage} entrega as teclas ao {@code Foco} visível mais alto. Quem refaz o painel
 * ({@code clearChildren}) cria outro {@code Foco} junto com o conteúdo. Sem foco inicial: o contorno
 * só aparece depois da primeira tecla de navegação (quem usa o mouse não vê anel nenhum).
 */
public final class Foco extends Actor {
    private record Item(Actor ator, Runnable ativar, IntConsumer lateral) {}

    private final Drawable contorno;
    private final List<Item> itens = new ArrayList<>();
    private int indice = -1;
    private Runnable aoFechar;
    private java.util.function.IntConsumer aoFocar;
    /** Grade: os primeiros {@code gradeN} itens formam linhas de {@code gradeCols}; cima e baixo andam uma linha. */
    private int gradeCols, gradeN;
    private final Vector2 tmp = new Vector2();

    /** @param contorno a peça {@code ui.foco} do tema */
    public Foco(Drawable contorno) {
        this.contorno = contorno;
        setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
    }

    /** Torna o ator focável; {@code ativar} roda com Enter (pode ser null). */
    public Foco adicionar(Actor ator, Runnable ativar) {
        return adicionar(ator, ativar, null);
    }

    /**
     * Como {@link #adicionar(Actor, Runnable)}, com ajuste lateral (deslizante, escolha): esquerda e direita
     * chamam {@code lateral} com -1 e +1 em vez de andar o foco.
     */
    public Foco adicionar(Actor ator, Runnable ativar, IntConsumer lateral) {
        itens.add(new Item(ator, ativar, lateral));
        return this;
    }

    /** Botão: Enter dispara o mesmo evento de mudança que o clique. */
    public Foco adicionar(Button botao) {
        return adicionar(botao, () -> botao.fire(new ChangeListener.ChangeEvent()));
    }

    /** O que Esc faz neste painel (null = nada: a tecla segue adiante). */
    public Foco aoFechar(Runnable acao) {
        this.aoFechar = acao;
        return this;
    }

    /** Chamado com o índice do item sempre que o foco passa a ele (teclado ou {@link #focar}). */
    public Foco aoFocar(java.util.function.IntConsumer acao) {
        this.aoFocar = acao;
        return this;
    }

    /**
     * Os {@code quantos} primeiros itens adicionados são uma grade de {@code colunas} por linha: as setas
     * para cima e para baixo andam uma linha (sem volta); esquerda, direita e Tab seguem a ordem das linhas.
     */
    public Foco grade(int colunas, int quantos) {
        this.gradeCols = colunas;
        this.gradeN = quantos;
        return this;
    }

    public void limpar() {
        itens.clear();
        indice = -1;
    }

    public Actor focado() {
        return indice >= 0 && indice < itens.size() ? itens.get(indice).ator() : null;
    }

    public int quantos() { return itens.size(); }

    /** O índice do item focado, ou -1 (ninguém usou o teclado ainda). */
    public int indice() { return indice; }

    /** Devolve o foco ao item {@code i} depois de a tela ser refeita: sem rolar e sem chamar {@link #aoFocar}. */
    public void restaurar(int i) {
        indice = i < 0 || i >= itens.size() ? -1 : i;
    }

    /** Foca o item {@code i} (ou tira o foco com -1) e rola a lista até ele. */
    public void focar(int i) {
        indice = i < 0 || i >= itens.size() ? -1 : i;
        Actor a = focado();
        if (a != null) rolarAte(a);
        if (aoFocar != null && indice >= 0) aoFocar.accept(indice);
    }

    /** Cima/baixo numa grade; false se o foco não está nela. */
    private boolean andarGrade(int linhas) {
        if (gradeCols <= 0 || indice < 0 || indice >= gradeN) return false;
        int alvo = indice + linhas * gradeCols;
        if (alvo >= 0 && alvo < gradeN) focar(alvo);
        else if (linhas > 0 && indice / gradeCols < (gradeN - 1) / gradeCols) focar(gradeN - 1); // última linha, mais curta
        else if (linhas > 0 && gradeN < itens.size()) focar(gradeN); // depois da grade: o que vem a seguir
        return true;
    }

    /** Anda {@code passo} itens (com volta ao redor), pulando os escondidos. */
    private void andar(int passo) {
        int n = itens.size();
        if (n == 0) return;
        int i = indice < 0 ? (passo > 0 ? -1 : 0) : indice;
        for (int tentativas = 0; tentativas < n; tentativas++) {
            i = ((i + passo) % n + n) % n;
            if (visivel(itens.get(i).ator())) {
                focar(i);
                return;
            }
        }
    }

    /**
     * Trata uma tecla; devolve true se foi consumida.
     * Tab/setas direita e baixo: próximo; Shift+Tab/esquerda/cima: anterior; Enter/espaço: ativa; Esc: fecha.
     */
    public boolean tecla(int keycode, boolean shift) {
        switch (keycode) {
            case Input.Keys.TAB -> andar(shift ? -1 : 1);
            case Input.Keys.DOWN -> {
                if (!andarGrade(1)) andar(1);
            }
            case Input.Keys.UP -> {
                if (!andarGrade(-1)) andar(-1);
            }
            case Input.Keys.RIGHT, Input.Keys.LEFT -> {
                int passo = keycode == Input.Keys.RIGHT ? 1 : -1;
                Item atual = indice >= 0 && indice < itens.size() ? itens.get(indice) : null;
                if (atual != null && atual.lateral() != null && visivel(atual.ator())) atual.lateral().accept(passo);
                else andar(passo);
            }
            case Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER, Input.Keys.SPACE -> {
                if (focado() == null) return false;
                Runnable r = itens.get(indice).ativar();
                if (r != null) r.run();
            }
            case Input.Keys.ESCAPE -> {
                if (aoFechar == null) return false;
                aoFechar.run();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public boolean tecla(int keycode) {
        boolean shift = Gdx.input != null
                && (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
        return tecla(keycode, shift);
    }

    /** O {@code Foco} visível mais alto (último na ordem de desenho) sob a raiz, com itens; ou null. */
    static Foco ativo(Actor raiz) {
        if (!raiz.isVisible()) return null;
        Foco achado = null;
        if (raiz instanceof Foco f && (!f.itens.isEmpty() || f.aoFechar != null)) achado = f;
        if (raiz instanceof Group g) {
            for (Actor filho : g.getChildren()) {
                Foco f = ativo(filho);
                if (f != null) achado = f;
            }
        }
        return achado;
    }

    private static boolean visivel(Actor a) {
        for (Actor x = a; x != null; x = x.getParent()) if (!x.isVisible()) return false;
        return a.getStage() != null;
    }

    private void rolarAte(Actor a) {
        ScrollPane pane = null;
        for (Actor x = a.getParent(); x != null; x = x.getParent()) {
            if (x instanceof ScrollPane p) {
                pane = p;
                break;
            }
        }
        if (pane == null || pane.getActor() == null) return;
        pane.validate();
        Actor conteudo = pane.getActor();
        a.localToAscendantCoordinates(conteudo, tmp.set(0, 0));
        pane.scrollTo(tmp.x, tmp.y, a.getWidth(), a.getHeight());
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Actor a = focado();
        if (a == null || !visivel(a) || getParent() == null) return;
        a.localToAscendantCoordinates(getParent(), tmp.set(0, 0));
        float m = 2;
        contorno.draw(batch, tmp.x - m, tmp.y - m, a.getWidth() + 2 * m, a.getHeight() + 2 * m);
    }
}
