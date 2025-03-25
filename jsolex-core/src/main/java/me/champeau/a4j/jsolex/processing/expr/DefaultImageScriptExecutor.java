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

import me.champeau.a4j.jsolex.expr.*;
import me.champeau.a4j.jsolex.expr.ast.Assignment;
import me.champeau.a4j.jsolex.expr.ast.Expression;
import me.champeau.a4j.jsolex.expr.ast.FunctionCall;
import me.champeau.a4j.jsolex.expr.ast.FunctionDef;
import me.champeau.a4j.jsolex.expr.ast.ImageMathScript;
import me.champeau.a4j.jsolex.processing.event.Notification;
import me.champeau.a4j.jsolex.processing.event.NotificationEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.LOGGER;

public class DefaultImageScriptExecutor implements ImageMathScriptExecutor {
    public static final String BLACK_POINT_VAR = "blackPoint";
    public static final String ANGLE_P_VAR = "angleP";
    public static final String B0_VAR = "b0";
    public static final String L0_VAR = "l0";
    public static final String CARROT_VAR = "carrot";
    public static final String DETECTED_WAVELEN = "detectedWavelen";

    public static final String OUTPUTS_SECTION_NAME = "outputs";

    private final Function<Double, ImageWrapper> imagesByShift;
    private final Map<Class, Object> context;
    private final AtomicInteger executionCount = new AtomicInteger(0);
    private final Broadcaster broadcaster;
    private final Map<String, Object> variables = new LinkedHashMap<>();
    private final ProgressOperation operation;

    private boolean isCollectingShifts = false;
    private Path includesDir;
    private boolean outputLogging = true;

    public DefaultImageScriptExecutor(Function<Double, ImageWrapper> imageSupplier,
                                      Map<Class, Object> context,
                                      Broadcaster broadcaster) {
        this.imagesByShift = img -> isCollectingShifts ? ImageWrapper32.createEmpty() : imageSupplier.apply(img);
        this.context = context;
        this.broadcaster = broadcaster;
        this.operation = createOperation(context);
    }

    private static ProgressOperation createOperation(Map<Class, Object> context) {
        var progressOperation = (ProgressOperation) context.get(ProgressOperation.class);
        if (progressOperation == null) {
            progressOperation = ProgressOperation.root("ImageScript evaluation", unused -> {
            });
        }
        return progressOperation;
    }

    public DefaultImageScriptExecutor(Function<Double, ImageWrapper> imagesByShift,
                                      Map<Class, Object> context) {
        this(imagesByShift, context, Broadcaster.NO_OP);
    }

    @Override
    public void setIncludesDir(Path includesDir) {
        this.includesDir = includesDir;
    }

    public <T> void putInContext(Class<T> key, T value) {
        context.put(key, value);
    }

    @Override
    public void putVariable(String name, Object value) {
        variables.put(name, value);
    }

    public void setCollectingShifts(boolean collectingShifts) {
        isCollectingShifts = collectingShifts;
    }

    @Override
    public ImageMathScriptResult execute(String script, SectionKind kind) {
        long nanoTime = System.nanoTime();
        var scriptOperation = operation.createChild("ImageScript");
        try {
            var index = executionCount.getAndIncrement();
            var evaluator = new MemoizingExpressionEvaluator(broadcaster);
            populateContext(evaluator);
            var outputs = prepareOutputExpressions(script, index, evaluator, kind);
            if (outputs.error != null) {
                broadcaster.broadcast(new NotificationEvent(
                        new Notification(
                                Notification.AlertType.ERROR,
                                message("imagemath.parse.error"),
                                message("imagemath.parse.error"),
                                outputs.error
                        )
                ));
                broadcaster.broadcast(scriptOperation.complete());
                return ImageMathScriptResult.EMPTY;
            }
            var producedImages = new HashMap<String, ImageWrapper>();
            var producedFiles = new HashMap<String, Path>();
            return outputs == null ? new ImageMathScriptResult(producedImages, producedFiles, List.of(), evaluator.getShifts(), Set.of(), evaluator.getAutoWavelenghts(), false) : executeScript(evaluator, outputs, producedImages, producedFiles);
        } finally {
            var dur = java.time.Duration.ofNanos(System.nanoTime() - nanoTime);
            LOGGER.info(message("script.completed.in"), dur.toSeconds(), dur.toMillisPart() / 100);
            var secs = dur.toSeconds() + (dur.toMillisPart() / 1000d);
            broadcaster.broadcast(scriptOperation.complete(String.format(message("script.completed.in.format"), secs)));
        }
    }

    public ImageMathScriptResult execute(List<Expression> expressions, List<UserFunction> userFunctions) {
        var evaluator = new MemoizingExpressionEvaluator(broadcaster);
        context.forEach(evaluator::putInContext);
        return executeScript(evaluator,
                new PreparedScript(Set.of("result"), expressions, userFunctions, null),
                new HashMap<>(),
                new HashMap<>());
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
        var processParams = (ProcessParams) context.get(ProcessParams.class);
        evaluator.putVariable(DETECTED_WAVELEN, processParams == null ? 0 : processParams.spectrumParams().ray().wavelength().angstroms());
        for (var entry : variables.entrySet()) {
            evaluator.putVariable(entry.getKey(), entry.getValue());
        }
        var variableShifts = new TreeSet<>(evaluator.getShifts());
        preparedScript.userFunctions.forEach(f -> evaluator.putFunction(f.name(), f.prepare(imagesByShift, context, variableShifts::add, broadcaster)));
        var invalidExpressions = new ArrayList<InvalidExpression>();
        evaluator.clearShifts();
        evaluator.clearCache();
        var progressOperation = operation.createChild("ImageScript evaluation");
        broadcaster.broadcast(progressOperation);
        double idx = 0;
        double size = preparedScript.expressions.size();
        for (var expression : preparedScript.expressions) {
            try {
                broadcaster.broadcast(progressOperation.update(idx / size, "ImageScript : " + expression));
                evaluator.evaluate(expression);
            } catch (Exception ex) {
                invalidExpressions.add(new InvalidExpression(
                        expression.toString(),
                        expression.toString(),
                        ex
                ));
            } finally {
                idx++;
            }
        }
        var variables = evaluator.getVariables();
        this.variables.putAll(variables);
        for (var label : preparedScript.outputVariables) {
            var result = variables.get(label);
            switch (result) {
                case ImageWrapper image -> producedImages.put(label, image);
                case FileOutput(Path file) -> producedFiles.put(label, file);
                case List<?> images -> {
                    int img = 0;
                    for (Object o : images) {
                        if (o instanceof ImageWrapper image) {
                            producedImages.put(label + "_" + img++, image);
                        } else if (o instanceof FileOutput(Path file)) {
                            producedFiles.put(label + "_" + img++, file);
                        }
                    }
                }
                case null -> {
                }
                default -> {
                    if (!isCollectingShifts && outputLogging) {
                        LOGGER.info(label + " = " + result);
                    }
                }
            }
        }
        broadcaster.broadcast(progressOperation.complete());
        var expressionShifts = new TreeSet<>(evaluator.getShifts());
        expressionShifts.removeAll(variableShifts);
        return new ImageMathScriptResult(producedImages, producedFiles, invalidExpressions, Collections.unmodifiableSet(variableShifts), Collections.unmodifiableSet(expressionShifts), evaluator.getAutoWavelenghts(), evaluator.usesAutoContinuum());
    }

    private PreparedScript prepareOutputExpressions(String script,
                                                    int index,
                                                    AbstractImageExpressionEvaluator evaluator,
                                                    SectionKind kind) {
        int cpt = 0;
        var parser = new ImageMathParser(script);
        parser.setIncludeDir(includesDir);
        ImageMathScript root = null;
        try {
            root = parser.parseAndInlineIncludes();
        } catch (ParseException ex) {
            var error = ex.getMessage();
            return new PreparedScript(Collections.emptySet(), List.of(), List.of(), error);
        }
        Set<String> outputVariables = new LinkedHashSet<>();
        var sections = root.findSections(kind);
        if (sections.isEmpty()) {
            return new PreparedScript(Collections.emptySet(), List.of(), List.of(), null);
        }
        var functions = readUserFunctions(root);
        var outputSection = sections.stream()
                .filter(section -> section.name().isPresent() && OUTPUTS_SECTION_NAME.equals(section.name().get()))
                .findFirst()
                .or(() -> sections.stream()
                        .filter(section -> section.name().isEmpty())
                        .findFirst())
                .or(() -> sections.stream()
                        .filter(section -> kind == SectionKind.BATCH && section.name().isPresent() && "batch".equals(section.name().get()))
                        .findFirst())
                .orElse(null);
        var allExpressions = new ArrayList<Expression>();
        for (var section : sections) {
            var isOutputSection = section == outputSection;
            var expressions = section.childrenOfType(Expression.class);
            allExpressions.addAll(expressions);
            for (var expression : expressions) {
                if (expression instanceof Assignment assignment) {
                    if (assignment.isAnonymous() && isOutputSection) {
                        var dynamicVarName = "imagemath_" + index + "_" + cpt;
                        cpt++;
                        assignment.setVariable(dynamicVarName);
                    }
                    if (isOutputSection && assignment.variableName().isPresent()) {
                        var variableName = assignment.variableName().get();
                        outputVariables.add(variableName);
                    }
                }
            }
        }
        collectInternalShifts(evaluator, allExpressions);
        return new PreparedScript(outputVariables, allExpressions, functions, null);
    }

    private List<UserFunction> readUserFunctions(ImageMathScript scriptNode) {
        var functionDefs = scriptNode.childrenOfType(FunctionDef.class);
        if (functionDefs == null || functionDefs.isEmpty()) {
            return List.of();
        }
        var userFunctions = new ArrayList<UserFunction>();
        functionDefs.stream()
                .map(def -> new UserFunction(
                        def.name(),
                        def.arguments(),
                        def.body(),
                        imagesByShift,
                        context,
                        d -> {
                        },
                        broadcaster,
                        userFunctions
                )).forEach(userFunctions::add);
        return Collections.unmodifiableList(userFunctions);
    }

    private void collectInternalShifts(AbstractImageExpressionEvaluator evaluator, List<Expression> expressions) {
        var collecting = isCollectingShifts;
        isCollectingShifts = true;
        var shiftCollectingEvaluator = new ShiftCollectingExpressionEvaluator(evaluator);
        var progressOperation = operation.createChild("ImageScript evaluation");
        try {
            for (Expression expression : expressions) {
                broadcaster.broadcast(progressOperation.update(0, "ImageScript evaluation " + expression));
                try {
                    shiftCollectingEvaluator.evaluate(expression);
                } catch (Exception ex) {
                    // ignore
                }
            }
        } finally {
            broadcaster.broadcast(progressOperation.complete());
            isCollectingShifts = collecting;
        }
    }

    private void populateContext(AbstractImageExpressionEvaluator evaluator) {
        for (Map.Entry<Class, Object> entry : context.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            evaluator.putInContext(key, value);
        }
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            evaluator.putVariable(key, value);
        }
        evaluator.putInContext(Broadcaster.class, broadcaster);
        evaluator.putInContext(ProgressOperation.class, operation);
    }

    public Optional<Object> getVariable(String result) {
        return Optional.ofNullable(variables.get(result));
    }

    public void disableOutputLogging() {
        outputLogging = false;
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
        protected Object doEvaluate(Node expression) {
            if (isCollectingShifts && expression instanceof FunctionCall funCall) {
                if (funCall.getBuiltinFunction().isPresent()) {
                    var fun = funCall.getBuiltinFunction().get();
                    if (fun == BuiltinFunction.LOAD || fun == BuiltinFunction.CHOOSE_FILE) {
                        return ImageWrapper32.createEmpty();
                    }
                    if (fun == BuiltinFunction.LOAD_MANY || fun == BuiltinFunction.CHOOSE_FILES) {
                        return List.of();
                    }
                }
            }
            // Not using `computeIfAbsent` to avoid recursive update
            var cacheKey = expression.getAllTokens(false).stream().map(Objects::toString).collect(Collectors.joining());
            if (memoizeCache.containsKey(cacheKey)) {
                var o = memoizeCache.get(cacheKey);
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
            var progressOperation = operation.createChild("Evaluating " + expression);
            broadcaster.broadcast(progressOperation);
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
                memoizeCache.put(cacheKey, result);
                return result;
            } finally {
                broadcaster.broadcast(progressOperation.complete());
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

    private record PreparedScript(
            Set<String> outputVariables,
            List<Expression> expressions,
            List<UserFunction> userFunctions,
            String error
    ) {
    }

}
