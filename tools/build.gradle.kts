// Ferramentas de desenvolvimento em linha de comando.
dependencies {
    implementation(project(":core-sim"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.23.0")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.20")
}

tasks.register<JavaExec>("validateContent") {
    group = "verification"
    description = "Valida o conteúdo em content/. Falha quebra o build."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("etrava.tools.ValidateContent")
    args(rootProject.file("content").absolutePath, rootProject.file("assets").absolutePath)
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

tasks.register<JavaExec>("generateArt") {
    group = "assets"
    description = "Gera a arte provisória (feita por código) em assets/ e o manifesto dela."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("etrava.tools.art.GenerateArt")
    args(rootProject.file("assets").absolutePath)
    jvmArgs("-Dstdout.encoding=UTF-8", "-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

tasks.register<JavaExec>("generateEstrutura") {
    group = "assets"
    description = "Gera o mapa da estrutura enterrada (content/areas/estrutura.tmj). Sobrescreve o arquivo."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("etrava.tools.maps.GenerateEstrutura")
    args(rootProject.file("content/areas/estrutura.tmj").absolutePath)
    jvmArgs("-Dstdout.encoding=UTF-8")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

tasks.register<JavaExec>("generateRegion") {
    group = "assets"
    description = "Gera os mapas da região (vila, estrada e interiores) em content/areas. Sobrescreve os arquivos."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("etrava.tools.maps.GenerateRegion")
    args(rootProject.file("content/areas").absolutePath)
    jvmArgs("-Dstdout.encoding=UTF-8")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

tasks.register<JavaExec>("generateAudio") {
    group = "assets"
    description = "Gera o áudio provisório (música, ambiente e efeitos) em assets/audio e o manifesto. Sobrescreve."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("etrava.tools.audio.GenerateAudio")
    args(rootProject.file("assets").absolutePath)
    jvmArgs("-Dstdout.encoding=UTF-8", "-Xmx1g")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

// Converte os WAV recém-gerados em OGG Vorbis (RF-422). Pede Python com o módulo soundfile.
tasks.register<Exec>("encodeAudio") {
    group = "assets"
    description = "Converte assets/audio de WAV para OGG (python + soundfile). Roda depois de generateAudio."
    workingDir = rootDir
    commandLine(System.getenv("ETRAVA_PYTHON") ?: "python", "tools/scripts/wav2ogg.py", "assets/audio")
}
tasks.named("generateAudio") { finalizedBy("encodeAudio") }

tasks.register<JavaExec>("licenses") {
    group = "assets"
    description = "Gera ASSETS_LICENSES.md a partir dos manifestos de assets."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("etrava.tools.GenerateLicenses")
    args(rootProject.file("assets").absolutePath, rootProject.file("ASSETS_LICENSES.md").absolutePath)
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

// Usado pelo empacotamento: não deixa sair release com asset sem origem/licença (RF-426).
tasks.register<JavaExec>("licensesStrict") {
    group = "assets"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("etrava.tools.GenerateLicenses")
    args(rootProject.file("assets").absolutePath, rootProject.file("ASSETS_LICENSES.md").absolutePath, "--strict")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

tasks.register<JavaExec>("simulate") {
    group = "verification"
    description = "Simulação headless: gradlew simulate --args=\"--dias 30 --semente 42 --politica parado\""
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("etrava.tools.Simulate")
    workingDir = rootDir
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

tasks.register<JavaExec>("importIllustrations") {
    group = "assets"
    description = "Converte as ilustrações brutas de arte-bruta/ilustracoes (menu, mapa, epílogo) para a paleta do jogo."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("etrava.tools.art.ImportIllustrations")
    args(rootProject.file("arte-bruta/ilustracoes").absolutePath, rootProject.file("assets").absolutePath)
    jvmArgs("-Dstdout.encoding=UTF-8", "-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

tasks.register<JavaExec>("importPortraits") {
    group = "assets"
    description = "Converte os retratos brutos de arte-bruta/retratos para a paleta do jogo e atualiza o manifesto."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("etrava.tools.art.ImportPortraits")
    args(rootProject.file("arte-bruta/retratos").absolutePath, rootProject.file("assets").absolutePath)
    jvmArgs("-Dstdout.encoding=UTF-8", "-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

// ---- Fase V: pipeline de arte (SPEC-visual §2.3). O Blender fica fora do build do jogo.
fun artArgs(modo: String): List<String> {
    val a = mutableListOf(rootDir.absolutePath, modo)
    (project.findProperty("so") as String?)?.let { a.add("--so=$it") }
    if (project.hasProperty("force")) a.add("--force")
    if (project.hasProperty("rebase")) a.add("--rebase")
    (project.findProperty("processos") as String?)?.let { a.add("--processos=$it") }
    (project.findProperty("dispositivo") as String?)?.let { a.add("--dispositivo=$it") }
    return a
}

tasks.register<JavaExec>("renderArt") {
    group = "assets"
    description = "Renderiza no Blender o que mudou (cache por hash) e pixeliza. -Pso=avel,carvalho limita; -Pforce refaz; -Pprocessos=N limita os Blender simultâneos; -Pdispositivo=HIP usa a GPU (padrões em local.properties)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("etrava.tools.art.pixel.RenderArt")
    args(artArgs("render"))
    jvmArgs("-Dstdout.encoding=UTF-8", "-Djava.awt.headless=true", "-Xmx8g", "-XX:+UseG1GC", "-XX:G1PeriodicGCInterval=3000", "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=30")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

tasks.register<JavaExec>("pixelize") {
    group = "assets"
    description = "Só o pixelizador, sobre os passes já renderizados em build/render (itera no visual sem o Blender)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("etrava.tools.art.pixel.RenderArt")
    args(artArgs("pixelize"))
    jvmArgs("-Dstdout.encoding=UTF-8", "-Djava.awt.headless=true", "-Xmx8g", "-XX:+UseG1GC", "-XX:G1PeriodicGCInterval=3000", "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=30")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

tasks.register<JavaExec>("sheets") {
    group = "assets"
    description = "Folhas de contato e GIFs da arte nova em build/revisao (SPEC-visual §9.2)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("etrava.tools.art.pixel.Revisao")
    args(rootDir.absolutePath)
    jvmArgs("-Dstdout.encoding=UTF-8", "-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

tasks.register<Exec>("blenderTeste") {
    group = "verification"
    description = "Teste do pipeline: o cubo de 1 m renderizado cai exatamente no losango de um tile (precisa do Blender)."
    workingDir = rootDir
    val exe = System.getenv("BLENDER") ?: "C:/Program Files/Blender Foundation/Blender 5.3/blender.exe"
    doFirst {
        file("build/render").mkdirs()
        file("build/render/_teste_cubo.json").writeText("{\"tipo\":\"cubo\",\"id\":\"cubo\",\"saida\":\"build/render/_teste_cubo\"}")
    }
    commandLine(exe, "-b", "--factory-startup", "-noaudio", "-P", "tools/blender/entrada.py", "--", "build/render/_teste_cubo.json")
}

tasks.register<JavaExec>("importTextures") {
    group = "assets"
    description = "Funil de textura: arte-bruta/texturas (Dreamina) → tools/blender/texturas (luminância repetível)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("etrava.tools.art.pixel.ImportTextures")
    args(rootProject.file("arte-bruta/texturas").absolutePath, rootProject.file("tools/blender/texturas").absolutePath)
    jvmArgs("-Dstdout.encoding=UTF-8", "-Djava.awt.headless=true")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}
