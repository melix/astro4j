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
    implementation(projects.jsolexCore)
    implementation(mn.micronaut.websocket)
    implementation(mn.micronaut.serde.bson)
    implementation(mn.micronaut.views.thymeleaf)
    implementation(mn.micronaut.views.htmx)
}

application {
    mainClass.set("me.champeau.a4j.jsolex.server.JSolexServer")
}
