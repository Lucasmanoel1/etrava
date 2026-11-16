package etrava.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import etrava.sim.content.AssetManifest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * O tema da interface (SPEC-visual §8.3): as peças de pixel art de {@code assets/ui/}
 * (declaradas em {@code assets/manifest/ui.yaml}, com 9-patches e recuos em
 * {@code assets/config/ui.yaml}), as cores de tinta, os cursores e os estilos prontos dos
 * componentes. Peça ausente ou sem metadado vira um placeholder de cor chapada e um aviso no
 * log por ID, nunca uma exceção (RF-421); sem o arquivo de configuração valem os padrões embutidos.
 * <pre>
 * pecas:
 *   ui.painel_madeira: {split: [esq, dir, cima, baixo], pad: [esq, dir, cima, baixo]}
 *   ui.sol: {}
 * cursores:
 *   ui.cursor_apontar: {ponto: [x, y]}
 * cores: {tinta: "241C18", tinta_suave: "...", ferrugem: "...", tinta_clara: "...", desativado: "..."}
 * </pre>
 * Tudo em pixels de arte: com a escala da interface ({@link Escala}) um pixel de arte é uma unidade do Stage.
 */
public final class Tema implements Disposable {
    private static final Logger LOG = LoggerFactory.getLogger(Tema.class);

    /** Peças sem 9-patch: imagem simples, usada pelo tamanho natural (valem mesmo sem metadado). */
    private static final Set<String> SIMPLES = Set.of("ui.sol", "ui.lua", "ui.bolsa", "ui.divisoria", "ui.divisoria_v");

    /** Placeholder: preenchimento, borda, espessura da borda, recuo, tamanho mínimo (largura, altura). */
    private record Padrao(String fill, String borda, int b, int pad, int minL, int minA) {}

    private static final Map<String, Padrao> PADROES = new HashMap<>();
    private static final Padrao GENERICO = new Padrao("D8CCB0", "2E2018", 1, 6, 0, 0);

    static {
        padrao("painel_madeira", "D8CCB0", "2E2018", 3, 12, 0, 0);
        padrao("pergaminho", "D8CCB0", "B9A888", 1, 8, 0, 0);
        padrao("pergaminho_escuro", "B9A888", "8A7A5C", 1, 8, 0, 0);
        padrao("couro", "5A3A28", "2E2018", 1, 8, 0, 0);
        padrao("bilhete", "E6DCC0", "8A7A5C", 1, 8, 0, 0);
        padrao("balao", "EEE6D2", "2E2018", 1, 6, 0, 0);
        padrao("dica", "D8CCB0", "2E2018", 1, 6, 0, 0);
        padrao("livro_esq", "D8CCB0", "2E2018", 2, 12, 0, 0);
        padrao("livro_dir", "D8CCB0", "2E2018", 2, 12, 0, 0);
        padrao("documento", "E6DCC0", "5A3A28", 2, 12, 0, 0);
        padrao("botao", "C4B48F", "2E2018", 1, 6, 0, 0);
        padrao("botao_sobre", "D8CCB0", "2E2018", 1, 6, 0, 0);
        padrao("botao_press", "A89870", "2E2018", 1, 6, 0, 0);
        padrao("botao_desativado", "B9A888", "8A7A5C", 1, 6, 0, 0);
        padrao("aba", "B9A888", "2E2018", 1, 6, 0, 0);
        padrao("aba_ativa", "D8CCB0", "2E2018", 1, 6, 0, 0);
        padrao("rolagem_trilho", "B9A888", "B9A888", 1, 0, 9, 8);
        padrao("rolagem_pegador", "5A3A28", "2E2018", 1, 0, 9, 24);
        padrao("divisoria", "5A3A28", "5A3A28", 0, 0, 0, 3);
        padrao("divisoria_v", "5A3A28", "5A3A28", 0, 0, 3, 0);
        padrao("moldura_retrato", "00000000", "2E2018", 2, 3, 0, 0);
        padrao("moldura_icone", "00000000", "2E2018", 2, 3, 0, 0);
        padrao("moldura_icone_sel", "00000000", "6E2C22", 2, 3, 0, 0);
        padrao("encaixe", "B9A888", "2E2018", 1, 3, 0, 0);
        padrao("foco", "00000000", "6E2C22", 2, 0, 0, 0);
        padrao("barra_fundo", "2E2018", "2E2018", 1, 1, 0, 0);
        padrao("barra_vida", "7A2A22", "7A2A22", 1, 0, 0, 0);
        padrao("barra_folego", "5A7A3A", "5A7A3A", 1, 0, 0, 0);
        padrao("caixa", "D8CCB0", "2E2018", 1, 0, 16, 16);
        padrao("caixa_marcada", "A8503A", "2E2018", 1, 0, 16, 16);
        padrao("deslizante_trilho", "5A3A28", "2E2018", 1, 0, 8, 6);
        padrao("deslizante_pino", "D8CCB0", "2E2018", 1, 0, 10, 18);
        padrao("sol", "E0B040", "E0B040", 0, 0, 16, 16);
        padrao("lua", "C8C8D8", "C8C8D8", 0, 0, 16, 16);
        padrao("bolsa", "5A3A28", "5A3A28", 0, 0, 16, 16);
    }

    private static void padrao(String nome, String fill, String borda, int b, int pad, int minL, int minA) {
        PADROES.put("ui." + nome, new Padrao(fill, borda, b, pad, minL, minA));
    }

    private record Meta(int[] split, int[] pad) {}

    private final Path raiz;
    private final AssetManifest manifesto;
    private final Map<String, Meta> metas = new HashMap<>();
    private final Map<String, int[]> pontos = new HashMap<>();
    private final Map<String, Color> cores = new HashMap<>();
    private final Map<String, Drawable> pecas = new HashMap<>();
    private final Map<String, int[]> pads = new HashMap<>();
    private final List<Texture> texturas = new ArrayList<>();
    private final Map<String, Cursor> cursores = new HashMap<>();
    private final Set<String> avisados = new HashSet<>();

    public final Label.LabelStyle tinta, tintaSuave, ferrugem, tintaClara, desativado;
    /** Botão com os quatro estados (normal, sobre, pressionado, desativado). */
    public final TextButton.TextButtonStyle botao;
    /** Aba: normal e marcada ({@code checked}). */
    public final TextButton.TextButtonStyle aba;
    public final ScrollPane.ScrollPaneStyle rolagem;
    public final CheckBox.CheckBoxStyle caixa;
    public final Slider.SliderStyle deslizante;
    public final TextTooltip.TextTooltipStyle dica;
    public final BitmapFont fonte;

    /**
     * @param raiz      pasta {@code assets/}
     * @param manifesto o manifesto de assets (as entradas {@code ui.*}); pode ser null (tudo placeholder)
     * @param fonte     a fonte dos estilos (já na escala do Stage, {@link Fonts})
     */
    public Tema(Path raiz, AssetManifest manifesto, BitmapFont fonte) {
        this.raiz = raiz;
        this.manifesto = manifesto;
        this.fonte = fonte;
        lerConfig();
        Color c = cor("tinta");
        tinta = new Label.LabelStyle(fonte, c);
        tintaSuave = new Label.LabelStyle(fonte, cor("tinta_suave"));
        ferrugem = new Label.LabelStyle(fonte, cor("ferrugem"));
        tintaClara = new Label.LabelStyle(fonte, cor("tinta_clara"));
        desativado = new Label.LabelStyle(fonte, cor("desativado"));

        botao = new TextButton.TextButtonStyle(peca("ui.botao"), peca("ui.botao_press"), null, fonte);
        botao.over = peca("ui.botao_sobre");
        botao.disabled = peca("ui.botao_desativado");
        botao.fontColor = c;
        botao.overFontColor = c;
        botao.downFontColor = c;
        botao.disabledFontColor = cor("desativado");
        aba = new TextButton.TextButtonStyle(peca("ui.aba"), peca("ui.aba_ativa"), peca("ui.aba_ativa"), fonte);
        aba.fontColor = cor("tinta_suave");
        aba.checkedFontColor = c;
        aba.downFontColor = c;

        rolagem = new ScrollPane.ScrollPaneStyle();
        rolagem.vScroll = peca("ui.rolagem_trilho");
        rolagem.vScrollKnob = peca("ui.rolagem_pegador");

        caixa = new CheckBox.CheckBoxStyle(peca("ui.caixa"), peca("ui.caixa_marcada"), fonte, c);
        deslizante = new Slider.SliderStyle(peca("ui.deslizante_trilho"), peca("ui.deslizante_pino"));

        dica = new TextTooltip.TextTooltipStyle(new Label.LabelStyle(fonte, c), peca("ui.dica"));
    }

    // ---------------------------------------------------------------- configuração

    private void lerConfig() {
        cores.put("tinta", Color.valueOf("241C18"));
        cores.put("tinta_suave", Color.valueOf("5A4636"));
        cores.put("ferrugem", Color.valueOf("6E2C22"));
        cores.put("tinta_clara", Color.valueOf("EEE6D2"));
        cores.put("desativado", Color.valueOf("8A7C68"));
        Path arquivo = raiz.resolve("config/ui.yaml");
        if (!Files.exists(arquivo)) {
            LOG.warn("Tema: {} ausente; usando os padrões embutidos", arquivo);
            return;
        }
        try {
            JsonNode no = new ObjectMapper(new YAMLFactory()).readTree(arquivo.toFile());
            if (no == null) return;
            for (var it = no.path("pecas").fields(); it.hasNext(); ) {
                var e = it.next();
                JsonNode v = e.getValue();
                metas.put(e.getKey(), new Meta(inteiros(v.path("split"), 4), inteiros(v.path("pad"), 4)));
            }
            for (var it = no.path("cursores").fields(); it.hasNext(); ) {
                var e = it.next();
                int[] p = inteiros(e.getValue().path("ponto"), 2);
                pontos.put(e.getKey(), p == null ? new int[] {0, 0} : p);
            }
            for (var it = no.path("cores").fields(); it.hasNext(); ) {
                var e = it.next();
                try {
                    cores.put(e.getKey(), Color.valueOf(e.getValue().asText()));
                } catch (RuntimeException ex) {
                    LOG.warn("Tema: cor '{}' inválida ({})", e.getKey(), e.getValue().asText());
                }
            }
        } catch (Exception ex) {
            LOG.warn("Tema: {} não pôde ser lido ({}); usando os padrões embutidos", arquivo, ex.getMessage());
        }
    }

    private static int[] inteiros(JsonNode n, int quantos) {
        if (n == null || !n.isArray() || n.size() != quantos) return null;
        int[] r = new int[quantos];
        for (int i = 0; i < quantos; i++) r[i] = n.get(i).asInt();
        return r;
    }

    // ---------------------------------------------------------------- consulta

    /** Cor de tinta pelo nome ({@code tinta, tinta_suave, ferrugem, tinta_clara, desativado}); sempre uma cópia. */
    public Color cor(String nome) {
        Color c = cores.get(nome);
        return c == null ? new Color(Color.WHITE) : new Color(c);
    }

    /** A peça pelo ID ({@code ui.painel_madeira}); placeholder se faltar. Nunca null. */
    public Drawable peca(String id) {
        Drawable d = pecas.get(id);
        if (d == null) {
            d = montar(id);
            pecas.put(id, d);
        }
        return d;
    }

    /** Recuo do conteúdo da peça {@code {esquerda, direita, cima, baixo}}, em pixels de arte. */
    public int[] pad(String id) {
        peca(id);
        return pads.get(id).clone();
    }

    private void avisar(String id, String motivo) {
        if (avisados.add(id)) LOG.warn("Tema: peça '{}': {} — usando placeholder", id, motivo);
    }

    private Drawable montar(String id) {
        AssetManifest.Entry e = manifesto == null ? null : manifesto.get(id);
        Meta meta = metas.get(id);
        Path arquivo = e == null || e.file() == null ? null : raiz.resolve(e.file());
        String motivo = null;
        if (e == null) motivo = "fora do manifesto";
        else if (arquivo == null || !Files.exists(arquivo)) motivo = "arquivo ausente";
        else if (meta == null && !SIMPLES.contains(id)) motivo = "sem metadado em config/ui.yaml";
        if (motivo == null) {
            try {
                Texture t = new Texture(Gdx.files.absolute(arquivo.toString()));
                t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                texturas.add(t);
                return montarPeca(id, new TextureRegion(t), meta == null ? null : meta.split(), meta == null ? null : meta.pad(), null);
            } catch (RuntimeException ex) {
                motivo = "falha ao carregar: " + ex.getMessage();
            }
        }
        avisar(id, motivo);
        return placeholder(id);
    }

    private Drawable montarPeca(String id, TextureRegion regiao, int[] split, int[] pad, Padrao ph) {
        int[] p = pad != null ? pad : new int[] {0, 0, 0, 0};
        pads.put(id, p);
        BaseDrawable d;
        if (split != null) {
            NinePatch np = new NinePatch(regiao, split[0], split[1], split[2], split[3]);
            np.setPadding(p[0], p[1], p[2], p[3]);
            d = new NinePatchDrawable(np);
        } else {
            d = new TextureRegionDrawable(regiao);
        }
        if (ph != null) {
            if (ph.minL() > 0) d.setMinWidth(ph.minL());
            if (ph.minA() > 0) d.setMinHeight(ph.minA());
        }
        return d;
    }

    /** Placeholder de cor chapada: moldura de 1 a 3 pixels, miolo liso, recuo coerente. */
    private Drawable placeholder(String id) {
        Padrao ph = PADROES.getOrDefault(id, GENERICO);
        int b = Math.max(ph.b(), 1), lado = 2 * b + 2;
        Pixmap px = new Pixmap(lado, lado, Pixmap.Format.RGBA8888);
        px.setBlending(Pixmap.Blending.None);
        px.setColor(Color.valueOf(ph.borda()));
        px.fill();
        px.setColor(Color.valueOf(ph.fill()));
        px.fillRectangle(b, b, lado - 2 * b, lado - 2 * b);
        Texture t = new Texture(px);
        px.dispose();
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        texturas.add(t);
        int[] split = SIMPLES.contains(id) ? null : new int[] {b, b, b, b};
        int[] pad = {ph.pad(), ph.pad(), ph.pad(), ph.pad()};
        return montarPeca(id, new TextureRegion(t), split, pad, ph);
    }

    // ---------------------------------------------------------------- cursores

    /**
     * O cursor com a imagem do tema ({@code apontar, falar, entrar, pegar, atacar, esperar}), ampliado
     * por {@code k} sem filtro; null se a imagem (ou o sistema de janelas) não existir: quem chama
     * volta ao cursor padrão do sistema.
     */
    public Cursor cursor(String nome, int k) {
        String chave = nome + "@" + k;
        if (cursores.containsKey(chave)) return cursores.get(chave);
        Cursor c = null;
        String id = "ui.cursor_" + nome;
        AssetManifest.Entry e = manifesto == null ? null : manifesto.get(id);
        Path arquivo = e == null || e.file() == null ? null : raiz.resolve(e.file());
        if (arquivo != null && Files.exists(arquivo)) {
            try {
                Pixmap origem = new Pixmap(Gdx.files.absolute(arquivo.toString()));
                Pixmap grande = new Pixmap(origem.getWidth() * k, origem.getHeight() * k, Pixmap.Format.RGBA8888);
                grande.setFilter(Pixmap.Filter.NearestNeighbour);
                grande.drawPixmap(origem, 0, 0, origem.getWidth(), origem.getHeight(), 0, 0, grande.getWidth(), grande.getHeight());
                int[] p = pontos.getOrDefault(id, new int[] {0, 0});
                if (Gdx.graphics != null) c = Gdx.graphics.newCursor(grande, p[0] * k, p[1] * k);
                origem.dispose();
                grande.dispose();
            } catch (RuntimeException ex) {
                avisar(id, "cursor não carregou: " + ex.getMessage());
            }
        } else {
            avisar(id, e == null ? "fora do manifesto" : "arquivo ausente");
        }
        cursores.put(chave, c);
        return c;
    }

    @Override
    public void dispose() {
        for (Texture t : texturas) t.dispose();
        texturas.clear();
        for (Cursor c : cursores.values()) if (c != null) c.dispose();
        cursores.clear();
        pecas.clear();
    }
}
