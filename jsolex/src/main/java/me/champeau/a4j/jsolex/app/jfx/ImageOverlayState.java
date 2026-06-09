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

import java.util.ArrayList;
import java.util.List;

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
        Float promScaleLineThickness,
        boolean drawActiveRegions,
        String activeRegionsColor,
        boolean activeRegionsBoxes,
        String signatureFontWeight,
        List<TextAreaOverlay> textAreas,
        String colorizeProfile,
        String colorizeColor,
        boolean colorizePreserveBrightness
) {
    public ImageOverlayState {
        textAreas = textAreas == null ? List.of() : List.copyOf(textAreas);
    }

    public static final ImageOverlayState DEFAULT = new ImageOverlayState(
            false, false, false, false, false, false, null, null,
            null, null, null, null, null, null, null, null,
            false, null, null, null, null, null, null,
            null, null, null, null, null, false, null, false, null, List.of(),
            null, null, false);

    public boolean isColorizeActive() {
        return colorizeProfile != null || colorizeColor != null;
    }

    public ImageOverlayState withDrawGlobe(boolean v) {
        return new ImageOverlayState(v, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withDrawObservationDetails(boolean v) {
        return new ImageOverlayState(drawGlobe, v, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withDrawSolarParameters(boolean v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, v, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withDrawEarth(boolean v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, v, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withDrawProminenceScale(boolean v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, v, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withPCorrected(boolean v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, v, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withEarthPosition(Integer x, Integer y) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, x, y, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withGridColor(String c) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, c, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withObsDetailsColor(String c) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, c, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withSolarParamsColor(String c) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, c, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withPromScaleColor(String c) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, c, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withPromCircles(Integer v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, v, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withPromStepKm(Integer v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, v, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withObsDetailsTemplate(String v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, v, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withLineThickness(Float v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, v, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withDrawSignature(boolean v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, v, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withSignatureText(String v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, v, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withSignatureColor(String v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, v, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withSignatureFontFamily(String v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, v, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withSignatureFontSize(Integer v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, v, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withSignatureFontWeight(String v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, v, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withSignaturePosition(Integer x, Integer y) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, x, y, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withObsDetailsPosition(Integer x, Integer y) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, x, y, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withSolarParamsPosition(Integer x, Integer y) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, x, y, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withPromScaleLineThickness(Float v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, v, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withDrawActiveRegions(boolean v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, v, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withActiveRegionsColor(String c) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, c, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withActiveRegionsBoxes(boolean v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, v, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withTextAreas(List<TextAreaOverlay> v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, v, colorizeProfile, colorizeColor, colorizePreserveBrightness);
    }

    public ImageOverlayState withColorizeProfile(String v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, v, null, colorizePreserveBrightness);
    }

    public ImageOverlayState withColorizeColor(String v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, null, v, colorizePreserveBrightness);
    }

    public ImageOverlayState withColorizePreserveBrightness(boolean v) {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, colorizeProfile, colorizeColor, v);
    }

    public ImageOverlayState withoutColorize() {
        return new ImageOverlayState(drawGlobe, drawObservationDetails, drawSolarParameters, drawEarth, drawProminenceScale, pCorrected, earthX, earthY, gridColor, obsDetailsColor, solarParamsColor, promScaleColor, promCircles, promStepKm, obsDetailsTemplate, lineThickness, drawSignature, signatureText, signatureColor, signatureFontFamily, signatureFontSize, signatureX, signatureY, obsDetailsX, obsDetailsY, solarParamsX, solarParamsY, promScaleLineThickness, drawActiveRegions, activeRegionsColor, activeRegionsBoxes, signatureFontWeight, textAreas, null, null, colorizePreserveBrightness);
    }

    public ImageOverlayState addTextArea(TextAreaOverlay area) {
        var copy = new ArrayList<>(textAreas);
        copy.add(area);
        return withTextAreas(copy);
    }

    public ImageOverlayState withTextAreaAt(int index, TextAreaOverlay area) {
        if (index < 0 || index >= textAreas.size()) {
            return this;
        }
        var copy = new ArrayList<>(textAreas);
        copy.set(index, area);
        return withTextAreas(copy);
    }

    public ImageOverlayState withoutTextAreaAt(int index) {
        if (index < 0 || index >= textAreas.size()) {
            return this;
        }
        var copy = new ArrayList<>(textAreas);
        copy.remove(index);
        return withTextAreas(copy);
    }

    public ImageOverlayState asPreset() {
        return this;
    }

    public ImageOverlayState mergePreset(ImageOverlayState preset) {
        return preset;
    }
}
