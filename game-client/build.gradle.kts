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
