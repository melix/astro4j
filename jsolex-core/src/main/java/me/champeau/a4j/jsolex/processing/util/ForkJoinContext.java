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

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A simplified fork-join context. A new sub-context
 * can be created by calling the {@link #forkJoin(Function)}
 * method. A context is closed when all tasks which have
 * been submitted in the context are done.
 */
public interface ForkJoinContext {
    /**
     * Sets the uncaught exception handler
     * @param handler the handler
     */
    void setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler handler);

    void setOnTaskStart(Consumer<? super Thread> consumer);

    void setOnTaskFinished(Consumer<? super Thread> consumer);

    /**
     * Submits a runnable for asynchronous execution
     * @param r the runnable
     */
    void async(Runnable r);

    /**
     * Runs some code into an isolated context which can have its
     * ownt task start and end handlers
     * @param context the context consumer
     */
    void isolate(Consumer<? super ForkJoinContext> context);

    /**
     * Submits a runnable for asynchronous execution and
     * blocks until the result is available
     * @param r the runnable
     */
    default void blocking(Runnable r) {
        submit(() -> {
            r.run();
            return null;
        }).get();
    }

    /**
     * Submits a callable for asynchonous execution.
     * @param callable the callable
     * @return a supplier which will block when calling {@link Supplier#get()}
     * @param <T> the type of the result
     */
    <T> Supplier<T> submit(Callable<T> callable);

    /**
     * Creates a forked context, allowing to submit tasks in that specific context.
     * This method blocks until the result of the function is available.
     * @param consumer a function which takes the new context as a parameter and
     * returns a value
     * @return a supplier on which the result can be gathered
     * @param <T> the type of the result.
     */
    <T> Supplier<T> forkJoin(Function<? super ForkJoinContext, T> consumer);

    /**
     * Executes a callable asynchronously, then calls the consumer on the result
     * of the callable when it's available. Returns a supplier for that computation.
     * @param callable the callable
     * @param consumer the consumer of the result of the callable
     * @return a non-blocking supplier for the result of the computation
     * @param <T> the type of the result of the callable
     * @param <U> the type of the result of the computation
     */
    default <T, U> Supplier<U> submitAndThen(Callable<T> callable, Function<? super T, U> consumer) {
        return submit(() -> consumer.apply(submit(callable).get()));
    }

    /**
     * A convenience method for running blocking code in a new context,
     * without having to provide a result. The method will block until
     * the execution of the new context is completed, including tasks
     * forked in that sub-context
     * @param context the blocking context
     */
    default void blocking(Consumer<? super ForkJoinContext> context) {
        forkJoin(ctx -> {
            context.accept(ctx);
            return null;
        }).get();
    }

    void cancel();
}
