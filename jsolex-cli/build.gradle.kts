plugins {
    id("me.champeau.astro4j.mnapp")
}

micronaut {
    testRuntime("spock")
    processing {
        incremental(true)
        annotations("me.champeau.a4j.jsolex.cli.*")
    }
}

dependencies {
    annotationProcessor(mn.picocli.codegen)
    implementation(projects.jsolexCore)
    implementation(mn.picocli)
    implementation("io.micronaut.picocli:micronaut-picocli")
    runtimeOnly(libs.logback)
}

application {
    mainClass.set("me.champeau.a4j.jsolex.cli.Main")
}

graalvmNative {
    metadataRepository.enabled.set(true)
    binaries.named("main") {
        imageName.set("jsolex")
    }
}

tasks.named<JavaExec>("run") {
    args(listOf(
        "-i", "/media/cchampeau/T7/sun/2023-04-19/Capture/13_06_30_best.ser",
    ))
}
