/*
 * Copyright 2026-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.sun

import me.champeau.a4j.jsolex.processing.event.ProgressOperation
import me.champeau.a4j.ser.Camera
import me.champeau.a4j.ser.ColorMode
import me.champeau.a4j.ser.ImageGeometry
import me.champeau.a4j.ser.ImageMetadata
import me.champeau.a4j.ser.SerFileReader
import me.champeau.a4j.ser.SerFileWriter
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.ByteOrder
import java.time.ZoneId
import java.time.ZonedDateTime

class OffDiskAverageImageTest extends Specification {
    private static final int WIDTH = 240
    private static final int HEIGHT = 60
    private static final int DISK_LEFT = 60
    private static final int DISK_RIGHT = 180
    private static final int SATURATION = 65535

    @TempDir
    File tempDir

    def "averages the frames recorded before and after a saturated disk"() {
        given:
        def scan = writeScan(30, 60)

        when:
        def offDisk = computeOffDiskAverage(scan, true)

        then:
        offDisk.present
        offDisk.get().frameCount() == 60

        and: "the spectral line is found over the whole slit"
        def polynomial = new SpectrumFrameAnalyzer(WIDTH, HEIGHT, false, null)
                .analyze(offDisk.get().image())
                .distortionPolynomial()
                .get()
        (0..<WIDTH).every { Math.abs(polynomial.applyAsDouble(it) - linePosition(it)) < 0.2 }
    }

    def "has no off-disk average when the disk never leaves the slit"() {
        given:
        def scan = writeScan(0, 40)

        expect:
        computeOffDiskAverage(scan, true).empty
    }

    def "does not compute the off-disk average unless requested"() {
        given:
        def scan = writeScan(30, 60)

        expect:
        computeOffDiskAverage(scan, false).empty
    }

    private Optional<AverageImageCreator.OffDiskAverage> computeOffDiskAverage(File scan, boolean requested) {
        def reader = SerFileReader.of(scan, true)
        try {
            def creator = new AverageImageCreator(ImageUtils.createImageConverter(ColorMode.MONO, false),
                    ProgressOperation.root('test', {}),
                    Broadcaster.NO_OP,
                    requested)
            creator.computeAverageImage(reader)
            return creator.getOffDiskAverage()
        } finally {
            reader.close()
        }
    }

    private File writeScan(int offDiskFramesOnEachSide, int diskFrames) {
        def file = new File(tempDir, "scan-${offDiskFramesOnEachSide}-${diskFrames}.ser")
        def date = ZonedDateTime.of(2026, 7, 21, 10, 0, 0, 0, ZoneId.of("UTC"))
        def geometry = new ImageGeometry(ColorMode.MONO, WIDTH, HEIGHT, 16, ByteOrder.BIG_ENDIAN)
        def metadata = new ImageMetadata("", "", "", false, date.toLocalDateTime(), date)
        def writer = new SerFileWriter(file, date, new Camera(0), geometry, metadata)
        try {
            int frameCount = 2 * offDiskFramesOnEachSide + diskFrames
            for (int i = 0; i < frameCount; i++) {
                boolean onDisk = i >= offDiskFramesOnEachSide && i < offDiskFramesOnEachSide + diskFrames
                writer.writeMonoFrame(null, { int x, int y -> (short) pixel(x, y, onDisk) } as SerFileWriter.MonoFrameReader)
            }
        } finally {
            writer.close()
        }
        file
    }

    private static int pixel(int x, int y, boolean onDisk) {
        if (onDisk && x >= DISK_LEFT && x < DISK_RIGHT) {
            return SATURATION
        }
        double offset = (x - WIDTH / 2d) / 80d
        double scatteredLight = 3000d + 5000d * Math.exp(-offset * offset)
        double distance = y - linePosition(x)
        (int) (scatteredLight * (1d - 0.5d * Math.exp(-distance * distance / 4.5d)))
    }

    private static double linePosition(int x) {
        double offset = x - WIDTH / 2d
        20d + 8e-4d * offset * offset
    }
}
