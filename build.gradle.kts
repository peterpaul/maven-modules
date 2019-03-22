import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "net.kleinhaneveld.mavendependencies"
version = "1.0-SNAPSHOT"

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin on the JVM
    kotlin("jvm").version("1.3.20")

    // Apply the application to add support for building a CLI application
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // Use the Kotlin JDK 8 standard library
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.github.ajalt:clikt:1.6.0")

    // Use the Kotlin test library
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

application {
    // Define the main class for the application
    mainClassName = "net.kleinhaneveld.tree.PomParserKt"
}

tasks.withType<Jar> {
    manifest.attributes.apply {
        put("Main-Class", "net.kleinhaneveld.tree.PomParserKt")
    }

    configurations.runtimeClasspath.get().filter {
        it.name.endsWith(".jar")
    }.forEach { jar -> from(zipTree(jar)) }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
