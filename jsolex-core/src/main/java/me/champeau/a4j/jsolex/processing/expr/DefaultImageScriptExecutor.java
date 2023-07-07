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

import me.champeau.a4j.jsolex.expr.Variable;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class DefaultImageScriptExecutor implements ImageMathScriptExecutor {
    public static final String BLACK_POINT_VAR = "blackPoint";
    public static final String OUTPUTS_SECTION_NAME = "outputs";

    private final Function<Integer, ImageWrapper> imagesByShift;
    private final Map<Class, Object> context;
    private final AtomicInteger executionCount = new AtomicInteger(0);
    private final Broadcaster broadcaster;
    private final ScriptTokenizer tokenizer = new ScriptTokenizer();

    public DefaultImageScriptExecutor(Function<Integer, ImageWrapper> imagesByShift,
                                      Map<Class, Object> context,
                                      Broadcaster broadcaster) {
        this.imagesByShift = imagesByShift;
        this.context = context;
        this.broadcaster = broadcaster;
    }

    public DefaultImageScriptExecutor(Function<Integer, ImageWrapper> imagesByShift,
                                      Map<Class, Object> context) {
        this(imagesByShift, context, Broadcaster.NO_OP);
    }

    @Override
    public ImageMathScriptResult execute(String script) {
        var index = executionCount.getAndIncrement();
        var evaluator = new MemoizingExpressionEvaluator();
        populateContext(evaluator);
        var invalidExpressions = new ArrayList<InvalidExpression>();
        var outputs = prepareOutputExpressions(script, index, evaluator, invalidExpressions);
        var producedImages = new HashMap<String, ImageWrapper>();
        return executeScript(evaluator, invalidExpressions, outputs, producedImages);
    }

    private ImageMathScriptResult executeScript(ShiftCollectingImageExpressionEvaluator evaluator,
                                                List<InvalidExpression> invalidExpressions,
                                                Map<String, String> outputs,
                                                Map<String, ImageWrapper> producedImages) {
        var imageStats = (ImageStats) context.get(ImageStats.class);
        if (imageStats != null) {
            evaluator.putVariable(BLACK_POINT_VAR, String.format(Locale.US, "%.3f", imageStats.blackpoint()));
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
        broadcaster.broadcast(ProgressEvent.of(0d, "ImageScript evaluation"));
        var entries = outputs.entrySet();
        double size = entries.size();
        double idx = 0d;
        for (Map.Entry<String, String> output : entries) {
            idx++;
            var label = output.getKey();
            var expression = output.getValue();
            broadcaster.broadcast(ProgressEvent.of(idx / size, "ImageScript : " + expression));
            try {
                var result = evaluator.evaluate(expression);
                if (result instanceof ImageWrapper image) {
                    producedImages.put(label, image);
                } else if (result instanceof List<?> images) {
                    int img = 0;
                    for (Object o : images) {
                        if (o instanceof ImageWrapper image) {
                            producedImages.put(label + "_" + img++, image);
                        }
                    }
                }
            } catch (Exception ex) {
                invalidExpressions.add(new InvalidExpression(label, expression, ex));
            }
        }
        broadcaster.broadcast(ProgressEvent.of(1.0, "ImageScript evaluation"));
        var expressionShifts = new TreeSet<>(evaluator.getShifts());
        expressionShifts.removeAll(variableShifts);
        return new ImageMathScriptResult(producedImages, invalidExpressions, Collections.unmodifiableSet(variableShifts), Collections.unmodifiableSet(expressionShifts));
    }

    private static boolean isOutputSection(String currentSection) {
        return OUTPUTS_SECTION_NAME.equals(currentSection);
    }

    private Map<String, String> prepareOutputExpressions(String script,
                                                         int index,
                                                         AbstractImageExpressionEvaluator evaluator,
                                                         List<InvalidExpression> invalidExpressions) {
        var outputs = new LinkedHashMap<String, String>();
        int cpt = 0;
        String currentSection = null;
        Map<String, String> variables = new LinkedHashMap<>();
        var tokens = tokenizer.tokenize(script);
        for (ScriptToken token : tokens) {
            if (token instanceof ScriptToken.Section section) {
                currentSection = section.name();
            } else if (token instanceof ScriptToken.VariableDefinition variableDefinition) {
                var variable = variableDefinition.variable();
                var candidate = variableDefinition.expression();
                var name = variable.name();
                if (Variable.isReservedName(name)) {
                    invalidExpressions.add(new InvalidExpression(name, candidate.value(), createReservedNameError(name)));
                }
                if (candidate instanceof ScriptToken.Expression expression) {
                    var text = expression.expression();
                    if (isOutputSection(currentSection)) {
                        outputs.put(name, text);
                        continue;
                    }
                    variables.put(name, text);
                    try {
                        evaluator.putVariable(name, text);
                    } catch (Exception ex) {
                        invalidExpressions.add(new InvalidExpression(name, text, ex));
                    }
                } else if (candidate instanceof ScriptToken.Invalid invalid) {
                    invalidExpressions.add(new InvalidExpression(name, invalid.value(), new SyntaxError("Syntax error")));
                }
            } else if (token instanceof ScriptToken.Expression expr) {
                var dynamicVarName = "imagemath_" + index + "_" + cpt;
                cpt++;
                if (isOutputSection(currentSection)) {
                    outputs.put(dynamicVarName, expr.expression());
                } else {
                    variables.put(dynamicVarName, expr.expression());
                }
            } else if (token instanceof ScriptToken.Invalid invalid) {
                var dynamicVarName = "imagemath_" + index + "_" + cpt;
                cpt++;
                invalidExpressions.add(new InvalidExpression(dynamicVarName, invalid.value(), new SyntaxError("Syntax error")));
            }
        }
        // Collect internal shifts
        var variableNames = variables.keySet();
        double size = variableNames.size();
        double idx = 0d;
        for (String variable : variableNames) {
            broadcaster.broadcast(ProgressEvent.of(idx / size, "ImageScript evaluation " + variable));
            try {
                evaluator.evaluate(variable);
            } catch (Exception ex) {
                // ignore
            }
        }
        broadcaster.broadcast(ProgressEvent.of(1.0d, "ImageScript evaluation"));
        if (outputs.isEmpty()) {
            // no explicit [outputs] section, consider everything an output
            outputs.putAll(variables);
        }
        return outputs;
    }

    private static Variable.InvalidNameException createReservedNameError(String name) {
        return new Variable.InvalidNameException("'" + name + "' is a reserved name. You cannot have a label which name is also the name of a built-in function.");
    }

    private void populateContext(AbstractImageExpressionEvaluator evaluator) {
        for (Map.Entry<Class, Object> entry : context.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            evaluator.putInContext(key, value);
        }
    }

    private class MemoizingExpressionEvaluator extends ShiftCollectingImageExpressionEvaluator {
        private final Map<String, Object> memoizeCache = new ConcurrentHashMap<>();

        public MemoizingExpressionEvaluator() {
            super(DefaultImageScriptExecutor.this.imagesByShift);
        }

        @Override
        public Object evaluate(String expression) {
            return memoizeCache.computeIfAbsent(expression, super::evaluate);
        }
    }

    public static class SyntaxError extends Exception {
        public SyntaxError(String message) {
            super(message);
        }
    }

}
