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
        if (report.ok()) {
            System.out.println("Conteúdo válido (" + report.warnings().size() + " aviso(s)): " + content.toAbsolutePath());
            return;
        }
        System.err.println("Conteúdo inválido (" + report.errors().size() + " problema(s)):");
        for (String p : report.errors()) System.err.println("  - " + p);
        System.exit(1);
    }
}
