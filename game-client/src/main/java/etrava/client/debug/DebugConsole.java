package etrava.client.debug;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import etrava.client.ui.Tema;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;

/**
 * Console de depuração (RF-440). Só existe em build de desenvolvimento. Abre com F1.
 * No tema novo (V7): placa escura ({@code ui.dica}) com tinta clara, discreta, no canto de baixo à esquerda.
 */
public final class DebugConsole implements Disposable {
    private static final int MAX_LINES = 11;

    private final Stage stage = new etrava.client.ui.UiStage(new ScreenViewport());
    private final Texture white;
    private final Table table = new Table();
    private final Label output;
    private final TextField input;
    private final Deque<String> lines = new ArrayDeque<>();
    private final Function<String, String> executor;
    private Container<Label> caixaSaida;
    private boolean open;

    /** @param font a fonte da escala da interface (sem sombra); {@code tema} pode ser null (sem peças, cores simples) */
    public DebugConsole(BitmapFont font, Tema tema, Function<String, String> executor) {
        this.executor = executor;
        Pixmap px = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        px.setColor(Color.WHITE);
        px.fill();
        white = new Texture(px);
        px.dispose();
        TextureRegionDrawable base = new TextureRegionDrawable(white);
        Color clara = tema != null ? tema.cor("tinta_clara") : new Color(0.85f, 0.85f, 0.8f, 1f);

        Label.LabelStyle labelStyle = new Label.LabelStyle(font, clara);
        labelStyle.background = tema != null ? tema.peca("ui.dica") : base.tint(new Color(0f, 0f, 0f, 0.78f));
        output = new Label("", labelStyle);
        output.setAlignment(Align.bottomLeft);
        output.setWrap(true);

        TextField.TextFieldStyle fieldStyle = new TextField.TextFieldStyle(font, clara,
                base.tint(clara), base.tint(new Color(0.43f, 0.17f, 0.13f, 0.8f)),
                tema != null ? tema.peca("ui.couro") : base.tint(new Color(0.12f, 0.12f, 0.14f, 0.95f)));
        input = new TextField("", fieldStyle);
        input.setMessageText("comando (ajuda)");
        input.setTextFieldListener((field, c) -> {
            if (c == '\n' || c == '\r') submit();
        });

        table.setFillParent(true);
        table.bottom().left().pad(6);
        float largura = 0f; // 0 = largura do Stage * 0,62 (ajustada em resize)
        caixaSaida = new Container<>(output).fill().clip();
        table.add(caixaSaida).width(largura).height(MAX_LINES * font.getLineHeight() + 12).row();
        table.add(input).width(largura).height(font.getLineHeight() + 12).padTop(2);
        table.setVisible(false);
        stage.addActor(table);
        print("Console de depuração. Digite 'ajuda'.");
    }

    private void submit() {
        String line = input.getText().trim();
        input.setText("");
        if (line.isEmpty()) return;
        print("> " + line);
        run(line);
    }

    /** Executa um comando como se tivesse sido digitado. */
    public void run(String line) {
        String result;
        try {
            result = executor.apply(line);
        } catch (RuntimeException e) {
            result = "erro: " + e.getMessage();
        }
        if (result != null && !result.isEmpty()) print(result);
    }

    public void print(String text) {
        for (String l : text.split("\n")) {
            lines.addLast(l);
            while (lines.size() > MAX_LINES) lines.removeFirst();
        }
        output.setText(String.join("\n", lines));
    }

    public void toggle() {
        open = !open;
        table.setVisible(open);
        stage.setKeyboardFocus(open ? input : null);
    }

    public boolean isOpen() { return open; }
    public Stage stage() { return stage; }

    public void resize(int width, int height) {
        etrava.client.ui.Escala.aplicar(stage.getViewport(), width, height);
        float w = Math.min(stage.getWidth() - 12, Math.max(stage.getWidth() * 0.62f, 480f));
        table.getCell(caixaSaida).width(w);
        table.getCell(input).width(w);
        table.invalidateHierarchy();
    }

    public void render(float dt) {
        if (!open) return;
        stage.act(dt);
        stage.draw();
    }

    @Override
    public void dispose() {
        stage.dispose();
        white.dispose();
    }
}
