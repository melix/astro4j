import me.champeau.astro4j.CongoCCTask

plugins {
    java
}

val generateParser = tasks.register<CongoCCTask>("generateParser") {
    grammarDirectory = project.file("src/main/congocc")
    jdkVersion.convention(22)
    congoCCJar.set(project.file("libs/congocc.jar"))
    outputDirectory.set(project.layout.buildDirectory.dir("generated-sources/congocc"))
}

sourceSets {
    main {
        java {
            srcDir(generateParser)
        }
    }
}
