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

import consulo.process.BaseProcessHandler;
import consulo.process.ProcessOutputTypes;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
class NativeDebuggerConsoleHandler extends BaseProcessHandler {
    private final NativeDriverDebugProcess myProcess;
    private final AtomicBoolean myFinished = new AtomicBoolean();
    private final OutputStream myInput = new OutputStream() {
        private final ByteArrayOutputStream myLine = new ByteArrayOutputStream();

        @Override
        public synchronized void write(int b) {
            if (b == '\n') {
                String command = myLine.toString(StandardCharsets.UTF_8).trim();
                myLine.reset();
                if (!command.isEmpty()) {
                    execute(command);
                }
            }
            else if (b != '\r') {
                myLine.write(b);
            }
        }
    };

    NativeDebuggerConsoleHandler(NativeDriverDebugProcess process) {
        myProcess = process;
    }

    @Override
    protected void destroyProcessImpl() {
        notifyExited();
    }

    @Override
    protected void detachProcessImpl() {
        notifyExited();
    }

    @Override
    public boolean detachIsDefault() {
        return false;
    }

    @Override
    public OutputStream getProcessInput() {
        return myInput;
    }

    void notifyExited() {
        if (myFinished.compareAndSet(false, true)) {
            notifyProcessTerminated(0);
        }
    }

    private void execute(String command) {
        myProcess.getDriver().executeCommand(command).whenComplete((output, error) -> {
            if (error != null) {
                notifyTextAvailable(NativeDriverDebugProcess.message(error) + "\n", ProcessOutputTypes.STDERR);
            }
        });
    }
}
