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
import consulo.execution.debug.frame.XStackFrame;
import consulo.nativeDev.debugger.driver.NativeFrame;
import consulo.nativeDev.debugger.driver.NativeThread;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
class NativeExecutionStack extends XExecutionStack {
    private static final int PAGE = 100;

    private final NativeDriverDebugProcess myProcess;
    private final NativeThread myThread;
    private final @Nullable NativeStackFrame myTopFrame;

    NativeExecutionStack(NativeDriverDebugProcess process, NativeThread thread) {
        super(displayName(thread));
        myProcess = process;
        myThread = thread;
        NativeFrame frame = thread.frame();
        myTopFrame = frame == null ? null : new NativeStackFrame(process, frame);
    }

    String getThreadId() {
        return myThread.id();
    }

    @Override
    public @Nullable XStackFrame getTopFrame() {
        return myTopFrame;
    }

    @Override
    public void computeStackFrames(XStackFrameContainer container) {
        loadFrames(container, 0);
    }

    private void loadFrames(XStackFrameContainer container, int from) {
        myProcess.getDriver().getFrames(myThread.id(), from, PAGE).whenCompleteAsync((frames, error) -> {
            if (container.isObsolete()) {
                return;
            }
            if (error != null) {
                container.errorOccurred(NativeDriverDebugProcess.message(error));
                return;
            }

            List<NativeStackFrame> stackFrames = new ArrayList<>(frames.size());
            for (NativeFrame frame : frames) {
                NativeStackFrame topFrame = myTopFrame;
                boolean top = topFrame != null && frame.level() == 0 && frame.address() == topFrame.getFrame().address();
                stackFrames.add(top ? topFrame : new NativeStackFrame(myProcess, frame));
            }
            boolean last = frames.size() < PAGE;
            container.addStackFrames(stackFrames, last);
            if (!last) {
                loadFrames(container, from + PAGE);
            }
        }, myProcess.getExecutor());
    }

    private static String displayName(NativeThread thread) {
        String name = thread.name();
        String targetId = thread.targetId();
        if (name != null && targetId != null) {
            return name + " (" + targetId + ")";
        }
        if (targetId != null) {
            return targetId;
        }
        return name != null ? name : "Thread " + thread.id();
    }
}
