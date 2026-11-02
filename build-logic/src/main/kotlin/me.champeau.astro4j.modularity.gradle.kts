import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * Fixes issues with automatic module names, and removes the Micronaut
 * generated module info classes, which are all in split packages
 * (e.g. io.micronaut.serde.info)
 */
plugins {
    java
}

val artifactType = Attribute.of("artifactType", String::class.java)
val patchedModule = moduleReady

dependencies {
    attributesSchema {
        attribute(patchedModule)
    }
    artifactTypes.getByName("jar") {
        attributes.attribute(patchedModule, false)
    }
}

configurations {
    listOf(compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath).forEach {
        it {
            attributes.attribute(patchedModule, true)
        }
    }
}

dependencies {
    registerTransform(AutomaticModuleNameFixup::class) {
        from.attribute(patchedModule, false).attribute(artifactType, "jar")
        to.attribute(patchedModule, true).attribute(artifactType, "jar")
    }
}

abstract class AutomaticModuleNameFixup : TransformAction<TransformParameters.None> {
    companion object {
        private val MICRONAUT_MODULE_INFO = Regex("io/micronaut/.+/info/[^/]+ModuleInfo\\.class")
    }

    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override
    fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        val outputName = input.name.replace("platform-native", "platform.ntve")
            .replace("-M2", "")
        JarFile(input).use { jar ->
            if (jar.entries().asSequence().any { MICRONAUT_MODULE_INFO.matches(it.name) }) {
                val output = outputs.file(outputName).outputStream()
                val manifest = jar.manifest
                val out = if (manifest == null) JarOutputStream(output) else JarOutputStream(output, manifest)
                out.use {
                    for (entry in jar.entries()) {
                        if (entry.name == JarFile.MANIFEST_NAME || MICRONAUT_MODULE_INFO.matches(entry.name)) {
                            continue
                        }
                        out.putNextEntry(JarEntry(entry.name).apply { time = entry.time })
                        jar.getInputStream(entry).use { it.copyTo(out) }
                        out.closeEntry()
                    }
                }
            } else if (outputName != input.name) {
                input.copyTo(outputs.file(outputName), overwrite = true)
            } else {
                outputs.file(inputArtifact)
            }
        }
    }
}
