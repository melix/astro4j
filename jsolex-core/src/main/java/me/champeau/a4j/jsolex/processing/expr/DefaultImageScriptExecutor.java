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
import me.champeau.a4j.jsolex.expr.ExpressionEvaluator;
import me.champeau.a4j.jsolex.expr.ImageMathParser;
import me.champeau.a4j.jsolex.expr.Node;
import me.champeau.a4j.jsolex.expr.ParseException;
import me.champeau.a4j.jsolex.expr.UserFunction;
import me.champeau.a4j.jsolex.expr.ast.Assignment;
import me.champeau.a4j.jsolex.expr.ast.Expression;
import me.champeau.a4j.jsolex.expr.ast.FunctionCall;
import me.champeau.a4j.jsolex.expr.ast.FunctionDef;
import me.champeau.a4j.jsolex.expr.ast.ImageMathScript;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.TransformationHistory;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator.computeDispersion;
import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.LOGGER;

public class DefaultImageScriptExecutor implements ImageMathScriptExecutor {
    public static final String BLACK_POINT_VAR = "blackPoint";
    public static final String ANGLE_P_VAR = "angleP";
    public static final String B0_VAR = "b0";
    public static final String L0_VAR = "l0";
    public static final String CARROT_VAR = "carrot";
    public static final String DETECTED_WAVELEN = "detectedWavelen";
    public static final String DETECTED_DISPERSION = "detectedDispersion";

    public static final String OUTPUTS_SECTION_NAME = "outputs";

    private final Function<PixelShift, ImageWrapper> imagesByShift;
    private final Map<Class, Object> context;
    private final AtomicInteger executionCount = new AtomicInteger(0);
    private final Broadcaster broadcaster;
    private final Map<String, Object> variables = new LinkedHashMap<>();
    private final ProgressOperation operation;

    private Path includesDir;
    private boolean outputLogging = true;

    public DefaultImageScriptExecutor(Function<PixelShift, ImageWrapper> imageSupplier,
                                      Map<Class, Object> context,
                                      Broadcaster broadcaster) {
        this.imagesByShift = imageSupplier;
        this.context = context;
        this.broadcaster = broadcaster;
        this.operation = createOperation(context);
    }

    public boolean isCollectingShifts() {
        return ShiftCollectingImageExpressionEvaluator.isShiftCollecting(imagesByShift);
    }

    private static ProgressOperation createOperation(Map<Class, Object> context) {
        var progressOperation = (ProgressOperation) context.get(ProgressOperation.class);
        if (progressOperation == null) {
            progressOperation = ProgressOperation.root("ImageScript evaluation", unused -> {
            });
        }
        return progressOperation;
    }

    public DefaultImageScriptExecutor(Function<PixelShift, ImageWrapper> imagesByShift,
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

    @Override
    public ImageMathScriptResult execute(String script, SectionKind kind) {
        long nanoTime = System.nanoTime();
        var scriptOperation = operation.createChild("ImageScript");
        try {
            var index = executionCount.getAndIncrement();
            var evaluator = new MemoizingExpressionEvaluator(broadcaster);
            populateContext(evaluator);
            var result = doExecuteScript(script, index, evaluator, kind);
            evaluator.getVariables().forEach((key, value) -> {
                if (!variables.containsKey(key)) {
                    variables.put(key, value);
                }
            });
            return result;
        } finally {
            if (!isCollectingShifts() && outputLogging) {
                var dur = java.time.Duration.ofNanos(System.nanoTime() - nanoTime);
                LOGGER.info(message("script.completed.in"), dur.toSeconds(), dur.toMillisPart() / 100);
                var secs = dur.toSeconds() + (dur.toMillisPart() / 1000d);
                broadcaster.broadcast(scriptOperation.complete(String.format(message("script.completed.in.format"), secs)));
            }
        }
    }

    public ImageMathScriptResult execute(List<Expression> expressions, List<UserFunction> userFunctions) {
        var evaluator = new MemoizingExpressionEvaluator(broadcaster);
        context.forEach(evaluator::putInContext);
        variables.forEach(evaluator::putVariable);
        userFunctions.forEach(fn -> {
            var prep = fn.prepare(imagesByShift, context, evaluator::addShift, broadcaster);
            evaluator.putFunction(fn.name(), prep);
        });
        var imagesByLabel = new LinkedHashMap<String, ImageWrapper>();
        var filesByLabel = new LinkedHashMap<String, Path>();
        var invalidExpressions = new ArrayList<InvalidExpression>();
        executeExpressions(executionCount.getAndIncrement(), evaluator, expressions, true, 0, imagesByLabel, filesByLabel, invalidExpressions);
        evaluator.getVariables().forEach((k, v) -> {
            if (!variables.containsKey(k)) {
                variables.put(k, v);
            }
        });
        return new ImageMathScriptResult(
                imagesByLabel,
                filesByLabel,
                invalidExpressions,
                Collections.emptySet(),
                evaluator.getShifts(),
                evaluator.getAutoWavelenghts(),
                evaluator.usesAutoContinuum()
        );
    }

    private void extractResults(Map<String, ImageWrapper> producedImages, Map<String, Path> producedFiles, String variableName, Object result) {
        switch (result) {
            case ImageWrapper image -> producedImages.put(variableName, image);
            case FileOutput(Path file) -> producedFiles.put(variableName, file);
            case List<?> images -> {
                int img = 0;
                for (Object o : images) {
                    if (o instanceof ImageWrapper image) {
                        producedImages.put(variableName + "_" + img++, image);
                    } else if (o instanceof FileOutput(Path file)) {
                        producedFiles.put(variableName + "_" + img++, file);
                    }
                }
            }
            case ExpressionEvaluator.NestedInvocationResult nestedResult -> {
                nestedResult.variables().forEach((key, value) -> {
                    extractResults(producedImages, producedFiles, key, value);
                });
            }
            case null -> {
            }
            default -> {
                if (outputLogging && !(result instanceof ExpressionEvaluator.NestedInvocationResult)) {
                    LOGGER.info(variableName + " = " + result);
                }
            }
        }
    }

    private ImageMathScriptResult doExecuteScript(String script,
                                                  int index,
                                                  MemoizingExpressionEvaluator evaluator,
                                                  SectionKind kind) {
        int cpt = 0;
        var parser = new ImageMathParser(script);
        parser.setIncludeDir(includesDir);
        ImageMathScript root;
        try {
            root = parser.parseAndInlineIncludes();
        } catch (ParseException ex) {
            throw new ProcessingException(ex);
        }
        var sections = root.findSections(kind);
        if (sections.isEmpty()) {
            return ImageMathScriptResult.EMPTY;
        }
        var functions = readUserFunctions(root);
        functions.forEach(function -> {
            var prep = function.prepare(imagesByShift, context, evaluator::addShift, broadcaster);
            evaluator.putFunction(function.name(), prep);
        });
        int outputSectionCount = 0;
        for (var section : sections) {
            if (section.name().isPresent() && OUTPUTS_SECTION_NAME.equals(section.name().get())) {
                outputSectionCount++;
            }
            if (section.name().isPresent() && section.name().get().equals("batch")) {
                outputSectionCount = 0;
            }
        }
        if (outputSectionCount > 1) {
            throw new ProcessingException(new SyntaxError("Only one [outputs] section is allowed"));
        }
        var outputSection = sections.stream()
                .filter(section -> section.name().isPresent() && OUTPUTS_SECTION_NAME.equals(section.name().get()))
                .findFirst()
                .or(() -> sections.stream()
                        .filter(section -> section.name().isEmpty())
                        .findFirst())
                .or(() -> {
                    if (kind == SectionKind.BATCH && sections.size() == 1) {
                        return Optional.of(sections.getFirst());
                    }
                    return Optional.empty();
                })
                .orElseThrow(() -> new ProcessingException(new SyntaxError("No [outputs] section found")));
        Map<String, ImageWrapper> imagesByLabel = new LinkedHashMap<>();
        Map<String, Path> filesByLabel = new LinkedHashMap<>();
        List<InvalidExpression> invalidExpressions = new ArrayList<>();
        var progressOperation = operation.createChild("ImageScript evaluation");
        broadcaster.broadcast(progressOperation);
        var internalShifts = new TreeSet<Double>();
        try {
            for (var section : sections) {
                var isOutputSection = section == outputSection;
                if (isOutputSection) {
                    internalShifts.addAll(evaluator.getShifts());
                    evaluator.clearShifts();
                }
                var expressions = section.childrenOfType(Expression.class);
                cpt = executeExpressions(index, evaluator, expressions, isOutputSection, cpt, imagesByLabel, filesByLabel, invalidExpressions);
            }
            return new ImageMathScriptResult(imagesByLabel, filesByLabel, invalidExpressions, internalShifts, evaluator.getShifts(), Collections.emptySet(), evaluator.usesAutoContinuum());
        } finally {
            broadcaster.broadcast(progressOperation.complete());
        }
    }


    private int executeExpressions(int index, MemoizingExpressionEvaluator evaluator, List<Expression> expressions, boolean isOutputSection, int cpt, Map<String, ImageWrapper> imagesByLabel, Map<String, Path> filesByLabel, List<InvalidExpression> invalidExpressions) {
        for (var expression : expressions) {
            try {
                var result = evaluator.evaluate(expression);
                if (expression instanceof Assignment assignment) {
                    if (assignment.isAnonymous() && isOutputSection) {
                        var dynamicVarName = "imagemath_" + index + "_" + cpt;
                        cpt++;
                        assignment.setVariable(dynamicVarName);
                    }
                    if (isOutputSection && assignment.variableName().isPresent()) {
                        var variableName = assignment.variableName().get();
                        extractResults(imagesByLabel, filesByLabel, variableName, result);
                    }
                }
            } catch (Exception ex) {
                var invalidExpression = new InvalidExpression(
                        expression.toString(),
                        expression.toString(),
                        ex
                );
                invalidExpressions.add(invalidExpression);
            }
        }
        return cpt;
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

    protected void populateContext(AbstractImageExpressionEvaluator evaluator) {
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
        evaluator.putVariable(DETECTED_DISPERSION, processParams == null ? 0 : computeDispersion(processParams, processParams.spectrumParams().ray().wavelength()).angstromsPerPixel());
        for (var entry : variables.entrySet()) {
            evaluator.putVariable(entry.getKey(), entry.getValue());
        }
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

    public class MemoizingExpressionEvaluator extends ShiftCollectingImageExpressionEvaluator {
        private final Map<String, Object> memoizeCache = new ConcurrentHashMap<>();

        public MemoizingExpressionEvaluator(Broadcaster broadcaster) {
            super(broadcaster, DefaultImageScriptExecutor.this.imagesByShift);
        }

        @Override
        protected Object doEvaluate(Node expression) {
            if (isCollectingShifts() && expression instanceof FunctionCall funCall) {
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

    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    public static class SyntaxError extends Exception {
        public SyntaxError(String message) {
            super(message);
        }
    }

}
