import me.champeau.astro4j.BuiltinFunctionAsciidocGenerator

plugins {
    id("me.champeau.astro4j.docs")
}

val generateBuiltinFunctions = tasks.register<BuiltinFunctionAsciidocGenerator>("builtinFunctionGenerator") {
    yamlDirectory = file("../jsolex-core/src/main/functions")
    generatedDocsDirectory = layout.buildDirectory.dir("generated-docs/functions")
}

tasks {
    asciidoctor {
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
        attributes(mapOf(
            "reproducible" to "",
            "nofooter" to "",
            "toc" to "left",
            "docinfo" to "shared",
            "source-highlighter" to "highlight.js",
            "highlightjs-theme" to "equilibrium-light",
            "highlightjsdir" to "highlight",
            "version" to downloadVersion,
            "prefixName" to prefixName,
            "generated-funs" to generateBuiltinFunctions.get().generatedDocsDirectory.get().asFile.absolutePath,
        ))
        resources(delegateClosureOf<CopySpec> {
            from("src/docs/asciidoc") {
                include("**/*.png")
                include("**/*.jpg")
            }
            into(".")
        })
    }
}

gitPublish {
    repoUri.set("git@github.com:melix/astro4j.git")
}
