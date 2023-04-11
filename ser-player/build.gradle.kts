plugins {
    id("me.champeau.astro4j.jfxapp")
    id("org.graalvm.buildtools.native") version "0.9.20"
}

dependencies {
    implementation(projects.jserfile)
}

javafx {
    version = "17"
    modules("javafx.controls")
}

application {
    mainClass.set("me.champeau.a4j.serplayer.SerPlayer")
}
