package etrava.tools.art.pixel;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/**
 * Folhas de contato e GIFs da arte nova (SPEC-visual §9.2), em {@code build/revisao/}, fora do
 * git: por folha, todas as animações × direções ampliadas, sobre o tom médio do chão (é contra
 * ele que o sprite precisa ler), e um GIF por animação com as 8 direções lado a lado.
 */
public final class Revisao {
    private static final int ZOOM = 4;
    private static final Color FUNDO = new Color(0x5A6438), FUNDO2 = new Color(0x7A5632);

    public static void main(String[] args) throws IOException {
        Path raiz = Path.of(args[0]).toAbsolutePath();
        Path assets = raiz.resolve("assets"), saida = raiz.resolve("build/revisao");
        Files.createDirectories(saida);
        Path man = assets.resolve("manifest/arte.yaml");
        if (!Files.exists(man)) {
            System.out.println("Sem assets/manifest/arte.yaml: rode renderArt antes.");
            return;
        }
        int n = 0;
        for (JsonNode e : RenderArt.YAML.readTree(man.toFile())) {
            List<String> files = new ArrayList<>();
            if (e.has("variants")) e.get("variants").forEach(v -> files.add(v.asText()));
            else files.add(e.path("file").asText());
            for (String f : files) {
                Path meta = assets.resolve(f.replace(".png", ".json"));
                BufferedImage img = ImageIO.read(assets.resolve(f).toFile());
                if (img == null || !Files.exists(meta)) continue;
                JsonNode m = RenderArt.JSON.readTree(meta.toFile());
                String nome = Path.of(f).getFileName().toString().replace(".png", "");
                folha(img, m, saida.resolve(nome + "_contato.png"));
                gifs(img, m, saida, nome);
                n++;
            }
        }
        System.out.println("Revisão: " + n + " folha(s) em build/revisao");
        quemEQuem(raiz, man, saida.resolve("quem_e_quem.png"));
    }

    /**
     * Prancha "quem é quem" (V2, tarefa 9): todos os humanos lado a lado, com os pés na mesma
     * linha. Duas faixas: no tamanho do zoom 1 do jogo (cada px de arte = 2 px), para julgar a
     * silhueta como o jogador a vê, e ampliada 4×. Em cada uma, de frente para a câmera (direção
     * 1) e de perfil (direção 0), com o nome de `content/npcs` embaixo. Camadas ficam de fora.
     */
    static void quemEQuem(Path raiz, Path man, Path destino) throws IOException {
        record Gente(String nome, BufferedImage img, int fw, int fh, double ax, double ay, int parado) {}
        List<Gente> gente = new ArrayList<>();
        for (JsonNode e : RenderArt.YAML.readTree(man.toFile())) {
            String id = e.path("id").asText();
            if (!id.startsWith("personagem.") || "camada".equals(e.path("role").asText())) continue;
            Path f = raiz.resolve("assets").resolve(e.path("file").asText());
            BufferedImage img = Files.exists(f) ? ImageIO.read(f.toFile()) : null;
            if (img == null || !e.path("animations").has("parado")) continue;
            String curto = id.substring("personagem.".length());
            String nome = curto;
            Path npc = raiz.resolve("content/npcs/" + curto + ".yaml");
            if (Files.exists(npc)) nome = RenderArt.YAML.readTree(npc.toFile()).path("name").asText(curto);
            gente.add(new Gente(nome, img, e.path("frame").get(0).asInt(), e.path("frame").get(1).asInt(),
                    e.path("anchor").get(0).asDouble(), e.path("anchor").get(1).asDouble(),
                    e.path("animations").path("parado").path("start").asInt()));
        }
        if (gente.isEmpty()) return;
        int maxCima = 0, maxBaixo = 0;
        for (Gente g : gente) {
            maxCima = Math.max(maxCima, (int) Math.ceil(g.ay()));
            maxBaixo = Math.max(maxBaixo, g.fh() - (int) Math.floor(g.ay()));
        }
        int coluna = 240, rotulo = 14;                    // px por pessoa; altura do nome
        int[] zooms = {2, 4};
        int largura = gente.size() * coluna + 16, altura = 8;
        for (int z : zooms) altura += (maxCima + maxBaixo) * z + rotulo + 8;
        BufferedImage out = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        g2.setColor(FUNDO);
        g2.fillRect(0, 0, largura, altura);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int y0 = 8;
        for (int z : zooms) {
            int base = y0 + maxCima * z;                  // linha dos pés
            g2.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, z == 2 ? 11 : 13));
            for (int i = 0; i < gente.size(); i++) {
                Gente g = gente.get(i);
                int colW = (largura - 16) / gente.size();   // a coluna de cada um (cabe a 4×)
                int cx = 8 + i * colW + colW / 2;
                for (int lado = 0; lado < 2; lado++) {
                    int dir = lado == 0 ? 1 : 0;
                    BufferedImage q = g.img().getSubimage(g.parado() * g.fw(), dir * g.fh(), g.fw(), g.fh());
                    int x = cx + (lado == 0 ? -1 : 1) * colW / 4 - (int) Math.round(g.ax() * z);
                    g2.drawImage(q, x, base - (int) Math.round(g.ay() * z), g.fw() * z, g.fh() * z, null);
                }
                g2.setColor(new Color(0xE8E0C8));
                int tw = g2.getFontMetrics().stringWidth(g.nome());
                g2.drawString(g.nome(), cx - tw / 2, base + maxBaixo * z + rotulo - 3);
            }
            y0 += (maxCima + maxBaixo) * z + rotulo + 8;
        }
        g2.dispose();
        ImageIO.write(out, "png", destino.toFile());
        System.out.println("Quem é quem: " + gente.size() + " pessoa(s) em " + raiz.relativize(destino));
    }

    static void folha(BufferedImage img, JsonNode m, Path destino) throws IOException {
        int fw = m.path("frame").get(0).asInt(), fh = m.path("frame").get(1).asInt();
        int dirs = m.path("directions").asInt(1), cols = img.getWidth() / fw;
        int gap = 2;
        BufferedImage out = new BufferedImage((cols * (fw + gap)) * ZOOM, dirs * (fh + gap) * ZOOM, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        // faixas de fundo alternadas por animação: grama e terra
        int a = 0;
        for (Iterator<Map.Entry<String, JsonNode>> it = m.path("animations").fields(); it.hasNext(); a++) {
            JsonNode an = it.next().getValue();
            g.setColor(a % 2 == 0 ? FUNDO : FUNDO2);
            g.fillRect(an.path("start").asInt() * (fw + gap) * ZOOM, 0, an.path("frames").asInt() * (fw + gap) * ZOOM, out.getHeight());
        }
        for (int d = 0; d < dirs; d++)
            for (int c = 0; c < cols; c++)
                g.drawImage(img.getSubimage(c * fw, d * fh, fw, fh), c * (fw + gap) * ZOOM, d * (fh + gap) * ZOOM,
                        fw * ZOOM, fh * ZOOM, null);
        g.dispose();
        ImageIO.write(out, "png", destino.toFile());
    }

    static void gifs(BufferedImage img, JsonNode m, Path saida, String nome) throws IOException {
        int fw = m.path("frame").get(0).asInt(), fh = m.path("frame").get(1).asInt();
        int dirs = m.path("directions").asInt(1);
        for (Iterator<Map.Entry<String, JsonNode>> it = m.path("animations").fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> e = it.next();
            int start = e.getValue().path("start").asInt(), frames = e.getValue().path("frames").asInt();
            if (frames <= 1) continue;
            int delay = (int) Math.round(100.0 / e.getValue().path("fps").asDouble(8));
            List<BufferedImage> qs = new ArrayList<>();
            for (int f = 0; f < frames; f++) {
                BufferedImage q = new BufferedImage(dirs * fw * ZOOM, fh * ZOOM, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = q.createGraphics();
                g.setColor(FUNDO);
                g.fillRect(0, 0, q.getWidth(), q.getHeight());
                for (int d = 0; d < dirs; d++)
                    g.drawImage(img.getSubimage((start + f) * fw, d * fh, fw, fh), d * fw * ZOOM, 0, fw * ZOOM, fh * ZOOM, null);
                g.dispose();
                qs.add(q);
            }
            escreverGif(qs, delay, saida.resolve(nome + "_" + e.getKey() + ".gif"));
        }
    }

    /** GIF animado em laço, pelo escritor do próprio JDK. */
    static void escreverGif(List<BufferedImage> quadros, int delayCentesimos, Path destino) throws IOException {
        ImageWriter w = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(destino.toFile())) {
            w.setOutput(out);
            w.prepareWriteSequence(null);
            for (BufferedImage q : quadros) {
                ImageTypeSpecifier tipo = ImageTypeSpecifier.createFromRenderedImage(q);
                IIOMetadata meta = w.getDefaultImageMetadata(tipo, null);
                String f = meta.getNativeMetadataFormatName();
                IIOMetadataNode raiz = (IIOMetadataNode) meta.getAsTree(f);
                IIOMetadataNode gce = filho(raiz, "GraphicControlExtension");
                gce.setAttribute("disposalMethod", "none");
                gce.setAttribute("userInputFlag", "FALSE");
                gce.setAttribute("transparentColorFlag", "FALSE");
                gce.setAttribute("delayTime", Integer.toString(delayCentesimos));
                gce.setAttribute("transparentColorIndex", "0");
                IIOMetadataNode apps = filho(raiz, "ApplicationExtensions");
                IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
                app.setAttribute("applicationID", "NETSCAPE");
                app.setAttribute("authenticationCode", "2.0");
                app.setUserObject(new byte[] {1, 0, 0});
                apps.appendChild(app);
                meta.setFromTree(f, raiz);
                w.writeToSequence(new IIOImage(q, null, meta), null);
            }
            w.endWriteSequence();
        } finally {
            w.dispose();
        }
    }

    private static IIOMetadataNode filho(IIOMetadataNode raiz, String nome) {
        for (int i = 0; i < raiz.getLength(); i++)
            if (raiz.item(i).getNodeName().equalsIgnoreCase(nome)) return (IIOMetadataNode) raiz.item(i);
        IIOMetadataNode n = new IIOMetadataNode(nome);
        raiz.appendChild(n);
        return n;
    }
}
