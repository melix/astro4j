plugins {
    id("me.champeau.astro4j.jfxapp")
}

dependencies {
    implementation(projects.jserfile)
    implementation(projects.math)
    implementation(libs.slf4j.api)
    implementation(libs.commons.math)
    testImplementation(testFixtures(projects.jserfile))
    runtimeOnly(libs.logback)
}

application {
    mainModule.set("me.champeau.a4j.jsolex")
    mainClass.set("me.champeau.a4j.jsolex.app.JSolEx")
}
