import java.util.*

plugins {
    id("me.champeau.astro4j.app")
    id("org.openjfx.javafxplugin")
    id("org.beryx.jlink")
}

javafx {
    version = "17"
    modules("javafx.controls", "javafx.fxml")
}

val os = System.getProperty("os.name").lowercase(Locale.ENGLISH)
if (os.startsWith("windows") || os.contains("mac")) {
    version = version.toString().substring(0, version.toString().lastIndexOf(".")) + ".0"
}

jlink {
    options.addAll(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    launcher {
        jvmArgs.add("--enable-preview")
    }
    jpackage {
        vendor = "Cédric Champeau"
        appVersion = version.toString()
        if (os.startsWith("windows")) {
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
