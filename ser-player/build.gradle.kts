plugins {
    id("me.champeau.astro4j.jfxapp")
    id("org.graalvm.buildtools.native") version "0.9.20"
    id("org.beryx.jlink") version "2.25.0"
}

dependencies {
    implementation(projects.jserfile)
}

javafx {
    version = "17"
    modules("javafx.controls", "javafx.fxml")
}

application {
    mainModule.set("me.champeau.a4j.serplayer")
    mainClass.set("me.champeau.a4j.serplayer.SerPlayer")
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

jlink {
    launcher {
        name = "ser-player"
    }
}
