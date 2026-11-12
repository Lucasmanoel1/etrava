// Cliente do jogo: libGDX (LWJGL3). Só lê o estado da simulação e envia comandos.
plugins { application }

val gdxVersion = "1.14.2"
val mainClassName = "etrava.client.Lwjgl3Launcher"

dependencies {
    implementation(project(":core-sim"))
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
    runtimeOnly("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
    implementation("org.slf4j:slf4j-api:2.0.20")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.23.0")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.38")
}

application { mainClass.set(mainClassName) }

// Modo desenvolvimento: gradlew run
tasks.named<JavaExec>("run") {
    workingDir = rootDir
    jvmArgs(
        "-Detrava.home=${rootDir.absolutePath}", "-Detrava.dev=true",
        "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"
    )
}

// ---- Distribuição Windows: pasta com Etrava.exe + Java embutido (D-014) ----
val packageInput = layout.buildDirectory.dir("package/input")
val packageOut = layout.buildDirectory.dir("package/out")

val stagePackage = tasks.register<Sync>("stagePackage") {
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    from(rootProject.file("content")) { into("content") }
    from(rootProject.file("assets")) { into("assets") }
    into(packageInput)
}

tasks.register<Exec>("packageWin") {
    group = "distribution"
    description = "Gera build/package/out/Etrava com Etrava.exe e runtime embutido."
    dependsOn(stagePackage, ":tools:licensesStrict")
    val jdkHome = javaToolchains.launcherFor(java.toolchain).get().metadata.installationPath.asFile
    val outDir = packageOut.get().asFile
    val jarName = tasks.jar.get().archiveFileName.get()
    doFirst {
        // o runtime embutido tem arquivos somente leitura; sem isso o Windows não deixa apagar
        outDir.walkBottomUp().forEach { it.setWritable(true); it.delete() }
    }
    commandLine(
        File(jdkHome, "bin/jpackage").absolutePath,
        "--type", "app-image",
        "--name", "Etrava",
        "--app-version", project.version.toString(),
        "--input", packageInput.get().asFile.absolutePath,
        "--main-jar", jarName,
        "--main-class", mainClassName,
        "--dest", outDir.absolutePath,
        "--add-modules", "java.base,java.desktop,java.logging,java.management,java.naming,java.xml,jdk.unsupported",
        "--java-options", "-Detrava.home=\$APPDIR",
        "--java-options", "-Xmx1g"
    )
}

tasks.register<Zip>("distWin") {
    group = "distribution"
    description = "Compacta a pasta do jogo em build/package/Etrava-<versão>-win.zip."
    dependsOn("packageWin")
    from(packageOut)
    archiveFileName.set("Etrava-${project.version}-win.zip")
    destinationDirectory.set(layout.buildDirectory.dir("package"))
}

// ---- AT-16: o jogo roda sem 10 assets, com placeholders e avisos no log ----
// gradlew at16 [-Psemente=N]: copia content/ e assets/, apaga 10 arquivos de asset ao acaso,
// deixa o jogador automático jogar 60 s com a janela oculta e confere a saída e o log.
tasks.register<JavaExec>("at16") {
    group = "verification"
    description = "AT-16: roda o jogo sem 10 assets sorteados; falha se cair ou se o log não avisar."
    val home = layout.buildDirectory.dir("at16/home").get().asFile
    val user = layout.buildDirectory.dir("at16/user").get().asFile
    val seed = (findProperty("semente") as String?)?.toLong() ?: 16L
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(mainClassName)
    workingDir = rootDir
    jvmArgs("-Detrava.home=${home.absolutePath}", "-Detrava.userdir=${user.absolutePath}",
        "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    args("--hidden", "--bot=60", "--vel=3", "--seed=$seed")
    doFirst {
        home.deleteRecursively()
        user.deleteRecursively()
        rootProject.file("content").copyRecursively(File(home, "content"))
        rootProject.file("assets").copyRecursively(File(home, "assets"))
        File(user, "opcoes.json").apply { parentFile.mkdirs(); writeText("{\"volumeGeral\":0.0}") }
        val assetFiles: List<File> = File(home, "assets").walkTopDown()
            .filter { it.isFile && !it.path.contains("manifest") && it.extension != "txt" && it.extension != "yaml" }
            .sortedBy { it.path }.toList()
        val removed: List<File> = assetFiles.shuffled(kotlin.random.Random(seed)).take(10)
        for (f in removed) {
            println("AT-16: removido " + f.relativeTo(home))
            f.delete()
        }
    }
    doLast {
        val log = File(user, "logs/etrava.log").readText()
        val warnings = log.lines().count { it.contains("WARN") && (it.contains("placeholder") || it.contains("silêncio") || it.contains("fonte embutida")) }
        println("AT-16: jogo rodou e saiu normalmente; $warnings aviso(s) de asset ausente no log")
        check(log.contains("BOT terminou")) { "o jogo não chegou ao fim da sessão automática" }
        check(warnings > 0) { "nenhum aviso de asset ausente no log" }
    }
}

// ---- VT-01 (Fase V): o zoom nunca trava depois de abrir e fechar painéis ----
// gradlew vt01 [-Psemente=N] [-Psegundos=S]: o jogador automático abre, rola e fecha painéis
// (caderno, mochila, ficha, mapa, conversa, comércio/contêiner, pausa, console) e, a cada
// fechamento, gira a roda pelo processador de entrada real e confere que a escala mudou.
tasks.register<JavaExec>("vt01") {
    group = "verification"
    description = "VT-01: zoom responde depois de cada painel fechado (jogador automático, janela oculta)."
    val user = layout.buildDirectory.dir("vt01/user").get().asFile
    val seed = (findProperty("semente") as String?)?.toLong() ?: 1L
    val seconds = (findProperty("segundos") as String?)?.toInt() ?: 150
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(mainClassName)
    workingDir = rootDir
    jvmArgs("-Detrava.home=${rootDir.absolutePath}", "-Detrava.userdir=${user.absolutePath}",
        "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    args("--hidden", "--debug", "--paineis", "--bot=$seconds", "--seed=$seed")
    doFirst {
        user.deleteRecursively()
        File(user, "opcoes.json").apply { parentFile.mkdirs(); writeText("{\"volumeGeral\":0.0}") }
    }
    doLast {
        val log = File(user, "logs/etrava.log").readText()
        val m = Regex("""VT-01 zoom: (\d+) verificações, (\d+) falhas""").find(log)
            ?: throw GradleException("o jogo não chegou ao fim da sessão automática")
        val (checks, fails) = m.destructured
        println("VT-01: $checks verificações de zoom depois de fechar um painel, $fails falha(s)")
        check(checks.toInt() >= 200) { "poucas verificações ($checks); a sessão foi curta demais" }
        check(fails.toInt() == 0) { "o zoom não respondeu $fails vez(es) — ver WARN VT-01 no log" }
    }
}

// ---- Capturas (Fase V, SPEC-visual §9.1) ----
// gradlew captures -Proteiro=v0 [-Psaida=docs/capturas/v0]: roda tools/capturas/<roteiro>.yaml com a
// janela oculta e grava as PNGs, relatorio.csv e contato.png (padrão: build/capturas/<roteiro>).
fun captureTask(name: String, script: String, outDir: File, check: Boolean) = tasks.register<JavaExec>(name) {
    group = "verification"
    val user = layout.buildDirectory.dir("capturas-user/$name").get().asFile
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(mainClassName)
    workingDir = rootDir
    jvmArgs("-Detrava.home=${rootDir.absolutePath}", "-Detrava.userdir=${user.absolutePath}",
        "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    args("--hidden", "--roteiro=" + rootProject.file("tools/capturas/$script.yaml").absolutePath,
        "--saida=" + outDir.absolutePath)
    doFirst {
        user.deleteRecursively()
        File(user, "opcoes.json").apply { parentFile.mkdirs(); writeText("{\"volumeGeral\":0.0}") }
    }
    doLast {
        val log = File(user, "logs/etrava.log").readText()
        val m = Regex("""CAPTURAS terminou: (\d+) cenas, (\d+) salvas, visibilidade: (\d+) medidas, (\d+) falhas""").find(log)
            ?: throw GradleException("o roteiro não chegou ao fim — ver $user/logs/etrava.log")
        val (scenes, saved, measured, fails) = m.destructured
        println("Capturas: $scenes cenas, $saved salvas em $outDir; visibilidade: $measured medidas, $fails falha(s)")
        if (check) {
            check(measured.toInt() > 0) { "nenhuma medida de visibilidade" }
            check(fails.toInt() == 0) { "VT-03: $fails medida(s) abaixo do mínimo — ver $outDir/relatorio.csv" }
        }
    }
}

val roteiro = (findProperty("roteiro") as String?) ?: "v0"
captureTask("captures", roteiro,
    (findProperty("saida") as String?)?.let { rootProject.file(it) } ?: layout.buildDirectory.dir("capturas/$roteiro").get().asFile,
    false).configure { description = "Roda um roteiro de capturas (tools/capturas/<roteiro>.yaml)." }
captureTask("vt03", "visibilidade", layout.buildDirectory.dir("capturas/visibilidade").get().asFile, true)
    .configure { description = "VT-03: o jogador se destaca do chão em todas as horas, zooms e áreas abertas." }

// Testes do cliente sem janela: backend headless do libGDX (V0).
dependencies {
    testImplementation("com.badlogicgames.gdx:gdx-backend-headless:$gdxVersion")
    testRuntimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
}
