import com.github.vlsi.gradle.license.api.SpdxLicense

plugins {
    id("me.champeau.astro4j.jfxapp")
}

description = "A Sol'Ex spectroheliographic video file processor (JavaFX version)"

dependencies {
    implementation(projects.jsolexCore)
    implementation(libs.gson)
    implementation(libs.richtextfx) {
        exclude(group = "org.openjfx")
    }
}

jlink {
    addExtraDependencies("javafx")
}

application {
    mainModule.set("me.champeau.a4j.jsolex")
    mainClass.set("me.champeau.a4j.jsolex.app.JSolEx")
}

astro4j {
    withVectorApi()
}

tasks.gatherLicenses {
    extraLicenseDir.set(rootProject.file("licenses"))
    overrideLicense("gov.nasa.gsfc.heasarc:nom-tam-fits") {
        effectiveLicense = SpdxLicense.Unlicense
    }
}

sourceSets {
    main {
        resources {
            srcDir(tasks.generateLicense.map { it.outputFile.get().asFile.parentFile })
        }
    }
}

tasks.withType<Javadoc>().configureEach {
    options {
        (this as CoreJavadocOptions).optionFiles?.add(file("src/javadoc.options"))
    }
}
