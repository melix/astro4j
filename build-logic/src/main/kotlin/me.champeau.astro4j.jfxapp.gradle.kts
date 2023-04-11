plugins {
    id("me.champeau.astro4j.base")
    id("org.openjfx.javafxplugin")
    id("application")
}

tasks.named<JavaExec>("run") {
    outputs.upToDateWhen { false }
}
