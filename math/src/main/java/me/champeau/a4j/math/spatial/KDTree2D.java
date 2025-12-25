/*
 * Copyright 2025-2025 the original author or authors.
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
package me.champeau.a4j.math.spatial;

import java.util.Arrays;

/**
 * A 2D KD-tree for efficient nearest neighbor queries on static point sets.
 * <p>
 * This implementation is optimized for bulk construction (build once, query many times).
 * Points are stored in a cache-friendly flattened array structure.
 * </p>
 * <p>
 * Construction time: O(n log n)<br>
 * Query time: O(log n) average for k-nearest neighbors
 * </p>
 */
public class KDTree2D {

    private static final int LEAF_SIZE = 16;

    private final float[] x;
    private final float[] y;
    private final int[] indices;
    private final int size;

    private KDTree2D(float[] x, float[] y, int[] indices, int size) {
        this.x = x;
        this.y = y;
        this.indices = indices;
        this.size = size;
    }

    /**
     * Builds a KD-tree from the given points.
     *
     * @param x      x coordinates of the points
     * @param y      y coordinates of the points
     * @param count  number of points
     * @return a new KD-tree
     */
    public static KDTree2D build(float[] x, float[] y, int count) {
        if (count == 0) {
            return new KDTree2D(new float[0], new float[0], new int[0], 0);
        }

        var treeX = new float[count];
        var treeY = new float[count];
        var treeIndices = new int[count];

        var sortIndices = new int[count];
        for (int i = 0; i < count; i++) {
            sortIndices[i] = i;
        }

        buildRecursive(x, y, sortIndices, 0, count, treeX, treeY, treeIndices, 0, 0);

        return new KDTree2D(treeX, treeY, treeIndices, count);
    }

    private static void buildRecursive(float[] srcX, float[] srcY, int[] sortIndices,
                                        int start, int end,
                                        float[] treeX, float[] treeY, int[] treeIndices,
                                        int treePos, int depth) {
        if (start >= end) {
            return;
        }

        int count = end - start;

        if (count <= LEAF_SIZE) {
            for (int i = start; i < end; i++) {
                int srcIdx = sortIndices[i];
                int dstIdx = treePos + (i - start);
                treeX[dstIdx] = srcX[srcIdx];
                treeY[dstIdx] = srcY[srcIdx];
                treeIndices[dstIdx] = srcIdx;
            }
            return;
        }

        boolean splitOnX = (depth % 2) == 0;
        int mid = start + count / 2;

        partialSort(srcX, srcY, sortIndices, start, end, mid, splitOnX);

        int srcIdx = sortIndices[mid];
        treeX[treePos] = srcX[srcIdx];
        treeY[treePos] = srcY[srcIdx];
        treeIndices[treePos] = srcIdx;

        int leftSize = mid - start;
        int rightSize = end - mid - 1;

        buildRecursive(srcX, srcY, sortIndices, start, mid,
                treeX, treeY, treeIndices, treePos + 1, depth + 1);
        buildRecursive(srcX, srcY, sortIndices, mid + 1, end,
                treeX, treeY, treeIndices, treePos + 1 + leftSize, depth + 1);
    }

    private static void partialSort(float[] x, float[] y, int[] indices,
                                     int start, int end, int k, boolean onX) {
        while (start < end) {
            int pivotIdx = start + (end - start) / 2;
            float pivotVal = onX ? x[indices[pivotIdx]] : y[indices[pivotIdx]];

            int temp = indices[pivotIdx];
            indices[pivotIdx] = indices[end - 1];
            indices[end - 1] = temp;

            int storeIdx = start;
            for (int i = start; i < end - 1; i++) {
                float val = onX ? x[indices[i]] : y[indices[i]];
                if (val < pivotVal) {
                    temp = indices[storeIdx];
                    indices[storeIdx] = indices[i];
                    indices[i] = temp;
                    storeIdx++;
                }
            }

            temp = indices[storeIdx];
            indices[storeIdx] = indices[end - 1];
            indices[end - 1] = temp;

            if (storeIdx == k) {
                return;
            } else if (storeIdx < k) {
                start = storeIdx + 1;
            } else {
                end = storeIdx;
            }
        }
    }

    /**
     * Returns the number of points in the tree.
     *
     * @return point count
     */
    public int size() {
        return size;
    }

    /**
     * Finds the k nearest neighbors to a query point.
     *
     * @param qx query x coordinate
     * @param qy query y coordinate
     * @param k  number of neighbors to find
     * @return array of original indices of the k nearest neighbors, sorted by distance
     */
    public int[] nearestK(float qx, float qy, int k) {
        if (size == 0 || k <= 0) {
            return new int[0];
        }

        k = Math.min(k, size);
        var heap = new NeighborHeap(k);

        searchNearestK(qx, qy, 0, size, 0, heap);

        return heap.getSortedIndices();
    }

    private void searchNearestK(float qx, float qy, int start, int end, int depth, NeighborHeap heap) {
        if (start >= end) {
            return;
        }

        int count = end - start;

        if (count <= LEAF_SIZE) {
            for (int i = start; i < end; i++) {
                float dx = x[i] - qx;
                float dy = y[i] - qy;
                float distSq = dx * dx + dy * dy;
                heap.add(indices[i], distSq);
            }
            return;
        }

        int mid = start + count / 2;
        boolean splitOnX = (depth % 2) == 0;

        float splitVal = splitOnX ? x[mid] : y[mid];
        float queryVal = splitOnX ? qx : qy;

        float dx = x[mid] - qx;
        float dy = y[mid] - qy;
        float distSq = dx * dx + dy * dy;
        heap.add(indices[mid], distSq);

        int leftSize = mid - start;

        boolean goLeftFirst = queryVal < splitVal;
        int firstStart, firstEnd, secondStart, secondEnd;
        if (goLeftFirst) {
            firstStart = start;
            firstEnd = mid;
            secondStart = mid + 1;
            secondEnd = end;
        } else {
            firstStart = mid + 1;
            firstEnd = end;
            secondStart = start;
            secondEnd = mid;
        }

        searchNearestK(qx, qy, firstStart, firstEnd, depth + 1, heap);

        float splitDist = queryVal - splitVal;
        float splitDistSq = splitDist * splitDist;
        if (!heap.isFull() || splitDistSq < heap.maxDistSq()) {
            searchNearestK(qx, qy, secondStart, secondEnd, depth + 1, heap);
        }
    }

    /**
     * Finds all neighbors within a given radius.
     *
     * @param qx     query x coordinate
     * @param qy     query y coordinate
     * @param radius the search radius
     * @return array of original indices of points within the radius
     */
    public int[] withinRadius(float qx, float qy, float radius) {
        if (size == 0 || radius <= 0) {
            return new int[0];
        }

        float radiusSq = radius * radius;
        var results = new IntArrayList();

        searchRadius(qx, qy, radiusSq, 0, size, 0, results);

        return results.toArray();
    }

    private void searchRadius(float qx, float qy, float radiusSq,
                               int start, int end, int depth, IntArrayList results) {
        if (start >= end) {
            return;
        }

        int count = end - start;

        if (count <= LEAF_SIZE) {
            for (int i = start; i < end; i++) {
                float dx = x[i] - qx;
                float dy = y[i] - qy;
                float distSq = dx * dx + dy * dy;
                if (distSq <= radiusSq) {
                    results.add(indices[i]);
                }
            }
            return;
        }

        int mid = start + count / 2;
        boolean splitOnX = (depth % 2) == 0;

        float splitVal = splitOnX ? x[mid] : y[mid];
        float queryVal = splitOnX ? qx : qy;

        float dx = x[mid] - qx;
        float dy = y[mid] - qy;
        float distSq = dx * dx + dy * dy;
        if (distSq <= radiusSq) {
            results.add(indices[mid]);
        }

        float splitDist = queryVal - splitVal;
        float splitDistSq = splitDist * splitDist;

        if (queryVal < splitVal) {
            searchRadius(qx, qy, radiusSq, start, mid, depth + 1, results);
            if (splitDistSq <= radiusSq) {
                searchRadius(qx, qy, radiusSq, mid + 1, end, depth + 1, results);
            }
        } else {
            searchRadius(qx, qy, radiusSq, mid + 1, end, depth + 1, results);
            if (splitDistSq <= radiusSq) {
                searchRadius(qx, qy, radiusSq, start, mid, depth + 1, results);
            }
        }
    }

    /**
     * Max-heap for tracking k nearest neighbors during search.
     */
    private static class NeighborHeap {
        private final int[] indices;
        private final float[] distsSq;
        private final int capacity;
        private int size;

        NeighborHeap(int capacity) {
            this.capacity = capacity;
            this.indices = new int[capacity];
            this.distsSq = new float[capacity];
            this.size = 0;
        }

        void add(int index, float distSq) {
            if (size < capacity) {
                indices[size] = index;
                distsSq[size] = distSq;
                size++;
                if (size == capacity) {
                    buildHeap();
                }
            } else if (distSq < distsSq[0]) {
                indices[0] = index;
                distsSq[0] = distSq;
                siftDown(0);
            }
        }

        boolean isFull() {
            return size >= capacity;
        }

        float maxDistSq() {
            return size > 0 ? (size < capacity ? Float.MAX_VALUE : distsSq[0]) : Float.MAX_VALUE;
        }

        int[] getSortedIndices() {
            var result = Arrays.copyOf(indices, size);
            var dists = Arrays.copyOf(distsSq, size);

            for (int i = 0; i < size - 1; i++) {
                int minIdx = i;
                for (int j = i + 1; j < size; j++) {
                    if (dists[j] < dists[minIdx]) {
                        minIdx = j;
                    }
                }
                if (minIdx != i) {
                    float tmpD = dists[i];
                    dists[i] = dists[minIdx];
                    dists[minIdx] = tmpD;
                    int tmpI = result[i];
                    result[i] = result[minIdx];
                    result[minIdx] = tmpI;
                }
            }
            return result;
        }

        private void buildHeap() {
            for (int i = size / 2 - 1; i >= 0; i--) {
                siftDown(i);
            }
        }

        private void siftDown(int i) {
            while (true) {
                int largest = i;
                int left = 2 * i + 1;
                int right = 2 * i + 2;

                if (left < size && distsSq[left] > distsSq[largest]) {
                    largest = left;
                }
                if (right < size && distsSq[right] > distsSq[largest]) {
                    largest = right;
                }

                if (largest == i) {
                    break;
                }

                float tmpD = distsSq[i];
                distsSq[i] = distsSq[largest];
                distsSq[largest] = tmpD;

                int tmpI = indices[i];
                indices[i] = indices[largest];
                indices[largest] = tmpI;

                i = largest;
            }
        }
    }

    /**
     * Simple resizable int array for collecting results.
     */
    private static class IntArrayList {
        private int[] data;
        private int size;

        IntArrayList() {
            this.data = new int[16];
            this.size = 0;
        }

        void add(int value) {
            if (size >= data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[size++] = value;
        }

        int[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }
}
