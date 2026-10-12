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
 *
 * OpenCL kernel fitting the H-alpha cloud model, one profile per work-group of LANES
 * work-items. This is the single precision counterpart of CloudModelFitter.fitBest and
 * CloudModel.evaluate in jsolex-core: both must be kept in sync.
 *
 * The lanes of a group share the work on one profile: each lane computes the model at
 * some points of the fine grid, then accumulates the convolution for the samples it owns
 * (sample i belongs to lane i % LANES). The control flow of the least squares is the same
 * for all the lanes, which compute the same scalars from the same reductions.
 */

#define LANES 32
#define MAX_SAMPLES 192
#define SAMPLES_PER_LANE (MAX_SAMPLES / LANES)
#define MAX_PARAMETERS 10
#define NO_CLOUD_PARAMETERS 2
#define NORMAL_ENTRIES 55
#define REDUCED_ENTRIES (NORMAL_ENTRIES + MAX_PARAMETERS)

// Indices in the constants buffer, filled by GpuCloudFitter
#define C_VELOCITY_TO_ANGSTROMS 0
#define C_MIN_DOPPLER_WIDTH 1
#define C_MAX_DOPPLER_WIDTH 2
#define C_NEGLIGIBLE_OPACITY 3
#define C_BACKGROUND_TOLERANCE 4
#define C_PRIOR_WIDTH 5
#define C_PRIOR_TOLERANCE 6
#define C_CONVERGENCE 7
#define C_MAX_ITERATIONS 8
#define C_INITIAL_DAMPING 9
#define C_MAX_DAMPING 10
#define C_MIN_DAMPING 11
#define C_INITIAL_SOURCE 12
#define C_INITIAL_WIDTH 13
#define C_INITIAL_OPACITY 14
#define C_SPLIT_GAP 15
#define C_WIDE_GAP 16
#define C_TRIPLE_THRESHOLD 17
#define C_SPLIT_SIDE_FRACTION 18
#define C_SPLIT_CENTER_FRACTION 19
#define C_WIDE_CENTER_OPACITY_FRACTION 20
#define C_LOWER_SINGLE 21
#define C_UPPER_SINGLE 27
#define C_LOWER_TRIPLE 33
#define C_UPPER_TRIPLE 43

// Parameter indices, as in CloudModel
#define P_SOURCE 0
#define P_WIDTH 1
#define P_SINGLE_TAU 2
#define P_SINGLE_VELOCITY 3
#define P_SINGLE_SCALE 4
#define P_SINGLE_BACKGROUND_WIDTH 5
#define P_TAU_BLUE 2
#define P_TAU_CENTER 3
#define P_TAU_RED 4
#define P_VELOCITY 5
#define P_BLUE_GAP 6
#define P_RED_GAP 7
#define P_SCALE 8
#define P_BACKGROUND_WIDTH 9

#define RESULT_STRIDE (2 + MAX_PARAMETERS + 1)

typedef struct {
    __global const int* kernelStart;
    __global const int* kernelLength;
    __global const float* kernelWeights;
    __constant float* c;
    __global const float* profile;
    __global const float* shape;
    int maxKernelLength;
    int samples;
    int fineCount;
    float fineStart;
    float fineStep;
    int lane;
} Problem;

/**
 * The data of the samples a lane owns, and the model and derivatives it accumulates for them.
 */
typedef struct {
    float observed[SAMPLES_PER_LANE];
    float inverseSigma[SAMPLES_PER_LANE];
    float reference[SAMPLES_PER_LANE];
    float convolvedProfile[SAMPLES_PER_LANE];
    float convolvedShape[SAMPLES_PER_LANE];
    float model[SAMPLES_PER_LANE];
    float jacobian[SAMPLES_PER_LANE][MAX_PARAMETERS];
} Lane;

typedef struct {
    __local float* chunk;     // (MAX_PARAMETERS + 1) * LANES
    __local float* reduction; // REDUCED_ENTRIES * LANES
    __local float* normal;    // MAX_PARAMETERS * MAX_PARAMETERS
    __local float* cholesky;  // MAX_PARAMETERS * MAX_PARAMETERS
    __local float* gradient;  // MAX_PARAMETERS
    __local float* step;      // MAX_PARAMETERS
    __local int* flag;        // 1
} Shared;

inline int parameter_count(int components) {
    return components == 1 ? 6 : 10;
}

inline int fitted_parameter_count(int components) {
    return components == 0 ? NO_CLOUD_PARAMETERS : parameter_count(components);
}

inline int scale_index(int components) {
    return components == 1 ? P_SINGLE_SCALE : P_SCALE;
}

inline int width_index(int components) {
    return components == 1 ? P_SINGLE_BACKGROUND_WIDTH : P_BACKGROUND_WIDTH;
}

inline float logistic(float u) {
    return 1.0f / (1.0f + exp(-u));
}

inline float doppler_width(const Problem* pb, float internalWidth) {
    float minW = pb->c[C_MIN_DOPPLER_WIDTH];
    float maxW = pb->c[C_MAX_DOPPLER_WIDTH];
    return minW + (maxW - minW) * logistic(internalWidth);
}

inline float width_to_internal(const Problem* pb, float width) {
    float minW = pb->c[C_MIN_DOPPLER_WIDTH];
    float maxW = pb->c[C_MAX_DOPPLER_WIDTH];
    float t = clamp((width - minW) / (maxW - minW), 1e-6f, 1.0f - 1e-6f);
    return log(t / (1.0f - t));
}

inline int fine_index_of(const Problem* pb, float offset) {
    int index = (int) round((offset - pb->fineStart) / pb->fineStep);
    return clamp(index, 0, pb->fineCount - 1);
}

/**
 * Sums a value over the lanes of the group. All the lanes receive the sum.
 */
float reduce_sum(const Problem* pb, const Shared* sh, float value) {
    sh->reduction[pb->lane] = value;
    barrier(CLK_LOCAL_MEM_FENCE);
    for (int stride = LANES / 2; stride > 0; stride >>= 1) {
        if (pb->lane < stride) {
            sh->reduction[pb->lane] += sh->reduction[pb->lane + stride];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }
    float sum = sh->reduction[0];
    barrier(CLK_LOCAL_MEM_FENCE);
    return sum;
}

void load(const Problem* pb, Lane* l, __global const float* observed, __global const float* sigma, __global const float* reference) {
    for (int s = 0; s < SAMPLES_PER_LANE; s++) {
        int i = pb->lane + s * LANES;
        if (i < pb->samples) {
            l->observed[s] = observed[i];
            l->inverseSigma[s] = 1.0f / sigma[i];
            l->reference[s] = reference[i];
            int first = pb->kernelStart[i];
            int length = pb->kernelLength[i];
            float sp = 0.0f;
            float ss = 0.0f;
            for (int j = 0; j < length; j++) {
                float weight = pb->kernelWeights[i * pb->maxKernelLength + j];
                sp += weight * pb->profile[first + j];
                ss += weight * pb->shape[first + j];
            }
            l->convolvedProfile[s] = sp;
            l->convolvedShape[s] = ss;
        } else {
            l->observed[s] = 0.0f;
            l->inverseSigma[s] = 0.0f;
            l->reference[s] = 0.0f;
            l->convolvedProfile[s] = 0.0f;
            l->convolvedShape[s] = 0.0f;
        }
    }
}

/**
 * Evaluates the model for the samples of each lane, and the derivatives if withJacobian is
 * set. Mirrors CloudModel.evaluate: the cloud only changes the model where its opacity is not
 * negligible, and the background alone is convolved once in load.
 */
void evaluate(const Problem* pb, const Shared* sh, Lane* l, const float* p, int components, bool withJacobian) {
    __constant float* c = pb->c;
    float source = exp(p[P_SOURCE]);
    float sigmoid = logistic(p[P_WIDTH]);
    float minW = c[C_MIN_DOPPLER_WIDTH];
    float maxW = c[C_MAX_DOPPLER_WIDTH];
    float width = minW + (maxW - minW) * sigmoid;
    float widthDerivative = (maxW - minW) * sigmoid * (1.0f - sigmoid);
    float velocityToAngstroms = c[C_VELOCITY_TO_ANGSTROMS];
    int count;
    float scale;
    float backgroundWidth;
    float blueGap = 0.0f;
    float redGap = 0.0f;
    float opacities[3];
    float centers[3];
    if (components == 1) {
        count = 1;
        opacities[0] = exp(p[P_SINGLE_TAU]);
        centers[0] = p[P_SINGLE_VELOCITY] * velocityToAngstroms;
        scale = p[P_SINGLE_SCALE];
        backgroundWidth = p[P_SINGLE_BACKGROUND_WIDTH];
    } else {
        count = 3;
        blueGap = exp(p[P_BLUE_GAP]);
        redGap = exp(p[P_RED_GAP]);
        opacities[0] = exp(p[P_TAU_BLUE]);
        opacities[1] = exp(p[P_TAU_CENTER]);
        opacities[2] = exp(p[P_TAU_RED]);
        centers[0] = (p[P_VELOCITY] - blueGap) * velocityToAngstroms;
        centers[1] = p[P_VELOCITY] * velocityToAngstroms;
        centers[2] = (p[P_VELOCITY] + redGap) * velocityToAngstroms;
        scale = p[P_SCALE];
        backgroundWidth = p[P_BACKGROUND_WIDTH];
    }
    int n = parameter_count(components);
    int scaleIdx = scale_index(components);
    int widthIdx = width_index(components);
    for (int s = 0; s < SAMPLES_PER_LANE; s++) {
        l->model[s] = 0.0f;
        if (withJacobian) {
            for (int q = 0; q < MAX_PARAMETERS; q++) {
                l->jacobian[s][q] = 0.0f;
            }
        }
    }
    int componentFrom[3];
    int componentTo[3];
    int from = pb->fineCount;
    int to = -1;
    float negligible = c[C_NEGLIGIBLE_OPACITY];
    for (int k = 0; k < count; k++) {
        if (opacities[k] > negligible) {
            float halfWidth = width * sqrt(log(opacities[k] / negligible));
            componentFrom[k] = fine_index_of(pb, centers[k] - halfWidth);
            componentTo[k] = fine_index_of(pb, centers[k] + halfWidth);
            from = min(from, componentFrom[k]);
            to = max(to, componentTo[k]);
        } else {
            componentFrom[k] = 1;
            componentTo[k] = 0;
        }
    }
    int rows = withJacobian ? n + 1 : 1;
    for (int chunkStart = from; chunkStart <= to; chunkStart += LANES) {
        int j = chunkStart + pb->lane;
        if (j <= to) {
            float x = pb->fineStart + j * pb->fineStep;
            float tau = 0.0f;
            float weighted[3];
            float reduced[3];
            for (int k = 0; k < count; k++) {
                float u = (x - centers[k]) / width;
                float contribution = 0.0f;
                if (j >= componentFrom[k] && j <= componentTo[k]) {
                    contribution = opacities[k] * exp(-u * u);
                }
                weighted[k] = contribution;
                reduced[k] = u;
                tau += contribution;
            }
            float absorbed = -expm1(-tau);
            float transmission = 1.0f - absorbed;
            float shapeValue = pb->shape[j];
            float unscaled = pb->profile[j] + backgroundWidth * shapeValue;
            float background0 = scale * unscaled;
            sh->chunk[pb->lane] = (source - background0) * absorbed;
            if (withJacobian) {
                float dTau = -(background0 - source) * transmission;
                float widthTerm = 0.0f;
                for (int k = 0; k < count; k++) {
                    widthTerm += weighted[k] * 2.0f * reduced[k] * reduced[k] / width;
                }
                __local float* jac = sh->chunk + LANES;
                jac[P_SOURCE * LANES + pb->lane] = source * absorbed;
                jac[P_WIDTH * LANES + pb->lane] = dTau * widthTerm * widthDerivative;
                if (count == 1) {
                    jac[P_SINGLE_TAU * LANES + pb->lane] = dTau * weighted[0];
                    jac[P_SINGLE_VELOCITY * LANES + pb->lane] = dTau * weighted[0] * 2.0f * reduced[0] / width * velocityToAngstroms;
                    jac[P_SINGLE_SCALE * LANES + pb->lane] = -unscaled * absorbed;
                    jac[P_SINGLE_BACKGROUND_WIDTH * LANES + pb->lane] = -scale * shapeValue * absorbed;
                } else {
                    float shiftBlue = dTau * weighted[0] * 2.0f * reduced[0] / width * velocityToAngstroms;
                    float shiftCenter = dTau * weighted[1] * 2.0f * reduced[1] / width * velocityToAngstroms;
                    float shiftRed = dTau * weighted[2] * 2.0f * reduced[2] / width * velocityToAngstroms;
                    jac[P_TAU_BLUE * LANES + pb->lane] = dTau * weighted[0];
                    jac[P_TAU_CENTER * LANES + pb->lane] = dTau * weighted[1];
                    jac[P_TAU_RED * LANES + pb->lane] = dTau * weighted[2];
                    jac[P_VELOCITY * LANES + pb->lane] = shiftBlue + shiftCenter + shiftRed;
                    jac[P_BLUE_GAP * LANES + pb->lane] = -shiftBlue * blueGap;
                    jac[P_RED_GAP * LANES + pb->lane] = shiftRed * redGap;
                    jac[P_SCALE * LANES + pb->lane] = -unscaled * absorbed;
                    jac[P_BACKGROUND_WIDTH * LANES + pb->lane] = -scale * shapeValue * absorbed;
                }
            }
        } else {
            for (int r = 0; r < rows; r++) {
                sh->chunk[r * LANES + pb->lane] = 0.0f;
            }
        }
        barrier(CLK_LOCAL_MEM_FENCE);
        int chunkEnd = min(chunkStart + LANES - 1, to);
        for (int s = 0; s < SAMPLES_PER_LANE; s++) {
            int i = pb->lane + s * LANES;
            if (i >= pb->samples) {
                break;
            }
            int first = pb->kernelStart[i];
            int start = max(first, chunkStart);
            int end = min(first + pb->kernelLength[i] - 1, chunkEnd);
            for (int jj = start; jj <= end; jj++) {
                float weight = pb->kernelWeights[i * pb->maxKernelLength + (jj - first)];
                int column = jj - chunkStart;
                l->model[s] += weight * sh->chunk[column];
                if (withJacobian) {
                    for (int q = 0; q < n; q++) {
                        l->jacobian[s][q] += weight * sh->chunk[(q + 1) * LANES + column];
                    }
                }
            }
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }
    for (int s = 0; s < SAMPLES_PER_LANE; s++) {
        float background = l->convolvedProfile[s] + backgroundWidth * l->convolvedShape[s];
        l->model[s] += scale * background;
        if (withJacobian) {
            l->jacobian[s][scaleIdx] += background;
            l->jacobian[s][widthIdx] += scale * l->convolvedShape[s];
        }
    }
}

float cost(const Problem* pb, const Shared* sh, const Lane* l, float scale, float backgroundWidth) {
    float inverseTolerance = 1.0f / pb->c[C_BACKGROUND_TOLERANCE];
    float sum = 0.0f;
    for (int s = 0; s < SAMPLES_PER_LANE; s++) {
        float r = (l->model[s] - l->observed[s]) * l->inverseSigma[s];
        float b = (scale * (l->convolvedProfile[s] + backgroundWidth * l->convolvedShape[s]) - l->reference[s]) * l->inverseSigma[s] * inverseTolerance;
        sum += r * r + b * b;
    }
    return reduce_sum(pb, sh, sum);
}

inline float width_prior(const Problem* pb, float internalWidth) {
    float r = (doppler_width(pb, internalWidth) - pb->c[C_PRIOR_WIDTH]) / pb->c[C_PRIOR_TOLERANCE];
    return r * r;
}

/**
 * Builds the normal equations in shared memory from the derivatives held by the lanes.
 */
void build_normal_equations(const Problem* pb, const Shared* sh, const Lane* l, int n, int scaleIdx, int widthIdx, float scale, float backgroundWidth, float internalWidth) {
    float inverseTolerance = 1.0f / pb->c[C_BACKGROUND_TOLERANCE];
    float partial[REDUCED_ENTRIES];
    for (int e = 0; e < REDUCED_ENTRIES; e++) {
        partial[e] = 0.0f;
    }
    for (int s = 0; s < SAMPLES_PER_LANE; s++) {
        float inverse = l->inverseSigma[s];
        float residual = (l->model[s] - l->observed[s]) * inverse;
        float column[MAX_PARAMETERS];
        for (int q = 0; q < n; q++) {
            column[q] = l->jacobian[s][q] * inverse;
        }
        int e = 0;
        for (int a = 0; a < n; a++) {
            partial[NORMAL_ENTRIES + a] += column[a] * residual;
            for (int b = 0; b <= a; b++) {
                partial[e++] += column[a] * column[b];
            }
        }
        float backgroundInverse = inverse * inverseTolerance;
        float unscaled = l->convolvedProfile[s] + backgroundWidth * l->convolvedShape[s];
        float backgroundResidual = (scale * unscaled - l->reference[s]) * backgroundInverse;
        float ds = unscaled * backgroundInverse;
        float dw = scale * l->convolvedShape[s] * backgroundInverse;
        int ss = scaleIdx * (scaleIdx + 1) / 2 + scaleIdx;
        int ww = widthIdx * (widthIdx + 1) / 2 + widthIdx;
        int ws = widthIdx > scaleIdx ? widthIdx * (widthIdx + 1) / 2 + scaleIdx : scaleIdx * (scaleIdx + 1) / 2 + widthIdx;
        partial[ss] += ds * ds;
        partial[ww] += dw * dw;
        partial[ws] += ds * dw;
        partial[NORMAL_ENTRIES + scaleIdx] += ds * backgroundResidual;
        partial[NORMAL_ENTRIES + widthIdx] += dw * backgroundResidual;
    }
    for (int e = 0; e < REDUCED_ENTRIES; e++) {
        sh->reduction[e * LANES + pb->lane] = partial[e];
    }
    barrier(CLK_LOCAL_MEM_FENCE);
    for (int e = pb->lane; e < REDUCED_ENTRIES; e += LANES) {
        float sum = 0.0f;
        for (int k = 0; k < LANES; k++) {
            sum += sh->reduction[e * LANES + k];
        }
        if (e < NORMAL_ENTRIES) {
            int a = (int) ((sqrt(8.0f * e + 1.0f) - 1.0f) / 2.0f);
            while ((a + 1) * (a + 2) / 2 <= e) {
                a++;
            }
            while (a * (a + 1) / 2 > e) {
                a--;
            }
            int b = e - a * (a + 1) / 2;
            if (a < n) {
                sh->normal[a * MAX_PARAMETERS + b] = sum;
                sh->normal[b * MAX_PARAMETERS + a] = sum;
            }
        } else if (e - NORMAL_ENTRIES < n) {
            sh->gradient[e - NORMAL_ENTRIES] = sum;
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);
    if (pb->lane == 0) {
        float minW = pb->c[C_MIN_DOPPLER_WIDTH];
        float maxW = pb->c[C_MAX_DOPPLER_WIDTH];
        float sigmoid = logistic(internalWidth);
        float r = (doppler_width(pb, internalWidth) - pb->c[C_PRIOR_WIDTH]) / pb->c[C_PRIOR_TOLERANCE];
        float derivative = (maxW - minW) * sigmoid * (1.0f - sigmoid) / pb->c[C_PRIOR_TOLERANCE];
        sh->normal[P_WIDTH * MAX_PARAMETERS + P_WIDTH] += derivative * derivative;
        sh->gradient[P_WIDTH] += derivative * r;
    }
    barrier(CLK_LOCAL_MEM_FENCE);
}

/**
 * Solves the damped normal equations. The first lane factorizes, and all the lanes read the step.
 */
bool solve(const Problem* pb, const Shared* sh, int n, float damping, float* step) {
    if (pb->lane == 0) {
        bool ok = true;
        for (int a = 0; a < n && ok; a++) {
            for (int b = 0; b <= a; b++) {
                float value = sh->normal[a * MAX_PARAMETERS + b];
                if (a == b) {
                    value += damping * fmax(sh->normal[a * MAX_PARAMETERS + a], 1e-12f);
                }
                for (int k = 0; k < b; k++) {
                    value -= sh->cholesky[a * MAX_PARAMETERS + k] * sh->cholesky[b * MAX_PARAMETERS + k];
                }
                if (a == b) {
                    if (value <= 0.0f || !isfinite(value)) {
                        ok = false;
                        break;
                    }
                    sh->cholesky[a * MAX_PARAMETERS + a] = sqrt(value);
                } else {
                    sh->cholesky[a * MAX_PARAMETERS + b] = value / sh->cholesky[b * MAX_PARAMETERS + b];
                }
            }
        }
        if (ok) {
            for (int a = 0; a < n; a++) {
                float value = -sh->gradient[a];
                for (int k = 0; k < a; k++) {
                    value -= sh->cholesky[a * MAX_PARAMETERS + k] * sh->step[k];
                }
                sh->step[a] = value / sh->cholesky[a * MAX_PARAMETERS + a];
            }
            for (int a = n - 1; a >= 0; a--) {
                float value = sh->step[a];
                for (int k = a + 1; k < n; k++) {
                    value -= sh->cholesky[k * MAX_PARAMETERS + a] * sh->step[k];
                }
                sh->step[a] = value / sh->cholesky[a * MAX_PARAMETERS + a];
            }
            for (int a = 0; a < n; a++) {
                if (!isfinite(sh->step[a])) {
                    ok = false;
                }
            }
        }
        sh->flag[0] = ok ? 1 : 0;
    }
    barrier(CLK_LOCAL_MEM_FENCE);
    bool ok = sh->flag[0] != 0;
    if (ok) {
        for (int q = 0; q < n; q++) {
            step[q] = sh->step[q];
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);
    return ok;
}

void clamp_parameters(const Problem* pb, float* p, int components) {
    int n = parameter_count(components);
    int lower = components == 1 ? C_LOWER_SINGLE : C_LOWER_TRIPLE;
    int upper = components == 1 ? C_UPPER_SINGLE : C_UPPER_TRIPLE;
    for (int q = 0; q < n; q++) {
        p[q] = clamp(p[q], pb->c[lower + q], pb->c[upper + q]);
    }
}

/**
 * Bounded Levenberg-Marquardt fit with a fixed number of components, as CloudModelFitter.fit.
 * Returns the cost, and leaves the solution in p.
 */
float fit(const Problem* pb, const Shared* sh, Lane* l, float* p, int components) {
    __constant float* c = pb->c;
    int n = parameter_count(components);
    int scaleIdx = scale_index(components);
    int widthIdx = width_index(components);
    float trial[MAX_PARAMETERS];
    float step[MAX_PARAMETERS];
    clamp_parameters(pb, p, components);
    evaluate(pb, sh, l, p, components, true);
    float currentCost = cost(pb, sh, l, p[scaleIdx], p[widthIdx]) + width_prior(pb, p[P_WIDTH]);
    float damping = c[C_INITIAL_DAMPING];
    int maxIterations = (int) c[C_MAX_ITERATIONS];
    for (int iteration = 0; iteration < maxIterations; iteration++) {
        build_normal_equations(pb, sh, l, n, scaleIdx, widthIdx, p[scaleIdx], p[widthIdx], p[P_WIDTH]);
        bool improved = false;
        while (damping < c[C_MAX_DAMPING]) {
            if (!solve(pb, sh, n, damping, step)) {
                damping *= 10.0f;
                continue;
            }
            for (int q = 0; q < n; q++) {
                trial[q] = p[q] + step[q];
            }
            clamp_parameters(pb, trial, components);
            evaluate(pb, sh, l, trial, components, false);
            float trialCost = cost(pb, sh, l, trial[scaleIdx], trial[widthIdx]) + width_prior(pb, trial[P_WIDTH]);
            if (trialCost < currentCost) {
                improved = currentCost - trialCost > c[C_CONVERGENCE];
                for (int q = 0; q < n; q++) {
                    p[q] = trial[q];
                }
                currentCost = trialCost;
                damping = fmax(c[C_MIN_DAMPING], damping * 0.3f);
                break;
            }
            damping *= 10.0f;
        }
        if (!improved) {
            break;
        }
        evaluate(pb, sh, l, p, components, true);
    }
    return currentCost - width_prior(pb, p[P_WIDTH]);
}

/**
 * Fits the background alone, as CloudModelFitter.fitNoCloud. Returns the cost and stores
 * the scale and the width in p.
 */
float fit_no_cloud(const Problem* pb, const Shared* sh, Lane* l, float* p) {
    float tolerance2 = pb->c[C_BACKGROUND_TOLERANCE] * pb->c[C_BACKGROUND_TOLERANCE];
    float aa = 0.0f, ab = 0.0f, bb = 0.0f, ay = 0.0f, by = 0.0f;
    for (int s = 0; s < SAMPLES_PER_LANE; s++) {
        float weight = l->inverseSigma[s] * l->inverseSigma[s];
        float backgroundWeight = weight / tolerance2;
        float a = l->convolvedProfile[s];
        float b = l->convolvedShape[s];
        float total = weight + backgroundWeight;
        aa += total * a * a;
        ab += total * a * b;
        bb += total * b * b;
        ay += a * (weight * l->observed[s] + backgroundWeight * l->reference[s]);
        by += b * (weight * l->observed[s] + backgroundWeight * l->reference[s]);
    }
    aa = reduce_sum(pb, sh, aa);
    ab = reduce_sum(pb, sh, ab);
    bb = reduce_sum(pb, sh, bb);
    ay = reduce_sum(pb, sh, ay);
    by = reduce_sum(pb, sh, by);
    float determinant = aa * bb - ab * ab;
    float scale;
    float product;
    if (determinant > 1e-12f * aa * bb) {
        scale = (ay * bb - by * ab) / determinant;
        product = (aa * by - ab * ay) / determinant;
    } else {
        scale = ay / aa;
        product = 0.0f;
    }
    float maxBackgroundWidth = pb->c[C_UPPER_SINGLE + P_SINGLE_BACKGROUND_WIDTH];
    float width = clamp(product / scale, -maxBackgroundWidth, maxBackgroundWidth);
    p[0] = scale;
    p[1] = width;
    for (int s = 0; s < SAMPLES_PER_LANE; s++) {
        l->model[s] = scale * (l->convolvedProfile[s] + width * l->convolvedShape[s]);
    }
    return cost(pb, sh, l, scale, width);
}

inline float bic(float cost, int components, int samples) {
    int k = fitted_parameter_count(components);
    return samples * log(fmax(cost, 1e-30f) / samples) + k * log((float) samples);
}

inline float reduced_chi2(float cost, int components, int samples) {
    int k = fitted_parameter_count(components);
    return cost / max(1, samples - k);
}

inline void initial_single(const Problem* pb, float* p, float velocity) {
    __constant float* c = pb->c;
    p[P_SOURCE] = log(c[C_INITIAL_SOURCE]);
    p[P_WIDTH] = width_to_internal(pb, c[C_INITIAL_WIDTH]);
    p[P_SINGLE_TAU] = log(c[C_INITIAL_OPACITY]);
    p[P_SINGLE_VELOCITY] = velocity;
    p[P_SINGLE_SCALE] = 1.0f;
    p[P_SINGLE_BACKGROUND_WIDTH] = 0.0f;
}

/**
 * Fits one profile per work-group and writes, for each, the number of components (0 when no
 * significant absorption was found), the cost and the internal parameters.
 */
__kernel __attribute__((reqd_work_group_size(LANES, 1, 1)))
void cloud_fit_best(
    __global const float* observed,
    __global const float* sigma,
    __global const float* reference,
    __global const float* profile,
    __global const float* shape,
    __global const float* hints,
    __global const int* kernelStart,
    __global const int* kernelLength,
    __global const float* kernelWeights,
    __constant float* constants,
    __global float* results,
    const int count,
    const int samples,
    const int fineCount,
    const int maxKernelLength,
    const float fineStart,
    const float fineStep
) {
    __local float chunk[(MAX_PARAMETERS + 1) * LANES];
    __local float reduction[REDUCED_ENTRIES * LANES];
    __local float normal[MAX_PARAMETERS * MAX_PARAMETERS];
    __local float cholesky[MAX_PARAMETERS * MAX_PARAMETERS];
    __local float gradient[MAX_PARAMETERS];
    __local float sharedStep[MAX_PARAMETERS];
    __local int flag[1];

    int id = get_group_id(0);
    if (id >= count) {
        return;
    }
    Problem pb;
    pb.kernelStart = kernelStart;
    pb.kernelLength = kernelLength;
    pb.kernelWeights = kernelWeights;
    pb.c = constants;
    pb.profile = profile + (long) id * fineCount;
    pb.shape = shape + (long) id * fineCount;
    pb.maxKernelLength = maxKernelLength;
    pb.samples = samples;
    pb.fineCount = fineCount;
    pb.fineStart = fineStart;
    pb.fineStep = fineStep;
    pb.lane = get_local_id(0);
    Shared sh;
    sh.chunk = chunk;
    sh.reduction = reduction;
    sh.normal = normal;
    sh.cholesky = cholesky;
    sh.gradient = gradient;
    sh.step = sharedStep;
    sh.flag = flag;
    Lane l;
    load(&pb, &l, observed + (long) id * samples, sigma + (long) id * samples, reference + (long) id * samples);

    float hint = hints[id];
    float noCloud[NO_CLOUD_PARAMETERS];
    float noCloudCost = fit_no_cloud(&pb, &sh, &l, noCloud);

    float single[MAX_PARAMETERS];
    float other[MAX_PARAMETERS];
    initial_single(&pb, single, hint);
    float singleCost = fit(&pb, &sh, &l, single, 1);
    initial_single(&pb, other, 0.0f);
    float otherCost = fit(&pb, &sh, &l, other, 1);
    if (otherCost < singleCost) {
        for (int q = 0; q < parameter_count(1); q++) {
            single[q] = other[q];
        }
        singleCost = otherCost;
    }

    int selected = bic(singleCost, 1, samples) < bic(noCloudCost, 0, samples) ? 1 : 0;
    float selectedCost = selected == 1 ? singleCost : noCloudCost;
    float triple[MAX_PARAMETERS];
    if (reduced_chi2(selectedCost, selected, samples) > constants[C_TRIPLE_THRESHOLD]) {
        float tau = single[P_SINGLE_TAU];
        float gap = log(constants[C_SPLIT_GAP]);
        triple[P_SOURCE] = single[P_SOURCE];
        triple[P_WIDTH] = single[P_WIDTH];
        float side = tau + log(constants[C_SPLIT_SIDE_FRACTION]);
        triple[P_TAU_BLUE] = side;
        triple[P_TAU_CENTER] = tau + log(constants[C_SPLIT_CENTER_FRACTION]);
        triple[P_TAU_RED] = side;
        triple[P_VELOCITY] = single[P_SINGLE_VELOCITY];
        triple[P_BLUE_GAP] = gap;
        triple[P_RED_GAP] = gap;
        triple[P_SCALE] = single[P_SINGLE_SCALE];
        triple[P_BACKGROUND_WIDTH] = single[P_SINGLE_BACKGROUND_WIDTH];
        float tripleCost = fit(&pb, &sh, &l, triple, 3);
        float opacity = constants[C_INITIAL_OPACITY];
        float wide = log(constants[C_WIDE_GAP]);
        other[P_SOURCE] = log(constants[C_INITIAL_SOURCE]);
        other[P_WIDTH] = width_to_internal(&pb, constants[C_INITIAL_WIDTH]);
        other[P_TAU_BLUE] = log(opacity);
        other[P_TAU_CENTER] = log(opacity * constants[C_WIDE_CENTER_OPACITY_FRACTION]);
        other[P_TAU_RED] = log(opacity);
        other[P_VELOCITY] = hint;
        other[P_BLUE_GAP] = wide;
        other[P_RED_GAP] = wide;
        other[P_SCALE] = 1.0f;
        other[P_BACKGROUND_WIDTH] = 0.0f;
        otherCost = fit(&pb, &sh, &l, other, 3);
        if (otherCost < tripleCost) {
            for (int q = 0; q < MAX_PARAMETERS; q++) {
                triple[q] = other[q];
            }
            tripleCost = otherCost;
        }
        if (bic(tripleCost, 3, samples) < bic(selectedCost, selected, samples)) {
            selected = 3;
            selectedCost = tripleCost;
        }
    }

    if (pb.lane == 0) {
        __global float* out = results + (long) id * RESULT_STRIDE;
        out[0] = (float) selected;
        out[1] = selectedCost;
        out[RESULT_STRIDE - 1] = noCloudCost;
        for (int q = 0; q < MAX_PARAMETERS; q++) {
            out[2 + q] = 0.0f;
        }
        if (selected == 0) {
            out[2] = noCloud[0];
            out[3] = noCloud[1];
        } else if (selected == 1) {
            for (int q = 0; q < parameter_count(1); q++) {
                out[2 + q] = single[q];
            }
        } else {
            for (int q = 0; q < MAX_PARAMETERS; q++) {
                out[2 + q] = triple[q];
            }
        }
    }
}
