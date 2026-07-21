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
package me.champeau.a4j.jsolex.processing.params;

import me.champeau.a4j.math.regression.Ellipse;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalDouble;

public class GeometryParams {
    private final Double tilt;
    private final Double xyRatio;
    private final boolean horizontalMirror;
    private final boolean verticalMirror;
    private final boolean disallowDownsampling;
    private final boolean autocorrectAngleP;
    private final RotationKind rotation;
    private final AutocropMode autocropMode;
    private final Integer fixedWidth;
    private final DeconvolutionMode deconvolutionMode;
    private final RichardsonLucyDeconvolutionParams richardsonLucyDeconvolutionParams;
    private final boolean forcePolynomial;
    private final String forcedPolynomial;
    private final boolean saturatedDiskMode;
    private final String referencePolynomialDirectory;
    private final boolean spectrumVFlip;
    private final EllipseFittingMode ellipseFittingMode;
    private final ConditionalFlip horizontalFlipCondition;
    private final ConditionalFlip verticalFlipCondition;

    public GeometryParams(Double tilt,
                          Double xyRatio,
                          boolean horizontalMirror,
                          boolean verticalMirror,
                          boolean disallowDownsampling,
                          boolean autocorrectAngleP,
                          RotationKind rotation,
                          AutocropMode autocropMode,
                          Integer fixedWidth,
                          DeconvolutionMode deconvolutionMode,
                          RichardsonLucyDeconvolutionParams richardsonLucyDeconvolutionParams,
                          boolean forcePolynomial,
                          String forcedPolynomial,
                          boolean saturatedDiskMode,
                          String referencePolynomialDirectory,
                          boolean spectrumVFlip,
                          EllipseFittingMode ellipseFittingMode,
                          ConditionalFlip horizontalFlipCondition,
                          ConditionalFlip verticalFlipCondition) {
        this.tilt = tilt;
        this.xyRatio = xyRatio;
        this.horizontalMirror = horizontalMirror;
        this.verticalMirror = verticalMirror;
        this.disallowDownsampling = disallowDownsampling;
        this.autocorrectAngleP = autocorrectAngleP;
        this.rotation = rotation;
        this.autocropMode = autocropMode;
        this.fixedWidth = fixedWidth;
        this.deconvolutionMode = deconvolutionMode;
        this.richardsonLucyDeconvolutionParams = richardsonLucyDeconvolutionParams;
        this.forcePolynomial = forcePolynomial;
        this.forcedPolynomial = forcedPolynomial;
        this.saturatedDiskMode = saturatedDiskMode;
        this.referencePolynomialDirectory = referencePolynomialDirectory;
        this.spectrumVFlip = spectrumVFlip;
        this.ellipseFittingMode = ellipseFittingMode;
        this.horizontalFlipCondition = horizontalFlipCondition;
        this.verticalFlipCondition = verticalFlipCondition;
    }

    public OptionalDouble tilt() {
        return tilt == null ? OptionalDouble.empty() : OptionalDouble.of(tilt);
    }

    public OptionalDouble xyRatio() {
        return xyRatio == null ? OptionalDouble.empty() : OptionalDouble.of(xyRatio);
    }

    public boolean isHorizontalMirror() {
        return horizontalMirror;
    }

    public boolean isVerticalMirror() {
        return verticalMirror;
    }


    public boolean isDisallowDownsampling() {
        return disallowDownsampling;
    }

    public boolean isAutocorrectAngleP() {
        return autocorrectAngleP;
    }

    public RotationKind rotation() {
        return rotation;
    }

    public AutocropMode autocropMode() {
        return autocropMode;
    }

    public Optional<Integer> fixedWidth() {
        return Optional.ofNullable(fixedWidth);
    }

    public DeconvolutionMode deconvolutionMode() {
        return deconvolutionMode;
    }

    public Optional<RichardsonLucyDeconvolutionParams> richardsonLucyDeconvolutionParams() {
        return Optional.ofNullable(richardsonLucyDeconvolutionParams);
    }

    public boolean isForcePolynomial() {
        return forcePolynomial;
    }

    public Optional<String> forcedPolynomial() {
        return Optional.ofNullable(forcedPolynomial);
    }

    public boolean isSaturatedDiskMode() {
        return saturatedDiskMode;
    }

    public Optional<String> referencePolynomialDirectory() {
        return Optional.ofNullable(referencePolynomialDirectory);
    }

    public boolean isSpectrumVFlip() {
        return spectrumVFlip;
    }

    public EllipseFittingMode ellipseFittingMode() {
        return ellipseFittingMode;
    }

    public Optional<ConditionalFlip> horizontalFlipCondition() {
        return Optional.ofNullable(horizontalFlipCondition);
    }

    public Optional<ConditionalFlip> verticalFlipCondition() {
        return Optional.ofNullable(verticalFlipCondition);
    }

    /**
     * Replaces the flip conditions, if any, with plain mirror flags resolved
     * against the capture date of the video being processed.
     *
     * @param utcDateTime the capture date, in UTC
     * @return geometry params without flip conditions
     */
    public GeometryParams resolveFlipConditions(LocalDateTime utcDateTime) {
        if (horizontalFlipCondition == null && verticalFlipCondition == null) {
            return this;
        }
        var hflip = horizontalFlipCondition != null ? horizontalFlipCondition.flipAt(utcDateTime) : horizontalMirror;
        var vflip = verticalFlipCondition != null ? verticalFlipCondition.flipAt(utcDateTime) : verticalMirror;
        return new GeometryParams(tilt, xyRatio, hflip, vflip, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, null, null);
    }

    public GeometryParams withTilt(Double tilt) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withXYRatio(Double xyRatio) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withHorizontalMirror(boolean horizontalMirror) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withVerticalMirror(boolean verticalMirror) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }


    public GeometryParams withDisallowDownsampling(boolean disallowDownsampling) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withAutocorrectAngleP(boolean autocorrectAngleP) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withRotation(RotationKind rotation) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withAutocropMode(AutocropMode autocropMode) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withFixedWidth(Integer fixedWidth) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withDeconvolutionMode(DeconvolutionMode deconvolutionMode) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withRichardsonLucyDeconvolutionParams(RichardsonLucyDeconvolutionParams richardsonLucyDeconvolutionParams) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withForcePolynomial(boolean forcePolynomial) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withForcedPolynomial(String forcedPolynomial) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withSaturatedDiskMode(boolean saturatedDiskMode) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withReferencePolynomialDirectory(String referencePolynomialDirectory) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withSpectrumVFlip(boolean spectrumVFlip) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withEllipseFittingMode(EllipseFittingMode ellipseFittingMode) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withHorizontalFlipCondition(ConditionalFlip horizontalFlipCondition) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withVerticalFlipCondition(ConditionalFlip verticalFlipCondition) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    public GeometryParams withUserEllipse(Ellipse userEllipse) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror, disallowDownsampling, autocorrectAngleP, rotation, autocropMode, fixedWidth, deconvolutionMode, richardsonLucyDeconvolutionParams, forcePolynomial, forcedPolynomial, saturatedDiskMode, referencePolynomialDirectory, spectrumVFlip, ellipseFittingMode, horizontalFlipCondition, verticalFlipCondition);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("GeometryParams[");
        if (tilt != null) {
            sb.append("tilt=").append(tilt);
        }
        if (xyRatio != null) {
            sb.append(", xyRatio=").append(xyRatio);
        }
        sb.append(", horizontalMirror=").append(horizontalMirror)
                .append(", verticalMirror=").append(verticalMirror)
                .append("]");
        return sb.toString();
    }
}
