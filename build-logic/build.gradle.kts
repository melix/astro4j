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
}
