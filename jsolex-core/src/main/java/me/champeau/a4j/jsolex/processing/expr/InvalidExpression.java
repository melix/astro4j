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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.IdentityHashMap;

public record InvalidExpression(
        String label,
        String expression,
        Exception error
) {
    /**
     * Returns a description of the error, which is never null nor empty, and includes
     * the whole cause chain. This must be preferred over {@code error().getMessage()},
     * which is null for exceptions like {@link NullPointerException} and hides the
     * nature of the failure.
     *
     * @return a description of the error
     */
    public String errorDescription() {
        if (error == null) {
            return "Unknown error";
        }
        var sb = new StringBuilder();
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var current = (Throwable) error;
        while (current != null && seen.add(current)) {
            if (!sb.isEmpty()) {
                sb.append(" caused by ");
            }
            sb.append(current.getClass().getSimpleName());
            var message = current.getMessage();
            if (message != null) {
                sb.append(": ").append(message);
            }
            current = current.getCause();
        }
        return sb.toString();
    }

    /**
     * Returns the full stack trace of the error, for reporting in contexts which cannot
     * hand the exception over to a logger.
     *
     * @return the stack trace, or an empty string if there is no error
     */
    public String stackTrace() {
        if (error == null) {
            return "";
        }
        var writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
