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
package consulo.nativeDev.debugger;

import consulo.platform.Platform;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.execution.debug.XDebugProcess;
import consulo.execution.debug.XDebugSession;
import consulo.localize.LocalizeValue;
import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.nativeDev.debugger.driver.NativeDebuggerSetup;
import consulo.process.ExecutionException;

import java.util.List;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface NativeDebuggerProvider {
    /**
     * Family of debuggers which take gdb commands - gdb itself, also over DAP.
     */
    String GDB_FAMILY = "gdb";

    /**
     * Family of debuggers which take lldb commands - lldb-dap, CodeLLDB.
     */
    String LLDB_FAMILY = "lldb";

    String getId();

    /**
     * Which debuggers this one speaks the commands of - so a {@link NativeDebuggerSetup} gives one set of init commands
     * to all of them. A debugger of its own kind is a family of its own.
     */
    default String getFamilyId() {
        return getId();
    }

    LocalizeValue getDisplayName();

    /**
     * The priority on {@code platform} - the machine the debugger runs on, the local one or a remote one alike.
     */
    default int getPriority(Platform platform) {
        return 0;
    }

    /**
     * The installations on {@code platform} - the machine the project lives on, where the debugger has to run. Looked up
     * through the platform - its PATH, its home, its file system - the same way for the local machine and a remote one.
     */
    List<NativeDebuggerInstallation> findInstallations(Platform platform);

    XDebugProcess createProcess(XDebugSession session, NativeDebugTarget target, NativeDebuggerInstallation installation)
        throws ExecutionException;
}
