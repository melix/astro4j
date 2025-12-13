import com.github.vlsi.gradle.license.api.SpdxLicense

plugins {
    id("me.champeau.astro4j.jfxapp")
}

description = "A Sol'Ex spectroheliographic video file processor (JavaFX version)"

val lwjglNatives = when {
    org.gradle.internal.os.OperatingSystem.current().isWindows -> "natives-windows"
    org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "natives-macos"
    else -> "natives-linux"
}

dependencies {
    implementation(projects.jsolexCore)
    implementation(projects.jsolexServer)
    implementation(mn.micronaut.context)
    implementation(libs.gson)
    implementation(libs.richtextfx) {
        exclude(group = "org.openjfx")
    }
    implementation(libs.commonmark)
    implementation(libs.ikonli)
    implementation(libs.ikonli.fluentui)
    implementation(libs.commons.net)
    runtimeOnly(libs.sqlite)

    // LWJGL for GPU acceleration (OpenCL, OpenGL, GLFW)
    implementation(libs.lwjgl)
    implementation(libs.lwjgl.opencl)
    implementation(libs.lwjgl.opengl)
    implementation(libs.lwjgl.glfw)
    implementation(variantOf(libs.lwjgl) { classifier(lwjglNatives) })
    implementation(variantOf(libs.lwjgl.opengl) { classifier(lwjglNatives) })
    implementation(variantOf(libs.lwjgl.glfw) { classifier(lwjglNatives) })
}

jlink {
    addExtraDependencies("javafx")
    mergedModule {
        additive = true
        uses("nom.tam.fits.compress.ICompressProvider")
        uses("ch.qos.logback.classic.spi.Configurator")
        excludeProvides(
            mapOf(
                "servicePattern" to "reactor.blockhound.integration.*"
            )
        )
        excludeProvides(
            mapOf(
                "servicePattern" to "jakarta.servlet.*"
            )
        )
        excludeProvides(
            mapOf(
                "servicePattern" to "io.micrometer.*"
            )
        )
    }
    forceMerge(
        "commons-compress",
        "logback-core", "logback-classic",
        "sqlite-jdbc"
    )
}

application {
    mainModule.set("me.champeau.a4j.jsolex")
    mainClass.set("me.champeau.a4j.jsolex.app.JSolEx")
}

astro4j {
    withVectorApi()
}

tasks.gatherLicenses {
    extraLicenseDir.set(rootProject.file("licenses"))
    overrideLicense("gov.nasa.gsfc.heasarc:nom-tam-fits") {
        effectiveLicense = SpdxLicense.Unlicense
    }
}

sourceSets {
    main {
        resources {
            srcDir(tasks.generateLicense.map { it.outputFile.get().asFile.parentFile })
        }
    }
}

tasks.withType<Javadoc>().configureEach {
    options {
        (this as CoreJavadocOptions).optionFiles?.add(file("src/javadoc.options"))
    }
}

tasks.withType<JavaCompile>().configureEach {
    doFirst {
        options.compilerArgs.addAll(
            listOf("--module-path", classpath.asPath)
        )
        classpath = files()
    }
}

tasks.named<JavaExec>("run") {
    doFirst {
        jvmArgs(
            listOf("--module-path", classpath.asPath)
        )
        classpath = files()
    }
}

tasks.withType<Javadoc>().configureEach {
    doFirst {
        options.setModulePath(classpath.files.toList())
        classpath = files()
    }
}
