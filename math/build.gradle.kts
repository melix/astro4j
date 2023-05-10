plugins {
    id("me.champeau.astro4j.library")
}

description = "Provides some mathematical functions like linear or elliptic regression"

astro4j {
    withVectorApi()
}

dependencies {
    implementation(libs.commons.math)
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
