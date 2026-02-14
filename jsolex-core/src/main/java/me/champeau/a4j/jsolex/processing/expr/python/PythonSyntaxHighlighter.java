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
package me.champeau.a4j.jsolex.processing.expr.python;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Provides syntax highlighting for Python code using Python's tokenize module.
 * This class uses GraalPy to tokenize Python code without executing it,
 * allowing highlighting of incomplete or invalid Python code.
 */
public class PythonSyntaxHighlighter {
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonSyntaxHighlighter.class);

    private static final AtomicReference<Context> HIGHLIGHTER_CONTEXT = new AtomicReference<>();
    private static final AtomicBoolean CONTEXT_INITIALIZING = new AtomicBoolean(false);
    private static final ReentrantLock CONTEXT_LOCK = new ReentrantLock();
    private static final List<Consumer<Void>> PENDING_CALLBACKS = new CopyOnWriteArrayList<>();

    private static final ExecutorService PYTHON_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "python-highlighter");
        thread.setDaemon(true);
        return thread;
    });

    private Consumer<Void> onContextReady;

    private static final Set<String> PYTHON_KEYWORDS = Set.of(
            "False", "None", "True", "and", "as", "assert", "async", "await",
            "break", "class", "continue", "def", "del", "elif", "else", "except",
            "finally", "for", "from", "global", "if", "import", "in", "is",
            "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try",
            "while", "with", "yield"
    );

    private static final Set<String> PYTHON_BUILTINS = Set.of(
            "abs", "all", "any", "bin", "bool", "bytes", "callable", "chr",
            "dict", "dir", "enumerate", "filter", "float", "format", "getattr",
            "hasattr", "hash", "hex", "id", "input", "int", "isinstance", "len",
            "list", "map", "max", "min", "open", "ord", "pow", "print", "range",
            "repr", "reversed", "round", "set", "setattr", "sorted", "str", "sum",
            "tuple", "type", "zip"
    );

    /**
     * Represents a syntax highlighting span.
     *
     * @param start the start position (inclusive)
     * @param end the end position (exclusive)
     * @param styleClass the CSS style class to apply
     */
    public record HighlightSpan(int start, int end, String styleClass) {}

    /**
     * Sets a callback to be invoked when the Python context becomes ready.
     * This is used to trigger re-highlighting after async initialization.
     *
     * @param callback the callback to invoke when ready
     */
    public void setOnContextReady(Consumer<Void> callback) {
        this.onContextReady = callback;
    }

    /**
     * Returns true if the Python context is ready for highlighting.
     *
     * @return true if the context is initialized
     */
    public boolean isReady() {
        return HIGHLIGHTER_CONTEXT.get() != null;
    }

    /**
     * Starts initializing the Python context in the background.
     * Call this when Python mode is selected to warm up the context.
     */
    public void warmUp() {
        var callback = onContextReady;

        // If context is already ready, invoke callback immediately
        if (HIGHLIGHTER_CONTEXT.get() != null) {
            if (callback != null) {
                callback.accept(null);
            }
            return;
        }

        // Register callback to be invoked when initialization completes
        if (callback != null) {
            PENDING_CALLBACKS.add(callback);
        }

        // If already initializing, callback will be invoked when done
        if (!CONTEXT_INITIALIZING.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                initializeContext();
                for (var pendingCallback : PENDING_CALLBACKS) {
                    try {
                        pendingCallback.accept(null);
                    } catch (Exception e) {
                        LOGGER.debug("Error invoking context ready callback", e);
                    }
                }
                PENDING_CALLBACKS.clear();
            } finally {
                CONTEXT_INITIALIZING.set(false);
            }
        }, PYTHON_EXECUTOR);
    }

    /**
     * Computes syntax highlighting spans for Python code.
     * Uses Python's tokenize module to extract tokens without executing code.
     * Returns empty list if the context is not yet initialized.
     *
     * @param code the Python code to highlight
     * @return a list of spans sorted by position
     */
    public List<HighlightSpan> computeHighlighting(String code) {
        if (code == null || code.isEmpty()) {
            return Collections.emptyList();
        }

        // If context is not ready, start warmup and return empty
        if (HIGHLIGHTER_CONTEXT.get() == null) {
            warmUp();
            return Collections.emptyList();
        }

        try {
            return doComputeHighlighting(code);
        } catch (Exception e) {
            LOGGER.debug("Error computing Python highlighting: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void initializeContext() {
        if (HIGHLIGHTER_CONTEXT.get() != null) {
            return;
        }
        CONTEXT_LOCK.lock();
        try {
            if (HIGHLIGHTER_CONTEXT.get() != null) {
                return;
            }
            LOGGER.debug("Initializing Python syntax highlighting context...");
            var context = Context.newBuilder("python")
                    .allowExperimentalOptions(false)
                    .allowAllAccess(false)
                    .allowHostAccess(HostAccess.ALL)
                    .allowPolyglotAccess(PolyglotAccess.ALL)
                    .allowIO(IOAccess.ALL)
                    .option("python.PosixModuleBackend", "java")
                    .option("python.DontWriteBytecodeFlag", "true")
                    .option("python.ForceImportSite", "false")
                    .option("python.CheckHashPycsMode", "never")
                    .option("python.WarnExperimentalFeatures", "false")
                    .build();
            HIGHLIGHTER_CONTEXT.set(context);
            LOGGER.debug("Python syntax highlighting context initialized");
        } finally {
            CONTEXT_LOCK.unlock();
        }
    }

    private List<HighlightSpan> doComputeHighlighting(String code) {
        CONTEXT_LOCK.lock();
        try {
            var context = HIGHLIGHTER_CONTEXT.get();
            if (context == null) {
                return Collections.emptyList();
            }
            var bindings = context.getBindings("python");
            bindings.putMember("_highlight_code", code);

            var result = context.eval("python", """
                import tokenize
                import io
                import token

                _tokens = []
                try:
                    _reader = io.StringIO(_highlight_code)
                    for tok in tokenize.generate_tokens(_reader.readline):
                        _tok_type = tok.type
                        _tok_string = tok.string
                        _start_row, _start_col = tok.start
                        _end_row, _end_col = tok.end
                        _tokens.append((_tok_type, _tok_string, _start_row, _start_col, _end_row, _end_col))
                except tokenize.TokenError:
                    pass
                except Exception:
                    pass
                _tokens
                """);

            return convertTokensToSpans(result, code);
        } catch (PolyglotException e) {
            LOGGER.debug("Python tokenization error: {}", e.getMessage());
            return Collections.emptyList();
        } finally {
            CONTEXT_LOCK.unlock();
        }
    }

    private record TokenInfo(int type, String string, int startRow, int startCol, int endRow, int endCol) {}

    private List<HighlightSpan> convertTokensToSpans(Value tokensValue, String code) {
        var spans = new ArrayList<HighlightSpan>();
        var lines = code.split("\n", -1);
        var lineOffsets = computeLineOffsets(lines);

        if (!tokensValue.hasArrayElements()) {
            return spans;
        }

        // First pass: collect all tokens
        var tokens = new ArrayList<TokenInfo>();
        for (long i = 0; i < tokensValue.getArraySize(); i++) {
            var token = tokensValue.getArrayElement(i);
            if (!token.hasArrayElements() || token.getArraySize() < 6) {
                continue;
            }
            tokens.add(new TokenInfo(
                    token.getArrayElement(0).asInt(),
                    token.getArrayElement(1).asString(),
                    token.getArrayElement(2).asInt(),
                    token.getArrayElement(3).asInt(),
                    token.getArrayElement(4).asInt(),
                    token.getArrayElement(5).asInt()
            ));
        }

        // Second pass: generate spans with lookahead for function calls
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            boolean isFollowedByParen = false;

            // Check if this NAME token is followed by '(' (function call)
            if (token.type == 1 && i + 1 < tokens.size()) {
                var nextToken = tokens.get(i + 1);
                // Just check the string - token type for OP varies between Python versions
                if ("(".equals(nextToken.string)) {
                    isFollowedByParen = true;
                }
            }

            var styleClass = mapTokenToStyle(token.type, token.string, isFollowedByParen);
            if (styleClass == null) {
                continue;
            }

            int start = getAbsolutePosition(lineOffsets, token.startRow, token.startCol);
            int end = getAbsolutePosition(lineOffsets, token.endRow, token.endCol);

            if (start >= 0 && end > start && end <= code.length()) {
                spans.add(new HighlightSpan(start, end, styleClass));
            }
        }

        spans.sort(Comparator.comparingInt(HighlightSpan::start));
        return spans;
    }

    private int[] computeLineOffsets(String[] lines) {
        var offsets = new int[lines.length + 1];
        offsets[0] = 0;
        for (int i = 0; i < lines.length; i++) {
            offsets[i + 1] = offsets[i] + lines[i].length() + 1;
        }
        return offsets;
    }

    private int getAbsolutePosition(int[] lineOffsets, int row, int col) {
        if (row < 1 || row > lineOffsets.length) {
            return -1;
        }
        return lineOffsets[row - 1] + col;
    }

    private String mapTokenToStyle(int tokenType, String tokenString, boolean isFollowedByParen) {
        return switch (tokenType) {
            case 64, 60 -> "comment"; // COMMENT (64 in Python 3.12+, 60 in older)
            case 3 -> "token_literal"; // STRING
            case 2 -> "token_literal"; // NUMBER
            case 1 -> { // NAME
                if (PYTHON_KEYWORDS.contains(tokenString)) {
                    yield "keyword";
                } else if (PYTHON_BUILTINS.contains(tokenString) || isFollowedByParen) {
                    yield "token_function";
                } else {
                    yield "token_variable";
                }
            }
            default -> null;
        };
    }
}
