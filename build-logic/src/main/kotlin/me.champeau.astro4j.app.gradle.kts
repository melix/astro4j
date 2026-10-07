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
    providers.systemProperty("memory.settings").getOrElse("-XX:MaxRAMPercentage=80"),
    "-XX:+UseZGC",
    // Soft references hold the cached images: the default policy keeps them for about one
    // second per free megabyte, which is hours on a large heap, so cached images would only
    // ever be written to disk under memory pressure
    "-XX:SoftRefLRUPolicyMSPerMB=8",
    "-XX:+ExplicitGCInvokesConcurrent",
    "-XX:+HeapDumpOnOutOfMemoryError",
    "-XX:+UseCompactObjectHeaders",
    "-Dpolyglotimpl.DisableMultiReleaseCheck=true"
)
extra["jvmMemorySettings"] = jvmMemorySettings

val nativeAccessArgs = listOf("--enable-preview", "--enable-native-access=javafx.graphics", "--enable-native-access=org.lwjgl.opengl", "--enable-native-access=org.lwjgl")
val sharedJvmArgs = jvmMemorySettings + nativeAccessArgs

application {
    applicationDefaultJvmArgs = sharedJvmArgs
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
}
