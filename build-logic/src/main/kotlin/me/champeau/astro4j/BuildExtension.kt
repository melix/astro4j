package me.champeau.astro4j

import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import javax.inject.Inject

abstract class BuildExtension(@Inject val project: Project) {

    fun withVectorApi() = withModule("jdk.incubator.vector", false)

    fun withModule(name: String, supportedInNative: Boolean = false) = project.tasks.run {
        val argsToAdd = listOf("--add-modules", name)
        withType<JavaCompile>().configureEach {
            options.compilerArgs.addAll(argsToAdd)
        }

        withType<Test>().configureEach {
            jvmArgs(argsToAdd)
        }

        withType<JavaExec>().configureEach {
            jvmArgs(argsToAdd)
        }

        if (supportedInNative) {
            project.pluginManager.withPlugin("org.graalvm.buildtools.native") {
                val ext = project.extensions.findByType(GraalVMExtension::class.java)
                ext?.binaries?.all {
                    jvmArgs(argsToAdd)
                }
            }
        }
    }
}
