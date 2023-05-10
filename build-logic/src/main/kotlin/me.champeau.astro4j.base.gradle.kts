import com.github.vlsi.gradle.license.GatherLicenseTask
import me.champeau.astro4j.BuildExtension
import me.champeau.astro4j.GenerateLicenseResourceFile

plugins {
    groovy
    java
    id("org.nosphere.apache.rat")
    id("com.diffplug.spotless")
    id("maven-publish")
    id("signing")
}

extensions.create("astro4j", BuildExtension::class.java, project)

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
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
    excludes.add("**/*.xml")
    excludes.add("**/*.md")
    excludes.add("**/*.properties")
}

group = "me.champeau.astro4j"

spotless {
    this.java {
        licenseHeaderFile(rootProject.file("gradle/license.java.txt"))
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}

val gatherLicenses by tasks.registering(GatherLicenseTask::class) {
    configurations.add(project.configurations.runtimeClasspath)
    extraLicenseDir.set(rootProject.file("licenses"))
}

val generateLicense by tasks.registering(GenerateLicenseResourceFile::class) {
    licensesDir.set(gatherLicenses.flatMap(GatherLicenseTask::licenseDir))
    outputFile.set(layout.buildDirectory.file("generated/resources/licenses/licenses.txt"))
}

publishing {
    repositories {
        maven {
            name = "build"
            setUrl(rootProject.layout.buildDirectory.dir("repo"))
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

signing {
    setRequired {
        gradle.taskGraph.allTasks.any {
            it.name.startsWith("publish")
        }
    }
    publishing.publications.configureEach {
        sign(this)
    }
    useGpgCmd()
}
