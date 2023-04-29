plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.openjfx:javafx-plugin:0.0.13")
    implementation("org.nosphere.apache:creadur-rat-gradle:0.8.0")
    implementation("org.graalvm.buildtools:native-gradle-plugin:0.9.21")
    implementation("org.beryx.jlink:org.beryx.jlink.gradle.plugin:2.25.0")
    implementation("org.javamodularity:moduleplugin:1.8.12")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.18.0")
}
