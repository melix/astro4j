plugins {
    id("me.champeau.astro4j.docs")
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
            "prefixName" to prefixName
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
