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

    /**
     * Computes the percentile value of an array using the quickselect algorithm.
     *
     * @param values the array of values
     * @param percentile the percentile to compute (0-100)
     * @return the percentile value, or {@link Double#NaN} if the array is empty or null
     */
    public static double percentile(double[] values, double percentile) {
        if (values == null || values.length == 0) {
            return Double.NaN;
        }
        var copy = new double[values.length];
        System.arraycopy(values, 0, copy, 0, values.length);
        var k = (int) Math.round((percentile / 100.0) * (copy.length - 1));
        k = Math.max(0, Math.min(copy.length - 1, k));
        return quickSelect(copy, 0, copy.length - 1, k);
    }

    private static double quickSelect(double[] arr, int left, int right, int k) {
        while (left < right) {
            if (right - left < 10) {
                insertionSort(arr, left, right);
                return arr[k];
            }
            var pivotIndex = partition(arr, left, right);
            if (k <= pivotIndex) {
                right = pivotIndex;
            } else {
                left = pivotIndex + 1;
            }
        }
        return arr[left];
    }

    private static void insertionSort(double[] arr, int left, int right) {
        for (var i = left + 1; i <= right; i++) {
            var key = arr[i];
            var j = i - 1;
            while (j >= left && arr[j] > key) {
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j + 1] = key;
        }
    }

    private static int partition(double[] arr, int left, int right) {
        var mid = left + (right - left) / 2;
        var pivot = medianOfThree(arr, left, mid, right);
        var i = left;
        var j = right;
        while (i <= j) {
            while (arr[i] < pivot) {
                i++;
            }
            while (arr[j] > pivot) {
                j--;
            }
            if (i <= j) {
                var temp = arr[i];
                arr[i] = arr[j];
                arr[j] = temp;
                i++;
                j--;
            }
        }
        return j;
    }

    private static double medianOfThree(double[] arr, int left, int mid, int right) {
        if (arr[left] > arr[mid]) {
            if (arr[mid] > arr[right]) {
                return arr[mid];
            } else if (arr[left] > arr[right]) {
                return arr[right];
            } else {
                return arr[left];
            }
        } else {
            if (arr[left] > arr[right]) {
                return arr[left];
            } else if (arr[mid] > arr[right]) {
                return arr[right];
            } else {
                return arr[mid];
            }
        }
    }
}
