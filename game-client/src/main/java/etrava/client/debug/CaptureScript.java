package etrava.client.debug;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.scenes.scene2d.Stage;
import etrava.client.ui.LimitesUi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Roteiro de capturas (V0, SPEC-visual §9.1): {@code --roteiro=arquivo.yaml --saida=pasta}.
 * Cada cena começa de um jogo novo (área e semente), roda comandos de console, espera alguns
 * quadros e salva a tela. Cenas com {@code medir: true} medem também a visibilidade do jogador
 * (VT-03): luminância média dele × a de uma moldura de chão em volta, na imagem final.
 * Saída: as PNGs, {@code relatorio.csv}, {@code relatorio_limites.csv} (VT-09: a checagem de limites da
 * interface em cada cena salva) e {@code contato.png}; no fim, o jogo fecha.
 *
 * <p>Uma cena pode ser uma matriz: {@code tamanhos: [[1280,720],[1920,1080]]} e/ou {@code textos: [1, 1.25, 1.5]}
 * viram uma captura por combinação ({@code nome_1280x720_t125}). {@code tela:} (padrão: o nome) é o que
 * {@code -Detrava.capturas.so=tela1,tela2} usa para rodar só algumas telas.
 */
public final class CaptureScript {
    private static final Logger LOG = LoggerFactory.getLogger(CaptureScript.class);

    /** O que o roteiro pede ao jogo. */
    public interface Host {
        void newGame(long seed, String area);
        String console(String command);
        void windowSize(int w, int h);
        void textScale(float scale);
        /** Pede a máscara do jogador no próximo quadro. */
        void requestPlayerMask();
        /** A máscara pedida (linhas de baixo para cima), ou null. */
        Pixmap playerMask();
        /** A tela do quadro atual, já com tudo desenhado (linhas de baixo para cima). */
        Pixmap grabScreen();
        /** Os Stages de interface que estão na tela agora (nome → Stage), para a checagem de limites. */
        default Map<String, Stage> stages() { return Map.of(); }
        int pixelScale();
        String clock();
        void exit();
    }

    /** {@code fresh}: começa um jogo novo antes desta cena. */
    record Shot(String name, String area, long seed, boolean fresh, List<String> commands, int w, int h, float text,
            int frames, boolean save, boolean measure) {}

    /** Variável de sistema {@code etrava.capturas.so}: telas (campo {@code tela}) a rodar; vazia = todas. */
    private static final String SO = "etrava.capturas.so";

    /**
     * Regra do VT-03 (D-090): pelo menos {@code MIN_STANDOUT} dos pixels do jogador diferem da
     * luminância média do chão em volta por {@code MIN_DIFF} ou mais — para cima (camisa clara) ou
     * para baixo (contorno, calça escura). A média sozinha engana: claro e escuro se anulam.
     */
    public static final double MIN_DIFF = 0.12, MIN_STANDOUT = 0.60;

    private final List<Shot> shots;
    private final Path out;
    private final Host host;
    private final List<String> report = new ArrayList<>();
    private final List<String> saved = new ArrayList<>();
    private final List<String> limitsReport = new ArrayList<>();
    private int index = -1, frame, failures, measured, limitScenes, limitProblems;

    private CaptureScript(List<Shot> shots, Path out, Host host) {
        this.shots = shots;
        this.out = out;
        this.host = host;
        report.add("cena;area;relogio;zoom;tamanho;jogador;entorno;destaque;ok");
        limitsReport.add("cena;resolucao;texto;stage;problema;ator;texto_do_label;medidas");
    }

    public static CaptureScript load(Path script, Path out, Host host) {
        try {
            JsonNode root = new ObjectMapper(new YAMLFactory()).readTree(script.toFile());
            JsonNode def = root.path("padrao");
            long seed = def.path("semente").asLong(5);
            int w = def.path("tamanho").path(0).asInt(1280), h = def.path("tamanho").path(1).asInt(720);
            float text = (float) def.path("texto").asDouble(1);
            int frames = def.path("quadros").asInt(45);
            List<Shot> shots = new ArrayList<>();
            Set<String> only = Set.of(System.getProperty(SO, "").split(","));
            for (JsonNode c : root.path("cenas")) {
                String nome = c.path("nome").asText();
                if (!System.getProperty(SO, "").isBlank() && !only.contains(c.path("tela").asText(nome))) continue;
                // matriz: tamanhos × textos (cada lista é opcional; sem nenhuma, é uma cena só)
                List<int[]> sizes = new ArrayList<>();
                for (JsonNode t : c.path("tamanhos")) sizes.add(new int[] {t.path(0).asInt(w), t.path(1).asInt(h)});
                boolean matrixSizes = !sizes.isEmpty(), matrixTexts = c.path("textos").size() > 0;
                if (!matrixSizes) sizes.add(new int[] {c.path("tamanho").path(0).asInt(w), c.path("tamanho").path(1).asInt(h)});
                List<Float> texts = new ArrayList<>();
                for (JsonNode t : c.path("textos")) texts.add((float) t.asDouble(1));
                if (!matrixTexts) texts.add((float) c.path("texto").asDouble(text));
                for (float tx : texts) {
                    for (int[] sz : sizes) {
                        String name = nome + (matrixSizes ? "_" + sz[0] + "x" + sz[1] : "")
                                + (matrixTexts ? "_t" + Math.round(tx * 100) : "");
                        shots.add(new Shot(name, c.path("area").asText("vila"), c.path("semente").asLong(seed), true,
                                strings(c.path("cmds")), sz[0], sz[1], tx, c.path("quadros").asInt(frames),
                                c.path("salvar").asBoolean(true), c.path("medir").asBoolean(false)));
                    }
                }
            }
            // grade: área × horas × zooms; um jogo novo por hora, os zooms em seguida no mesmo jogo
            for (JsonNode g : root.path("grades")) {
                String area = g.path("area").asText("vila");
                for (JsonNode hour : g.path("horas")) {
                    boolean first = true;
                    for (JsonNode zoom : g.path("zooms")) {
                        List<String> cmds = new ArrayList<>();
                        if (first) {
                            cmds.addAll(strings(g.path("cmds")));
                            cmds.add("hora " + hour.asText());
                        }
                        cmds.add("zoom " + zoom.asInt());
                        String name = g.path("prefixo").asText("grade_" + area) + "_h" + hour.asText().replace('.', '_')
                                + "_z" + zoom.asInt();
                        shots.add(new Shot(name, area, g.path("semente").asLong(seed), first, cmds, w, h, text,
                                first ? g.path("quadros").asInt(frames) : 8, g.path("salvar").asBoolean(false), true));
                        first = false;
                    }
                }
            }
            Files.createDirectories(out);
            LOG.info("Roteiro de capturas: {} cenas, saída em {}", shots.size(), out);
            return new CaptureScript(shots, out, host);
        } catch (IOException e) {
            throw new UncheckedIOException("roteiro de capturas ilegível: " + script, e);
        }
    }

    private static List<String> strings(JsonNode n) {
        List<String> out = new ArrayList<>();
        for (JsonNode s : n) out.add(s.asText());
        return out;
    }

    /** Chamado no fim de cada quadro, depois de tudo desenhado. */
    public void afterFrame() {
        if (index >= shots.size()) return;
        if (index < 0 || frame >= shots.get(index).frames()) {
            if (index >= 0) capture(shots.get(index));
            index++;
            frame = 0;
            if (index >= shots.size()) {
                finish();
                return;
            }
            Shot s = shots.get(index);
            host.windowSize(s.w(), s.h());
            host.textScale(s.text());
            if (s.fresh()) host.newGame(s.seed(), s.area());
            return;
        }
        frame++;
        // comandos dois quadros depois: o tamanho de janela e de texto novos refazem a interface no
        // quadro seguinte, e um painel aberto antes disso se perderia
        if (frame == 2) for (String c : shots.get(index).commands()) host.console(c);
        if (shots.get(index).measure() && frame == shots.get(index).frames()) host.requestPlayerMask();
    }

    private void capture(Shot s) {
        if (s.save()) checkLimits(s);
        Pixmap screen = host.grabScreen();
        try {
            String metric = s.measure() ? measure(s, screen) : "";
            if (s.save()) {
                Path file = out.resolve(s.name() + ".png");
                ImageIO.write(toImage(screen), "png", file.toFile());
                saved.add(s.name());
            }
            if (!metric.isEmpty()) report.add(metric);
            LOG.info("Captura {} ({}){}", s.name(), host.clock(), metric.isEmpty() ? "" : " — " + metric);
        } catch (IOException e) {
            LOG.warn("Captura {} não salva: {}", s.name(), e.getMessage());
        } finally {
            screen.dispose();
        }
    }

    /** VT-09: roda a checagem de limites nos Stages visíveis e acumula o relatório. */
    private void checkLimits(Shot s) {
        int found = 0;
        for (Map.Entry<String, Stage> e : host.stages().entrySet()) {
            for (LimitesUi.Problema p : LimitesUi.verificar(e.getKey(), e.getValue())) {
                found++;
                limitsReport.add(String.join(";", s.name(), s.w() + "x" + s.h(), String.format(Locale.ROOT, "%.2f", s.text()),
                        e.getKey(), p.tipo(), csv(p.ator()), csv(p.texto()), csv(p.medidas())));
                LOG.warn("Limites {}: {}", s.name(), p.legivel());
            }
        }
        limitScenes++;
        limitProblems += found;
    }

    private static String csv(String text) {
        return text.replace(';', ',').replace('\n', ' ');
    }

    /** Luminância média do jogador × a de uma moldura de 12 px de arte em volta dele (VT-03). */
    private String measure(Shot s, Pixmap screen) {
        Pixmap mask = host.playerMask();
        int zoom = host.pixelScale();
        String head = s.name() + ";" + s.area() + ";" + host.clock() + ";" + zoom + ";" + s.w() + "x" + s.h() + ";";
        if (mask == null || mask.getWidth() != screen.getWidth() || mask.getHeight() != screen.getHeight()) {
            if (mask != null) mask.dispose();
            return head + ";;;sem-mascara";
        }
        int w = screen.getWidth(), h = screen.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1, count = 0;
        double sumP = 0;
        boolean[] in = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((mask.getPixel(x, y) >>> 24) & 0xFF) < 128) continue; // canal vermelho da máscara
                in[y * w + x] = true;
                sumP += luma(screen.getPixel(x, y));
                count++;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        measured++;
        if (count < 20) {
            mask.dispose();
            return head + ";;;oculto";
        }
        int r = 12 * zoom;
        double sumR = 0;
        int ring = 0;
        for (int y = Math.max(0, minY - r); y <= Math.min(h - 1, maxY + r); y++) {
            for (int x = Math.max(0, minX - r); x <= Math.min(w - 1, maxX + r); x++) {
                if (in[y * w + x]) continue;
                sumR += luma(screen.getPixel(x, y));
                ring++;
            }
        }
        mask.dispose();
        double lp = sumP / count, lr = ring == 0 ? 0 : sumR / ring;
        int standing = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (in[y * w + x] && Math.abs(luma(screen.getPixel(x, y)) - lr) >= MIN_DIFF) standing++;
            }
        }
        double standout = standing / (double) count;
        boolean ok = standout >= MIN_STANDOUT;
        if (!ok) failures++;
        return head + String.format(Locale.ROOT, "%.3f;%.3f;%.2f;%s", lp, lr, standout, ok ? "ok" : "FALHA");
    }

    /** Luminância relativa (Rec. 709) de um pixel RGBA8888, de 0 a 1. */
    static double luma(int rgba) {
        double r = ((rgba >>> 24) & 0xFF) / 255.0, g = ((rgba >>> 16) & 0xFF) / 255.0, b = ((rgba >>> 8) & 0xFF) / 255.0;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static BufferedImage toImage(Pixmap p) {
        int w = p.getWidth(), h = p.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) img.setRGB(x, h - 1 - y, p.getPixel(x, y) >>> 8); // de baixo para cima → PNG
        }
        return img;
    }

    private void finish() {
        try {
            Files.write(out.resolve("relatorio.csv"), report, StandardCharsets.UTF_8);
            Files.write(out.resolve("relatorio_limites.csv"), limitsReport, StandardCharsets.UTF_8);
            if (!saved.isEmpty()) contactSheet();
            String summary = String.format(Locale.ROOT, "CAPTURAS terminou: %d cenas, %d salvas, visibilidade: %d medidas, %d falhas",
                    shots.size(), saved.size(), measured, failures);
            LOG.info(summary);
            System.out.println(summary);
            String limits = String.format(Locale.ROOT, "LIMITES terminou: %d cenas, %d problemas", limitScenes, limitProblems);
            LOG.info(limits);
            System.out.println(limits);
        } catch (IOException e) {
            LOG.warn("Relatório de capturas não salvo: {}", e.getMessage());
        }
        host.exit();
    }

    /** Todas as capturas salvas numa folha, três por linha, com o nome embaixo. */
    private void contactSheet() throws IOException {
        int cols = Math.min(3, saved.size()), tw = 480, th = 270, label = 22;
        int rows = (saved.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * tw, rows * (th + label), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x1E1A22));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        for (int i = 0; i < saved.size(); i++) {
            BufferedImage img = ImageIO.read(out.resolve(saved.get(i) + ".png").toFile());
            int x = (i % cols) * tw, y = (i / cols) * (th + label);
            double scale = Math.min(tw / (double) img.getWidth(), th / (double) img.getHeight());
            int dw = (int) (img.getWidth() * scale), dh = (int) (img.getHeight() * scale);
            g.drawImage(img, x + (tw - dw) / 2, y + (th - dh) / 2, dw, dh, null);
            g.setColor(new Color(0xD8CCB0));
            g.drawString(saved.get(i), x + 6, y + th + 16);
        }
        g.dispose();
        ImageIO.write(sheet, "png", out.resolve("contato.png").toFile());
    }
}
