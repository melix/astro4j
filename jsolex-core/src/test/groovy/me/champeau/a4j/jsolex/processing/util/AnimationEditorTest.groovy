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
package me.champeau.a4j.jsolex.processing.util

import spock.lang.Specification
import spock.lang.TempDir

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Path

class AnimationEditorTest extends Specification {
    private static final int WIDTH = 64
    private static final int HEIGHT = 48
    private static final int FRAMES = 6

    @TempDir
    Path tempDir

    def "rotates, flips and crops a single frame"() {
        given:
        def frame = gradientFrame(0)

        when:
        def rotated = new AnimationEditor.Rotate(1).apply(frame)
        def flipped = new AnimationEditor.Flip(true).apply(frame)
        def cropped = new AnimationEditor.Crop(10, 5, 20, 10).apply(frame)

        then:
        rotated.width == HEIGHT
        rotated.height == WIDTH
        rotated.getRGB(HEIGHT - 1, 0) == frame.getRGB(0, 0)
        rotated.getRGB(0, 0) == frame.getRGB(0, HEIGHT - 1)
        flipped.getRGB(3, 0) == frame.getRGB(3, HEIGHT - 1)
        cropped.width == 20
        cropped.height == 10
        cropped.getRGB(0, 0) == frame.getRGB(10, 5)
    }

    def "four quarter turns are the identity"() {
        given:
        def frame = gradientFrame(2)

        when:
        def result = AnimationEditor.apply(frame, (1..4).collect { new AnimationEditor.Rotate(1) })

        then:
        pixels(result) == pixels(frame)
    }

    def "transforms every frame of a #format animation"() {
        given:
        def source = encode(format)
        def operations = [new AnimationEditor.Rotate(1), new AnimationEditor.Crop(4, 4, 30, 40)]

        when:
        def result = AnimationEditor.transform(source, tempDir.resolve("out"), operations, { })

        then:
        result.fileName.toString().endsWith(format.extension())
        (0..<FRAMES).every { i ->
            def position = i * 0.1
            def actual = AnimationEditor.readFrame(result, position)
            def expected = AnimationEditor.apply(AnimationEditor.readFrame(source, position), operations)
            actual.width == 30 && actual.height == 40 && Math.abs(brightness(actual) - brightness(expected)) < 12
        }
        brightness(AnimationEditor.readFrame(result, 100)) > brightness(AnimationEditor.readFrame(result, 0)) + 100

        where:
        format << AnimationFormat.values()
    }

    def "reads the frame at a given position"() {
        given:
        def source = encode(format)

        expect:
        def frame = AnimationEditor.readFrame(source, 0)
        frame.width == WIDTH
        frame.height == HEIGHT
        Math.abs(brightness(frame) - brightness(gradientFrame(0))) < 8

        where:
        format << AnimationFormat.values()
    }

    def "transforms a single-frame #format animation"() {
        given:
        def source = encode(format, 1)

        when:
        def result = AnimationEditor.transform(source, tempDir.resolve("out"), [new AnimationEditor.Flip(false)], { })
        def frame = AnimationEditor.readFrame(result, 0)

        then:
        frame.width == WIDTH
        frame.height == HEIGHT
        Math.abs(brightness(frame) - brightness(gradientFrame(0))) < 12

        where:
        format << AnimationFormat.values()
    }

    def "reads and transforms frames of a video with B-frames in presentation order"() {
        given:
        def video = Path.of(AnimationEditorTest.getResource("/animation/bframes.mp4").toURI())

        when:
        def levels = (0..<FRAMES).collect { brightness(AnimationEditor.readFrame(video, it * 0.1)) }
        def flipped = AnimationEditor.transform(video, tempDir.resolve("out"), [new AnimationEditor.Flip(true)], { })
        def flippedLevels = (0..<FRAMES).collect { brightness(AnimationEditor.readFrame(flipped, it * 0.1)) }

        then:
        (1..<FRAMES).every { levels[it] > levels[it - 1] + 30 }
        (1..<FRAMES).every { flippedLevels[it] > flippedLevels[it - 1] + 30 }
        (0..<FRAMES).every { Math.abs(levels[it] - flippedLevels[it]) < 12 }
    }

    private Path encode(AnimationFormat format, int frames = FRAMES) {
        def base = tempDir.resolve("source")
        def files = VideoEncoder.encodeToMultipleFormats(base.toString(), Set.of(format), frames, 10, 100, { i -> gradientFrame(i) }, null)
        files.first().toPath()
    }

    private static BufferedImage gradientFrame(int index) {
        def image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int level = Math.min(255, 40 * index + (x + y).intdiv(2))
                image.setRGB(x, y, new Color(level, level, level).RGB)
            }
        }
        image
    }

    private static int[] pixels(BufferedImage image) {
        image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    }

    private static double brightness(BufferedImage image) {
        pixels(image).collect { it & 0xFF }.sum() / (double) (image.width * image.height)
    }
}
