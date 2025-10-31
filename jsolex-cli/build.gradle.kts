plugins {
    id("me.champeau.astro4j.mnapp")
}

description = "A Sol'Ex spectroheliographic video file processor (CLI version)"

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
//        buildArgs.add("--pgo=/home/cchampeau/DEV/PROJECTS/GITHUB/astro4j/default.iprof")
    }
}

tasks.named<JavaExec>("run") {
    val profile = providers.systemProperty("profile")
        .map(String::toBoolean)
        .getOrElse(false)
    if (profile) {
        jvmArgs(listOf(
                "-agentpath:/home/cchampeau/TOOLS/hp/liblagent.so=interval=7,logPath=/tmp/log.hpl",
        ))
    }
    args(listOf(
        "-i", "/media/cchampeau/T7/sun/2023-04-19/Capture/13_06_30_best.ser",
    ))
}
