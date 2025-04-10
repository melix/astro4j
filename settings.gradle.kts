pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("io.micronaut.platform.catalog") version "4.5.1"
}

rootProject.name = "astro4j-parent"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include("docs")
include("jserfile")
include("ser-player")
include("math")
include("jsolex-server")
include("jsolex-core")
include("jsolex-cli")
include("jsolex")
include("jsolex-scripting")
