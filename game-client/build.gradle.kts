// Cliente do jogo: libGDX (LWJGL3). Só lê o estado da simulação e envia comandos.
plugins { application }

val gdxVersion = "1.14.2"
val mainClassName = "etrava.client.Lwjgl3Launcher"

dependencies {
    implementation(project(":core-sim"))
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("org.slf4j:slf4j-api:2.0.20")
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
    dependsOn(stagePackage)
    val jdkHome = javaToolchains.launcherFor(java.toolchain).get().metadata.installationPath.asFile
    val outDir = packageOut.get().asFile
    val jarName = tasks.jar.get().archiveFileName.get()
    doFirst { outDir.deleteRecursively() }
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
