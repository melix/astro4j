package me.champeau.a4j.jsolex.processing.util;

import me.champeau.a4j.math.opencl.NativeLibraryLoader;

public class NativeLibrariesUtils {
    public static void ensureNativesLoaded() {
        var version = VersionUtil.getVersion();
        var nativeDir = VersionUtil.getJsolexDir().resolve("native").resolve(version);
        NativeLibraryLoader.ensureNativesLoaded(nativeDir, version);
    }
}
