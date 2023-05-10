import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.javamodularity.moduleplugin.extensions.TestModuleOptions
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

plugins {
    id("me.champeau.astro4j.base")
    id("application")
    id("org.graalvm.buildtools.native")
    id("me.champeau.astro4j.modularity")
}

val date = LocalDateTime.now()
    .atZone(ZoneId.of("UTC"))
    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
val os = System.getProperty("os.name").lowercase(Locale.ENGLISH)
if (os.startsWith("windows") || os.contains("mac")) {
    version = version.toString().substring(0, version.toString().lastIndexOf(".")) + ".0"
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
        jvmArgs("--enable-preview")
    }
}
