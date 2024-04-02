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
package me.champeau.a4j.jsolex.processing.util

import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

abstract class AbstractForkJoinParallelExecutorTest extends Specification {
    @Subject
    private ForkJoinParallelExecutor executor = ForkJoinParallelExecutor.newExecutor(maxPermits())

    abstract int maxPermits();

    void cleanup() {
        executor.close()
    }

    void "can run simple task"() {
        def fut = executor.submit((Callable<Integer>) {
            123
        })

        expect:
        fut.get() == 123
    }

    void "can fork execution"() {
        when:
        def result = new AtomicInteger()
        executor.forkJoin {
            def v1 = it.submit((Callable<Integer>) {
                Thread.sleep(300)
                100
            })
            def v2 = it.submit((Callable<Integer>) {
                Thread.sleep(200)
                23
            })
            result.set(v1.get() + v2.get())
        }

        then:
        result.get() == 123
    }

    void "can fork in fork"() {
        def check = new AtomicInteger()
        when:
        def result = new AtomicInteger()
        executor.forkJoin {
            def r1 = new AtomicInteger()
            it.forkJoin {
                Thread.sleep(10)
                it.submit {
                    Thread.sleep(100)
                    check.incrementAndGet()
                }
                r1.set(it.submit((Callable<Integer>) {
                    Thread.sleep(200)
                    check.incrementAndGet()
                    100
                }).get())
            }
            def v2 = it.submit((Callable<Integer>) {
                Thread.sleep(200)
                check.incrementAndGet()
                23
            })
            assert check.get() <= 3
            check.incrementAndGet()
            result.set(r1.get() + v2.get())
        }

        then:
        check.get() == 4
        result.get() == 123
    }

    void "can chain execution"() {
        def result = new AtomicInteger()
        when:
        executor.forkJoin {
            result.set(it.submitAndThen(() -> {
                Thread.sleep(100)
                100
            }, i -> {
                it.submit((Callable<Integer>) {
                    Thread.sleep(50)
                    i + 23
                }).get()
            }).get())
        }

        then:
        result.get() == 123
    }

    void "can add tasks during execution"() {
        def result = new AtomicInteger()
        executor.forkJoin { ctx ->
            def cpt = new AtomicInteger(10)
            Callable<Integer> callback
            callback = {
                if (cpt.decrementAndGet() == 0) {
                    return 123
                } else {
                    Thread.sleep(10)
                    return ctx.submit(callback).get()
                }
            }
            result.set(ctx.submit(callback).get())
        }

        expect:
        result.get() == 123
    }

    def "doesn't return until result is produced, even if not consumed"() {
        def b = new AtomicBoolean()
        executor.forkJoin {
            // do not call .get()
            it.submit {
                Thread.sleep(200)
                b.set(true)
                123
            }
            true
        }

        expect:
        b.get()
    }

    def "handles exceptions"() {
        def captured = []
        executor.setUncaughtExceptionHandler {t, e ->
            captured << e
        }


        when:
        executor.forkJoin {
            it.submit {
                throw new RuntimeException("exception 1")
            }.get()
        }

        then:
        captured.size() == 1
    }

    def "in a try-with-resources block executor waits for completion of tasks"() {
        def b = new AtomicBoolean()
        try (def executor = ForkJoinParallelExecutor.newExecutor()) {
            executor.forkJoin {
                // do not call .get()
                it.submit {
                    Thread.sleep(200)
                    b.set(true)
                    123
                }
                true
            }
        }

        expect:
        b.get()
    }

    def "can run an async call in a blocking one"() {
        def b = new AtomicBoolean()
        try (def executor = ForkJoinParallelExecutor.newExecutor()) {
            executor.blocking({ e ->
                // do not call .get()
                e.async {
                    Thread.sleep(200)
                    b.set(true)
                    123
                }
                true
            } as Consumer)
        }

        expect:
        b.get()
    }

}
