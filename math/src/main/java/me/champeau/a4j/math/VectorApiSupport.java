/*
 * Copyright 2023-2023 the original author or authors.
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
package me.champeau.a4j.math;

public class VectorApiSupport {
    public static final String VECTOR_API_ENV_VAR = "VECTOR_API";
    private static final boolean VECTOR_API_PRESENT;

    static {
        boolean check = true;
        try {
            Class.forName("jdk.incubator.vector.VectorOperators");
        } catch (ClassNotFoundException ex) {
            check = false;
        }
        VECTOR_API_PRESENT = check;
    }

    public static boolean isEnabled() {
        var enabled = System.getenv(VECTOR_API_ENV_VAR);
        if (enabled == null) {
            enabled = "true";
        }
        return VECTOR_API_PRESENT && Boolean.parseBoolean(enabled);
    }

    public static boolean isPresent() {
        return VECTOR_API_PRESENT;
    }
}
