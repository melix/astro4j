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
        // Vector API is not supported in native-image yet
//        buildArgs.addAll(listOf("--add-modules", "jdk.incubator.vector"))
        buildArgs.add("--gc=G1")
//        buildArgs.add("-march=native")
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
//    jvmArgs("-agentpath:/home/cchampeau/TOOLS/YourKit-JavaProfiler-2023.9/bin/linux-x86-64/libyjpagent.so=_no_java_version_check,sampling")
    args(listOf(
        "-o", "/tmp/out/serie4",
        "-c", "/home/cchampeau/DEV/PROJECTS/GITHUB/astro4j/config.json",
        "-s", "/home/cchampeau/DEV/PROJECTS/GITHUB/astro4j/stacking-conti.math",
        "-p", "input_dir=/home/cchampeau/Downloads/stack_151124",
//        "-p", "input_dir=/home/cchampeau/Downloads/stack_151124/renamed",
//        "-p", "kind=clahe",
        "-p", "tile_size=32",
        "-p", "sampling=.25",
//        "-f", "jpg",
        //"-i", "/home/cchampeau/Downloads/sunscan_2025_01_06-13_31_58.ser"
//        "-d"
    ))
}
