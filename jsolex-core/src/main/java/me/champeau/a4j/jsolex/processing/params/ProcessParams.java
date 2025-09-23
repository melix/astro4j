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

import java.io.File;
import java.util.ArrayList;
import java.util.Optional;

public record ProcessParams(
    SpectrumParams spectrumParams,
    ObservationDetails observationDetails,
    ExtraParams extraParams,
    VideoParams videoParams,
    GeometryParams geometryParams,
    BandingCorrectionParams bandingCorrectionParams,
    RequestedImages requestedImages,
    ClaheParams claheParams,
    AutoStretchParams autoStretchParams,
    ContrastEnhancement contrastEnhancement,
    EnhancementParams enhancementParams
) {
    public static ProcessParams loadDefaults() {
        return ProcessParamsIO.loadDefaults();
    }

    public static void saveDefaults(ProcessParams params) {
        ProcessParamsIO.saveDefaults(params);
    }

    public static Optional<ProcessParams> readFrom(File configFile) {
        return Optional.ofNullable(ProcessParamsIO.readFrom(configFile.toPath()));
    }

    public void saveTo(File destination) {
        ProcessParamsIO.saveTo(this, destination);
    }

    public ProcessParams withGeometry(double tilt, double xyRatio) {
        return new ProcessParams(
            spectrumParams,
            observationDetails,
            extraParams,
            videoParams,
            new GeometryParams(tilt,
                xyRatio,
                geometryParams().isHorizontalMirror(),
                geometryParams().isVerticalMirror(),
                geometryParams().isDisallowDownsampling(),
                geometryParams().isAutocorrectAngleP(),
                geometryParams().rotation(),
                geometryParams().autocropMode(),
                geometryParams().fixedWidth().orElse(null),
                geometryParams().deconvolutionMode(),
                geometryParams().richardsonLucyDeconvolutionParams().orElse(null),
                geometryParams().isForcePolynomial(),
                geometryParams().forcedPolynomial().orElse(null),
                geometryParams().isSpectrumVFlip(),
                geometryParams().ellipseFittingMode()
            ),
            bandingCorrectionParams,
            requestedImages,
            claheParams,
            autoStretchParams,
            contrastEnhancement,
            enhancementParams
        );
    }

    public ProcessParams withSpectrumParams(SpectrumParams spectrumParams) {
        return new ProcessParams(
            spectrumParams,
            observationDetails,
            extraParams,
            videoParams,
            geometryParams,
            bandingCorrectionParams,
            requestedImages,
            claheParams,
            autoStretchParams,
            contrastEnhancement,
            enhancementParams
        );
    }

    public ProcessParams withObservationDetails(ObservationDetails observationDetails) {
        return new ProcessParams(
            spectrumParams,
            observationDetails,
            extraParams,
            videoParams,
            geometryParams,
            bandingCorrectionParams,
            requestedImages,
            claheParams,
            autoStretchParams,
            contrastEnhancement,
            enhancementParams
        );
    }

    public ProcessParams withExtraParams(ExtraParams debugParams) {
        return new ProcessParams(
            spectrumParams,
            observationDetails,
            debugParams,
            videoParams,
            geometryParams,
            bandingCorrectionParams,
            requestedImages,
            claheParams,
            autoStretchParams,
            contrastEnhancement,
            enhancementParams
        );
    }

    public ProcessParams withVideoParams(VideoParams videoParams) {
        return new ProcessParams(
            spectrumParams,
            observationDetails,
            extraParams,
            videoParams,
            geometryParams,
            bandingCorrectionParams,
            requestedImages,
            claheParams,
            autoStretchParams,
            contrastEnhancement,
            enhancementParams
        );
    }

    public ProcessParams withGeometryParams(GeometryParams geometryParams) {
        return new ProcessParams(
            spectrumParams,
            observationDetails,
            extraParams,
            videoParams,
            geometryParams,
            bandingCorrectionParams,
            requestedImages,
            claheParams,
            autoStretchParams,
            contrastEnhancement,
            enhancementParams
        );
    }

    public ProcessParams withBandingCorrectionParams(BandingCorrectionParams bandingCorrectionParams) {
        return new ProcessParams(
            spectrumParams,
            observationDetails,
            extraParams,
            videoParams,
            geometryParams,
            bandingCorrectionParams,
            requestedImages,
            claheParams,
            autoStretchParams,
            contrastEnhancement,
            enhancementParams
        );
    }

    public ProcessParams withRequestedImages(RequestedImages requestedImages) {
        return new ProcessParams(
            spectrumParams,
            observationDetails,
            extraParams,
            videoParams,
            geometryParams,
            bandingCorrectionParams,
            requestedImages,
            claheParams,
            autoStretchParams,
            contrastEnhancement,
            enhancementParams
        );
    }

    public ProcessParams withClaheParams(ClaheParams claheParams) {
        return new ProcessParams(
            spectrumParams,
            observationDetails,
            extraParams,
            videoParams,
            geometryParams,
            bandingCorrectionParams,
            requestedImages,
            claheParams,
            autoStretchParams,
            contrastEnhancement,
            enhancementParams
        );
    }

    public ProcessParams withAutoStretchParams(AutoStretchParams autoStretchParams) {
        return new ProcessParams(
            spectrumParams,
            observationDetails,
            extraParams,
            videoParams,
            geometryParams,
            bandingCorrectionParams,
            requestedImages,
            claheParams,
            autoStretchParams,
            contrastEnhancement,
            enhancementParams
        );
    }

    public ProcessParams withContrastEnhancement(ContrastEnhancement contrastEnhancement) {
        return new ProcessParams(
            spectrumParams,
            observationDetails,
            extraParams,
            videoParams,
            geometryParams,
            bandingCorrectionParams,
            requestedImages,
            claheParams,
            autoStretchParams,
            contrastEnhancement,
            enhancementParams
        );
    }

    public ProcessParams withEnhancementParams(EnhancementParams enhancementParams) {
        return new ProcessParams(
            spectrumParams,
            observationDetails,
            extraParams,
            videoParams,
            geometryParams,
            bandingCorrectionParams,
            requestedImages,
            claheParams,
            autoStretchParams,
            contrastEnhancement,
            enhancementParams
        );
    }

    public ImageMathParams combinedImageMathParams() {
        if (!requestedImages().applyAutomaticScripts()) {
            return requestedImages.mathImages();
        }

        var currentRay = spectrumParams().ray();
        if (currentRay.automaticScripts().isEmpty()) {
            return requestedImages.mathImages();
        }

        var combinedScripts = new ArrayList<>(requestedImages.mathImages().scriptFiles());
        for (var scriptPath : currentRay.automaticScripts()) {
            combinedScripts.add(scriptPath.toFile());
        }

        return new ImageMathParams(combinedScripts, requestedImages.mathImages().parameterValues());
    }
}
