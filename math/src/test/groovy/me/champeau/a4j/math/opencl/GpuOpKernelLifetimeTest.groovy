/*
 * Copyright 2026-2026 the original author or authors.
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
package me.champeau.a4j.math.opencl

import spock.lang.Requires
import spock.lang.Specification

import java.util.function.Consumer

import static org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE

/**
 * Regression tests for kernel-handle lifetime in {@link GpuOp}.
 *
 * Background: OpenCL stores kernel arguments on the kernel object itself and
 * reads them when the kernel actually executes on the device, not when it is
 * enqueued. Returning a handle to the manager pool right after
 * {@code clEnqueueNDRangeKernel} would let a concurrent op re-borrow it and
 * call {@code clSetKernelArg} on it before the previously-enqueued execution
 * has run — corrupting the in-flight kernel's args and causing native
 * crashes. The fix: GpuOp keeps the borrowed handle out of the pool until
 * its {@code close()} drains the queue via {@code clFinish}.
 */
@Requires({ OpenCLSupport.isAvailable() })
class GpuOpKernelLifetimeTest extends Specification {

    private static final String PROGRAM = "arithmetic"
    private static final String KERNEL = "add_scalar"

    private OpenCLContext context
    private OpenCLKernelManager manager
    private List<Long> drained

    def setup() {
        context = OpenCLSupport.getContext()
        manager = context.getKernelManager()
        // Drain the pool for this kernel so we have a deterministic baseline.
        drained = []
        while (manager.poolSize(PROGRAM, KERNEL) > 0) {
            drained << manager.borrow(PROGRAM, KERNEL)
        }
    }

    def cleanup() {
        drained.each { manager.release(PROGRAM, KERNEL, it) }
    }

    def "borrowed kernel handle stays out of the pool until the runOp closes"() {
        given:
        int poolWhileOpen = -1

        when: "we run a kernel inside a runOp body and inspect the pool from inside the body"
        context.runOp({ op ->
            long buf = op.allocateBuffer(4 * Float.BYTES, CL_MEM_READ_WRITE)
            op.kernel(PROGRAM, KERNEL)
                    .arg(buf).arg(0f).arg(buf).arg(4)
                    .run(4)
            poolWhileOpen = manager.poolSize(PROGRAM, KERNEL)
        } as Consumer)

        then: "the handle is held by the op, not yet returned to the pool"
        poolWhileOpen == 0

        and: "after the runOp closes, the handle is returned to the pool"
        manager.poolSize(PROGRAM, KERNEL) == 1
    }

    def "multiple kernel calls in one runOp consume distinct handles"() {
        given:
        int poolAfterFirst = -1
        int poolAfterSecond = -1

        when: "two kernel calls in the same runOp body, with no clFinish between them"
        context.runOp({ op ->
            long buf = op.allocateBuffer(4 * Float.BYTES, CL_MEM_READ_WRITE)
            op.kernel(PROGRAM, KERNEL)
                    .arg(buf).arg(0f).arg(buf).arg(4)
                    .run(4)
            poolAfterFirst = manager.poolSize(PROGRAM, KERNEL)

            op.kernel(PROGRAM, KERNEL)
                    .arg(buf).arg(1f).arg(buf).arg(4)
                    .run(4)
            poolAfterSecond = manager.poolSize(PROGRAM, KERNEL)
        } as Consumer)

        then: "neither handle was recycled while the op was still open"
        poolAfterFirst == 0
        poolAfterSecond == 0

        and: "both handles are released back to the pool after close"
        manager.poolSize(PROGRAM, KERNEL) == 2
    }

    def "a concurrent op cannot borrow the in-flight handle"() {
        given:
        long innerBorrow = 0L

        when: "while one op is still open, another caller borrows the same kernel from the manager"
        context.runOp({ op ->
            long buf = op.allocateBuffer(4 * Float.BYTES, CL_MEM_READ_WRITE)
            op.kernel(PROGRAM, KERNEL)
                    .arg(buf).arg(0f).arg(buf).arg(4)
                    .run(4)
            // Simulate a concurrent op grabbing the same (program, kernel)
            // from the pool. With the fix, the pool is empty, so the manager
            // creates a fresh handle distinct from the one the op holds.
            innerBorrow = manager.borrow(PROGRAM, KERNEL)
            manager.release(PROGRAM, KERNEL, innerBorrow)
        } as Consumer)

        then: "the second borrow had to allocate a new handle (pool was empty)"
        innerBorrow != 0L

        and: "after close, both handles are pooled"
        manager.poolSize(PROGRAM, KERNEL) == 2
    }
}
