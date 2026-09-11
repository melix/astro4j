import me.champeau.astro4j.BuiltinFunctionCodeGenerator
import me.champeau.astro4j.SpectrumFileConverter
import me.champeau.astro4j.TelluricFileConverter
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

val telluricConverter = tasks.register<TelluricFileConverter>("convertTelluricFile") {
    inputFile = file("src/nso/visatl-telluric.dat")
    outputFile = layout.buildDirectory.file("atlas/telluric.txt")
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
            // Both converters write to the same directory, added once but depending on both
            srcDir(converter.flatMap { it.outputFile }.zip(telluricConverter.flatMap { it.outputFile }) { atlas, _ -> atlas.asFile.parentFile })
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
