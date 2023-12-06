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
package me.champeau.a4j.jsolex.app;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.layout.EchoLayout;
import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class LogbackConfigurer {

    private static final Map<String, Integer> THREAD_NAME_TO_PID = new ConcurrentHashMap<>();
    public static final String LOGGER_PATTERN = "%d{HH:mm:ss.SSS} [%level] %msg%n";

    private LogbackConfigurer() {

    }

    public static void recordThreadOwner(String name, int id) {
        THREAD_NAME_TO_PID.put(name, id);
    }

    public static void clearOwners() {
        THREAD_NAME_TO_PID.clear();
    }

    static void configureLogger(StyleClassedTextArea console) {
        Logger logbackLogger = findRootLogger();
        logbackLogger.setLevel(Level.INFO);
        logbackLogger.detachAndStopAllAppenders();
        AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent eventObject) {
                BatchOperations.submit(() -> {
                    var level = eventObject.getLevel();
                    var message = eventObject.getFormattedMessage() + System.lineSeparator();
                    console.append(message, "log_" + level.levelStr.toLowerCase());
                    console.requestFollowCaret();
                });
            }
        };
        logbackLogger.addAppender(appender);
        appender.start();
    }

    private static Logger findRootLogger() {
        return (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    }

    static Appender<ILoggingEvent> createContextualFileAppender(int id, File logFile) {
        var contextBasedAppender = new ContextualFileAppender(id);
        contextBasedAppender.setFile(logFile.getAbsolutePath());
        var logbackLogger = findRootLogger();
        logbackLogger.addAppender(contextBasedAppender);
        try {
            var loggerContext = logbackLogger.getLoggerContext();
            contextBasedAppender.setContext(loggerContext);
            contextBasedAppender.setLayout(new EchoLayout<>());
            var encoder = new PatternLayoutEncoder();
            encoder.setPattern(LOGGER_PATTERN);
            encoder.setContext(loggerContext);
            encoder.start();
            contextBasedAppender.setEncoder(encoder);
            contextBasedAppender.start();
        } catch (Exception ex) {
            System.out.println("ex = " + ex);
        }
        return contextBasedAppender;
    }

    private static class ContextualFileAppender extends FileAppender<ILoggingEvent> {
        private final int id;

        public ContextualFileAppender(int id) {
            this.id = id;
        }

        @Override
        public void doAppend(ILoggingEvent eventObject) {
            var tid = THREAD_NAME_TO_PID.get(eventObject.getThreadName());
            if (tid != null && id == tid) {
                super.doAppend(eventObject);
            }
        }

    }
}
