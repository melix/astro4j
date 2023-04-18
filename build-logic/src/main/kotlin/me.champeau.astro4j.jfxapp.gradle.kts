plugins {
    id("me.champeau.astro4j.base")
    id("org.openjfx.javafxplugin")
    id("application")
    id("org.graalvm.buildtools.native")
    id("org.beryx.jlink")
}

tasks.withType<JavaExec>().configureEach {
    outputs.upToDateWhen { false }
}

javafx {
    version = "17"
    modules("javafx.controls", "javafx.fxml")
}

graalvmNative {
    binaries.all {
        resources {
            autodetection {
                enabled.set(true)
                restrictToProjectDependencies.set(false)
            }
        }
    }
}
