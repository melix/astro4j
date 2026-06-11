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
package me.champeau.a4j.math.opencl;

import jdk.jfr.Category;
import jdk.jfr.DataAmount;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * Per-call event for {@link OpenCLContext#runOp}. The duration spans the
 * whole operation, including the {@code clFinish} drain, so it reflects the
 * wall-clock time the caller actually waited on the GPU. Stack trace
 * disabled — the kernel names identify the operation.
 */
@Name("me.champeau.a4j.math.opencl.GpuOperation")
@Label("GPU operation")
@Category({"jsolex", "GPU"})
@StackTrace(false)
public class GpuOperationEvent extends Event {
    @Label("Kernels")
    public String kernels;

    @Label("Kernel launches")
    public int kernelLaunches;

    @Label("Bytes uploaded")
    @DataAmount
    public long bytesUploaded;

    @Label("Bytes downloaded")
    @DataAmount
    public long bytesDownloaded;

    @Label("GPU memory allocated")
    @DataAmount
    public long bytesAllocated;

    @Label("Buffers allocated")
    public int buffersAllocated;
}
