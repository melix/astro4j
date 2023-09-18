plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.openjfx:javafx-plugin:0.0.14")
    implementation("org.nosphere.apache:creadur-rat-gradle:0.8.1")
    implementation("org.graalvm.buildtools:native-gradle-plugin:0.9.25")
    implementation("org.beryx.jlink:org.beryx.jlink.gradle.plugin:2.26.0")
    implementation("org.javamodularity:moduleplugin:1.8.12")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.21.0")
    implementation("com.github.vlsi.gradle:license-gather-plugin:1.90")
    implementation("io.micronaut.gradle:micronaut-gradle-plugin:4.1.1")
    implementation("io.github.gradle-nexus:publish-plugin:1.3.0")
    implementation("org.asciidoctor:asciidoctor-gradle-jvm:3.3.2")
    implementation("org.ajoberstar:gradle-git-publish:3.0.0")

}
