plugins {
    id("me.champeau.astro4j.library")
}

astro4j {
    withVectorApi()
}

dependencies {
    implementation(libs.commons.math)
}
