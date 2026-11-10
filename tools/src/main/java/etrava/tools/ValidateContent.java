package etrava.tools;

import etrava.sim.validation.ContentValidator;
import java.nio.file.Path;
import java.util.List;

/** Linha de comando do validador de conteúdo: {@code gradlew validateContent}. */
public final class ValidateContent {
    public static void main(String[] args) {
        Path dir = Path.of(args.length > 0 ? args[0] : "content");
        List<String> problems = ContentValidator.validate(dir);
        if (problems.isEmpty()) {
            System.out.println("Conteúdo válido: " + dir.toAbsolutePath());
            return;
        }
        System.err.println("Conteúdo inválido (" + problems.size() + " problema(s)):");
        for (String p : problems) System.err.println("  - " + p);
        System.exit(1);
    }
}
