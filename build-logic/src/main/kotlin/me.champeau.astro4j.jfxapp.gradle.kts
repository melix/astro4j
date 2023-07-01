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

jlink {
    options.addAll(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    launcher {
        jvmArgs.add("--enable-preview")
    }
    jpackage {
        if (version.toString().endsWith("-SNAPSHOT")) {
            appVersion = version.toString().substringBefore("-SNAPSHOT")
            installerName = project.name + "-devel"
        } else {
            appVersion = version.toString()
        }
        vendor = "Cédric Champeau"
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

val installersDir = layout.buildDirectory.dir("installers")

var jpackageInstallers = tasks.register<Copy>("jpackageInstallers") {
    setDestinationDir(installersDir.get().asFile)
    from(tasks.jpackage) {
        include("*.zip")
        include("*.msi")
        include("*.deb")
        include("*.pkg")
    }
}

val jlinkTgz = tasks.register<Tar>("jlinkTgz") {
    destinationDirectory.set(installersDir)
    mustRunAfter(tasks.jpackageImage, tasks.jpackage)
    from(tasks.jlink)
    setCompression(Compression.GZIP)
    archiveExtension.set("tar.gz")
}

val jlinkZipArchive = tasks.register<Zip>("jlinkZipArchive") {
    destinationDirectory.set(installersDir)
    mustRunAfter(tasks.jpackageImage, tasks.jpackage)
    from(tasks.jlink)
}

tasks.register("allDistributions") {
    dependsOn(jpackageInstallers, jlinkTgz, jlinkZipArchive)
}
