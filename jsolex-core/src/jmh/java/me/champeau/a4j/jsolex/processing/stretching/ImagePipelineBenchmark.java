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
package me.champeau.a4j.jsolex.processing.stretching;

import me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval;
import me.champeau.a4j.jsolex.processing.sun.workflow.AnalysisUtils;
import me.champeau.a4j.jsolex.processing.util.FitsUtils;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.opencl.OpenCLSupport;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.tuples.DoubleSextuplet;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.File;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Times the image operations of a processing run. By default they run on a
 * synthetic solar disk; pass the path of a FITS file written by JSol'Ex with
 * {@code -p fits=/path/to/disk.fits} to use a real, geometry corrected image.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 1, jvmArgsAppend = {
        "--add-modules", "jdk.incubator.vector",
        "--enable-native-access=ALL-UNNAMED",
        "-XX:+UseZGC"
})
@State(Scope.Benchmark)
public class ImagePipelineBenchmark {
    private static final int SYNTHETIC_SIZE = 2048;

    @Param("")
    public String fits;

    private ImageWrapper32 source;
    private Ellipse ellipse;

    @Setup
    public void setup() {
        OpenCLSupport.configureDefault(false);
        source = fits.isEmpty() ? syntheticDisk() : (ImageWrapper32) FitsUtils.readFitsFile(new File(fits));
        ellipse = source.findMetadata(Ellipse.class).orElseThrow();
    }

    private static ImageWrapper32 syntheticDisk() {
        double cx = SYNTHETIC_SIZE / 2.0;
        double cy = SYNTHETIC_SIZE / 2.0;
        double radius = SYNTHETIC_SIZE * 0.4;
        var random = new Random(42);
        var data = new float[SYNTHETIC_SIZE][SYNTHETIC_SIZE];
        for (int y = 0; y < SYNTHETIC_SIZE; y++) {
            for (int x = 0; x < SYNTHETIC_SIZE; x++) {
                double r = Math.hypot(x - cx, y - cy) / radius;
                double level = r < 1 ? 30000 * (0.4 + 0.6 * Math.sqrt(1 - r * r)) : 800;
                data[y][x] = (float) (level + random.nextGaussian() * 200);
            }
        }
        double inverseSquare = 1 / (radius * radius);
        var disk = Ellipse.ofCartesian(new DoubleSextuplet(inverseSquare, 0, inverseSquare,
                -2 * cx * inverseSquare, -2 * cy * inverseSquare, (cx * cx + cy * cy) * inverseSquare - 1));
        var metadata = new HashMap<Class<?>, Object>();
        metadata.put(Ellipse.class, disk);
        return new ImageWrapper32(SYNTHETIC_SIZE, SYNTHETIC_SIZE, data, metadata);
    }

    @Benchmark
    public ImageWrapper32 copy() {
        return source.copy();
    }

    @Benchmark
    public ImageWrapper32 autoStretch() {
        var image = source.copy();
        new AutohistogramStrategy(1.5, true, 0.5, 0).stretch(image);
        return image;
    }

    @Benchmark
    public ImageWrapper32 clahe() {
        var image = source.copy();
        new ClaheStrategy(8, 64, 1.0).stretch(image);
        return image;
    }

    @Benchmark
    public ImageWrapper32 linear() {
        var image = source.copy();
        LinearStrechingStrategy.DEFAULT.stretch(image);
        return image;
    }

    @Benchmark
    public ImageWrapper32 rangeExpansion() {
        var image = source.copy();
        RangeExpansionStrategy.DEFAULT.stretch(image);
        return image;
    }

    @Benchmark
    public ImageWrapper32 cutoff() {
        var image = source.copy();
        CutoffStretchingStrategy.DEFAULT.stretch(image);
        return image;
    }

    @Benchmark
    public ImageWrapper32 gamma() {
        var image = source.copy();
        new GammaStrategy(1.5).stretch(image);
        return image;
    }

    @Benchmark
    public ImageWrapper32 arcsinh() {
        var image = source.copy();
        new ArcsinhStretchingStrategy(0, 50, 50).stretch(image);
        return image;
    }

    @Benchmark
    public ImageWrapper32 neutralizeBackground() {
        return BackgroundRemoval.neutralizeBackground(source);
    }

    @Benchmark
    public double blackPoint() {
        return AnalysisUtils.estimateBlackPoint(source, ellipse);
    }
}
