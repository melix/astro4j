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

import me.champeau.a4j.jsolex.processing.spectrum.ReferenceIntensities
import me.champeau.a4j.jsolex.processing.util.Wavelen
import spock.lang.Specification

class CloudModelTest extends Specification {
    private static final double LAMBDA0 = 6562.81
    private static final double DISPERSION = 0.0906
    private static final double INSTRUMENT_SIGMA = 0.12

    def "physical quantities match the values of Chae et al. for a single component"() {
        given:
        def parameters = new CloudParameters(0.11, 0.32, [0.80, 0, 0] as double[], [-16.9, 0, 0] as double[], 1, 0, 1)

        when:
        def q = CloudPhysics.compute(parameters, 6562.82, 8)

        then:
        relative(q.temperature(), 9.0e3) < 0.03
        relative(q.columnMass(), 4.8e-5) < 0.05
        relative(q.massFluxVelocity(), -16.9) < 0.01
        relative(q.kineticEnergyVelocity(), 17.8) < 0.01
        relative(q.kineticEnergyFluxVelocity(), -18.6) < 0.01
    }

    def "physical quantities match the values of Chae et al. for three components"() {
        given:
        def parameters = new CloudParameters(0.12, 0.31, [0.61, 0.33, 0.04] as double[], [-13.1, -0.5, 13.7] as double[], 1, 0, 3)

        when:
        def q = CloudPhysics.compute(parameters, 6562.82, 8)

        then:
        relative(q.temperature(), 8.3e3) < 0.03
        relative(q.columnMass(), 4.5e-5) < 0.05
        relative(q.massFluxVelocity(), -7.9) < 0.03
        relative(q.kineticEnergyVelocity(), 12.1) < 0.01
        relative(q.kineticEnergyFluxVelocity(), -12.7) < 0.01
    }

    def "transparent cloud shows the background and opaque cloud shows the source function"() {
        given:
        def grid = SpectralGrid.of(sampleOffsets(), INSTRUMENT_SIGMA)
        def background = CloudModel.Background.of(atlas(grid.fineOffsets()), grid.fineOffsets())
        def model = new CloudModel(grid, LAMBDA0)
        def expected = new double[grid.sampleCount()]
        grid.sample(background.profile(), expected)
        def transparent = new double[grid.sampleCount()]
        def opaque = new double[grid.sampleCount()]

        when:
        model.evaluate(CloudModel.single(0.2, 0.3, 1e-6, 0, 1), 1, background, transparent, null)
        model.evaluate([Math.log(0.2), 0, Math.log(20), 0, 1, 0] as double[], 1, background, opaque, null)

        then:
        (0..<grid.sampleCount()).every { Math.abs(transparent[it] - expected[it]) < 1e-5 }
        def center = (int) (grid.sampleCount() / 2)
        Math.abs(opaque[center] - 0.2) < 1e-3
    }

    def "analytic jacobian matches finite differences with #components components"() {
        given:
        def grid = SpectralGrid.of(sampleOffsets(), INSTRUMENT_SIGMA)
        def background = CloudModel.Background.of(atlas(grid.fineOffsets()), grid.fineOffsets())
        def model = new CloudModel(grid, LAMBDA0)
        def n = CloudModel.parameterCount(components)
        def jacobian = new double[n][grid.sampleCount()]
        def values = new double[grid.sampleCount()]
        def plus = new double[grid.sampleCount()]
        def minus = new double[grid.sampleCount()]

        when:
        model.evaluate(p, components, background, values, jacobian)

        then:
        (0..<n).every { q ->
            def h = 1e-6
            def pp = p.clone()
            def pm = p.clone()
            pp[q] += h
            pm[q] -= h
            model.evaluate(pp, components, background, plus, null)
            model.evaluate(pm, components, background, minus, null)
            (0..<grid.sampleCount()).every { i ->
                def numeric = (plus[i] - minus[i]) / (2 * h)
                Math.abs(numeric - jacobian[q][i]) < 1e-5 + 1e-4 * Math.abs(numeric)
            }
        }

        where:
        components | p
        1          | withBackgroundWidth(CloudModel.single(0.15, 0.33, 0.7, -6, 1.02), 0.05)
        3          | withBackgroundWidth(CloudModel.triple(0.15, 0.31, [0.5, 0.4, 0.1] as double[], [-15, -2, 12] as double[], 0.97), -0.04)
    }

    def "recovers the velocity and temperature of a single component cloud at Sol'Ex resolution"() {
        given:
        def grid = SpectralGrid.of(sampleOffsets(), INSTRUMENT_SIGMA)
        def trueProfile = atlas(grid.fineOffsets())
        def trueBackground = CloudModel.Background.of(trueProfile, grid.fineOffsets())
        def reference = new double[grid.sampleCount()]
        grid.sample(trueProfile, reference)
        def background = CloudModel.Background.of(grid.correctTemplate(reference, trueProfile), grid.fineOffsets())
        def model = new CloudModel(grid, LAMBDA0)
        def fitter = new CloudModelFitter(model)
        def truth = CloudModel.single(0.15, 0.32, 0.8, velocity, 1)
        def clean = new double[grid.sampleCount()]
        model.evaluate(truth, 1, trueBackground, clean, null)
        def sigma = noise(reference, 0.008d)
        def random = new Random(42)
        def velocities = []
        def widths = []

        when:
        50.times {
            def noisy = perturb(clean, sigma, random)
            def observation = new CloudModelFitter.Observation(noisy, sigma, reference, background)
            def fit = fitter.fitBest(observation, CloudModelFitter.estimateVelocity(observation, grid.sampleOffsets(), LAMBDA0, 2), Double.POSITIVE_INFINITY)
            def decoded = fit.decode()
            velocities << decoded.velocities()[CloudParameters.CENTER]
            widths << decoded.dopplerWidth()
        }
        def medianVelocity = velocities.sort()[velocities.size() / 2 as int]
        def meanTemperature = CloudPhysics.temperature(widths.sum() / widths.size(), LAMBDA0, 8)

        then:
        Math.abs(medianVelocity - velocity) < 2
        Math.abs(meanTemperature - CloudPhysics.temperature(0.32, LAMBDA0, 8)) < 1000

        where:
        velocity << [-15, -5, 0, 8, 20]
    }

    def "the number of components is the one which best explains the profile"() {
        given:
        def grid = SpectralGrid.of(sampleOffsets(), INSTRUMENT_SIGMA)
        def trueProfile = atlas(grid.fineOffsets())
        def trueBackground = CloudModel.Background.of(trueProfile, grid.fineOffsets())
        def reference = new double[grid.sampleCount()]
        grid.sample(trueProfile, reference)
        def background = CloudModel.Background.of(grid.correctTemplate(reference, trueProfile), grid.fineOffsets())
        def model = new CloudModel(grid, LAMBDA0)
        def fitter = new CloudModelFitter(model)
        def clean = new double[grid.sampleCount()]
        model.evaluate(truth, components, trueBackground, clean, null)
        def sigma = noise(reference, 0.004d)
        def random = new Random(7)
        def noisy = perturb(clean, sigma, random)

        when:
        def observation = new CloudModelFitter.Observation(noisy, sigma, reference, background)
        def hint = CloudModelFitter.estimateVelocity(observation, grid.sampleOffsets(), LAMBDA0, 2)
        def fit = fitter.fitBest(observation, hint, 1.3)

        then:
        fit.components() == expectedComponents

        where:
        components | truth                                                                                                | expectedComponents
        1          | withBackgroundWidth(CloudModel.single(0.15, 0.32, 1e-6, 0, 1.01), 0.04)                              | 0
        1          | CloudModel.single(0.15, 0.32, 0.8, -5, 1)                                                            | 1
        3          | CloudModel.triple(0.15, 0.30, [0.8, 0.2, 0.8] as double[], [-35, 0, 35] as double[], 1)              | 3
    }

    private static double[] noise(double[] reference, double level) {
        def sigma = new double[reference.length]
        for (int i = 0; i < reference.length; i++) {
            sigma[i] = level * Math.sqrt(reference[i])
        }
        sigma
    }

    private static double[] perturb(double[] clean, double[] sigma, Random random) {
        def noisy = new double[clean.length]
        for (int i = 0; i < clean.length; i++) {
            noisy[i] = clean[i] + sigma[i] * random.nextGaussian()
        }
        noisy
    }

    private static double[] withBackgroundWidth(double[] parameters, double width) {
        parameters[parameters.length - 1] = width
        parameters
    }

    private static double relative(double actual, double expected) {
        Math.abs(actual - expected) / Math.abs(expected)
    }

    private static double[] sampleOffsets() {
        def count = (int) Math.floor(2.5 / DISPERSION)
        (-count..count).collect { it * DISPERSION } as double[]
    }

    private static double[] atlas(double[] offsets) {
        offsets.collect { ReferenceIntensities.intensityAt(Wavelen.ofAngstroms(LAMBDA0 + it)) / 10000d } as double[]
    }
}
