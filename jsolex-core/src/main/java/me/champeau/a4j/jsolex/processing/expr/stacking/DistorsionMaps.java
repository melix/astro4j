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
package me.champeau.a4j.jsolex.processing.expr.stacking;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds a list of distorsion maps, typically one per iteration of dedistorsion.
 * This allows reapplying all distorsion corrections in sequence when applying
 * precomputed maps to new images.
 *
 * @param maps the list of distorsion maps to apply in sequence
 */
public record DistorsionMaps(List<DistorsionMap> maps) {

    private static final int FORMAT_VERSION = 1;

    public DistorsionMaps {
        maps = List.copyOf(maps);
    }

    public static DistorsionMaps of(DistorsionMap map) {
        return new DistorsionMaps(List.of(map));
    }

    public static DistorsionMaps of(List<DistorsionMap> maps) {
        return new DistorsionMaps(maps);
    }

    public DistorsionMaps append(DistorsionMap map) {
        var newMaps = new ArrayList<>(maps);
        newMaps.add(map);
        return new DistorsionMaps(newMaps);
    }

    public int size() {
        return maps.size();
    }

    public DistorsionMap get(int index) {
        return maps.get(index);
    }

    public DistorsionMap getFirst() {
        return maps.getFirst();
    }

    public DistorsionMap getLast() {
        return maps.getLast();
    }

    public static void saveTo(OutputStream out, DistorsionMaps maps) throws IOException {
        try (var channel = Channels.newChannel(out)) {
            var headerBuffer = ByteBuffer.allocate(Integer.BYTES * 2);
            headerBuffer.putInt(FORMAT_VERSION);
            headerBuffer.putInt(maps.size());
            headerBuffer.flip();
            channel.write(headerBuffer);

            for (var map : maps.maps()) {
                var baos = new ByteArrayOutputStream();
                DistorsionMap.saveTo(baos, map);
                var mapBytes = baos.toByteArray();

                var sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
                sizeBuffer.putInt(mapBytes.length);
                sizeBuffer.flip();
                channel.write(sizeBuffer);

                var mapBuffer = ByteBuffer.wrap(mapBytes);
                channel.write(mapBuffer);
            }
        }
    }

    public static DistorsionMaps loadFrom(InputStream in) throws IOException {
        try (var channel = Channels.newChannel(in)) {
            var headerBuffer = ByteBuffer.allocate(Integer.BYTES * 2);
            channel.read(headerBuffer);
            headerBuffer.flip();

            var version = headerBuffer.getInt();
            if (version != FORMAT_VERSION) {
                throw new IllegalStateException("Unsupported DistorsionMaps format version: " + version);
            }

            var count = headerBuffer.getInt();
            var maps = new ArrayList<DistorsionMap>(count);

            for (int i = 0; i < count; i++) {
                var sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
                channel.read(sizeBuffer);
                sizeBuffer.flip();
                var mapSize = sizeBuffer.getInt();

                var mapBuffer = ByteBuffer.allocate(mapSize);
                channel.read(mapBuffer);
                mapBuffer.flip();

                var mapBytes = new byte[mapSize];
                mapBuffer.get(mapBytes);
                maps.add(DistorsionMap.loadFrom(new ByteArrayInputStream(mapBytes)));
            }

            return new DistorsionMaps(maps);
        }
    }

    public void saveTo(OutputStream outputStream) throws IOException {
        saveTo(outputStream, this);
    }

    @Override
    public String toString() {
        return "Distorsion maps (" + maps.size() + " iterations)";
    }
}
