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

val sharedJvmArgs = listOf("--enable-preview", "--enable-native-access=javafx.graphics", "--enable-native-access=org.lwjgl.opengl", "--enable-native-access=org.lwjgl")

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
        jvmArgs(sharedJvmArgs)
    }
}

tasks.register<Zip>("nativeZip") {
    archiveBaseName = project.name
    archiveVersion = version.toString()
    archiveClassifier = System.getProperty("os.name").lowercase() + "-" +System.getProperty("os.arch")
    destinationDirectory = layout.buildDirectory.dir("distributions")
    from(tasks.nativeCompile.flatMap(BuildNativeImageTask::getOutputDirectory))
}
