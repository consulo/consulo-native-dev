/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.nativeDev.debugger.driver;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public interface NativeDebuggerDriver {
    String getName();

    Set<NativeDebuggerCapability> getCapabilities();

    CompletableFuture<?> start(NativeDebugTarget target);

    CompletableFuture<NativeBreakpoint> insertBreakpoint(NativeBreakpointRequest request);

    CompletableFuture<?> removeBreakpoint(NativeBreakpoint breakpoint);

    CompletableFuture<?> run();

    CompletableFuture<?> resume();

    CompletableFuture<?> pause();

    CompletableFuture<?> step(String threadId, NativeStepKind kind);

    CompletableFuture<?> runToLocation(String file, int line);

    CompletableFuture<List<NativeThread>> getThreads();

    CompletableFuture<List<NativeFrame>> getFrames(String threadId, int from, int count);

    CompletableFuture<List<NativeVariable>> getVariables(NativeFrame frame);

    default CompletableFuture<List<NativeVariable>> getFileVariables(NativeFrame frame) {
        return CompletableFuture.completedFuture(List.of());
    }

    CompletableFuture<List<NativeVariable>> getRegisters(NativeFrame frame);

    CompletableFuture<List<NativeVariable>> getChildren(NativeVariable variable, int from, int count);

    CompletableFuture<NativeVariable> evaluate(NativeFrame frame, String expression, @Nullable String language);

    CompletableFuture<List<NativeInstruction>> disassemble(long start, long end);

    CompletableFuture<List<NativeInstruction>> disassembleFunction(long address);

    CompletableFuture<List<NativeMemoryBlock>> readMemory(long address, int length);

    CompletableFuture<String> executeCommand(String command);

    CompletableFuture<?> terminate();
}
