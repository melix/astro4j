plugins {
    id("me.champeau.astro4j.jfxapp")
}

dependencies {
    implementation(projects.jserfile)
    implementation(projects.math)
    implementation(libs.slf4j.api)
    implementation(libs.commons.math)
    implementation(libs.logback)
    testImplementation(testFixtures(projects.jserfile))
}

application {
    mainModule.set("me.champeau.a4j.jsolex")
    mainClass.set("me.champeau.a4j.jsolex.app.JSolEx")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.incubator.vector"))
}

tasks.withType<Test>().configureEach {
    jvmArgs(listOf("--add-modules", "jdk.incubator.vector"))
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs(listOf("--add-modules", "jdk.incubator.vector"))
}
