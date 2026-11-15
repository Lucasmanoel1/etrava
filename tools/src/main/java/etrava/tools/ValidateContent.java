package etrava.tools;

import etrava.sim.validation.ContentValidator;
import java.nio.file.Path;

/** Linha de comando do validador de conteúdo: {@code gradlew validateContent}. */
public final class ValidateContent {
    public static void main(String[] args) {
        Path content = Path.of(args.length > 0 ? args[0] : "content");
        Path assets = Path.of(args.length > 1 ? args[1] : "assets");
        ContentValidator.Report report = ContentValidator.validate(content, assets);
        for (String w : report.warnings()) System.out.println("aviso: " + w);
        // arte da Fase V: paleta v3 (VT-04) e folhas de personagem (VT-05)
        java.util.List<String> arte = etrava.tools.art.pixel.ValidaArte.validar(assets,
                assets.toAbsolutePath().getParent().resolve("tools/art/paleta.yaml"));
        // VT-07: fichas das construções contra as pegadas e as portas dos mapas
        arte = new java.util.ArrayList<>(arte);
        arte.addAll(etrava.tools.art.pixel.ValidaConstrucoes.validar(content,
                assets.toAbsolutePath().getParent().resolve("tools/blender/construcoes")));
        if (!arte.isEmpty()) {
            System.err.println("Arte inválida (" + arte.size() + " problema(s)):");
            for (String p : arte) System.err.println("  - " + p);
            System.exit(1);
        }
        if (report.ok()) {
            System.out.println("Conteúdo válido (" + report.warnings().size() + " aviso(s)): " + content.toAbsolutePath());
            return;
        }
        System.err.println("Conteúdo inválido (" + report.errors().size() + " problema(s)):");
        for (String p : report.errors()) System.err.println("  - " + p);
        System.exit(1);
    }
}
