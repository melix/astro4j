import me.champeau.astro4j.BuiltinFunctionCodeGenerator
import me.champeau.astro4j.SpectrumFileConverter
import java.util.*

plugins {
    id("me.champeau.astro4j.library")
    id("me.champeau.astro4j.congocc")
    id("me.champeau.jmh") version "0.7.3"
}

description = "Shared library for spectroheliographic video file processing"

dependencies {
    api(projects.jserfile)
    api(projects.math)
    api(libs.slf4j.api)
    api(libs.logback)
    implementation(libs.commons.compress)
    implementation(libs.commons.math)
    implementation(libs.gson)
    implementation(libs.fits) {
        setTransitive(false)
    }
    implementation(libs.jcodec)
    implementation(libs.lwjgl)
    implementation(libs.lwjgl.opencl)
    implementation(libs.graalpy.polyglot)
    implementation(libs.graalpy.python)
    implementation(libs.graalpy.python.language)
    implementation(libs.graalpy.python.resources)
    implementation(libs.arrow.memory)
    implementation(libs.arrow.memory.unsafe)
    implementation(libs.arrow.vector)
    implementation(libs.arrow.c.data)
    testImplementation(testFixtures(projects.jserfile))
}

astro4j {
    withVectorApi()
}


tasks.withType<JavaCompile>().configureEach {
    if (!name.lowercase(Locale.ENGLISH).contains("jmh")) {
        doFirst {
            options.compilerArgs.addAll(
                listOf("--module-path", classpath.asPath)
            )
            classpath = files()
        }
    }
}

tasks.withType<Javadoc>().configureEach {
    doFirst {
        options.setModulePath(classpath.files.toList())
        classpath = files()
    }
}

tasks {
    generateParser {
        grammarFile = "ImageMath.ccc"
    }
}

val converter = tasks.register<SpectrumFileConverter>("convertSpectrumFile") {
    inputFile = file("src/bass2000/atlasvi.dat")
    outputFile = layout.buildDirectory.file("atlas/atlasvi.txt")
}

val generateBuiltinFunctions = tasks.register<BuiltinFunctionCodeGenerator>("builtinFunctionGenerator") {
    yamlDirectory = file("src/main/functions")
    generatedSourcesDirectory = layout.buildDirectory.dir("generated-sources/astro4j-functions")
}

sourceSets {
    main {
        java {
            srcDir(generateBuiltinFunctions.flatMap { it.generatedSourcesDirectory })
        }
        resources {
            srcDir(converter.map { it.outputFile.get().asFile.parentFile })
        }
    }
}

spotless {
    java {
        targetExclude(fileTree("build/generated-sources"))
    }
}

jmh {
}
