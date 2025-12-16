import com.github.vlsi.gradle.license.GatherLicenseTask
import me.champeau.astro4j.BuildExtension
import me.champeau.astro4j.GenerateLicenseResourceFile
import me.champeau.astro4j.PropertiesFileSanityCheckTask

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
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.spockframework:spock-core:2.3-groovy-4.0")
    testImplementation("org.apache.groovy:groovy:4.0.29")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "utf-8"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.rat {
    excludes.add("**/*.gradle.kts")
    excludes.add("build/**")
    excludes.add("**/*.css")
    excludes.add("**/*.fits")
    excludes.add("**/*.fits.ko")
    excludes.add("**/*.fxml")
    excludes.add("**/*.xml")
    excludes.add("**/*.md")
    excludes.add("**/*.txt")
    excludes.add("**/*.math")
    excludes.add("**/*.log")
    excludes.add("**/*.properties")
    excludes.add("**/*.iprof")
    excludes.add("**/*.webm")
    excludes.add("**/*.html")
    excludes.add("**/*.js")
    excludes.add("**/*.jar")
    excludes.add("**/*.ccc")
    excludes.add("**/*.yml")
    excludes.add("**/javadoc.options")
}

group = "me.champeau.astro4j"

spotless {
    this.java {
        licenseHeader("""
/*
 * Copyright ${'$'}YEAR the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
""".trimIndent())
    }
}

val versionFile = layout.buildDirectory.file("version/version.txt")
val writeVersionTxt = tasks.register("writeVersion") {
    inputs.property("version", providers.gradleProperty("version"))
    outputs.file(versionFile)
    doLast {
        versionFile.get().asFile.writeText(providers.gradleProperty("version").get())
    }
}

sourceSets {
    main {
        resources {
            srcDir(writeVersionTxt.map { versionFile.get().asFile.parent })
        }
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

val propertiesSanityCheck by tasks.registering(PropertiesFileSanityCheckTask::class) {
    propertiesFiles.from(sourceSets.main.get().resources.asFileTree.filter { it.name.endsWith(".properties") })
}

tasks.named("processResources") {
    dependsOn(propertiesSanityCheck)
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
        this as MavenPublication
        pom {
            name.set(project.name)
            description.set(provider { project.description })
            url.set("https://github.com/melix/astro4j")
            licenses {
                license {
                    name.set("The Apache Software License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("melix")
                    name.set("Cédric Champeau")
                    email.set("cedric.champeau@gmail.com")
                }
            }
            scm {
                connection.set("scm:git@github.com:melix/astro4j.git")
                developerConnection.set("<scm:git@github.com:melix/astro4j.git")
                url.set("scm:git@github.com:melix/astro4j.git")
            }
        }
    }
    useGpgCmd()
}
