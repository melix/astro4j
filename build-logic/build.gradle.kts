import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.openjfx:javafx-plugin:0.1.0")
    implementation("org.nosphere.apache:creadur-rat-gradle:0.8.1")
    implementation("org.graalvm.buildtools:native-gradle-plugin:0.10.3")
    implementation("org.beryx.jlink:org.beryx.jlink.gradle.plugin:3.1.1")
    implementation("org.javamodularity:moduleplugin:1.8.15")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
    implementation("com.github.vlsi.gradle:license-gather-plugin:1.90")
    implementation("io.micronaut.gradle:micronaut-gradle-plugin:4.4.4")
    implementation("io.github.gradle-nexus:publish-plugin:2.0.0")
    implementation("org.asciidoctor:asciidoctor-gradle-jvm:4.0.3")
    implementation("org.ajoberstar:gradle-git-publish:3.0.0")

}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}
