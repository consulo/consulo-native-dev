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
package consulo.nativeDev.debugger.gdb;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.concurrent.ApplicationConcurrency;
import consulo.execution.debug.XDebugProcess;
import consulo.execution.debug.XDebugSession;
import consulo.localize.LocalizeValue;
import consulo.nativeDev.debugger.NativeDebuggerInstallation;
import consulo.nativeDev.debugger.NativeDebuggerProvider;
import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.nativeDev.debugger.process.NativeDriverDebugProcess;
import consulo.platform.Platform;
import consulo.process.PathEnvironmentVariableUtil;
import consulo.process.ProcessHandlerBuilderFactory;
import jakarta.inject.Inject;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@ExtensionImpl
public class GdbNativeDebuggerProvider implements NativeDebuggerProvider {
    public static final String ID = "gdb";

    private final ApplicationConcurrency myConcurrency;
    private final ProcessHandlerBuilderFactory myProcessHandlerBuilderFactory;

    @Inject
    public GdbNativeDebuggerProvider(ApplicationConcurrency concurrency, ProcessHandlerBuilderFactory processHandlerBuilderFactory) {
        myConcurrency = concurrency;
        myProcessHandlerBuilderFactory = processHandlerBuilderFactory;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.of("GDB");
    }

    @Override
    public int getPriority() {
        return Platform.current().os().isMac() ? 10 : 100;
    }

    @Override
    public List<NativeDebuggerInstallation> findInstallations() {
        File gdb = PathEnvironmentVariableUtil.findInPath(Platform.current().os().isWindows() ? "gdb.exe" : "gdb");
        return gdb == null ? List.of() : List.of(new NativeDebuggerInstallation(ID, gdb.toPath()));
    }

    @Override
    public XDebugProcess createProcess(XDebugSession session, NativeDebugTarget target, NativeDebuggerInstallation installation) {
        ExecutorService executor = myConcurrency.createBoundedApplicationPoolExecutor("GDB", myConcurrency.getExecutorService(), 1);
        return new NativeDriverDebugProcess(session,
            target,
            executor,
            listener -> new GdbMiDriver(myProcessHandlerBuilderFactory, installation.executable(), List.of(), listener, executor));
    }
}
