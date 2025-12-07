import java.util.*

plugins {
    id("me.champeau.astro4j.library")
    id("me.champeau.jmh") version "0.7.3"
}

description = "Provides some mathematical functions like linear or elliptic regression"

val lwjglNatives = when {
    org.gradle.internal.os.OperatingSystem.current().isWindows -> "natives-windows"
    org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "natives-macos"
    else -> "natives-linux"
}

astro4j {
    withVectorApi()
}

dependencies {
    implementation(libs.commons.math)

    implementation(libs.lwjgl)
    implementation(libs.lwjgl.opencl)

    // Runtime natives
    runtimeOnly(variantOf(libs.lwjgl) { classifier(lwjglNatives) })
}

tasks.withType<JavaCompile>().configureEach {
    if (!name.lowercase(Locale.ENGLISH).contains("jmh")) {
        doFirst {
            options.compilerArgs.addAll(
                listOf("--module-path", classpath.asPath)
            )
            classpath = files()
        }
    }
}

tasks.withType<Javadoc>().configureEach {
    doFirst {
        options.setModulePath(classpath.files.toList())
        classpath = files()
    }
}

jmh {
    includes.set(listOf("OpenCLFFTBenchmark"))
}
