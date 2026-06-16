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
package me.champeau.a4j.jsolex.processing.session

import me.champeau.a4j.jsolex.processing.params.ProcessParams
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange
import me.champeau.a4j.jsolex.processing.util.FileBackedImage
import me.champeau.a4j.jsolex.processing.util.ImageWrapper
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.RGBImage
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class SessionRoundTripTest extends Specification {

    @TempDir
    Path tempDir

    def "round-trips mono and RGB images with their metadata"() {
        given: "a mono image carrying process params, a pixel shift and an ellipse"
        def monoMeta = [:] as Map<Class<?>, Object>
        def params = ProcessParamsIO.createNewDefaults()
        monoMeta.put(ProcessParams.class, params)
        monoMeta.put(PixelShift.class, new PixelShift(1.5d))
        monoMeta.put(Ellipse.class, Ellipse.ofCartesian(new DoubleSextuplet(1d, 2d, 3d, 4d, 5d, 6d)))
        def mono = new ImageWrapper32(64, 48, synthesise(64, 48, 1), monoMeta)

        and: "an RGB image with no metadata"
        def rgb = new RGBImage(32, 24,
                synthesise(32, 24, 2), synthesise(32, 24, 3), synthesise(32, 24, 4),
                [:] as Map<Class<?>, Object>)

        and: "a session containing both"
        def data = new SessionData([
                new SessionImage(GeneratedImageKind.GEOMETRY_CORRECTED, "Mono", "mono", "a mono image", mono),
                new SessionImage(GeneratedImageKind.COLORIZED, "Color", "color", null, rgb)
        ], [] as List<SessionMedia>)

        when: "the session is written and read back"
        def file = tempDir.resolve("session" + Session.FILE_EXTENSION)
        SessionWriter.write(data, file, null)
        def restored = SessionReader.read(file, null)

        then: "the file exists and is not empty"
        Files.exists(file)
        Files.size(file) > 0

        and: "both images are restored in order"
        restored.images().size() == 2
        restored.media().isEmpty()

        and: "the mono image matches pixel-for-pixel"
        def restoredMono = restored.images()[0]
        restoredMono.kind() == GeneratedImageKind.GEOMETRY_CORRECTED
        restoredMono.title() == "Mono"
        restoredMono.baseName() == "mono"
        restoredMono.description() == "a mono image"
        def monoImg = (ImageWrapper32) restoredMono.image()
        monoImg.width() == 64
        monoImg.height() == 48
        floatArraysEqual(monoImg.data(), mono.data())

        and: "its metadata round-trips"
        monoImg.findMetadata(PixelShift.class).get().pixelShift() == 1.5d
        monoImg.findMetadata(ProcessParams.class).isPresent()
        def restoredEllipse = monoImg.findMetadata(Ellipse.class).get()
        restoredEllipse.cartesianCoefficients == new DoubleSextuplet(1d, 2d, 3d, 4d, 5d, 6d)

        and: "the RGB image matches on all three channels"
        def rgbImg = (RGBImage) restored.images()[1].image()
        floatArraysEqual(rgbImg.r(), rgb.r())
        floatArraysEqual(rgbImg.g(), rgb.g())
        floatArraysEqual(rgbImg.b(), rgb.b())
    }

    def "round-trips file-backed images and skips unknown metadata"() {
        given: "an image wrapped as a FileBackedImage, as it is held by the viewer"
        def meta = [:] as Map<Class<?>, Object>
        meta.put(PixelShift.class, new PixelShift(0.5d))
        // a metadata type that is not in the allowlist: must be skipped, not fail the export
        meta.put(String.class, "some-internal-marker")
        def mono = new ImageWrapper32(40, 30, synthesise(40, 30, 9), meta)
        def backed = FileBackedImage.wrap(mono)
        def data = new SessionData([
                new SessionImage(GeneratedImageKind.RAW, "Backed", "backed", null, backed)
        ], [] as List<SessionMedia>)

        when:
        def file = tempDir.resolve("backed" + Session.FILE_EXTENSION)
        SessionWriter.write(data, file, null)
        def restored = SessionReader.read(file, null)

        then: "the image is restored with the allowlisted metadata, the unknown one dropped"
        restored.images().size() == 1
        def img = (ImageWrapper32) restored.images()[0].image()
        floatArraysEqual(img.data(), mono.data())
        img.findMetadata(PixelShift.class).get().pixelShift() == 0.5d
        img.findMetadata(String.class).isEmpty()
    }

    def "overwrites an existing session file"() {
        given:
        def mono = new ImageWrapper32(20, 20, synthesise(20, 20, 5), [:] as Map<Class<?>, Object>)
        def data = new SessionData([
                new SessionImage(GeneratedImageKind.RAW, "t", "t", null, mono)
        ], [] as List<SessionMedia>)
        def file = tempDir.resolve("ma_session" + Session.FILE_EXTENSION)

        when: "the same target is written twice"
        SessionWriter.write(data, file, null)
        def firstSize = Files.size(file)
        SessionWriter.write(data, file, null)

        then: "no exception is thrown, the file is valid and no temp file is left behind"
        Files.exists(file)
        Files.size(file) == firstSize
        !Files.exists(tempDir.resolve("ma_session" + Session.FILE_EXTENSION + ".tmp"))
        SessionReader.read(file, null).images().size() == 1
    }

    def "concurrent exports to the same target do not collide"() {
        given:
        def mk = {
            def mono = new ImageWrapper32(64, 64, synthesise(64, 64, 3), [:] as Map<Class<?>, Object>)
            new SessionData([
                    new SessionImage(GeneratedImageKind.RAW, "t", "t", null, mono)
            ], [] as List<SessionMedia>)
        }
        def file = tempDir.resolve("concurrent" + Session.FILE_EXTENSION)
        def errors = Collections.synchronizedList([])

        when: "two exports run at the same time against the same destination"
        def threads = (1..2).collect {
            Thread.start {
                try {
                    SessionWriter.write(mk(), file, null)
                } catch (Throwable t) {
                    errors.add(t)
                }
            }
        }
        threads*.join()

        then: "neither fails and the resulting file is a valid session"
        errors.isEmpty()
        Files.exists(file)
        SessionReader.read(file, null).images().size() == 1

        and: "no temporary files are left behind"
        Files.list(tempDir).filter { it.toString().endsWith(".tmp") }.count() == 0
    }

    def "reports one completion tick per item, covering 1/n..1.0"() {
        given:
        def imgs = (0..<5).collect { idx ->
            new SessionImage(GeneratedImageKind.RAW, "img$idx", "img$idx", null,
                    new ImageWrapper32(16, 16, synthesise(16, 16, idx), [:] as Map<Class<?>, Object>))
        }
        def data = new SessionData(imgs, [] as List<SessionMedia>)
        def file = tempDir.resolve("progress" + Session.FILE_EXTENSION)
        def fractions = Collections.synchronizedList([])

        when:
        SessionWriter.write(data, file, { fraction, item -> fractions.add(fraction) } as SessionProgressListener)

        then: "exactly one report per image, completion fractions 0.2..1.0 are all reported"
        fractions.size() == 5
        (new HashSet(fractions)) == ([0.2d, 0.4d, 0.6d, 0.8d, 1.0d] as Set)
    }

    def "round-trips re-run data (single runs and batch outputs)"() {
        given: "a single run descriptor and a batch outputs map"
        def params = ProcessParamsIO.createNewDefaults()
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1d, 2d, 3d, 4d, 5d, 6d))
        def singleRun = new SessionSingleRun("/data/sun.ser", params, ellipse, new PixelShiftRange(-5d, 5d, 0.5d))

        def img1 = new ImageWrapper32(16, 16, synthesise(16, 16, 1), [:] as Map<Class<?>, Object>)
        def img2 = new ImageWrapper32(16, 16, synthesise(16, 16, 2), [:] as Map<Class<?>, Object>)
        def imagesByLabel = ["recon": [img1, img2] as List<ImageWrapper>] as Map<String, List<ImageWrapper>>
        def valuesByLabel = ["score": [1.5d, 2.0d] as List<Object>, "name": ["best"] as List<Object>] as Map<String, List<Object>>
        def batch = new SessionBatchOutputs(imagesByLabel, valuesByLabel)

        def reRun = new SessionReRunData([singleRun], batch)
        def data = new SessionData([] as List<SessionImage>, [] as List<SessionMedia>, reRun)
        def file = tempDir.resolve("rerun" + Session.FILE_EXTENSION)

        when:
        SessionWriter.write(data, file, null)
        def restored = SessionReader.read(file, null)

        then: "the single run round-trips with its SER path, params and ellipse"
        restored.reRun() != null
        restored.reRun().singleRuns().size() == 1
        def rr = restored.reRun().singleRuns()[0]
        rr.serFilePath() == "/data/sun.ser"
        rr.params() != null
        rr.ellipse().cartesianCoefficients == new DoubleSextuplet(1d, 2d, 3d, 4d, 5d, 6d)

        and: "the batch outputs round-trip"
        def outs = restored.reRun().batchOutputs()
        outs.imagesByLabel().get("recon").size() == 2
        floatArraysEqual(((ImageWrapper32) outs.imagesByLabel().get("recon")[0]).data(), img1.data())
        outs.valuesByLabel().get("score") == [1.5d, 2.0d]
        outs.valuesByLabel().get("name") == ["best"]
    }

    def "round-trips media files"() {
        given: "a fake media file"
        def media = tempDir.resolve("anim.mp4")
        Files.write(media, "fake-video-bytes".getBytes("UTF-8"))
        def data = new SessionData([] as List<SessionImage>, [
                new SessionMedia(GeneratedImageKind.RAW, "Anim", "an animation", media, SessionMedia.Type.VIDEO)
        ])

        when:
        def file = tempDir.resolve("media-session" + Session.FILE_EXTENSION)
        SessionWriter.write(data, file, null)
        def restored = SessionReader.read(file, null)

        then:
        restored.media().size() == 1
        def restoredMedia = restored.media()[0]
        restoredMedia.kind() == GeneratedImageKind.RAW
        restoredMedia.title() == "Anim"
        restoredMedia.type() == SessionMedia.Type.VIDEO
        new String(Files.readAllBytes(restoredMedia.file()), "UTF-8") == "fake-video-bytes"
    }

    private static float[][] synthesise(int width, int height, int seed) {
        def random = new Random(seed)
        def data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = 1000.0f + random.nextFloat() * 60000.0f
            }
        }
        return data
    }

    private static boolean floatArraysEqual(float[][] a, float[][] b) {
        if (a.length != b.length) {
            return false
        }
        for (int y = 0; y < a.length; y++) {
            if (!Arrays.equals(a[y], b[y])) {
                return false
            }
        }
        return true
    }
}
