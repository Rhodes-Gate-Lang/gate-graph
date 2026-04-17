import java.net.URI
import java.nio.file.Files
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    application
    java
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.rhodesgatelang"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val gateoJavaVersion = "2.0.2"
val gateoJavaJar =
    layout.buildDirectory
        .dir("gateo-release")
        .map { it.file("gateo-java-${gateoJavaVersion}.jar") }

/** gateo-java is attached to GitHub Releases (not Maven Central). */
val downloadGateoJava by tasks.registering {
    outputs.file(gateoJavaJar)
    doLast {
        val dest = gateoJavaJar.get().asFile.toPath()
        Files.createDirectories(dest.parent)
        val url =
            URI(
                    "https://github.com/Rhodes-Gate-Lang/gateo-java/releases/download/v${gateoJavaVersion}/gateo-java-${gateoJavaVersion}.jar"
                )
                .toURL()
        url.openStream().use { input -> Files.newOutputStream(dest).use { output -> input.copyTo(output) } }
    }
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(downloadGateoJava)
}

dependencies {
    implementation(files(gateoJavaJar))
    implementation("com.google.protobuf:protobuf-java:4.29.3")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

javafx {
    version = "17"
    modules = listOf("javafx.controls", "javafx.web", "javafx.fxml")
}

application {
    mainClass = "com.rhodesgatelang.gategraph.GateGraphApp"
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(downloadGateoJava)
    options.encoding = "UTF-8"
}
