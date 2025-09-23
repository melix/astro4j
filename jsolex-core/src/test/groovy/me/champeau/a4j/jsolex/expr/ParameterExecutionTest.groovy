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
package me.champeau.a4j.jsolex.expr

import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification

class ParameterExecutionTest extends Specification {
    def "extracts parameter default values as variables"() {
        def script = """meta {
    params {
        brightness {
            type = "number"
            default = 1.5
            min = 0.0
            max = 2.0
        }
        contrast {
            type = "number"
            default = 2.0
        }
        filter_size {
            type = "number"
            default = 3
        }
    }
}

[[single]]
result = brightness + contrast + filter_size"""

        def imageSupplier = { ImageWrapper32.createEmpty() }
        def executor = new DefaultImageScriptExecutor(imageSupplier, [:])

        when:
        def result = executor.execute(script, ImageMathScriptExecutor.SectionKind.SINGLE)

        then:
        // Check that parameters are available as variables in the executor
        executor.getVariable("brightness").isPresent()
        executor.getVariable("brightness").get() == 1.5
        executor.getVariable("contrast").isPresent()
        executor.getVariable("contrast").get() == 2.0
        executor.getVariable("filter_size").isPresent()
        executor.getVariable("filter_size").get() == 3L
    }

    def "handles string parameters"() {
        def script = """meta {
    params {
        method {
            type = "choice"
            default = "median"
            choices = "median,average,maximum"
        }
    }
}

[[single]]
result = method"""

        def imageSupplier = { ImageWrapper32.createEmpty() }
        def executor = new DefaultImageScriptExecutor(imageSupplier, [:])

        when:
        def result = executor.execute(script, ImageMathScriptExecutor.SectionKind.SINGLE)

        then:
        executor.getVariable("method").isPresent()
        executor.getVariable("method").get() == "median"
    }
}