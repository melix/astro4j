import me.champeau.astro4j.BuiltinFunctionAsciidocGenerator

plugins {
    id("me.champeau.astro4j.docs")
}

val generateBuiltinFunctions = tasks.register<BuiltinFunctionAsciidocGenerator>("builtinFunctionGenerator") {
    yamlDirectory = file("../jsolex-core/src/main/functions")
    generatedDocsDirectory = layout.buildDirectory.dir("generated-docs/functions")
}

asciidoc {
    publications {
        named("main") {
            sourceSet {
                externalSource {
                    from(files(generateBuiltinFunctions))
                    builtBy(generateBuiltinFunctions)
                    into("_functions")
                    sources {
                        exclude("**")
                    }
                }
                val downloadVersion = if (version.toString().contains("-SNAPSHOT")) {
                    version.toString().substringBefore("-SNAPSHOT")
                } else {
                    version.toString()
                }
                val prefixName = if (version.toString().contains("-SNAPSHOT")) {
                    "jsolex-devel"
                } else {
                    "jsolex"
                }
                var fullName = "jsolex"
                var fullVersion = version.toString()
                attributes {
                    addAll(mapOf(
                        "reproducible" to "",
                        "nofooter" to "",
                        "toc" to "left",
                        "docinfo" to "shared",
                        "source-highlighter" to "highlight.js",
                        "highlightjs-theme" to "github",
                        "version" to downloadVersion,
                        "prefixName" to prefixName,
                        "fullName" to fullName,
                        "fullVersion" to fullVersion,
                        "revnumber" to fullVersion,
                        "generated-funs" to "_functions",
                        "python-examples" to rootProject.file("jsolex-core/src/test/resources/python-examples").absolutePath,
                    ))
                }
            }
        }
    }
}

gitPublish {
    repoUri.set("git@github.com:melix/astro4j.git")
}
