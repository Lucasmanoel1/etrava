// Núcleo de simulação: Java puro, sem libGDX (RNF-010).
plugins {
    `java-library`
    jacoco
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.23.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.23.0")
    implementation("org.slf4j:slf4j-api:2.0.20")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.20")
}

tasks.test { finalizedBy(tasks.jacocoTestReport) }

// Cobertura mínima de 70% no core-sim (RNF-030).
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules { rule { limit { minimum = "0.70".toBigDecimal() } } }
}
tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
