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

import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.nativeDev.debugger.driver.NativeDebugTargetKind;
import consulo.process.BaseProcessHandler;
import org.jspecify.annotations.Nullable;

import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
class NativeDebugProcessHandler extends BaseProcessHandler {
    private final NativeDriverDebugProcess myProcess;
    private final NativeDebugTarget myTarget;
    private final AtomicBoolean myFinished = new AtomicBoolean();

    NativeDebugProcessHandler(NativeDriverDebugProcess process, NativeDebugTarget target) {
        myProcess = process;
        myTarget = target;
    }

    @Override
    protected void destroyProcessImpl() {
        myProcess.terminateDriver().whenComplete((o, error) -> notifyExited(-1));
    }

    @Override
    protected void detachProcessImpl() {
        myProcess.terminateDriver().whenComplete((o, error) -> {
            if (myFinished.compareAndSet(false, true)) {
                notifyProcessDetached();
            }
        });
    }

    @Override
    public boolean detachIsDefault() {
        return myTarget.kind() == NativeDebugTargetKind.ATTACH;
    }

    @Override
    public @Nullable OutputStream getProcessInput() {
        return null;
    }

    void notifyExited(int exitCode) {
        if (myFinished.compareAndSet(false, true)) {
            notifyProcessTerminated(exitCode);
        }
    }
}
