plugins {
    id("me.champeau.astro4j.base")
    id("org.openjfx.javafxplugin")
    id("application")
    id("org.graalvm.buildtools.native")
    id("org.beryx.jlink")
}

// We can safely enable preview features because it's
// an application, so no consumers except for the final
// deliverable

tasks.withType<JavaExec>().configureEach {
    outputs.upToDateWhen { false }
    jvmArgs("--enable-preview")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
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
