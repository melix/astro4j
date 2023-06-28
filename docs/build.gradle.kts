plugins {
    id("me.champeau.astro4j.docs")
}

tasks {
    asciidoctor {
        attributes(mapOf(
            "reproducible" to "",
            "nofooter" to "",
            "toc" to "left",
            "docinfo" to "shared",
            "source-highlighter" to "highlight.js",
            "highlightjs-theme" to "equilibrium-light",
            "highlightjsdir" to "highlight",
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
