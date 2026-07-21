plugins {
    `java-gradle-plugin`
    id("com.gradleup.shadow") version "9.0.0-beta6"
}

group = "io.github.ieshishinjin.splice"
version = "1.0.0"

repositories {
    mavenCentral()
}

gradlePlugin {
    website.set("https://github.com/ieshishinjin/splice")
    vcsUrl.set("https://github.com/ieshishinjin/splice")
    plugins {
        register("splice-migration") {
            id = "io.github.ieshishinjin.splice"
            implementationClass = "io.github.ieshishinjin.splice.gradle.SplicePlugin"
            displayName = "Splice Minecraft Mod Migration Plugin"
            description = "Gradle plugin for Minecraft Mod cross-version migration"
            tags.set(listOf("minecraft", "migration", "forge", "fabric"))
        }
    }
}

dependencies {
    // Depend on the core Splice module
    implementation(project(":"))
}

// Use the shadow JAR from the root project
tasks.register<Copy>("collectShadowJar") {
    dependsOn(":shadowJar")
    from(layout.projectDirectory.dir("../build/libs")) {
        include("Splice-*-all.jar")
    }
    into(layout.buildDirectory.dir("libs"))
}
