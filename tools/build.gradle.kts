// Ferramentas de desenvolvimento em linha de comando.
dependencies {
    implementation(project(":core-sim"))
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

tasks.register<JavaExec>("generateRefMap") {
    group = "assets"
    description = "Gera o mapa da cena de referência (content/areas/vila_ref.tmj). Sobrescreve o arquivo."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("etrava.tools.maps.GenerateRefMap")
    args(rootProject.file("content/areas/vila_ref.tmj").absolutePath)
    jvmArgs("-Dstdout.encoding=UTF-8")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

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
