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
package me.champeau.a4j.math.image

import spock.lang.Specification

class ImagePlaneOnDiskTest extends Specification {

    def "an image moved to disk keeps its pixels and can still be modified"() {
        given:
        def plane = ImagePlane.allocate(1024, 512)
        def row = new float[1024]
        512.times { y ->
            Arrays.fill(row, (float) y)
            plane.setRow(y, row)
        }
        def inMemory = ImageMemory.used()
        def onDisk = ImageMemory.onDisk()

        when:
        def moved = plane.moveToDisk()

        then:
        moved
        plane.isOnDisk()
        ImageMemory.used() == inMemory - 1024L * 512 * Float.BYTES
        ImageMemory.onDisk() == onDisk + 1024L * 512 * Float.BYTES
        plane.get(10, 20) == 20f
        plane.get(1023, 511) == 511f

        when:
        plane.set(10, 20, 42f)
        def copy = plane.copy()

        then:
        plane.get(10, 20) == 42f
        copy.get(10, 20) == 42f
        copy.get(11, 20) == 20f
        !plane.moveToDisk()
    }

    def "an image which is being used by native code is not moved to disk"() {
        given:
        def plane = ImagePlane.allocate(1024, 512)
        def moved = null

        when:
        plane.withSegment { moved = plane.moveToDisk() }

        then:
        moved == false
        plane.moveToDisk()
    }

    def "small images are not moved to disk"() {
        expect:
        !ImagePlane.allocate(64, 64).moveToDisk()
    }

    def "writes made while images are moved to disk are never lost"() {
        when:
        def outcome = MoveToDiskStress.run(48, 1024, 512, 8, 40)

        then:
        outcome.failures() == 0
        outcome.moved() > 40
        outcome.wrongPixels() == 0
    }
}
