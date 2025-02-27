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

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.expr.Expression;
import me.champeau.a4j.jsolex.expr.ExpressionEvaluator;
import me.champeau.a4j.jsolex.expr.FunctionCall;
import me.champeau.a4j.jsolex.expr.Variable;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.sun.workflow.TransformationHistory;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.LOGGER;

public class DefaultImageScriptExecutor implements ImageMathScriptExecutor {
    public static final String BLACK_POINT_VAR = "blackPoint";
    public static final String ANGLE_P_VAR = "angleP";
    public static final String B0_VAR = "b0";
    public static final String L0_VAR = "l0";
    public static final String CARROT_VAR = "carrot";
    public static final String OUTPUTS_SECTION_NAME = "outputs";
    public static final String BATCH_SECTION_NAME = "batch";

    private final Function<Double, ImageWrapper> imagesByShift;
    private final Map<Class, Object> context;
    private final AtomicInteger executionCount = new AtomicInteger(0);
    private final Broadcaster broadcaster;
    private final ScriptTokenizer tokenizer = new ScriptTokenizer();
    private final Map<String, Object> variables = new HashMap<>();

    private boolean isCollectingShifts = false;

    public DefaultImageScriptExecutor(Function<Double, ImageWrapper> imageSupplier,
                                      Map<Class, Object> context,
                                      Broadcaster broadcaster) {
        this.imagesByShift = img -> isCollectingShifts ? ImageWrapper32.createEmpty() : imageSupplier.apply(img);
        this.context = context;
        this.broadcaster = broadcaster;
    }

    public DefaultImageScriptExecutor(Function<Double, ImageWrapper> imagesByShift,
                                      Map<Class, Object> context) {
        this(imagesByShift, context, Broadcaster.NO_OP);
    }

    @Override
    public void putVariable(String name, Object value) {
        variables.put(name, value);
    }

    public <T> void putInContext(Class<T> key, T value) {
        context.put(key, value);
    }

    @Override
    public ImageMathScriptResult execute(String script, SectionKind kind) {
        long nanoTime = System.nanoTime();
        try {
            var index = executionCount.getAndIncrement();
            var evaluator = new MemoizingExpressionEvaluator(broadcaster);
            populateContext(evaluator);
            var outputs = prepareOutputExpressions(script, index, evaluator, kind);
            var producedImages = new HashMap<String, ImageWrapper>();
            var producedFiles = new HashMap<String, Path>();
            return outputs == null ? new ImageMathScriptResult(producedImages, producedFiles, List.of(), Set.of(), Set.of(), Set.of(), false) : executeScript(evaluator, outputs, producedImages, producedFiles);
        } finally {
            var dur = java.time.Duration.ofNanos(System.nanoTime() - nanoTime);
            LOGGER.info(message("script.completed.in"), dur.toSeconds(), dur.toMillisPart() / 100);
        }
    }

    private ImageMathScriptResult executeScript(MemoizingExpressionEvaluator evaluator,
                                                PreparedScript preparedScript,
                                                Map<String, ImageWrapper> producedImages,
                                                Map<String, Path> producedFiles) {
        var imageStats = (ImageStats) context.get(ImageStats.class);
        if (imageStats != null) {
            evaluator.putVariable(BLACK_POINT_VAR, String.format(Locale.US, "%.3f", imageStats.blackpoint()));
        }
        var solarParams = (SolarParameters) context.get(SolarParameters.class);
        if (solarParams != null) {
            evaluator.putVariable(ANGLE_P_VAR, String.format(Locale.US, "%.4f", solarParams.p()));
            evaluator.putVariable(B0_VAR, String.format(Locale.US, "%.4f", solarParams.b0()));
            evaluator.putVariable(L0_VAR, String.format(Locale.US, "%.4f", solarParams.l0()));
            evaluator.putVariable(CARROT_VAR, String.valueOf(solarParams.carringtonRotation()));
        }
        var invalidExpressions = new ArrayList<InvalidExpression>();
        var variableShifts = new TreeSet<>(evaluator.getShifts());
        var outputs = preparedScript.outputs;
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
        evaluator.clearCache();
        broadcaster.broadcast(ProgressEvent.of(0d, "ImageScript evaluation"));
        var entries = outputs.entrySet();
        var vars = preparedScript.variables();
        double size = entries.size() + vars.size();
        double idx = 0d;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            try {
                evaluator.evaluate(entry.getValue());
            } catch (Exception ex) {
                // ignore
            }
        }
        for (Map.Entry<String, String> output : entries) {
            idx++;
            var label = output.getKey();
            var expression = output.getValue();
            broadcaster.broadcast(ProgressEvent.of(idx / size, "ImageScript : " + expression));
            try {
                var result = evaluator.evaluate(expression);
                if (result instanceof ImageWrapper image) {
                    producedImages.put(label, image);
                } else if (result instanceof FileOutput file) {
                    producedFiles.put(label, file.file());
                } else if (result instanceof List<?> images) {
                    int img = 0;
                    for (Object o : images) {
                        if (o instanceof ImageWrapper image) {
                            producedImages.put(label + "_" + img++, image);
                        } else if (o instanceof FileOutput file) {
                            producedFiles.put(label + "_" + img++, file.file());
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
        return new ImageMathScriptResult(producedImages, producedFiles, invalidExpressions, Collections.unmodifiableSet(variableShifts), Collections.unmodifiableSet(expressionShifts), evaluator.getAutoWavelenghts(), evaluator.usesAutoContinuum());
    }

    private static boolean isOutputSection(String currentSection) {
        return OUTPUTS_SECTION_NAME.equals(currentSection);
    }

    private static boolean isBatchSectionName(String currentSection) {
        return BATCH_SECTION_NAME.equals(currentSection);
    }

    private PreparedScript prepareOutputExpressions(String script,
                                                    int index,
                                                    AbstractImageExpressionEvaluator evaluator,
                                                    SectionKind kind) {
        var scriptsPerSection = new EnumMap<SectionKind, PreparedScript>(SectionKind.class);
        var currentSectionKind = SectionKind.SINGLE;
        int cpt = 0;
        String currentSection = null;
        var invalidExpressions = new ArrayList<InvalidExpression>();
        var variables = new LinkedHashMap<String, String>();
        var outputs = new LinkedHashMap<String, String>();
        var tokens = tokenizer.tokenize(script);
        for (ScriptToken token : tokens) {
            if (token instanceof ScriptToken.Section section) {
                currentSection = section.name();
                if (section.isMajor() && isBatchSectionName(currentSection)) {
                    collectInternalShifts(evaluator, variables, outputs);
                    scriptsPerSection.put(SectionKind.SINGLE, new PreparedScript(outputs, variables, invalidExpressions));
                    outputs = new LinkedHashMap<>();
                    invalidExpressions = new ArrayList<>();
                    variables = new LinkedHashMap<>();
                    currentSectionKind = SectionKind.BATCH;
                }
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
        collectInternalShifts(evaluator, variables, outputs);
        scriptsPerSection.put(currentSectionKind, new PreparedScript(outputs, variables, invalidExpressions));
        var single = scriptsPerSection.get(SectionKind.SINGLE);
        var batch = scriptsPerSection.get(SectionKind.BATCH);
        return switch (kind) {
            case SINGLE -> single;
            case BATCH -> scriptsPerSection.get(SectionKind.BATCH);
            case ALL -> {
                var allOutputs = new HashMap<>(single.outputs());
                var allInvalidExpressions = new ArrayList<>(single.invalidExpressions());
                if (batch != null) {
                    allOutputs.putAll(batch.outputs());
                    allInvalidExpressions.addAll(batch.invalidExpressions());
                }
                yield new PreparedScript(
                    allOutputs,
                    variables,
                    allInvalidExpressions
                );
            }
        };
    }

    private void collectInternalShifts(AbstractImageExpressionEvaluator evaluator, LinkedHashMap<String, String> variables, LinkedHashMap<String, String> outputs) {
        isCollectingShifts = true;
        var shiftCollectingEvaluator = new ShiftCollectingExpressionEvaluator(evaluator);
        try {
            // Collect internal shifts
            var variableNames = variables.keySet();
            double size = variableNames.size();
            double idx = 0d;
            for (String variable : variableNames) {
                broadcaster.broadcast(ProgressEvent.of(idx / size, "ImageScript evaluation " + variable));
                try {
                    shiftCollectingEvaluator.evaluate(variable);
                } catch (Exception ex) {
                    // ignore
                }
            }
            if (outputs.isEmpty()) {
                // no explicit [outputs] section, consider everything an output
                outputs.putAll(variables);
            }
        } finally {
            broadcaster.broadcast(ProgressEvent.of(1.0d, "ImageScript evaluation"));
            isCollectingShifts = false;
        }
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
        evaluator.putInContext(Broadcaster.class, broadcaster);
    }

    /**
     * This evaluator is used to handle some function calls when we're collecting shifts,
     * so that it avoids doing the actual computation.
     */
    private static class ShiftCollectingExpressionEvaluator extends ExpressionEvaluator {
        private final AbstractImageExpressionEvaluator evaluator;

        public ShiftCollectingExpressionEvaluator(AbstractImageExpressionEvaluator evaluator) {
            this.evaluator = evaluator;
        }

        @Override
        protected Object plus(Object left, Object right) {
            return evaluator.plus(left, right);
        }

        @Override
        protected Object minus(Object left, Object right) {
            return evaluator.minus(left, right);
        }

        @Override
        protected Object mul(Object left, Object right) {
            return evaluator.mul(left, right);
        }

        @Override
        protected Object div(Object left, Object right) {
            return evaluator.mul(left, right);
        }

        @Override
        protected Object functionCall(BuiltinFunction function, List<Object> arguments) {
            return switch (function) {
                case LOAD -> ImageWrapper32.createEmpty();
                case LOAD_MANY -> List.of(ImageWrapper32.createEmpty(), ImageWrapper32.createEmpty());
                default -> evaluator.functionCall(function, arguments);
            };
        }
    }

    private class MemoizingExpressionEvaluator extends ShiftCollectingImageExpressionEvaluator {
        private final Map<String, Object> memoizeCache = new ConcurrentHashMap<>();

        public MemoizingExpressionEvaluator(Broadcaster broadcaster) {
            super(broadcaster, DefaultImageScriptExecutor.this.imagesByShift);
        }

        @Override
        protected Object variable(String name) {
            if (variables.containsKey(name)) {
                return variables.get(name);
            }
            return super.variable(name);
        }

        @Override
        public Object evaluate(String expression) {
            broadcaster.broadcast(ProgressEvent.of(0, "Evaluating " + expression));
            try {
                return super.evaluate(expression);
            } finally {
                broadcaster.broadcast(ProgressEvent.of(1.0, "Evaluating " + expression));
            }
        }

        @Override
        protected Object doEvaluate(Expression expression) {
            if (isCollectingShifts && expression instanceof FunctionCall funCall) {
                if (funCall.function() == BuiltinFunction.LOAD || funCall.function() == BuiltinFunction.CHOOSE_FILE) {
                    return ImageWrapper32.createEmpty();
                }
                if (funCall.function() == BuiltinFunction.LOAD_MANY || funCall.function() == BuiltinFunction.CHOOSE_FILES) {
                    return List.of();
                }
            }
            // Not using `computeIfAbsent` to avoid recursive update
            var exprAsString = expression.toString();
            if (memoizeCache.containsKey(exprAsString)) {
                var o = memoizeCache.get(exprAsString);
                if (o instanceof ImageWrapper image) {
                    return image.copy();
                } else if (o instanceof List<?> list) {
                    var copy = new ArrayList<>(list.size());
                    for (Object object : list) {
                        if (object instanceof ImageWrapper image) {
                            copy.add(image.copy());
                        } else {
                            copy.add(object);
                        }
                    }
                    return Collections.unmodifiableList(copy);
                }
                return o;
            }
            broadcaster.broadcast(ProgressEvent.of(0, "Evaluating " + expression));
            try {
                var result = super.doEvaluate(expression);
                if (result instanceof ImageWrapper image) {
                    TransformationHistory.recordTransform(image, "ImageMath: " + expression);
                    result = FileBackedImage.wrap(image);
                } else if (result instanceof List<?> list) {
                    if (list.stream().allMatch(ImageWrapper.class::isInstance)) {
                        result = list.stream()
                            .map(ImageWrapper.class::cast)
                            .map(image -> TransformationHistory.recordTransform(image, "ImageMath: " + expression))
                            .map(FileBackedImage::wrap)
                            .toList();
                    }
                }
                memoizeCache.put(exprAsString, result);
                return result;
            } finally {
                broadcaster.broadcast(ProgressEvent.of(1.0, "Evaluating " + expression));
            }
        }

        public void clearCache() {
            super.clearCache();
            memoizeCache.clear();
        }
    }

    public static class SyntaxError extends Exception {
        public SyntaxError(String message) {
            super(message);
        }
    }

    private record PreparedScript(Map<String, String> outputs,
                                  Map<String, String> variables,
                                  List<InvalidExpression> invalidExpressions) {

    }

}
