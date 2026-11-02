/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.jsolex.ni;

import me.champeau.a4j.jsolex.processing.expr.python.PythonImageMathBridge;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JSolExScriptingFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            access.registerAsUsed(Class.forName("me.champeau.a4j.math.image.OpenCLImageMath"));
            access.registerAsUsed(Class.forName("me.champeau.a4j.math.image.VectorApiImageMath"));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        var registered = new HashSet<Class<?>>();
        for (var type : List.of(PythonImageMathBridge.class, ImageWrapper.class, ImageWrapper32.class, RGBImage.class, FileBackedImage.class, ProcessParams.class)) {
            registerPythonHostAccess(type, registered);
        }
    }

    private static void registerPythonHostAccess(Class<?> type, Set<Class<?>> registered) {
        if (!registered.add(type)) {
            return;
        }
        RuntimeReflection.register(type);
        RuntimeReflection.register(type.getMethods());
        if (type.isRecord()) {
            for (var component : type.getRecordComponents()) {
                var componentType = component.getType();
                if (componentType.isRecord() || componentType.isEnum()) {
                    registerPythonHostAccess(componentType, registered);
                }
            }
        }
    }
}
