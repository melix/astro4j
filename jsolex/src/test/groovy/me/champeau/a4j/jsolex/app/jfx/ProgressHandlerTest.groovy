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
package me.champeau.a4j.jsolex.app.jfx

import me.champeau.a4j.jsolex.processing.event.ProgressOperation
import spock.lang.Specification

class ProgressHandlerTest extends Specification {

    private final ProgressHandler handler = new ProgressHandler({ })

    def setup() {
        handler.close()
    }

    def "a late completion does not bring back a finished batch"() {
        given:
        var batch = startBatch()
        var file = batch.createChild("file.ser")
        handler.ensureTracked(file)
        handler.setCompletionMessage("Batch finished")

        when:
        var finished = handler.computeSnapshot()
        handler.ensureTracked(file.complete())
        var afterLateCompletion = handler.computeSnapshot()

        then:
        finished.currentTaskLabel() == "Batch finished"
        afterLateCompletion.currentTaskLabel() == "Batch finished"
        afterLateCompletion.progress() == 1.0d
    }

    def "a root whose children are all complete does not keep spinning"() {
        given:
        var batch = startBatch()
        var file = batch.createChild("file.ser")
        handler.ensureTracked(file)
        handler.setCompletionMessage("Batch finished")
        handler.computeSnapshot()

        when: "a task of the batch reports progress after the end of the batch"
        var late = file.createChild("Saving")
        handler.ensureTracked(late)
        var running = handler.computeSnapshot()
        late.complete()
        file.complete()
        var done = handler.computeSnapshot()

        then:
        running.progress() == -1d
        running.currentTaskLabel() == "Batch processing / file.ser / Saving"
        done == ProgressHandler.ProgressSnapshot.hidden()
    }

    private ProgressOperation startBatch() {
        var batch = ProgressOperation.root("Batch processing", { })
        handler.registerRoot(batch)
        batch
    }
}
