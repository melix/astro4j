plugins {
    id("me.champeau.astro4j.mnapp")
}

description = "Standalone scripting for Sol'Ex/Sunscan"

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
    mainClass.set("me.champeau.a4j.jsolex.cli.ScriptingEntryPoint")
}

graalvmNative {
    metadataRepository.enabled.set(true)
    binaries.named("main") {
        imageName.set("jsolex-scripting")
        buildArgs.addAll(listOf("--add-modules", "jdk.incubator.vector"))
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
}

tasks.withType<JavaExec>().configureEach {
    args(listOf(
        "-o", "/tmp/out2",
        "-s", "/home/cchampeau/DEV/PROJECTS/GITHUB/astro4j/stacking.math",
        "-p", "input_dir=/home/cchampeau/Downloads/stack_191124/stack_191124"
    ))
}
