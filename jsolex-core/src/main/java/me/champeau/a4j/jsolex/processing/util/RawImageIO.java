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
package me.champeau.a4j.jsolex.processing.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Reads and writes images as raw {@code float[][]} data with their metadata, without any
 * conversion to a display range. Formats such as FITS or PNG quantize the pixels to 16 bits
 * and rescale them, which is fine for viewing but loses the exact values; this format keeps
 * them bit for bit, so an intermediate image can be saved by a script and reloaded later to
 * debug a processing step in isolation.
 * <p>
 * The layout is a magic header, the metadata as a JSON document, then the pixel planes
 * written by {@link ImageSerializer} behind a deflater.
 */
public final class RawImageIO {
    /**
     * The extension of raw image files.
     */
    public static final String EXTENSION = "jraw";

    private static final byte[] MAGIC = {'J', 'S', 'R', 'A', 'W', '1'};
    private static final int BUFFER_SIZE = 65536;

    private RawImageIO() {
    }

    /**
     * Writes an image and its metadata to a file, creating the parent directories if needed.
     *
     * @param image the image to write
     * @param target the destination file
     */
    public static void write(ImageWrapper image, Path target) {
        try {
            var parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (var dos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(target), BUFFER_SIZE))) {
                dos.write(MAGIC);
                var json = MetadataIO.serialize(new LinkedHashMap<>(image.metadata())).getBytes(StandardCharsets.UTF_8);
                dos.writeInt(json.length);
                dos.write(json);
                var deflater = new Deflater(Deflater.BEST_SPEED);
                try {
                    var deflaterStream = new DeflaterOutputStream(dos, deflater, BUFFER_SIZE);
                    ImageSerializer.write(new DataOutputStream(deflaterStream), image);
                    deflaterStream.finish();
                } finally {
                    deflater.end();
                }
            }
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    /**
     * Reads an image previously written by {@link #write}.
     *
     * @param source the file to read
     * @return the image, with its metadata attached
     */
    public static ImageWrapper read(Path source) {
        var inflater = new Inflater();
        try (var dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(source), BUFFER_SIZE))) {
            var magic = new byte[MAGIC.length];
            dis.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new ProcessingException("Not a raw image file: " + source);
            }
            var json = new byte[dis.readInt()];
            dis.readFully(json);
            var metadata = MetadataIO.deserialize(new String(json, StandardCharsets.UTF_8));
            return ImageSerializer.read(new DataInputStream(new InflaterInputStream(dis, inflater, BUFFER_SIZE)), metadata);
        } catch (IOException e) {
            throw new ProcessingException(e);
        } finally {
            inflater.end();
        }
    }
}
