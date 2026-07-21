plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "9.0.0-beta6"
}

group = "io.github.ieshishinjin"
version = "1.0.0"

application {
    mainClass = "io.github.ieshishinjin.splice.SpliceCli"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    maven("https://files.minecraftforge.net/maven")
    maven("https://maven.fabricmc.net")
}

dependencies {
    // CLI parsing
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    // Bytecode manipulation (ASM)
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-commons:9.7.1")
    implementation("org.ow2.asm:asm-util:9.7.1")

    // HTTP client for downloading mappings
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.11.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.13")

    // Testing
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.withType<Jar> {
    manifest {
        attributes(
            "Main-Class" to application.mainClass,
            "Implementation-Title" to "Splice",
            "Implementation-Version" to project.version
        )
    }
}

// Native image support (GraalVM) for faster startup
tasks.register("generateGraalReflectConfig") {
    doLast {
        val outputDir = layout.buildDirectory.dir("graal").get().asFile
        outputDir.mkdirs()
        file("${outputDir}/reflect-config.json").writeText("""
[
  {
    "name" : "io.github.ieshishinjin.splice.SpliceCli",
    "allDeclaredFields" : true,
    "allDeclaredMethods" : true,
    "allDeclaredConstructors" : true
  },
  {
    "name" : "io.github.ieshishinjin.splice.model.Version",
    "allDeclaredFields" : true
  },
  {
    "name" : "io.github.ieshishinjin.splice.model.MigrationConfig",
    "allDeclaredFields" : true
  }
]
        """.trimIndent())
    }
}
