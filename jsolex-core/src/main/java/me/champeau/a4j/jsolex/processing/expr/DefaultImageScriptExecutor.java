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
package me.champeau.a4j.jsolex.processing.expr;

import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class DefaultImageScriptExecutor implements ImageMathScriptExecutor {
    public static final String BLACK_POINT_VAR = "blackPoint";
    private final Function<Integer, ImageWrapper> imagesByShift;
    private final Map<Class, Object> context;
    private final AtomicInteger executionCount = new AtomicInteger(0);

    public DefaultImageScriptExecutor(Function<Integer, ImageWrapper> imagesByShift,
                                      Map<Class, Object> context) {
        this.imagesByShift = imagesByShift;
        this.context = context;
    }

    @Override
    public ImageMathScriptResult execute(List<String> lines) {
        var index = executionCount.getAndIncrement();
        var evaluator = new ShiftCollectingImageExpressionEvaluator(imagesByShift);
        populateContext(evaluator);
        var invalidExpressions = new ArrayList<InvalidExpression>();
        var outputs = prepareOutputExpressions(lines, index, evaluator, invalidExpressions);
        var producedImages = new HashMap<String, ImageWrapper>();
        return executeScript(evaluator, invalidExpressions, outputs, producedImages);
    }

    private ImageMathScriptResult executeScript(ShiftCollectingImageExpressionEvaluator evaluator, ArrayList<InvalidExpression> invalidExpressions, Map<String, String> outputs, HashMap<String, ImageWrapper> producedImages) {
        var imageStats = (ImageStats) context.get(ImageStats.class);
        if (imageStats != null) {
            evaluator.putVariable(BLACK_POINT_VAR, String.format("%.3f", imageStats.blackpoint()));
        }
        var variableShifts = new TreeSet<>(evaluator.getShifts());
        for (Map.Entry<String, String> output : outputs.entrySet()) {
            var label = output.getKey();
            var expression = output.getValue();
            try {
                evaluator.putVariable(label, expression);
            } catch (Exception ex) {
                // ignore
            }
        }
        evaluator.clearShifts();
        for (Map.Entry<String, String> output : outputs.entrySet()) {
            var label = output.getKey();
            var expression = output.getValue();
            try {
                var result = evaluator.evaluate(expression);
                if (result instanceof ImageWrapper image) {
                    producedImages.put(label, image);
                }
            } catch (Exception ex) {
                invalidExpressions.add(new InvalidExpression(label, expression, ex));
            }
        }
        var expressionShifts = new TreeSet<>(evaluator.getShifts());
        expressionShifts.removeAll(variableShifts);
        return new ImageMathScriptResult(producedImages, invalidExpressions, Collections.unmodifiableSet(variableShifts), Collections.unmodifiableSet(expressionShifts));
    }

    private static boolean isOutputSection(String currentSection) {
        return "[outputs]".equals(currentSection);
    }

    private static Map<String, String> prepareOutputExpressions(List<String> lines, int index, AbstractImageExpressionEvaluator evaluator, ArrayList<InvalidExpression> invalidExpressions) {
        var outputs = new HashMap<String, String>();
        int cpt = 0;
        String currentSection = null;
        Map<String, String> variables = new HashMap<>();
        for (String line : lines) {
            if (line.startsWith("//") || line.startsWith("#")) {
                // comment
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.toLowerCase(Locale.US);
                continue;
            }
            var i = line.indexOf("=");
            if (i != -1) {
                var name = line.substring(0, i).trim();
                var expression = line.substring(i + 1).trim();
                if (isOutputSection(currentSection)) {
                    outputs.put(name, expression);
                    continue;
                }
                variables.put(name, expression);
                try {
                    evaluator.putVariable(name, expression);
                } catch (Exception ex) {
                    invalidExpressions.add(new InvalidExpression(name, expression, ex));
                }
            } else {
                var dynamicVarName = "imagemath_" + index + "_" + cpt;
                cpt++;
                if (isOutputSection(currentSection)) {
                    outputs.put(dynamicVarName, line);
                } else {
                    variables.put(dynamicVarName, line);
                }
            }
        }
        // Collect internal shifts
        for (String variable : variables.keySet()) {
            try {
                evaluator.evaluate(variable);
            } catch (Exception ex) {
                // ignore
            }
        }
        if (outputs.isEmpty()) {
            // no explicit [outputs] section, consider everything an output
            outputs.putAll(variables);
        }
        return outputs;
    }

    private void populateContext(AbstractImageExpressionEvaluator evaluator) {
        for (Map.Entry<Class, Object> entry : context.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            evaluator.putInContext(key, value);
        }
    }

}
