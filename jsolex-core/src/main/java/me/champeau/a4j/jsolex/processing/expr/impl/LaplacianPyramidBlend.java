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
package me.champeau.a4j.jsolex.processing.expr.impl;

/**
 * Multi-band image blender using Burt &amp; Adelson Laplacian pyramids.
 * Combines two images according to a soft float mask in {@code [0,1]}
 * so that low-frequency intensity differences are hidden across a wide
 * feathered seam while high-frequency detail is preserved.
 * <p>
 * References:
 * <ul>
 *   <li>Burt, P. J., and Adelson, E. H. (1983). "The Laplacian Pyramid as a Compact Image Code."
 *       <i>IEEE Transactions on Communications</i>, vol. COM-31, no. 4, pp. 532-540.
 *       <a href="https://persci.mit.edu/pub_pdfs/pyramid83.pdf">PDF</a></li>
 *   <li>Burt, P. J., and Adelson, E. H. (1983). "A Multiresolution Spline with Application to Image Mosaics."
 *       <i>ACM Transactions on Graphics</i>, vol. 2, no. 4, pp. 217-236.
 *       <a href="https://persci.mit.edu/pub_pdfs/spline83.pdf">PDF</a></li>
 * </ul>
 * <p>
 * The generating kernel used here is the binomial form {@code [1, 4, 6, 4, 1] / 16}
 * (equivalent to Burt &amp; Adelson's parameter {@code a = 3/8}), chosen for its exact
 * rational coefficients. Burt's own recommended {@code a = 0.4} yields {@code [1, 5, 8, 5, 1] / 20}
 * and produces visually equivalent results.
 */
public final class LaplacianPyramidBlend {
    private static final float[] KERNEL = {1f / 16f, 4f / 16f, 6f / 16f, 4f / 16f, 1f / 16f};

    private LaplacianPyramidBlend() {
    }

    /**
     * Blends images {@code a} and {@code b} using a float mask.
     * A mask value of {@code 0} selects {@code a}, {@code 1} selects {@code b},
     * intermediate values yield a multi-band feathered transition.
     *
     * @param a        base image
     * @param b        image to composite over {@code a}
     * @param mask     per-pixel weight in {@code [0,1]}
     * @param levels   requested pyramid depth; silently clamped to what the image size allows
     * @return the blended image
     */
    public static float[][] blend(float[][] a, float[][] b, float[][] mask, int levels) {
        int h = a.length;
        int w = a[0].length;
        if (b.length != h || b[0].length != w || mask.length != h || mask[0].length != w) {
            throw new IllegalArgumentException("a, b and mask must share dimensions");
        }
        int usable = Math.max(1, Math.min(levels, maxLevels(w, h)));
        if (usable == 1) {
            return combine(a, b, mask);
        }
        var gA = gaussianPyramid(a, usable);
        var gB = gaussianPyramid(b, usable);
        var gM = gaussianPyramid(mask, usable);
        var lA = laplacianPyramid(gA);
        var lB = laplacianPyramid(gB);

        var current = combine(lA[usable - 1], lB[usable - 1], gM[usable - 1]);
        for (int i = usable - 2; i >= 0; i--) {
            var blended = combine(lA[i], lB[i], gM[i]);
            int lw = blended[0].length;
            int lh = blended.length;
            var up = expand(current, lw, lh);
            current = addInto(blended, up);
        }
        return current;
    }

    /**
     * Blends two images by stitching them at a horizontal midline using Laplacian pyramids.
     * At each pyramid level, pixels above the scaled midline come from {@code upper} and
     * pixels below from {@code lower}. The pyramid's multi-band reconstruction hides the
     * hard seam at all frequency scales naturally.
     *
     * @param upper    image providing content above the midline
     * @param lower    image providing content below the midline
     * @param midline  y coordinate of the seam in {@code upper}/{@code lower} frame
     * @param levels   requested pyramid depth; clamped to what the image size allows
     * @return the blended image
     */
    public static float[][] joinAtMidline(float[][] upper, float[][] lower, int midline, int levels) {
        int h = upper.length;
        int w = upper[0].length;
        if (lower.length != h || lower[0].length != w) {
            throw new IllegalArgumentException("upper and lower must share dimensions");
        }
        int usable = Math.max(1, Math.min(levels, maxLevels(w, h)));
        var gU = gaussianPyramid(upper, usable);
        var gL = gaussianPyramid(lower, usable);
        var lU = laplacianPyramid(gU);
        var lLp = laplacianPyramid(gL);
        var current = joinLevelAtMidline(lU[usable - 1], lLp[usable - 1], midline >> (usable - 1));
        for (int i = usable - 2; i >= 0; i--) {
            int levelMid = midline >> i;
            var blended = joinLevelAtMidline(lU[i], lLp[i], levelMid);
            int lw = blended[0].length;
            int lh = blended.length;
            var up = expand(current, lw, lh);
            current = addInto(blended, up);
        }
        return current;
    }

    private static float[][] joinLevelAtMidline(float[][] upper, float[][] lower, int midline) {
        int h = upper.length;
        int w = upper[0].length;
        var out = new float[h][w];
        int clampedMid = Math.clamp(midline, 0, h);
        for (int y = 0; y < clampedMid; y++) {
            System.arraycopy(upper[y], 0, out[y], 0, w);
        }
        for (int y = clampedMid; y < h; y++) {
            System.arraycopy(lower[y], 0, out[y], 0, w);
        }
        return out;
    }

    /**
     * Returns the largest pyramid depth that keeps the coarsest level above {@code 4} pixels
     * on its smallest side.
     */
    public static int maxLevels(int width, int height) {
        int m = Math.min(width, height);
        int l = 1;
        while (m >= 8) {
            m = (m + 1) / 2;
            l++;
        }
        return l;
    }

    private static float[][][] gaussianPyramid(float[][] src, int levels) {
        var out = new float[levels][][];
        out[0] = src;
        for (int i = 1; i < levels; i++) {
            out[i] = reduce(out[i - 1]);
        }
        return out;
    }

    private static float[][][] laplacianPyramid(float[][][] g) {
        int n = g.length;
        var out = new float[n][][];
        for (int i = 0; i < n - 1; i++) {
            var up = expand(g[i + 1], g[i][0].length, g[i].length);
            out[i] = subtract(g[i], up);
        }
        out[n - 1] = g[n - 1];
        return out;
    }

    private static float[][] reduce(float[][] src) {
        var blurred = gaussianBlur(src);
        int h = src.length;
        int w = src[0].length;
        int nh = (h + 1) / 2;
        int nw = (w + 1) / 2;
        var out = new float[nh][nw];
        for (int y = 0; y < nh; y++) {
            int sy = Math.min(2 * y, h - 1);
            for (int x = 0; x < nw; x++) {
                int sx = Math.min(2 * x, w - 1);
                out[y][x] = blurred[sy][sx];
            }
        }
        return out;
    }

    private static float[][] expand(float[][] src, int targetW, int targetH) {
        int h = src.length;
        int w = src[0].length;
        var out = new float[targetH][targetW];
        double sx = (double) w / targetW;
        double sy = (double) h / targetH;
        for (int y = 0; y < targetH; y++) {
            double fy = (y + 0.5) * sy - 0.5;
            int y0 = (int) Math.floor(fy);
            double ty = fy - y0;
            int y1 = Math.clamp(y0, 0, h - 1);
            int y2 = Math.clamp(y0 + 1, 0, h - 1);
            for (int x = 0; x < targetW; x++) {
                double fx = (x + 0.5) * sx - 0.5;
                int x0 = (int) Math.floor(fx);
                double tx = fx - x0;
                int x1 = Math.clamp(x0, 0, w - 1);
                int x2 = Math.clamp(x0 + 1, 0, w - 1);
                float v11 = src[y1][x1];
                float v12 = src[y1][x2];
                float v21 = src[y2][x1];
                float v22 = src[y2][x2];
                float top = (float) ((1 - tx) * v11 + tx * v12);
                float bot = (float) ((1 - tx) * v21 + tx * v22);
                out[y][x] = (float) ((1 - ty) * top + ty * bot);
            }
        }
        return out;
    }

    private static float[][] gaussianBlur(float[][] src) {
        int h = src.length;
        int w = src[0].length;
        var tmp = new float[h][w];
        for (int y = 0; y < h; y++) {
            var row = src[y];
            var drow = tmp[y];
            for (int x = 0; x < w; x++) {
                float sum = 0f;
                for (int k = -2; k <= 2; k++) {
                    int sxi = Math.clamp(x + k, 0, w - 1);
                    sum += KERNEL[k + 2] * row[sxi];
                }
                drow[x] = sum;
            }
        }
        var out = new float[h][w];
        for (int y = 0; y < h; y++) {
            var drow = out[y];
            for (int x = 0; x < w; x++) {
                float sum = 0f;
                for (int k = -2; k <= 2; k++) {
                    int syi = Math.clamp(y + k, 0, h - 1);
                    sum += KERNEL[k + 2] * tmp[syi][x];
                }
                drow[x] = sum;
            }
        }
        return out;
    }

    private static float[][] subtract(float[][] a, float[][] b) {
        int h = a.length;
        int w = a[0].length;
        var out = new float[h][w];
        for (int y = 0; y < h; y++) {
            var ra = a[y];
            var rb = b[y];
            var ro = out[y];
            for (int x = 0; x < w; x++) {
                ro[x] = ra[x] - rb[x];
            }
        }
        return out;
    }

    private static float[][] addInto(float[][] a, float[][] b) {
        int h = a.length;
        int w = a[0].length;
        var out = new float[h][w];
        for (int y = 0; y < h; y++) {
            var ra = a[y];
            var rb = b[y];
            var ro = out[y];
            for (int x = 0; x < w; x++) {
                ro[x] = ra[x] + rb[x];
            }
        }
        return out;
    }

    private static float[][] combine(float[][] a, float[][] b, float[][] mask) {
        int h = a.length;
        int w = a[0].length;
        var out = new float[h][w];
        for (int y = 0; y < h; y++) {
            var ra = a[y];
            var rb = b[y];
            var rm = mask[y];
            var ro = out[y];
            for (int x = 0; x < w; x++) {
                float m = rm[x];
                if (m < 0f) {
                    m = 0f;
                } else if (m > 1f) {
                    m = 1f;
                }
                ro[x] = (1f - m) * ra[x] + m * rb[x];
            }
        }
        return out;
    }
}
