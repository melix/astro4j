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
package me.champeau.a4j.jsolex.processing.ser;

import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.ImageGeometry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class FastImageConverterBenchmark {

    @Param({"2048"})
    private int width;

    @Param({"512"})
    private int height;

    @Param({"16", "12"})
    private int depth;

    private ByteBuffer frameData;
    private ImageGeometry geometry;
    private FastImageConverter converter;
    private float[][] output;
    private int bitsToDiscard;

    @Setup(Level.Trial)
    public void setup() {
        var random = new Random(42);
        frameData = ByteBuffer.allocateDirect(width * height * 2);
        for (int i = 0; i < width * height; i++) {
            frameData.putShort((short) random.nextInt(0x10000));
        }
        geometry = new ImageGeometry(ColorMode.MONO, width, height, depth, ByteOrder.LITTLE_ENDIAN);
        converter = new FastImageConverter(false);
        output = new float[height][width];
        bitsToDiscard = 16 - depth;
    }

    @Benchmark
    public float[][] converter() {
        frameData.rewind();
        converter.convert(0, frameData, geometry, output);
        return output;
    }

    @Benchmark
    public float[][] scalarReference() {
        frameData.rewind();
        var shorts = frameData.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        var row = new short[width];
        for (int y = 0; y < height; y++) {
            var line = output[y];
            shorts.get(row);
            for (int x = 0; x < width; x++) {
                line[x] = (short) ((row[x] & 0xFFFF) << bitsToDiscard) & 0xFFFF;
            }
        }
        return output;
    }
}
