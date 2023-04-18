plugins {
    id("me.champeau.astro4j.jfxapp")
}

dependencies {
    implementation(projects.jserfile)
}

application {
    mainModule.set("me.champeau.a4j.serplayer")
    mainClass.set("me.champeau.a4j.serplayer.SerPlayer")
}

jlink {
    launcher {
        name = "ser-player"
    }
}
