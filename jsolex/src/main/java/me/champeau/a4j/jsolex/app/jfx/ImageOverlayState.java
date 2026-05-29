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
package me.champeau.a4j.jsolex.app.jfx;

public record ImageOverlayState(
        boolean drawGlobe,
        boolean drawObservationDetails,
        boolean drawSolarParameters,
        boolean drawEarth,
        boolean drawProminenceScale,
        boolean pCorrected,
        Integer earthX,
        Integer earthY,
        String gridColor,
        String obsDetailsColor,
        String solarParamsColor,
        String promScaleColor,
        Integer promCircles,
        Integer promStepKm,
        String obsDetailsTemplate,
        Float lineThickness,
        boolean drawSignature,
        String signatureText,
        String signatureColor,
        String signatureFontFamily,
        Integer signatureFontSize,
        Integer signatureX,
        Integer signatureY,
        Integer obsDetailsX,
        Integer obsDetailsY,
        Integer solarParamsX,
        Integer solarParamsY,
        Float promScaleLineThickness
) {
    public static final ImageOverlayState DEFAULT = new ImageOverlayState(
            false, false, false, false, false, false, null, null,
            null, null, null, null, null, null, null, null,
            false, null, null, null, null, null, null,
            null, null, null, null, null);

    public ImageOverlayState withDrawGlobe(boolean v) {
        return new ImageOverlayState(v, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withDrawObservationDetails(boolean v) {
        return new ImageOverlayState(drawGlobe, v, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withDrawSolarParameters(boolean v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, v, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withDrawEarth(boolean v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, v, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withDrawProminenceScale(boolean v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, v, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withPCorrected(boolean v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, v, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withEarthPosition(Integer x, Integer y) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, x, y, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withGridColor(String c) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, c, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withObsDetailsColor(String c) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, c, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withSolarParamsColor(String c) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, c, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withPromScaleColor(String c) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, c, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withPromCircles(Integer v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, v, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withPromStepKm(Integer v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, v, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withObsDetailsTemplate(String v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, v, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withLineThickness(Float v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, v, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withDrawSignature(boolean v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, v, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withSignatureText(String v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, v, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withSignatureColor(String v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, v, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withSignatureFontFamily(String v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, v, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withSignatureFontSize(Integer v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, v, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withSignaturePosition(Integer x, Integer y) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, x, y, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withObsDetailsPosition(Integer x, Integer y) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, x, y, solarParamsX, solarParamsY, promScaleLineThickness);
    }

    public ImageOverlayState withSolarParamsPosition(Integer x, Integer y) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, x, y, promScaleLineThickness);
    }

    public ImageOverlayState withPromScaleLineThickness(Float v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, v);
    }

    public ImageOverlayState asPreset() {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, null, null, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, null, null, null, null, null, null, promScaleLineThickness);
    }

    public ImageOverlayState mergePreset(ImageOverlayState preset) {
        return new ImageOverlayState(preset.drawGlobe, preset.drawObservationDetails, preset.drawSolarParameters, preset.drawEarth, preset.drawProminenceScale, preset.pCorrected, earthX, earthY, preset.gridColor, preset.obsDetailsColor, preset.solarParamsColor, preset.promScaleColor, preset.promCircles, preset.promStepKm, preset.obsDetailsTemplate, preset.lineThickness, preset.drawSignature, preset.signatureText, preset.signatureColor, preset.signatureFontFamily, preset.signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness);
    }
}
