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
package me.champeau.a4j.jsolex.processing.sun.align

import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.sun.ImageUtils
import me.champeau.a4j.math.image.Image
import me.champeau.a4j.math.image.ImageMath
import spock.lang.PendingFeature
import spock.lang.Specification
import spock.lang.Subject

import javax.imageio.ImageIO

import static java.lang.Math.abs

class AlignerTest extends Specification {
    private static final double EPSILON = 0.015d
    private static final List<Float> ROTATION_ANGLES = (-3..3).collect { (float) (it * Math.PI / 4) }

    private static final Image REFERENCE = loadImage("meudon-ref.png")
    private static final Image STRETCHED = loadImage("candidate-stretched.png")
    private static final ImageMath IMAGE_MATH = ImageMath.newInstance()

    @Subject
    Aligner aligner

    Broadcaster broadcaster = Stub(Broadcaster)

    def "can detect rotation of the same image without flipping nor rotation"() {
        var ref = REFERENCE
        aligner = Aligner.forReferenceImage(broadcaster, ref)

        when:
        def aligned = aligner.align(ref)

        then:
        aligned.rotation() == 0
        !aligned.flipped()
    }

    def "can detect flips without rotation"() {
        var ref = REFERENCE
        aligner = Aligner.forReferenceImage(broadcaster, ref)

        expect:
        verifyAll {
            !shouldFlip(ref, false, false)
            shouldFlip(ref, true, false)
            shouldFlip(ref, false, true)
            !shouldFlip(ref, true, true)
        }
    }

    def "can detect flips with rotation"() {
        var ref = REFERENCE
        aligner = Aligner.forReferenceImage(broadcaster, ref)
        given:
        var candidate = IMAGE_MATH.rotate(REFERENCE, angle, 0, true)

        expect:
        verifyAll {
            !shouldFlip(candidate, false, false)
            shouldFlip(candidate, true, false)
            shouldFlip(candidate, false, true)
            !shouldFlip(candidate, true, true)
        }

        where:
        angle << ROTATION_ANGLES
    }

    def "can detect rotation of the same image without flipping (angle #angle)"() {
        aligner = Aligner.forReferenceImage(broadcaster, REFERENCE)

        when:
        var candidate = IMAGE_MATH.rotate(REFERENCE, angle, 0, true)
        def aligned = aligner.align(candidate)
        double detectedRotation = aligned.rotation()

        then:
        withinTolerance(detectedRotation, -angle)
        !aligned.flipped()

        where:
        angle << ROTATION_ANGLES
    }

    @PendingFeature
    def "can detect rotation of a similar image without flipping (angle #angle)"() {
        aligner = Aligner.forReferenceImage(broadcaster, REFERENCE)
        double realRotation = .38d

        when:
        var candidate = IMAGE_MATH.rotate(STRETCHED, angle, 0, true)
        def aligned = aligner.align(candidate)
        double detectedRotation = aligned.rotation()

        then:
        ImageUtils.writeMonoImage(aligned.rotated().width(), aligned.rotated().height(), aligned.rotated().data(), new File("/tmp/rotation-$angle-${detectedRotation}.png"))
        new File("/tmp/data.txt").append """
            angle: $angle detected: $detectedRotation diff: ${abs(detectedRotation + angle)}
        """
        withinTolerance(detectedRotation - realRotation, -angle)
        !aligned.flipped()

        where:
        angle << ROTATION_ANGLES
    }

    @PendingFeature
    def "can detect rotation of a similar image with flipping (angle #angle)"() {
        aligner = Aligner.forReferenceImage(broadcaster, REFERENCE)
        double realRotation = .38d

        when:
        var min = REFERENCE.data().toList().min()
        var candidate = IMAGE_MATH.rotate(STRETCHED, angle, min, true)
        candidate = IMAGE_MATH.mirror(candidate, false, true)
        def aligned = aligner.align(candidate)
        double detectedRotation = aligned.rotation()

        then:
        ImageUtils.writeMonoImage(aligned.rotated().width(), aligned.rotated().height(), aligned.rotated().data(), new File("/tmp/rotation-$angle-${detectedRotation}.png"))
        new File("/tmp/data.txt").append """
            angle: $angle detected: $detectedRotation diff: ${abs(detectedRotation + angle)}
        """
        withinTolerance(detectedRotation - realRotation, -angle)
        !aligned.flipped()

        where:
        angle << ROTATION_ANGLES
    }

    private static void withinTolerance(double a, double b) {
        double diff = abs(a - b)
        diff = abs(Math.min(2 * Math.PI - diff, diff))
        println("Diff = $diff")
        assert diff < EPSILON
    }

    private static Image loadImage(String name) {
        var img = ImageIO.read(AlignerTest.getResource(name))
        var refW = img.width
        var refH = img.height
        var loaded = new float[refW * refH]
        for (int x = 0; x < refW; x++) {
            for (int y = 0; y < refH; y++) {
                loaded[x + y * refW] = (img.getRGB(x, y) & 0xFF) << 8
            }
        }
        new Image(refW, refH, loaded)
    }

    private boolean shouldFlip(Image image, boolean horizontal, boolean vertical) {
        def img = IMAGE_MATH.mirror(image, horizontal, vertical)
        aligner.align(img).flipped()
    }
}
