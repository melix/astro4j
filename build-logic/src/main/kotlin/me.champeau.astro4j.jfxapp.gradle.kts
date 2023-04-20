import org.javamodularity.moduleplugin.extensions.TestModuleOptions

plugins {
    id("me.champeau.astro4j.base")
    id("org.openjfx.javafxplugin")
    id("application")
    id("org.graalvm.buildtools.native")
    id("org.beryx.jlink")
    id("me.champeau.astro4j.modularity")
}

// We can safely enable preview features because it's
// an application, so no consumers except for the final
// deliverable

application {
    applicationDefaultJvmArgs = listOf("--enable-preview")
}

tasks.withType<JavaExec>().configureEach {
    outputs.upToDateWhen { false }
    jvmArgs("--enable-preview")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
    modularity.inferModulePath.set(false)
    extensions.findByType(TestModuleOptions::class.java)!!.runOnClasspath = true
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
        jvmArgs("--enable-preview")
    }
}

jlink {
    options.addAll(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    launcher {
        jvmArgs.add("--enable-preview")
    }
    jpackage {
        if (System.getProperty("os.name").startsWith("Windows")) {
            installerType = "msi"
            installerOptions.addAll(listOf("--win-per-user-install", "--win-dir-chooser", "--win-menu"))
        } else {
            installerType = "deb"
            installerOptions.addAll(listOf("--linux-shortcut"))
        }
    }
}
