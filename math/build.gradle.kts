plugins {
    id("me.champeau.astro4j.library")
}

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
