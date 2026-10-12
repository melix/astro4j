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
package me.champeau.a4j.jsolex.processing.spectrum.cloud

import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift
import me.champeau.a4j.jsolex.processing.util.ImageWrapper
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.math.opencl.OpenCLSupport
import me.champeau.a4j.math.regression.Ellipse
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

class CloudInversionTest extends Specification {
    private static final double LAMBDA0 = 6562.81
    private static final double DISPERSION = 0.0906
    private static final double INSTRUMENT_SIGMA = 0.11
    private static final double CONTINUUM = 30000
    private static final int SIZE = 120
    private static final double RADIUS = 50
    private static final double CLOUD_VELOCITY = -12
    private static final double CLOUD_WIDTH = 0.32
    private static final double ROTATION_KMS = 3
    private static final double CENTER_RING_CONTINUUM = CONTINUUM * (0.4 + 0.6 * 0.956)

    @Shared
    CloudInversionResult result

    @Shared
    CloudInversionResult flipped

    @Shared
    CloudInversionResult rotating

    def setupSpec() {
        System.setProperty(OpenCLSupport.OPENCL_SYSTEM_PROPERTY, 'false')
        try {
            result = synthesize(1, 0)
            flipped = synthesize(-1, 0)
            rotating = synthesize(1, ROTATION_KMS)
        } finally {
            System.clearProperty(OpenCLSupport.OPENCL_SYSTEM_PROPERTY)
        }
    }

    /**
     * Synthesizes an observation of a quiet disk with one cloud.
     *
     * @param orientation 1 if the pixel shifts increase towards longer wavelengths, -1 otherwise
     * @param rotation the line of sight velocity at the east and west limbs, in km/s
     */
    private static CloudInversionResult synthesize(int orientation, double rotation) {
        synthesize(orientation, rotation, 0.15, 0.8, 1)
    }

    private static CloudInversionResult synthesize(int orientation, double rotation, double source, double opacity, int binning) {
        def shifts = (-33..33).collect { it as double } as double[]
        def center = SIZE / 2d
        def data = (0..<shifts.length).collect { new float[SIZE][SIZE] }
        def random = new Random(11)
        for (int x = 0; x < SIZE; x++) {
            def dx = (x - center) / RADIUS
            def columnShift = rotation * dx * LAMBDA0 / CloudModel.SPEED_OF_LIGHT_KMS
            def offsets = shifts.collect { orientation * it * DISPERSION - columnShift } as double[]
            def sorted = offsets.toList().withIndex().sort { it[0] }
            def grid = SpectralGrid.of(sorted.collect { it[0] } as double[], INSTRUMENT_SIGMA)
            def atlas = grid.fineOffsets().collect { InstrumentProfileEstimator.atlasAt(LAMBDA0 + it) } as double[]
            def model = new CloudModel(grid, LAMBDA0)
            def quiet = new double[grid.sampleCount()]
            grid.sample(atlas, quiet)
            def cloudy = new double[grid.sampleCount()]
            model.evaluate(CloudModel.single(source, CLOUD_WIDTH, opacity, CLOUD_VELOCITY, 1), 1, CloudModel.Background.of(atlas, grid.fineOffsets()), cloudy, null)
            for (int y = 0; y < SIZE; y++) {
                def dy = (y - center) / RADIUS
                def r2 = dx * dx + dy * dy
                def darkening = r2 < 1 ? 0.4 + 0.6 * Math.sqrt(1 - r2) : 0.02
                def profile = inCloud(x, y) ? cloudy : quiet
                for (int k = 0; k < sorted.size(); k++) {
                    def i = sorted[k][1] as int
                    def value = CONTINUUM * darkening * profile[k]
                    data[i][y][x] = (float) (value * (1 + 0.003 * random.nextGaussian()))
                }
            }
        }
        def ellipse = CloudInversion.circle(center, center, RADIUS)
        def images = data.withIndex().collect { d, i -> new ImageWrapper32(SIZE, SIZE, d, [(Ellipse): ellipse, (PixelShift): new PixelShift(shifts[i])]) as ImageWrapper }
        CloudInversion.invert(new CloudInversion.Input(images, shifts, ellipse, LAMBDA0, DISPERSION), CloudInversion.Options.DEFAULT.withBinning(binning), {})
    }

    private static boolean inCloud(int x, int y) {
        x >= 55 && x < 70 && y >= 50 && y < 65
    }

    def "calibrates the instrumental profile and the wavelength scale"() {
        expect:
        def calibration = result.calibration()
        Math.abs(calibration.instrumentSigma() - INSTRUMENT_SIGMA) < 0.01
        Math.abs(calibration.offset()) < 0.01
        Math.abs(calibration.dispersionScale() - 1) < 0.01
        calibration.strayLight() < 0.01
        Math.abs(calibration.continuum() - CENTER_RING_CONTINUUM) / CENTER_RING_CONTINUUM < 0.02
    }

    def "detects a spectrum whose wavelengths decrease with the pixel shift"() {
        expect:
        Math.abs(flipped.calibration().dispersionScale() + 1) < 0.01
        Math.abs(flipped.calibration().instrumentSigma() - INSTRUMENT_SIGMA) < 0.01
    }

    def "recovers the cloud velocity and finds no absorption on the quiet disk"() {
        expect:
        Math.abs(medianCloudVelocity(inversion) - CLOUD_VELOCITY) < 2
        def stats = inversion.statistics()
        stats.fittedPixels() + stats.noCloudPixels() > 0.95 * stats.diskPixels()
        stats.noCloudPixels() > 0.8 * (stats.diskPixels() - cloudArea())

        where:
        inversion << [result, flipped, rotating]
    }

    private static double medianCloudVelocity(CloudInversionResult inversion, int minimumPoints = 50) {
        def velocity = inversion.data(CloudMap.MASS_FLUX_VELOCITY)
        def status = inversion.data(CloudMap.STATUS)
        def cloud = []
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (status[y][x] == CloudMap.STATUS_FITTED && inCloud(x, y) && inCloud(x - 2, y - 2) && inCloud(x + 2, y + 2)) {
                    cloud << velocity[y][x]
                }
            }
        }
        assert cloud.size() > minimumPoints
        cloud.sort()[cloud.size() / 2 as int]
    }

    private static int cloudArea() {
        int count = 0
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (inCloud(x, y)) {
                    count++
                }
            }
        }
        count
    }

    def "the maps have no pixel shift"() {
        expect:
        !result.metadata().containsKey(PixelShift)
        !result.image(CloudMap.MASS_FLUX_VELOCITY).findMetadata(PixelShift).present
    }

    def "the fit at one position can be shown"() {
        when:
        def fit = result.fitAt(62, 57)

        then:
        fit.present
        fit.get().observed().length == fit.get().fitted().length
        Math.abs(fit.get().quantities().massFluxVelocity() - result.data(CloudMap.MASS_FLUX_VELOCITY)[57][62]) < 1e-3
    }

    def "averages blocks of pixels and returns maps at the size of the images"() {
        when:
        def binned = synthesize(1, 0, 0.15, 0.8, 2)

        then:
        binned.width() == SIZE
        binned.height() == SIZE
        binned.binning() == 2
        Math.abs(medianCloudVelocity(binned, 30) - CLOUD_VELOCITY) < 2
        binned.fitAt(62, 57).present
        !binned.metadata().containsKey(PixelShift)
        !binned.image(CloudMap.MASS_FLUX_VELOCITY).findMetadata(PixelShift).present
        binned.data(CloudMap.MASS_FLUX_VELOCITY)[57][62] == binned.data(CloudMap.MASS_FLUX_VELOCITY)[56][63]
    }

    def "does not measure the velocity of a weak absorption"() {
        when:
        def weak = synthesize(1, 0, 0.15, 0.01, 1)

        then:
        def detection = weak.data(CloudMap.DETECTION)
        def status = weak.data(CloudMap.STATUS)
        int defined = 0
        int weakClouds = 0
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (status[y][x] == CloudMap.STATUS_FITTED && detection[y][x] < CloudInversionResult.MIN_DETECTION) {
                    weakClouds++
                    if (weak.isDefined(CloudMap.MASS_FLUX_VELOCITY, x, y) || weak.isDefined(CloudMap.TEMPERATURE, x, y)) {
                        defined++
                    }
                }
            }
        }
        weakClouds > 0
        defined == 0
        weak.statistics().detectedPixels() < cloudArea()
    }

    def "detects a strong absorption"() {
        expect:
        def detection = result.data(CloudMap.DETECTION)
        int detected = 0
        for (int y = 55; y < 65; y++) {
            for (int x = 60; x < 68; x++) {
                if (detection[y][x] >= CloudInversionResult.MIN_DETECTION) {
                    detected++
                }
            }
        }
        detected > 70
    }

    def "does not apply the model where the line is brighter than its background"() {
        when:
        def bright = synthesize(1, 0, 0.35, 2, 1)

        then:
        def status = bright.data(CloudMap.STATUS)
        int brighter = 0
        for (int y = 52; y < 63; y++) {
            for (int x = 57; x < 68; x++) {
                if (status[y][x] == CloudMap.STATUS_BRIGHTER) {
                    brighter++
                }
            }
        }
        brighter > 100
        bright.statistics().brighterPixels() >= brighter
        !bright.isDefined(CloudMap.INTEGRATED_OPACITY, 62, 57) || bright.data(CloudMap.INTEGRATED_OPACITY)[57][62] == 0
    }

    @Requires({ OpenCLSupport.isAvailable() })
    def "the GPU finds the same clouds as the CPU"() {
        when:
        System.setProperty(OpenCLSupport.OPENCL_SYSTEM_PROPERTY, 'true')
        CloudInversionResult gpu
        try {
            gpu = synthesize(1, 0)
        } finally {
            System.clearProperty(OpenCLSupport.OPENCL_SYSTEM_PROPERTY)
        }

        then:
        def cpuStatus = result.data(CloudMap.STATUS)
        def gpuStatus = gpu.data(CloudMap.STATUS)
        def cpuVelocity = result.data(CloudMap.MASS_FLUX_VELOCITY)
        def gpuVelocity = gpu.data(CloudMap.MASS_FLUX_VELOCITY)
        int same = 0
        int total = 0
        def differences = []
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (cpuStatus[y][x] == CloudMap.STATUS_NOT_FITTED) {
                    continue
                }
                total++
                if (cpuStatus[y][x] == gpuStatus[y][x]) {
                    same++
                    if (cpuStatus[y][x] == CloudMap.STATUS_FITTED) {
                        differences << Math.abs(cpuVelocity[y][x] - gpuVelocity[y][x])
                    }
                }
            }
        }
        same > 0.99 * total
        differences.size() > 50
        differences.sort()[(int) (0.95 * (differences.size() - 1))] < 0.1
    }

    def "the fastest flow is the most shifted component which absorbs light"() {
        expect:
        CloudInversion.fastestVelocity(new CloudParameters(0.2, 0.3, opacities as double[], velocities as double[], 1, 0, 3)) == expected

        where:
        opacities         | velocities          | expected
        [0.3, 1.0, 0.2]   | [-95, -5, 30]       | -95
        [0.05, 1.0, 0.2]  | [-95, -5, 30]       | 30
        [0.05, 0.08, 0.02] | [-95, -5, 30]      | -5
        [0, 0.6, 0]       | [-12, -12, -12]     | -12
    }

    def "colors the zero values and the sky differently, and hatches the edge of the disk"() {
        when:
        def result = synthesize(1, 0)
        def rgb = CloudMapRenderer.colorize(result, CloudMap.INTEGRATED_OPACITY)
        def center = (int) (SIZE / 2)
        def quiet = center - 30
        def sky = SIZE - 2

        then:
        result.data(CloudMap.STATUS)[center][quiet] == CloudMap.STATUS_NO_CLOUD
        [rgb[0][center][quiet], rgb[1][center][quiet], rgb[2][center][quiet]] == [40 * 256f, 11 * 256f, 84 * 256f]
        edgeColors(result, rgb) == [[0f, 0f, 0f], [80 * 256f, 80 * 256f, 80 * 256f]] as Set
        [rgb[0][center][sky], rgb[1][center][sky], rgb[2][center][sky]] == [0f, 0f, 0f]
    }

    private static Set<List<Float>> edgeColors(CloudInversionResult result, float[][][] rgb) {
        def status = result.data(CloudMap.STATUS)
        def center = SIZE / 2d
        def colors = [] as Set
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                def r = Math.hypot(x - center, y - center)
                if (r > 0.97 * RADIUS && r < 0.99 * RADIUS && status[y][x] == CloudMap.STATUS_NOT_FITTED) {
                    colors << [rgb[0][y][x], rgb[1][y][x], rgb[2][y][x]]
                }
            }
        }
        colors
    }
}
