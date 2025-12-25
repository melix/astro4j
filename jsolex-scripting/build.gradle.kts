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
        buildArgs.add("-march=compatibility")
        buildArgs.add("--features=me.champeau.a4j.jsolex.ni.JSolExScriptingFeature")
        buildArgs.addAll("--add-modules", "jdk.incubator.vector")
        buildArgs.add("-H:+VectorAPISupport")
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
        "-c", "/home/cchampeau/DEV/astro4j/test-scripts/config.json",
        "-i", "/home/cchampeau/DEV/astro4j/test-scripts/Ha-bin2",
//        "-i", "/home/cchampeau/DEV/astro4j/test-scripts/quick",
        "-s", "/home/cchampeau/DEV/astro4j/test-scripts/load-images.math",
        "-s", "/home/cchampeau/DEV/astro4j/test-scripts/stack-Ha-aggressive-unsharp.math",
        "-s", "/home/cchampeau/DEV/astro4j/test-scripts/anim.math",
//        "-s", "/home/cchampeau/DEV/astro4j/test-scripts/stacking-conti.math",
//        "-p", "input_dir=/home/cchampeau/DEV/astro4j/test-scripts/Ha-bin2",
//        "-p", "input_dir=/home/cchampeau/Downloads/stack_151124/renamed",
//        "-p", "kind=clahe",
        "-p", "tile_size=32",
        "-p", "sampling=.25",
        "-f", "jpg", "-f", "png", "-f", "tif", "-f", "fits", "-f", "mp4", "-f", "gif"
        //"-i", "/home/cchampeau/Downloads/sunscan_2025_01_06-13_31_58.ser"
//        "-d"
    ))
    systemProperty("disable.ffmpeg", "true")
}
