plugins {
    id("me.champeau.astro4j.mnapp")
}

description = "The JSol'Ex embedded server"

micronaut {
    runtime("netty")
    testRuntime("spock")
    processing {
        incremental(true)
        annotations("me.champeau.a4j.jsolex.server.*")
    }
}

dependencies {
    annotationProcessor(mn.micronaut.serde.processor)
    annotationProcessor(mn.micronaut.openapi)
    compileOnly(mn.micronaut.openapi.annotations)
    implementation(projects.jsolexCore)
    implementation(mn.micronaut.serde.jackson)
    implementation(mn.micronaut.websocket)
    implementation(mn.micronaut.serde.bson)
    implementation(mn.micronaut.views.thymeleaf)
    implementation(mn.micronaut.views.htmx)
    testImplementation(mn.groovy.json)
}

application {
    mainClass.set("me.champeau.a4j.jsolex.server.JSolexServer")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:none")
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.addAll(listOf(
        "-Amicronaut.openapi.filename=jsolex-ui-api",
        "-Amicronaut.openapi.environments=ui-api"
    ))
}