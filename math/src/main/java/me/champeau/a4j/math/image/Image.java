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
package me.champeau.a4j.math.image;

import java.util.Arrays;

public record Image(int width, int height, float[] data) {

    int length() {
        return data.length;
    }

    Image withData(float[] data) {
        return new Image(width, height, data);
    }

    @Override
    public String toString() {
        return "{ width = " + width + ", height = " + height + ", data = " + Arrays.toString(data) + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Image image = (Image) o;

        if (width != image.width) {
            return false;
        }
        if (height != image.height) {
            return false;
        }
        return Arrays.equals(data, image.data);
    }

    @Override
    public int hashCode() {
        int result = width;
        result = 31 * result + height;
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }
}
