package me.champeau.a4j.jsolex.ni;

import org.graalvm.nativeimage.hosted.Feature;

public class JSolExScriptingFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            access.registerAsUsed(Class.forName("me.champeau.a4j.math.image.OpenCLImageMath"));
            access.registerAsUsed(Class.forName("me.champeau.a4j.math.image.VectorApiImageMath"));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
