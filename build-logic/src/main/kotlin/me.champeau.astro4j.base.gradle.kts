plugins {
    groovy
    `java`
    id("org.nosphere.apache.rat")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.spockframework:spock-core:2.3-groovy-4.0")
    testImplementation("org.apache.groovy:groovy:4.0.9")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.rat {
    excludes.add("**/*.gradle.kts")
    excludes.add("build/**")
    excludes.add("**/*.css")
    excludes.add("**/*.fxml")
}

group = "me.champeau.astro4j"
