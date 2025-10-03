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
package me.champeau.a4j.math;

/**
 * Mathematical utility functions.
 */
public class MathUtils {
    private MathUtils() {
    }

    /**
     * Computes the median value of an array using the quickselect algorithm.
     * This is an O(n) average-case algorithm that partially sorts a copy of
     * the array to find the middle element.
     *
     * <p>The input array is not modified.</p>
     *
     * @param values the array of values
     * @return the median value, or {@link Double#NaN} if the array is empty or null
     */
    public static double median(double[] values) {
        if (values == null || values.length == 0) {
            return Double.NaN;
        }
        var copy = new double[values.length];
        System.arraycopy(values, 0, copy, 0, values.length);
        return quickSelect(copy, 0, copy.length - 1, copy.length / 2);
    }

    private static double quickSelect(double[] arr, int left, int right, int k) {
        if (left == right) {
            return arr[left];
        }
        var pivotIndex = partition(arr, left, right);
        if (k == pivotIndex) {
            return arr[k];
        } else if (k < pivotIndex) {
            return quickSelect(arr, left, pivotIndex - 1, k);
        } else {
            return quickSelect(arr, pivotIndex + 1, right, k);
        }
    }

    private static int partition(double[] arr, int left, int right) {
        var pivot = arr[right];
        var i = left;
        for (var j = left; j < right; j++) {
            if (arr[j] <= pivot) {
                var temp = arr[i];
                arr[i] = arr[j];
                arr[j] = temp;
                i++;
            }
        }
        var temp = arr[i];
        arr[i] = arr[right];
        arr[right] = temp;
        return i;
    }
}
