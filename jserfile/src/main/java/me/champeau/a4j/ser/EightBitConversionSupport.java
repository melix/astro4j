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
package me.champeau.a4j.ser;

public class EightBitConversionSupport {
    private EightBitConversionSupport() {

    }

    public static byte[] to8BitImage(short[] image) {
        byte[] converted = new byte[image.length];
        for (int i = 0; i < image.length; i++) {
            int value = image[i];
            converted[i] = (byte) (value >> 8);
        }
        return converted;
    }

    public static byte[] to8BitImage(float[] image) {
        byte[] converted = new byte[image.length];
        for (int i = 0; i < image.length; i++) {
            int value = Math.round(image[i]);
            converted[i] = (byte) (value >> 8);
        }
        return converted;
    }

}
