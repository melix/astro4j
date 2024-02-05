pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("io.micronaut.platform.catalog") version "4.3.0"
}

rootProject.name = "astro4j-parent"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include("docs")
include("jserfile")
include("ser-player")
include("math")
include("jsolex-core")
include("jsolex-cli")
include("jsolex")
