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
package me.champeau.a4j.jsolex.app.listeners

import me.champeau.a4j.jsolex.processing.event.ProgressOperation
import spock.lang.Specification

import static me.champeau.a4j.jsolex.processing.util.Constants.message

class ReconstructionProgressTest extends Specification {

    def "the progress never goes backwards and ignores the sub-steps"() {
        given:
        def root = ProgressOperation.root("root", {})
        def average = root.createChild(message("computing.average.image.limb.detect"))
        def reconstruction = root.createChild(message("reconstruction"))
        def outerGeneration = root.createChild(message("generating.images"))
        def preparation = root.createChild(message("preparing.for.corrections"))
        def ellipse = root.createChild(message("ellipse.fitting"))
        def generation = root.createChild(message("generating.images"))
        def geometry = generation.createChild("geometry")
        def progress = new ReconstructionProgress()
        def values = []
        def record = { ProgressOperation operation -> progress.update(operation).ifPresent { values << it.progress() } }

        when:
        [{ average.update(0) }, { average.update(1) },
         { reconstruction.update(0) }, { reconstruction.update(0.5) }, { reconstruction.update(1) },
         { outerGeneration.update(0) },
         { preparation.update(0) }, { ellipse.update(0) }, { ellipse.update(1) }, { preparation.update(0.5) }, { preparation.update(1) },
         { generation.update(0) }, { geometry.update(0.9) }, { geometry.update(0) }, { generation.update(0.5) }, { generation.update(1) },
         { outerGeneration.update(1) }].each { record(it()) }

        then:
        values == values.toSorted()
        values.first() == 0
        values.last() == 1
        values.contains(0.25d)
        values.contains(0.575d)
        values.size() == 12
    }

    def "the average image step is optional"() {
        given:
        def root = ProgressOperation.root("root", {})
        def progress = new ReconstructionProgress()

        when:
        def state = progress.update(root.createChild(message("reconstruction")).update(1))

        then:
        state.present
        state.get().progress() == 0.25d
    }
}
