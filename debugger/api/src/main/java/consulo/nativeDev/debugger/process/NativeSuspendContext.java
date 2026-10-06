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
package consulo.nativeDev.debugger.process;

import consulo.execution.debug.frame.XExecutionStack;
import consulo.execution.debug.frame.XSuspendContext;
import consulo.nativeDev.debugger.driver.NativeThread;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
class NativeSuspendContext extends XSuspendContext {
    private final NativeExecutionStack[] myStacks;
    private final @Nullable NativeExecutionStack myActiveStack;

    NativeSuspendContext(NativeDriverDebugProcess process, List<NativeThread> threads, @Nullable String activeThreadId) {
        myStacks = threads.stream().map(thread -> new NativeExecutionStack(process, thread)).toArray(NativeExecutionStack[]::new);
        NativeExecutionStack active = null;
        for (NativeExecutionStack stack : myStacks) {
            if (stack.getThreadId().equals(activeThreadId)) {
                active = stack;
                break;
            }
        }
        myActiveStack = active != null || myStacks.length == 0 ? active : myStacks[0];
    }

    @Override
    public @Nullable XExecutionStack getActiveExecutionStack() {
        return myActiveStack;
    }

    @Override
    public XExecutionStack[] getExecutionStacks() {
        return myStacks;
    }
}
