import org.gradle.api.attributes.Attribute

val arch = System.getProperty("os.arch")
val lwjglNatives = when {
    org.gradle.internal.os.OperatingSystem.current().isWindows -> "natives-windows"
    org.gradle.internal.os.OperatingSystem.current().isMacOsX -> if (arch == "aarch64") "natives-macos-arm64" else "natives-macos"
    else -> "natives-linux" + (if (arch.contains("aarch") || arch.contains("arm")) { "-arm64" } else { "" })
}

val moduleReady: Attribute<Boolean> = Attribute.of("module-ready", Boolean::class.javaObjectType)

// The GraalVM 25.4 JIT miscompiles our Vector API code: Java code runs on C2,
// while Truffle keeps compiling Python with Graal
val jitSettings = listOf("-XX:+UnlockExperimentalVMOptions", "-XX:-UseJVMCICompiler")
