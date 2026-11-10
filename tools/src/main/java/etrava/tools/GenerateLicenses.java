package etrava.tools;

import etrava.sim.content.AssetManifest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Gera {@code ASSETS_LICENSES.md} a partir dos manifestos (RF-426): {@code gradlew licenses}.
 * Com {@code --strict}, falha se algum asset com arquivo não tiver origem e licença —
 * é a verificação usada antes de um release.
 */
public final class GenerateLicenses {
    public static void main(String[] args) throws IOException {
        Path assets = Path.of(args.length > 0 ? args[0] : "assets");
        Path out = Path.of(args.length > 1 ? args[1] : "ASSETS_LICENSES.md");
        boolean strict = List.of(args).contains("--strict");
        AssetManifest manifest = AssetManifest.load(assets);

        StringBuilder md = new StringBuilder();
        md.append("# Origem e licença dos assets\n\n")
                .append("Gerado por `gradlew licenses` a partir de `assets/manifest/*.yaml`. Não edite à mão.\n\n")
                .append("| ID | Tipo | Arquivo(s) | Origem | Licença | Situação |\n|---|---|---|---|---|---|\n");
        List<String> missingCredits = new ArrayList<>();
        for (AssetManifest.Entry e : manifest.entries().values()) {
            boolean hasFile = e.allFiles().stream().anyMatch(f -> Files.exists(assets.resolve(f)));
            boolean credited = e.source() != null && !e.source().isBlank() && e.license() != null && !e.license().isBlank();
            if (hasFile && !credited) missingCredits.add(e.id());
            md.append("| `").append(e.id()).append("` | ").append(e.type()).append(" | ")
                    .append(String.join("<br>", e.allFiles())).append(" | ")
                    .append(e.source() == null ? "—" : e.source()).append(" | ")
                    .append(e.license() == null ? "—" : e.license()).append(" | ")
                    .append(!hasFile ? "sem arquivo (placeholder)" : credited ? "ok" : "**falta origem/licença**")
                    .append(" |\n");
        }
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        System.out.println("Gerado " + out.toAbsolutePath() + " (" + manifest.entries().size() + " assets)");
        if (strict && !missingCredits.isEmpty()) {
            System.err.println("Assets sem origem/licença: " + missingCredits);
            System.exit(1);
        }
    }
}
