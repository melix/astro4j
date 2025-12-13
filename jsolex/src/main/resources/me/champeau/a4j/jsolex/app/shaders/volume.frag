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
#version 150 core

// Fragment shader for volume ray marching through spherical chromospheric data

in vec2 vTexCoord;

out vec4 fragColor;

// 3D volume texture (x, y, depth/wavelength)
uniform sampler3D volumeTexture;

// Camera and view parameters
uniform mat3 rotationMatrix;    // Precomputed rotation matrix
uniform float cameraDistance;
uniform float aspectRatio;

// Volume parameters
uniform float baseRadius;        // BASE_RADIUS constant (0.8)
uniform float radialExaggeration;

// Rendering parameters
uniform int numSteps;           // Ray marching quality (64, 128, 256)
uniform int colorMapMode;       // 0=MONO, 1=RED_TO_BLUE, 2=BLUE_TO_RED
uniform float globalOpacity;    // Overall transparency control
uniform int showProminences;    // Whether to render limb features (0 or 1)

// Texture mapping parameters (ellipse-based UV mapping)
uniform float diskCenterU;      // Disk center U in texture space [0,1]
uniform float diskCenterV;      // Disk center V in texture space [0,1]
uniform float diskRadiusU;      // Disk radius U in texture space
uniform float diskRadiusV;      // Disk radius V in texture space
uniform float lineCenterDepth;  // Texture depth for line center (pixel shift 0)

// Constants
const float PI = 3.14159265359;
const float PROMINENCE_RADIUS = 1.25;
const int MAX_STEPS = 256;
const float UV_CLAMP_RADIUS = 0.99; // Clamp UV sampling to avoid dark edge regions

// Clamp normalized position to stay inside the disk for UV sampling
vec2 clampForUV(vec3 normal) {
    vec2 xy = normal.xy;
    float r = length(xy);
    if (r > UV_CLAMP_RADIUS) {
        xy = xy * (UV_CLAMP_RADIUS / r);
    }
    return xy;
}

// Sphere-ray intersection
// Returns (tNear, tFar) or (-1, -1) if no intersection
vec2 intersectSphere(vec3 origin, vec3 direction, float radius) {
    float b = dot(origin, direction);
    float c = dot(origin, origin) - radius * radius;
    float discriminant = b * b - c;

    if (discriminant < 0.0) {
        return vec2(-1.0, -1.0);
    }

    float sqrtD = sqrt(discriminant);
    return vec2(-b - sqrtD, -b + sqrtD);
}

// HSL to RGB conversion
vec3 hslToRgb(float h, float s, float l) {
    if (s < 0.001) {
        return vec3(l);
    }

    float q = l < 0.5 ? l * (1.0 + s) : l + s - l * s;
    float p = 2.0 * l - q;

    vec3 rgb;

    // Red
    float t = h + 1.0/3.0;
    if (t < 0.0) t += 1.0;
    if (t > 1.0) t -= 1.0;
    if (t < 1.0/6.0) rgb.r = p + (q - p) * 6.0 * t;
    else if (t < 0.5) rgb.r = q;
    else if (t < 2.0/3.0) rgb.r = p + (q - p) * (2.0/3.0 - t) * 6.0;
    else rgb.r = p;

    // Green
    t = h;
    if (t < 0.0) t += 1.0;
    if (t > 1.0) t -= 1.0;
    if (t < 1.0/6.0) rgb.g = p + (q - p) * 6.0 * t;
    else if (t < 0.5) rgb.g = q;
    else if (t < 2.0/3.0) rgb.g = p + (q - p) * (2.0/3.0 - t) * 6.0;
    else rgb.g = p;

    // Blue
    t = h - 1.0/3.0;
    if (t < 0.0) t += 1.0;
    if (t > 1.0) t -= 1.0;
    if (t < 1.0/6.0) rgb.b = p + (q - p) * 6.0 * t;
    else if (t < 0.5) rgb.b = q;
    else if (t < 2.0/3.0) rgb.b = p + (q - p) * (2.0/3.0 - t) * 6.0;
    else rgb.b = p;

    return rgb;
}

// Apply colormap based on intensity and depth
vec3 applyColorMap(float intensity, float depth) {
    if (colorMapMode == 0) {
        // MONO - grayscale, intensity directly maps to brightness
        return vec3(intensity);
    }

    // depth: 0 = inner (wing), 1 = outer (core)
    float layerPosition = colorMapMode == 1 ? depth : (1.0 - depth);

    // Hue from red (0) to blue (240/360)
    float hue = layerPosition * 240.0 / 360.0;
    // Full saturation for vivid colors
    float saturation = 1.0;
    // Lightness at 0.5 for maximum saturation, slight variation with intensity
    float lightness = 0.45 + intensity * 0.1;

    return hslToRgb(hue, saturation, lightness);
}

// Transfer function: maps intensity to opacity
// For shell-like rendering, we want uniform opacity across the surface
float transferFunction(float intensity, float stepSize) {
    // Constant opacity per step - the color carries the information, not the opacity
    float alpha = globalOpacity * stepSize * 8.0;
    return clamp(alpha, 0.0, 1.0);
}

void main() {
    // Map texture coords to [-1, 1] range
    vec2 screenPos = vTexCoord * 2.0 - 1.0;

    // Inner radius is fixed at baseRadius (the "sun surface")
    // Outer radius matches shell renderer: BASE_RADIUS * (1 + (maxNormRadius - 1) * exaggeration)
    // The shells have normalizedRadius from 1.0 (wings) to ~1.2 (line center)
    // So maxNormRadius - 1 ≈ 0.2 (from extractor's radialExaggeration)
    float effInnerRadius = baseRadius;
    float effOuterRadius = baseRadius * (1.0 + 0.2 * radialExaggeration);
    float volumeThickness = effOuterRadius - effInnerRadius;

    // Orthographic ray setup with zoom
    // Use fixed baseRadius for view size so inner disk stays same size on screen
    float zoomFactor = cameraDistance / 3.0;
    float viewSize = baseRadius * 1.5 * zoomFactor;
    vec2 worldPos = screenPos * viewSize;

    if (aspectRatio > 1.0) {
        worldPos.x *= aspectRatio;
    } else {
        worldPos.y /= aspectRatio;
    }

    // Orthographic ray looking down -Z
    vec3 rayOrigin = vec3(worldPos, 10.0);
    vec3 rayDir = vec3(0.0, 0.0, -1.0);

    // Apply rotation
    mat3 invRotation = transpose(rotationMatrix);
    rayOrigin = invRotation * rayOrigin;
    rayDir = invRotation * rayDir;

    // Start with transparent black
    vec4 accumulated = vec4(0.0);

    // For prominences, check if ray could hit the z=0 plane in the prominence region
    bool couldHitProminences = false;
    if (showProminences != 0 && abs(rayDir.z) > 0.001) {
        float t = -rayOrigin.z / rayDir.z;
        vec3 planePos = rayOrigin + t * rayDir;
        float distFromCenter = length(planePos.xy);
        couldHitProminences = distFromCenter > effOuterRadius && distFromCenter < effOuterRadius * PROMINENCE_RADIUS;
    }

    // Find intersection with outer sphere for the main disk
    vec2 outerHit = intersectSphere(rayOrigin, rayDir, effOuterRadius);
    if (outerHit.x < 0.0 && !couldHitProminences) {
        // Output solid black (alpha=1) to prevent JavaFX background showing through
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // 3D texture layout (sorted by pixelShift from negative to positive):
    // depth=0 -> blue wing (large negative shift, LOW altitude, innermost)
    // depth=0.5 -> line center (shift ~0, HIGH altitude, outermost)
    // depth=1 -> red wing (large positive shift, LOW altitude, innermost)

    // If no exaggeration, just render line center as a single opaque sphere
    if (volumeThickness < 0.001) {
        vec2 sphereHit = intersectSphere(rayOrigin, rayDir, effInnerRadius);
        if (sphereHit.x > 0.0) {
            vec3 pos = rayOrigin + sphereHit.x * rayDir;
            if (pos.z > 0.0) {
                vec2 clampedXY = clampForUV(normalize(pos));
                vec2 uv = vec2(
                    diskCenterU + clampedXY.x * diskRadiusU,
                    diskCenterV - clampedXY.y * diskRadiusV
                );
                if (uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0) {
                    float intensity = texture(volumeTexture, vec3(uv, lineCenterDepth)).r;
                    vec3 color = applyColorMap(intensity, lineCenterDepth);
                    accumulated = vec4(color, 1.0);
                }
            }
        }
    } else {
        // Back-to-front compositing like the shell renderer
        // But using continuous sampling with per-pixel alpha from intensity
        int numSamples = numSteps;
        if (numSamples < 8) {
            numSamples = 8;
        }
        if (numSamples > MAX_STEPS) {
            numSamples = MAX_STEPS;
        }

        // Check if ray hits the inner sphere (sun surface)
        vec2 innerHit = intersectSphere(rayOrigin, rayDir, effInnerRadius);
        bool hitsInner = innerHit.x > 0.0;

        // Start with innermost layer as base (if hit)
        bool hasBase = false;
        float baseFacing = 1.0;

        if (hitsInner) {
            vec3 innerPos = rayOrigin + innerHit.x * rayDir;
            if (innerPos.z > 0.0) {
                vec3 normal = normalize(innerPos);
                baseFacing = abs(dot(normal, -rayDir));

                // UV from surface normal (matches shell renderer)
                vec2 clampedXY = clampForUV(normal);
                vec2 innerUv = vec2(
                    diskCenterU + clampedXY.x * diskRadiusU,
                    diskCenterV - clampedXY.y * diskRadiusV
                );
                if (innerUv.x >= 0.0 && innerUv.x <= 1.0 && innerUv.y >= 0.0 && innerUv.y <= 1.0) {
                    // Sample the far wings (photosphere) for base - use maximum (brightest)
                    // since photosphere is typically bright and uniform
                    float blueInt = texture(volumeTexture, vec3(innerUv, 0.0)).r;
                    float redInt = texture(volumeTexture, vec3(innerUv, 1.0)).r;
                    float baseIntensity = max(blueInt, redInt);

                    // Base layer with depth=0.0 (innermost = red for COLOR mode)
                    // This represents the photosphere - light originates here
                    accumulated = vec4(applyColorMap(baseIntensity, 0.0), 1.0);
                    hasBase = true;
                }
            }
        }

        // Different compositing strategies for MONO vs COLOR modes
        if (colorMapMode == 0) {
            // MONO: Same absorption model as COLOR but with grayscale
            // Light travels from inner to outer, each layer absorbs based on darkness

            vec3 finalColor = vec3(0.0);
            float remainingLight = 1.0;
            int validSamples = 0;

            for (int i = 1; i < MAX_STEPS; i++) {
                if (i >= numSamples) {
                    break;
                }
                if (remainingLight < 0.01) {
                    break;
                }

                float shellPos = float(i) / float(numSamples - 1);
                float shellRadius = effInnerRadius + shellPos * volumeThickness;

                vec2 hit = intersectSphere(rayOrigin, rayDir, shellRadius);
                if (hit.x < 0.0) {
                    continue;
                }

                vec3 pos = rayOrigin + hit.x * rayDir;
                if (pos.z < 0.0) {
                    continue;
                }

                vec3 normal = normalize(pos);
                vec2 clampedXY = clampForUV(normal);
                vec2 uv = vec2(
                    diskCenterU + clampedXY.x * diskRadiusU,
                    diskCenterV - clampedXY.y * diskRadiusV
                );

                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                    continue;
                }

                float facingFactor = abs(dot(normal, -rayDir));

                float wingDepth = 1.0 - lineCenterDepth;
                float textureDepth = mix(wingDepth, lineCenterDepth, shellPos);
                float intensity = texture(volumeTexture, vec3(uv, textureDepth)).r;

                validSamples++;

                // Absorption model (same as COLOR)
                float absorptionRate = (1.0 - intensity);
                absorptionRate = absorptionRate * absorptionRate;
                float outerBoost = 1.0 + shellPos * 1.0;
                float limbCompensation = 0.3 + 0.7 * facingFactor;
                float absorption = absorptionRate * globalOpacity / float(numSamples) * 8.0 * limbCompensation * outerBoost;

                float absorbed = remainingLight * absorption;

                // Grayscale: use intensity as the "color"
                vec3 layerColor = vec3(intensity);
                finalColor += absorbed * layerColor;

                remainingLight *= (1.0 - absorption);
            }

            if (validSamples > 0) {
                float totalAbsorbed = 1.0 - remainingLight;
                if (totalAbsorbed > 0.01) {
                    finalColor = finalColor / totalAbsorbed;
                    if (hasBase) {
                        accumulated.rgb = mix(accumulated.rgb, finalColor, totalAbsorbed);
                    } else {
                        accumulated.rgb = finalColor;
                    }
                }
                accumulated.a = 1.0;
            }
        } else {
            // COLOR: Light travels from inner to outer, each layer can absorb and color it
            //
            // Model: Start with uncolored light (amount = 1.0)
            // Each layer absorbs some light based on darkness, and colors what it absorbs
            // Remaining light continues to next layer
            //
            // Final color = sum of (light absorbed at each layer × layer's color)

            vec3 finalColor = vec3(0.0);
            float remainingLight = 1.0;
            int validSamples = 0;

            for (int i = 1; i < MAX_STEPS; i++) {
                if (i >= numSamples) {
                    break;
                }
                if (remainingLight < 0.01) {
                    break; // No more light to absorb
                }

                float shellPos = float(i) / float(numSamples - 1);
                float shellRadius = effInnerRadius + shellPos * volumeThickness;

                vec2 hit = intersectSphere(rayOrigin, rayDir, shellRadius);
                if (hit.x < 0.0) {
                    continue;
                }

                vec3 pos = rayOrigin + hit.x * rayDir;
                // Only render front-facing hemisphere
                if (pos.z < 0.0) {
                    continue;
                }

                vec3 normal = normalize(pos);
                vec2 clampedXY = clampForUV(normal);
                vec2 uv = vec2(
                    diskCenterU + clampedXY.x * diskRadiusU,
                    diskCenterV - clampedXY.y * diskRadiusV
                );

                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                    continue;
                }

                // Facing factor: 1.0 at disk center, approaches 0 at limb
                float facingFactor = abs(dot(normal, -rayDir));

                float wingDepth = 1.0 - lineCenterDepth;
                float textureDepth = mix(wingDepth, lineCenterDepth, shellPos);
                float intensity = texture(volumeTexture, vec3(uv, textureDepth)).r;

                validSamples++;

                // How much light this layer absorbs (dark = absorbs more)
                // Scale by number of samples to distribute absorption across layers
                float absorptionRate = (1.0 - intensity);
                absorptionRate = absorptionRate * absorptionRate; // Square for more contrast
                // Boost outer layers (blue) to make them more visible
                float outerBoost = 1.0 + shellPos * 1.0; // 1x at inner, 2x at outer
                // Compensate for limb darkening: reduce absorption at grazing angles
                float limbCompensation = 0.3 + 0.7 * facingFactor;
                float absorption = absorptionRate * globalOpacity / float(numSamples) * 8.0 * limbCompensation * outerBoost;

                // Light absorbed at this layer
                float absorbed = remainingLight * absorption;

                // This layer's color (based on its depth/altitude)
                // Use high brightness for saturated color, darkness is encoded in absorption
                vec3 layerColor = applyColorMap(0.7, shellPos);

                // Add this layer's contribution (absorbed light × layer color)
                finalColor += absorbed * layerColor;

                // Remaining light continues to next layer
                remainingLight *= (1.0 - absorption);
            }

            if (validSamples > 0) {
                float totalAbsorbed = 1.0 - remainingLight;
                if (totalAbsorbed > 0.01) {
                    // Normalize the absorbed color
                    finalColor = finalColor / totalAbsorbed;

                    // Blend with base based on how much was absorbed
                    if (hasBase) {
                        accumulated.rgb = mix(accumulated.rgb, finalColor, totalAbsorbed);
                    } else {
                        accumulated.rgb = finalColor;
                    }
                }
                accumulated.a = 1.0;
            }
        }

        // Clamp final alpha
        accumulated.a = clamp(accumulated.a, 0.0, 1.0);
    }

    // Handle prominences (beyond the limb)
    // Prominences are rendered as a flat ring at z=0 in object space, rotating with the sphere
    // Only draw when ray misses the sphere (outerHit.x < 0) to avoid showing through the disk
    if (showProminences != 0 && outerHit.x < 0.0) {
        // Find where ray intersects the z=0 plane in object space (after rotation)
        if (abs(rayDir.z) > 0.001) {
            float t = -rayOrigin.z / rayDir.z;
            vec3 planePos = rayOrigin + t * rayDir;
            float distFromCenter = length(planePos.xy);

            // Check if we're in the prominence region (beyond outer sphere but within max extent)
            if (distFromCenter > effOuterRadius && distFromCenter < effOuterRadius * PROMINENCE_RADIUS) {
                float relativeRadius = distFromCenter / effOuterRadius;
                vec2 direction = normalize(planePos.xy);

                vec2 uv = vec2(
                    diskCenterU + direction.x * relativeRadius * diskRadiusU,
                    diskCenterV - direction.y * relativeRadius * diskRadiusV
                );

                if (uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0) {
                    float intensity = texture(volumeTexture, vec3(uv, lineCenterDepth)).r;
                    accumulated.rgb = vec3(intensity);
                }
            }
        }
    }

    // Post-processing: boost saturation for COLOR modes
    if (colorMapMode != 0 && accumulated.a > 0.0) {
        vec3 color = accumulated.rgb;
        float gray = (color.r + color.g + color.b) / 3.0;
        // Boost saturation by 1.5x
        color = mix(vec3(gray), color, 1.5);
        accumulated.rgb = clamp(color, 0.0, 1.0);
    }

    // Ensure alpha is always 1 to prevent JavaFX background showing through
    accumulated.a = 1.0;
    fragColor = accumulated;
}
