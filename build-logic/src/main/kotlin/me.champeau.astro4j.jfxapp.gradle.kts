import org.javamodularity.moduleplugin.extensions.TestModuleOptions
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

plugins {
    id("me.champeau.astro4j.base")
    id("org.openjfx.javafxplugin")
    id("application")
    id("org.graalvm.buildtools.native")
    id("org.beryx.jlink")
    id("me.champeau.astro4j.modularity")
}

val date = LocalDateTime.now()
    .atZone(ZoneId.of("UTC"))
    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
val os = System.getProperty("os.name")
if (os.startsWith("Windows")) {
    version = version.toString().substring(0, version.toString().lastIndexOf(".")) + "0"
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
        vendor = "Cédric Champeau"
        if (os.startsWith("Windows")) {
            installerType = "msi"
            installerOptions.addAll(listOf("--win-per-user-install", "--win-dir-chooser", "--win-menu"))
            if (file("src/installer/icon.ico").exists()) {
                icon = "src/installer/icon.ico"
            }
        } else if (os.contains("mac")) {
            installerType = "pkg"
            if (file("src/installer/icon.icns").exists()) {
                icon = "src/installer/icon.icns"
            }
        } else {
            installerType = "deb"
            installerOptions.addAll(listOf("--linux-shortcut"))
            if (file("src/installer/icon.png").exists()) {
                icon = "src/installer/icon.png"
            }
        }
    }
}

tasks.register<Copy>("jpackageInstallers") {
    setDestinationDir(layout.buildDirectory.dir("installers").get().asFile)
    from(tasks.jpackage) {
        include("*.zip")
        include("*.msi")
        include("*.deb")
        include("*.pkg")
    }
}
