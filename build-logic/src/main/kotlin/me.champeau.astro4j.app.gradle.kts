import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.javamodularity.moduleplugin.extensions.TestModuleOptions
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

plugins {
    id("me.champeau.astro4j.base")
    id("application")
    id("org.graalvm.buildtools.native")
    id("me.champeau.astro4j.modularity")
}

val date = LocalDateTime.now()
    .atZone(ZoneId.of("UTC"))
    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))

// We can safely enable preview features because it's
// an application, so no consumers except for the final
// deliverable

application {
    applicationDefaultJvmArgs = listOf("--enable-preview")
}

tasks.withType<JavaExec>().configureEach {
    outputs.upToDateWhen { false }
    providers.systemPropertiesPrefixedBy("sysprop.").get().forEach { s, p ->
        systemProperty(s.substringAfter("sysprop."), p)
    }
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
