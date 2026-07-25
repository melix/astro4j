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
 */
package me.champeau.a4j.jsolex.processing.expr.impl

import me.champeau.a4j.jsolex.processing.expr.FileCounts
import me.champeau.a4j.jsolex.processing.params.ProcessParams
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification
import spock.lang.Subject

class ImageDrawVariablesTest extends Specification {
    @Subject
    ImageDraw imageDraw

    def setup() {
        imageDraw = new ImageDraw(Map.of(), Broadcaster.NO_OP, () -> Map.of(
                "inputFilesCount", 12,
                "keptFilesCount", 9,
                "exposure", 1.5d,
                "label", "sun",
                "picture", new ImageWrapper32(1, 1, new float[1][1], [:])
        ))
    }

    def "substitutes script variables in text"() {
        expect:
        imageDraw.substituteScriptVariables(template) == expected

        where:
        template                                     || expected
        "%VAR_inputFilesCount% files"                || "12 files"
        "%VAR_INPUTFILESCOUNT%/%VAR_keptfilescount%" || "12/9"
        "%VAR_exposure%s on %VAR_label%"             || "1.50s on sun"
        "%VAR_unknown%"                              || "%VAR_unknown%"
        "%VAR_picture%"                              || "%VAR_picture%"
        "no variable here"                           || "no variable here"
    }

    def "leaves text untouched when no script variable is available"() {
        given:
        def draw = new ImageDraw(Map.of(), Broadcaster.NO_OP)

        expect:
        draw.substituteScriptVariables("%VAR_inputFilesCount%") == "%VAR_inputFilesCount%"
    }

    def "substitutes file counts in the observation details template"() {
        given:
        def draw = new ImageDraw(Map.of(FileCounts, new FileCounts(12, 9)), Broadcaster.NO_OP)

        expect:
        draw.computeObservationDetailsContent(image(), "%INPUT_FILES% files, %KEPT_FILES% kept") == "12 files, 9 kept"
    }

    def "renders empty file counts when they are unknown"() {
        given:
        def draw = new ImageDraw(Map.of(), Broadcaster.NO_OP)

        expect:
        draw.computeObservationDetailsContent(image(), "[%INPUT_FILES%][%KEPT_FILES%]") == "[][]"
    }

    private static ImageWrapper32 image() {
        new ImageWrapper32(1, 1, new float[1][1], [(ProcessParams): ProcessParams.loadDefaults()])
    }
}
