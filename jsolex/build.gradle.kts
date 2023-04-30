plugins {
    id("me.champeau.astro4j.jfxapp")
}

dependencies {
    implementation(projects.jserfile)
    implementation(projects.math)
    implementation(libs.slf4j.api)
    implementation(libs.commons.math)
    implementation(libs.logback)
    implementation(libs.gson)
    testImplementation(testFixtures(projects.jserfile))
}

application {
    mainModule.set("me.champeau.a4j.jsolex")
    mainClass.set("me.champeau.a4j.jsolex.app.JSolEx")
}

astro4j {
    withVectorApi()
}
