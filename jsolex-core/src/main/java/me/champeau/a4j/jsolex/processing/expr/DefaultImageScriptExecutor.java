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
import me.champeau.a4j.jsolex.processing.expr.python.PythonScriptExecutor;
import me.champeau.a4j.jsolex.processing.params.ImageMathParameterExtractor;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.TransformationHistory;
import me.champeau.a4j.jsolex.processing.util.AnimationFormat;
import me.champeau.a4j.jsolex.processing.util.DurationFormatter;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.FilesUtils;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
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

    private Path includesDir;
    private boolean outputLogging = true;

    public DefaultImageScriptExecutor(Function<PixelShift, ImageWrapper> imageSupplier,
                                      Map<Class, Object> context,
                                      Broadcaster broadcaster) {
        this.imagesByShift = imageSupplier;
        this.context = context;
        this.broadcaster = broadcaster;
    }

    public boolean isCollectingShifts() {
        return ShiftCollectingImageExpressionEvaluator.isShiftCollecting(imagesByShift);
    }

    private ProgressOperation getProgressOperation() {
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

    @Override
    public ImageMathScriptResult execute(Path source, SectionKind kind) throws IOException {
        setIncludesDir(source.getParent());

        if (source.toString().endsWith(".py")) {
            return executePythonScript(source, kind);
        }

        // Default: read as ImageMath script
        return execute(FilesUtils.readString(source), kind);
    }

    private ImageMathScriptResult executePythonScript(Path scriptPath, SectionKind kind) {
        // Skip Python execution during shift-collecting phase
        if (isCollectingShifts()) {
            return ImageMathScriptResult.EMPTY;
        }

        long nanoTime = System.nanoTime();
        try {
            // Create evaluator to get context for Python executor
            var evaluator = new MemoizingExpressionEvaluator(broadcaster);
            evaluator.setIncludesDir(includesDir);
            populateContext(evaluator, getProgressOperation());

            @SuppressWarnings("unchecked")
            var contextMap = (Map<Class<?>, Object>) (Map<?, ?>) context;
            var pythonExecutor = new PythonScriptExecutor(evaluator, contextMap, broadcaster, true);

            // For batch mode, check if script has a batch() function BEFORE executing
            // This avoids running top-level code that may fail in batch context
            if (kind == SectionKind.BATCH) {
                var scriptContent = FilesUtils.readString(scriptPath);
                if (!pythonExecutor.scriptDefinesFunction(scriptContent, "batch")) {
                    return ImageMathScriptResult.EMPTY;
                }
            }

            // Execute the file (runs top-level code and defines functions)
            var scriptResult = pythonExecutor.executeFile(scriptPath.toString(), variables);

            var hasSingle = pythonExecutor.hasFunction("single");
            var hasBatch = pythonExecutor.hasFunction("batch");

            if (kind == SectionKind.SINGLE) {
                if (!hasSingle && hasBatch) {
                    // Batch-only script, skip in single mode
                    return ImageMathScriptResult.EMPTY;
                }

                Map<String, Object> outputs;
                if (hasSingle) {
                    // Call single() function with outputs object
                    outputs = pythonExecutor.callSingleFunction();
                } else {
                    // Implicit single mode - merge result variable with outputs object
                    outputs = new LinkedHashMap<>();
                    // First add any outputs set via outputs.name = value
                    outputs.putAll(pythonExecutor.extractOutputs());
                    // Then add/override with result variable for backwards compatibility
                    outputs.putAll(resultToMap(scriptResult));
                }

                return toScriptResult(outputs);
            } else { // BATCH
                if (!hasBatch) {
                    return ImageMathScriptResult.EMPTY;
                }

                // Collect variables that are lists (from single mode executions)
                var collectedResults = new LinkedHashMap<String, List<Object>>();
                for (var entry : variables.entrySet()) {
                    if (entry.getValue() instanceof List<?> list) {
                        collectedResults.put(entry.getKey(), new ArrayList<>(list));
                    }
                }

                var outputs = pythonExecutor.callBatchFunction(collectedResults);

                return toScriptResult(outputs);
            }
        } catch (Exception e) {
            // Capture Python errors and return them as invalid expressions for display
            var invalidExpression = new InvalidExpression(
                scriptPath.getFileName().toString(),
                scriptPath.toString(),
                e
            );
            return new ImageMathScriptResult(
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(invalidExpression),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet(),
                false
            );
        } finally {
            if (!isCollectingShifts() && outputLogging) {
                var formatted = DurationFormatter.formatNanos(System.nanoTime() - nanoTime);
                LOGGER.info(message("script.completed.in"), formatted);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resultToMap(Object result) {
        if (result instanceof Map<?, ?> map) {
            var outputs = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                outputs.put(entry.getKey().toString(), entry.getValue());
            }
            return outputs;
        }
        if (result != null) {
            return Map.of("result", result);
        }
        return Map.of();
    }

    private ImageMathScriptResult toScriptResult(Map<String, Object> outputs) {
        var imagesByLabel = new LinkedHashMap<String, ImageWrapper>();
        var filesByLabel = new LinkedHashMap<String, FileOutputResult>();
        var valuesByLabel = new LinkedHashMap<String, Object>();

        for (var entry : outputs.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();

            if (value instanceof ImageWrapper image) {
                imagesByLabel.put(key, image);
            } else if (value instanceof SingleFileOutput singleFile) {
                filesByLabel.put(key, singleFile);
            } else if (value instanceof MultiFileOutput multiFile) {
                filesByLabel.put(key, multiFile);
            } else if (value != null) {
                valuesByLabel.put(key, value);
            }
        }

        return new ImageMathScriptResult(
            imagesByLabel,
            filesByLabel,
            valuesByLabel,
            List.of(),
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.emptySet(),
            false
        );
    }

    public <T> void putInContext(Class<T> key, Object value) {
        context.put(key, value);
    }

    @Override
    public void putVariable(String name, Object value) {
        variables.put(name, value);
    }

    @Override
    public ImageMathScriptResult executePythonScript(String script, SectionKind kind) {
        // Skip Python execution during shift-collecting phase
        if (isCollectingShifts()) {
            return ImageMathScriptResult.EMPTY;
        }

        long nanoTime = System.nanoTime();
        try {
            // Create evaluator to get context for Python executor
            var evaluator = new MemoizingExpressionEvaluator(broadcaster);
            evaluator.setIncludesDir(includesDir);
            populateContext(evaluator, getProgressOperation());

            @SuppressWarnings("unchecked")
            var contextMap = (Map<Class<?>, Object>) (Map<?, ?>) context;
            var pythonExecutor = new PythonScriptExecutor(evaluator, contextMap, broadcaster, true);

            // For batch mode, check if script has a batch() function BEFORE executing
            // This avoids running top-level code that may fail in batch context
            if (kind == SectionKind.BATCH && !pythonExecutor.scriptDefinesFunction(script, "batch")) {
                return ImageMathScriptResult.EMPTY;
            }

            // Execute the script text to define functions
            var scriptResult = pythonExecutor.executeInline(script, variables);

            var hasSingle = pythonExecutor.hasFunction("single");
            var hasBatch = pythonExecutor.hasFunction("batch");

            if (kind == SectionKind.SINGLE) {
                if (!hasSingle && hasBatch) {
                    // Batch-only script, skip in single mode
                    return ImageMathScriptResult.EMPTY;
                }

                Map<String, Object> outputs;
                if (hasSingle) {
                    // Call single() function with outputs object
                    outputs = pythonExecutor.callSingleFunction();
                } else {
                    // Implicit single mode - merge result variable with outputs object
                    outputs = new LinkedHashMap<>();
                    // First add any outputs set via outputs.name = value
                    outputs.putAll(pythonExecutor.extractOutputs());
                    // Then add/override with result variable for backwards compatibility
                    outputs.putAll(resultToMap(scriptResult));
                }

                return toScriptResult(outputs);
            } else { // BATCH
                if (!hasBatch) {
                    return ImageMathScriptResult.EMPTY;
                }

                // Collect variables that are lists (from single mode executions)
                var collectedResults = new LinkedHashMap<String, List<Object>>();
                for (var entry : variables.entrySet()) {
                    if (entry.getValue() instanceof List<?> list) {
                        collectedResults.put(entry.getKey(), new ArrayList<>(list));
                    }
                }

                var outputs = pythonExecutor.callBatchFunction(collectedResults);

                return toScriptResult(outputs);
            }
        } catch (Exception e) {
            // Capture Python errors and return them as invalid expressions for display
            var invalidExpression = new InvalidExpression(
                "Python script",
                script.length() > 100 ? script.substring(0, 100) + "..." : script,
                e
            );
            return new ImageMathScriptResult(
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(invalidExpression),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet(),
                false
            );
        } finally {
            if (!isCollectingShifts() && outputLogging) {
                var formatted = DurationFormatter.formatNanos(System.nanoTime() - nanoTime);
                LOGGER.info(message("script.completed.in"), formatted);
            }
        }
    }

    @Override
    public ImageMathScriptResult execute(String script, SectionKind kind) {
        long nanoTime = System.nanoTime();
        var operation = getProgressOperation();
        try {
            var index = executionCount.getAndIncrement();
            var evaluator = new MemoizingExpressionEvaluator(broadcaster);
            evaluator.setIncludesDir(includesDir);
            populateContext(evaluator, operation);
            return doExecuteScript(script, index, evaluator, kind, operation);
        } finally {
            if (!isCollectingShifts() && outputLogging) {
                var formatted = DurationFormatter.formatNanos(System.nanoTime() - nanoTime);
                LOGGER.info(message("script.completed.in"), formatted);
            }
        }
    }

    @Override
    public void removeVariable(String variable) {
        variables.remove(variable);
    }

    public ImageMathScriptResult execute(List<Expression> expressions, List<UserFunction> userFunctions) {
        var evaluator = new MemoizingExpressionEvaluator(broadcaster);
        evaluator.setIncludesDir(includesDir);
        context.forEach(evaluator::putInContext);
        variables.forEach(evaluator::putVariable);
        userFunctions.forEach(fn -> {
            var prep = fn.prepare(imagesByShift, context, evaluator::addShift, broadcaster, includesDir);
            evaluator.putFunction(fn.name(), prep);
        });
        var imagesByLabel = new LinkedHashMap<String, ImageWrapper>();
        var filesByLabel = new LinkedHashMap<String, FileOutputResult>();
        var valuesByLabel = new LinkedHashMap<String, Object>();
        var invalidExpressions = new ArrayList<InvalidExpression>();
        executeExpressions(executionCount.getAndIncrement(), evaluator, expressions, new AtomicInteger(), imagesByLabel, filesByLabel, valuesByLabel, invalidExpressions);
        evaluator.getVariables().forEach((k, v) -> {
            if (!variables.containsKey(k)) {
                variables.put(k, v);
            }
        });
        return new ImageMathScriptResult(
                imagesByLabel,
                filesByLabel,
                valuesByLabel,
                invalidExpressions,
                Collections.emptySet(),
                evaluator.getShifts(),
                evaluator.getAutoWavelenghts(),
                evaluator.usesAutoContinuum()
        );
    }

    private void extractResults(Map<String, ImageWrapper> producedImages, Map<String, FileOutputResult> producedFiles, Map<String, Object> producedValues, String variableName, Object result) {
        switch (result) {
            case ImageWrapper image -> producedImages.put(variableName, image);
            case SingleFileOutput singleFile -> producedFiles.put(variableName, singleFile);
            case MultiFileOutput multiFile -> producedFiles.put(variableName, multiFile);
            case List<?> items -> {
                int idx = 0;
                boolean allImages = true;
                boolean allFiles = true;
                for (Object o : items) {
                    if (!(o instanceof ImageWrapper)) {
                        allImages = false;
                    }
                    if (!(o instanceof SingleFileOutput) && !(o instanceof MultiFileOutput)) {
                        allFiles = false;
                    }
                }
                if (allImages || allFiles) {
                    for (Object o : items) {
                        if (o instanceof ImageWrapper image) {
                            producedImages.put(variableName + "_" + idx++, image);
                        } else if (o instanceof SingleFileOutput singleFile) {
                            producedFiles.put(variableName + "_" + idx++, singleFile);
                        }
                    }
                } else {
                    producedValues.put(variableName, result);
                }
            }
            case ExpressionEvaluator.NestedInvocationResult nestedResult -> {
                nestedResult.variables().forEach((key, value) -> {
                    extractResults(producedImages, producedFiles, producedValues, key, value);
                });
            }
            case null -> {
            }
            default -> {
                producedValues.put(variableName, result);
                if (outputLogging) {
                    LOGGER.info(variableName + " = " + result);
                }
            }
        }
    }

    private ImageMathScriptResult doExecuteScript(String script,
                                                  int index,
                                                  MemoizingExpressionEvaluator evaluator,
                                                  SectionKind kind,
                                                  ProgressOperation parentOperation) {
        var cpt = new AtomicInteger();
        var parser = new ImageMathParser(script);
        parser.setIncludeDir(includesDir);
        ImageMathScript root;
        try {
            root = parser.parseAndInlineIncludes();
        } catch (ParseException ex) {
            throw new ProcessingException(ex);
        }

        extractParametersAsVariables(root, evaluator);

        var sections = root.findSections(kind);
        if (sections.isEmpty()) {
            return ImageMathScriptResult.EMPTY;
        }
        var functions = readUserFunctions(root);
        functions.forEach(function -> {
            var prep = function.prepare(imagesByShift, context, evaluator::addShift, broadcaster, includesDir);
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
                    if (kind == SectionKind.SINGLE && sections.size() == 1) {
                        return Optional.of(sections.getFirst());
                    }
                    return Optional.empty();
                })
                .orElseThrow(() -> new ProcessingException(new SyntaxError("No [outputs] section found")));
        Map<String, ImageWrapper> imagesByLabel = new LinkedHashMap<>();
        Map<String, FileOutputResult> filesByLabel = new LinkedHashMap<>();
        Map<String, Object> valuesByLabel = new LinkedHashMap<>();
        List<InvalidExpression> invalidExpressions = new ArrayList<>();
        var resultsLock = new ReentrantLock();
        var internalShifts = new TreeSet<Double>();
        var assignmentsBySectionName = new LinkedHashMap<String, List<Assignment>>();
        var outputAssignments = new HashSet<Assignment>();
        var nonAssignmentsBySectionIndex = new LinkedHashMap<Integer, List<Expression>>();

        for (int i = 0; i < sections.size(); i++) {
            var section = sections.get(i);
            var sectionName = section.name().orElse("");
            var expressions = section.childrenOfType(Expression.class);

            var assignments = expressions.stream()
                    .filter(expr -> expr instanceof Assignment)
                    .map(expr -> (Assignment) expr)
                    .toList();
            assignmentsBySectionName.computeIfAbsent(sectionName, k -> new ArrayList<>()).addAll(assignments);

            if (section == outputSection) {
                outputAssignments.addAll(assignments);
            }

            var nonAssignments = expressions.stream()
                    .filter(expr -> !(expr instanceof Assignment))
                    .toList();
            nonAssignmentsBySectionIndex.put(i, nonAssignments);
        }

        for (int i = 0; i < sections.size(); i++) {
            var section = sections.get(i);
            var isOutputSection = section == outputSection;
            if (isOutputSection) {
                internalShifts.addAll(evaluator.getShifts());
                evaluator.clearShifts();
            }

            var nonAssignments = nonAssignmentsBySectionIndex.get(i);
            for (var expr : nonAssignments) {
                try {
                    evaluator.evaluate(expr);
                } catch (Exception ex) {
                    var invalidExpression = new InvalidExpression(
                            expr.toString(),
                            expr.toString(),
                            ex
                    );
                    invalidExpressions.add(invalidExpression);
                }
            }
        }

        if (!assignmentsBySectionName.isEmpty()) {
            var parameterNames = evaluator.getVariables().keySet();
            var analyzer = new ExpressionDependencyAnalyzer(parameterNames);
            var dependencyInfos = new ArrayList<ExpressionDependencyAnalyzer.DependencyInfo>();

            for (var entry : assignmentsBySectionName.entrySet()) {
                var sectionName = entry.getKey();
                var assignments = entry.getValue();
                for (var assignment : assignments) {
                    dependencyInfos.add(analyzer.analyze(assignment, sectionName));
                }
            }

            var dag = ExpressionDAG.build(dependencyInfos);
            var executionLevels = dag.computeExecutionLevels();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Expression DAG for {} sections:\n{}", kind, dag.toDotFormat());
            }

            for (var level : executionLevels) {
                if (level.canRunInParallel()) {
                    executeExpressionsInParallel(index, evaluator, level.assignments(), outputAssignments, cpt, imagesByLabel, filesByLabel, valuesByLabel, invalidExpressions, resultsLock, parentOperation);
                } else {
                    for (var assignment : level.assignments()) {
                        var isFromOutputSection = outputAssignments.contains(assignment);
                        executeSingleExpression(index, evaluator, assignment, isFromOutputSection, cpt, imagesByLabel, filesByLabel, valuesByLabel, invalidExpressions, resultsLock, parentOperation);
                    }
                }
            }
        }

        return new ImageMathScriptResult(imagesByLabel, filesByLabel, valuesByLabel, invalidExpressions, internalShifts, evaluator.getShifts(), Collections.emptySet(), evaluator.usesAutoContinuum());
    }


    private void executeExpressions(int index, MemoizingExpressionEvaluator evaluator, List<Expression> expressions, AtomicInteger cpt, Map<String, ImageWrapper> imagesByLabel, Map<String, FileOutputResult> filesByLabel, Map<String, Object> valuesByLabel, List<InvalidExpression> invalidExpressions) {
        var assignments = expressions.stream()
                .filter(expr -> expr instanceof Assignment)
                .map(expr -> (Assignment) expr)
                .toList();

        var nonAssignments = expressions.stream()
                .filter(expr -> !(expr instanceof Assignment))
                .toList();

        for (var expr : nonAssignments) {
            try {
                evaluator.evaluate(expr);
            } catch (Exception ex) {
                var invalidExpression = new InvalidExpression(
                        expr.toString(),
                        expr.toString(),
                        ex
                );
                invalidExpressions.add(invalidExpression);
            }
        }

        if (assignments.isEmpty()) {
            return;
        }

        var resultsLock = new ReentrantLock();
        var parameterNames = evaluator.getVariables().keySet();
        var analyzer = new ExpressionDependencyAnalyzer(parameterNames);
        var dependencyInfos = analyzer.analyzeAll(assignments);
        var dag = ExpressionDAG.build(dependencyInfos);
        var executionLevels = dag.computeExecutionLevels();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Expression DAG:\n{}", dag.toDotFormat());
        }

        for (var level : executionLevels) {
            if (level.canRunInParallel()) {
                executeExpressionsInParallel(index, evaluator, level.assignments(), new HashSet<>(assignments), cpt, imagesByLabel, filesByLabel, valuesByLabel, invalidExpressions, resultsLock, null);
            } else {
                for (var assignment : level.assignments()) {
                    executeSingleExpression(index, evaluator, assignment, true, cpt, imagesByLabel, filesByLabel, valuesByLabel, invalidExpressions, resultsLock, null);
                }
            }
        }
    }

    private void executeExpressionsInParallel(int index, MemoizingExpressionEvaluator evaluator, List<Assignment> assignments, Set<Assignment> outputAssignments, AtomicInteger cpt, Map<String, ImageWrapper> imagesByLabel, Map<String, FileOutputResult> filesByLabel, Map<String, Object> valuesByLabel, List<InvalidExpression> invalidExpressions, ReentrantLock resultsLock, ProgressOperation progressOperation) {
        var forkJoinPool = ForkJoinPool.commonPool();
        try {
            forkJoinPool.submit(() -> assignments.parallelStream().forEach(assignment -> {
                var isFromOutputSection = outputAssignments != null && outputAssignments.contains(assignment);
                executeSingleExpression(index, evaluator, assignment, isFromOutputSection, cpt, imagesByLabel, filesByLabel, valuesByLabel, invalidExpressions, resultsLock, progressOperation);
            })).get();
        } catch (InterruptedException | ExecutionException ex) {
            LOGGER.warn("Parallel execution interrupted: " + ex.getMessage());
        }
    }

    private void executeSingleExpression(int index, MemoizingExpressionEvaluator evaluator, Assignment assignment, boolean isOutputSection, AtomicInteger cpt, Map<String, ImageWrapper> imagesByLabel, Map<String, FileOutputResult> filesByLabel, Map<String, Object> valuesByLabel, List<InvalidExpression> invalidExpressions, ReentrantLock resultsLock, ProgressOperation progressOperation) {
        ProgressOperation exprOperation = null;
        if (progressOperation != null) {
            exprOperation = progressOperation.createChild("Evaluating " + truncateForProgress(assignment.toString()));
            broadcaster.broadcast(exprOperation);
        }
        try {
            var result = evaluator.evaluate(assignment);
            if (assignment.isAnonymous() && isOutputSection) {
                var dynamicVarName = "imagemath_" + index + "_" + cpt.getAndIncrement();
                assignment.setVariable(dynamicVarName);
            }
            if (isOutputSection && assignment.variableName().isPresent()) {
                var variableName = assignment.variableName().get();
                resultsLock.lock();
                try {
                    extractResults(imagesByLabel, filesByLabel, valuesByLabel, variableName, result);
                } finally {
                    resultsLock.unlock();
                }
            }
        } catch (Exception ex) {
            var invalidExpression = new InvalidExpression(
                    assignment.toString(),
                    assignment.toString(),
                    ex
            );
            resultsLock.lock();
            try {
                invalidExpressions.add(invalidExpression);
            } finally {
                resultsLock.unlock();
            }
        } finally {
            if (exprOperation != null) {
                broadcaster.broadcast(exprOperation.complete());
            }
        }
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
                        userFunctions,
                        null // includesDir is set later via prepare()
                )).forEach(userFunctions::add);
        return Collections.unmodifiableList(userFunctions);
    }

    protected void populateContext(AbstractImageExpressionEvaluator evaluator, ProgressOperation executionOperation) {
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
        evaluator.putInContext(ProgressOperation.class, executionOperation);
        if (context.containsKey(AnimationFormat.class)) {
            evaluator.putInContext(AnimationFormat.class, context.get(AnimationFormat.class));
        }
    }

    public <T> Optional<T> getVariable(String result) {
        return Optional.ofNullable((T) variables.get(result));
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
            // Skip caching for functions with side effects (e.g., python scripts)
            boolean hasSideEffect = hasSideEffects(expression);
            // Not using `computeIfAbsent` to avoid recursive update
            var cacheKey = expression.getAllTokens(false).stream().map(Objects::toString).collect(Collectors.joining());
            if (!hasSideEffect && memoizeCache.containsKey(cacheKey)) {
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
            if (!hasSideEffect && result != null) {
                memoizeCache.put(cacheKey, result);
            }
            return result;
        }

        public void clearCache() {
            super.clearCache();
            memoizeCache.clear();
        }

        private boolean hasSideEffects(Node expression) {
            if (expression instanceof FunctionCall funCall) {
                var builtin = funCall.getBuiltinFunction();
                if (builtin.isPresent()) {
                    return builtin.get().hasSideEffect();
                }
                var userFun = getUserFunctions().get(funCall.getFunctionName());
                if (userFun != null) {
                    return userFunctionHasSideEffects(userFun, new HashSet<>());
                }
            }
            return false;
        }

        private boolean userFunctionHasSideEffects(UserFunction fn, Set<String> visited) {
            if (!visited.add(fn.name())) {
                return false;
            }
            for (var expr : fn.body()) {
                for (var call : expr.descendantsOfType(FunctionCall.class)) {
                    var builtin = call.getBuiltinFunction();
                    if (builtin.isPresent() && builtin.get().hasSideEffect()) {
                        return true;
                    }
                    if (builtin.isEmpty()) {
                        var nestedFn = getUserFunctions().get(call.getFunctionName());
                        if (nestedFn != null && userFunctionHasSideEffects(nestedFn, visited)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    private void extractParametersAsVariables(ImageMathScript script, MemoizingExpressionEvaluator evaluator) {
        try {
            var extractor = new ImageMathParameterExtractor();
            var extractionResult = extractor.extractParametersFromAST(script, "runtime");

            for (var parameter : extractionResult.getParameters()) {
                var paramName = parameter.getName();
                if (!evaluator.getVariables().containsKey(paramName)) {
                    var defaultValue = parameter.getDefaultValue();
                    if (defaultValue != null) {
                        evaluator.putVariable(paramName, defaultValue);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to extract script parameters: " + e.getMessage());
        }
    }

    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    private static String truncateForProgress(String text) {
        var firstLine = text.lines().findFirst().orElse(text);
        if (firstLine.length() > 80) {
            return firstLine.substring(0, 77) + "...";
        }
        if (!firstLine.equals(text)) {
            return firstLine + "...";
        }
        return firstLine;
    }

    public static class SyntaxError extends Exception {
        public SyntaxError(String message) {
            super(message);
        }
    }

}
