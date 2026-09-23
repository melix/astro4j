import org.asciidoctor.gradle.model5.core.tasks.AsciidoctorTask

plugins {
    id("org.asciidoctor.jvm")
    id("org.ajoberstar.git-publish")
}

asciidoc {
    publications {
        named("main") {
            sourceSet {
                setSourceDir("src/docs/asciidoc")
                missingIncludesAreFatal()
                resources {
                    include("**/*.png")
                    include("**/*.jpg")
                    include("**/*.webm")
                }
                docInfo {
                    setDocInfoDir("src/docs/asciidoc")
                }
            }
            output("asciidoctorj", "html")
        }
    }
}

abstract class FileOperations {
    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations
}

tasks.named<AsciidoctorTask>("asciidoctorHtml") {
    val fileSystemOperations = objects.newInstance<FileOperations>().fileSystemOperations
    val outputDir = outputDir
    doFirst {
        fileSystemOperations.delete {
            delete(outputDir)
        }
    }
}

gitPublish {

    branch.set("gh-pages")
    sign.set(false)

    contents {
        from(tasks.named("asciidoctorHtml")) {
            into(providers.provider { "$version" })
        }
    }

    preserve {
        include("**")
        exclude(KotlinClosure1<FileVisitDetails, Boolean>({ name.equals("$version") }, this, this))
    }

    commitMessage.set(providers.provider {
        "Publishing documentation for version $version"
    })
}
