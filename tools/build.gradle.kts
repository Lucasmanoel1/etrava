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
    args(rootProject.file("content").absolutePath)
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}
