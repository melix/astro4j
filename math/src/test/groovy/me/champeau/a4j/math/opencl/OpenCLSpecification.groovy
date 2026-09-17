/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.math.opencl

import spock.lang.Specification

/**
 * Base class for tests which obtain a GPU context through
 * {@link OpenCLSupport#getContext()}. That method hands back a context only
 * when OpenCL is explicitly opted in, so the property has to be set for the
 * whole duration of the test, not merely available on the machine.
 *
 * <p>Subclasses still carry their own {@code @Requires} guard, because Spock
 * does not inherit conditions from a base specification.
 */
abstract class OpenCLSpecification extends Specification {

    def setup() {
        System.setProperty(OpenCLSupport.OPENCL_SYSTEM_PROPERTY, "true")
    }

    def cleanup() {
        System.clearProperty(OpenCLSupport.OPENCL_SYSTEM_PROPERTY)
    }
}
