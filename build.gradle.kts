// ETRAVA — build raiz. Java 21 fixado por toolchain (D-001).
plugins { base }

allprojects {
    group = "etrava"
    version = "0.1.0"
}

subprojects {
    apply(plugin = "java")

    repositories { mavenCentral() }

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }

    tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.14.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("etrava.content", rootProject.file("content").absolutePath)
    }
}

// O validador de conteúdo faz parte do build (RF-442).
tasks.named("check") { dependsOn(":tools:validateContent") }
