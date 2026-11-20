import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.javamodularity.moduleplugin.extensions.TestModuleOptions

plugins {
    id("me.champeau.astro4j.base")
    id("application")
    id("org.graalvm.buildtools.native")
    id("me.champeau.astro4j.modularity")
}

// We can safely enable preview features because it's
// an application, so no consumers except for the final
// deliverable

val jvmMemorySettings = listOf(
    providers.systemProperty("memory.settings").getOrElse("-XX:MaxRAMPercentage=40"),
    "-XX:+UseZGC",
    // The memory of an image is given back when the garbage collector finds that the image is
    // no longer used. With pixels outside of the heap, the heap fills slowly and collections
    // would be rare: collecting recent objects every second gives back the memory of images
    // which were only used briefly
    "-XX:ZCollectionIntervalMinor=1",
    "-XX:+ExplicitGCInvokesConcurrent",
    "-XX:+HeapDumpOnOutOfMemoryError",
    "-XX:+UseCompactObjectHeaders",
    "-Dpolyglotimpl.DisableMultiReleaseCheck=true"
)
extra["jvmMemorySettings"] = jvmMemorySettings

val nativeAccessArgs = listOf("--enable-preview", "--enable-native-access=javafx.graphics", "--enable-native-access=org.lwjgl.opengl", "--enable-native-access=org.lwjgl", "--enable-native-access=ALL-UNNAMED")
val sharedJvmArgs = jvmMemorySettings + jitSettings + nativeAccessArgs

application {
    applicationDefaultJvmArgs = sharedJvmArgs
}

// All GraalVM dependencies use the GraalPy version of the catalog
val graalpyVersion = the<VersionCatalogsExtension>().named("libs").findVersion("graalpy").get().requiredVersion

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group.startsWith("org.graalvm.") && requested.group != "org.graalvm.buildtools") {
            useVersion(graalpyVersion)
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    outputs.upToDateWhen { false }
    providers.systemPropertiesPrefixedBy("sysprop.").get().forEach { s, p ->
        systemProperty(s.substringAfter("sysprop."), p)
    }
    jvmArgs(sharedJvmArgs)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
    modularity.inferModulePath.set(false)
    extensions.findByType(TestModuleOptions::class.java)?.also {
        it.runOnClasspath = true
    }
}

graalvmNative {
    binaries.all {
        resources {
            autodetection {
                enabled.set(true)
                restrictToProjectDependencies.set(false)
            }
        }
        jvmArgs(nativeAccessArgs)
    }
}

tasks.register<Zip>("nativeZip") {
    archiveBaseName = project.name
    archiveVersion = version.toString()
    archiveClassifier = System.getProperty("os.name").lowercase() + "-" +System.getProperty("os.arch")
    destinationDirectory = layout.buildDirectory.dir("distributions")
    from(tasks.nativeCompile.flatMap(BuildNativeImageTask::getOutputDirectory))
    eachFile {
        if (!name.contains('.')) {
            permissions { unix("rwxr-xr-x") }
        }
    }
}
