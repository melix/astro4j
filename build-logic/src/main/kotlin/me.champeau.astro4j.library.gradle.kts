plugins {
    id("me.champeau.astro4j.base")
    `java-library`
}

tasks.named("generateLicense") {
    enabled = false
}
