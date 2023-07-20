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
package me.champeau.a4j.jsolex.processing.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class LoggingSupport {
    public static final Logger LOGGER = LoggerFactory.getLogger(LoggingSupport.class);

    public static String logError(Throwable ex) {
        if (isProcessingCancelled(ex)) {
            var message = message("processing.cancelled");
            LOGGER.error(message);
            return message;
        }
        var out = new ByteArrayOutputStream();
        var s = new PrintWriter(out);
        ex.printStackTrace(s);
        s.flush();
        String trace = out.toString();
        LOGGER.error("Error while processing\n{}", trace);
        return trace;
    }

    private static boolean isProcessingCancelled(Throwable ex) {
        if (ex instanceof CancellationException
            || ex instanceof RejectedExecutionException
            || ex instanceof InterruptedException
            || ex instanceof ClosedByInterruptException) {
            return true;
        }
        if (ex instanceof ProcessingException pe) {
            return isProcessingCancelled(pe.getCause());
        }
        return false;
    }
}
