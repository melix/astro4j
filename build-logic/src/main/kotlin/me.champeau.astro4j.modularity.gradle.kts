/**
 * Fixes issues with automatic module names
 */
plugins {
    java
}

val artifactType = Attribute.of("artifactType", String::class.java)
val patchedModule = Attribute.of("module-ready", Boolean::class.javaObjectType)

dependencies {
    attributesSchema {
        attribute(patchedModule)
    }
    artifactTypes.getByName("jar") {
        attributes.attribute(patchedModule, false)
    }
}

configurations {
    testRuntimeClasspath {
        attributes.attribute(patchedModule, true)
    }
}

dependencies {
    registerTransform(AutomaticModuleNameFixup::class) {
        from.attribute(patchedModule, false).attribute(artifactType, "jar")
        to.attribute(patchedModule, true).attribute(artifactType, "jar")
    }
}


abstract class AutomaticModuleNameFixup : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    @get:Inject
    abstract val archiveOperations: ArchiveOperations

    override
    fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        val output = outputs.file(
            input.name.replace("platform-native", "platform.ntve")
                .replace("-M2", "")
        )

        input.copyTo(output, overwrite = true)
    }
}
