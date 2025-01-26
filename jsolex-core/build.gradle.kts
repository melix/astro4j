import me.champeau.astro4j.SpectrumFileConverter

plugins {
    id("me.champeau.astro4j.library")
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
    testImplementation(testFixtures(projects.jserfile))
}

astro4j {
    withVectorApi()
}


tasks.withType<JavaCompile>().configureEach {
    doFirst {
        options.compilerArgs.addAll(
            listOf("--module-path", classpath.asPath)
        )
        classpath = files()
    }
}

tasks.withType<Javadoc>().configureEach {
    doFirst {
        options.setModulePath(classpath.files.toList())
        classpath = files()
    }
}

val converter = tasks.register<SpectrumFileConverter>("convertSpectrumFile") {
    inputFile = file("src/bass2000/atlasvi.dat")
    outputFile = layout.buildDirectory.file("atlas/atlasvi.txt")
}

sourceSets {
    main {
        resources {
            srcDir(converter.map { it.outputFile.get().asFile.parentFile })
        }
    }
}
