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
package consulo.nativeDev.debugger.dap;

import consulo.execution.debug.XDebugProcess;
import consulo.execution.debug.XDebugSession;
import consulo.nativeDev.debugger.NativeDebuggerInstallation;
import consulo.nativeDev.debugger.NativeDebuggerProvider;
import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.platform.Platform;
import consulo.process.PathEnvironmentVariableUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public abstract class NativeDapDebuggerProvider implements NativeDebuggerProvider {
    protected abstract List<String> getExecutableNames();

    public abstract List<String> getCommandLine(Path executable);

    public abstract Map<String, Object> createLaunchArguments(NativeDebugTarget target);

    @Override
    public List<NativeDebuggerInstallation> findInstallations() {
        boolean windows = Platform.current().os().isWindows();
        List<NativeDebuggerInstallation> installations = new ArrayList<>();
        for (String name : getExecutableNames()) {
            File executable = PathEnvironmentVariableUtil.findInPath(windows ? name + ".exe" : name);
            if (executable != null) {
                installations.add(new NativeDebuggerInstallation(getId(), executable.toPath()));
            }
        }
        return installations;
    }

    @Override
    public XDebugProcess createProcess(XDebugSession session, NativeDebugTarget target, NativeDebuggerInstallation installation) {
        return new NativeDapDebugProcess(session, target, installation, this);
    }
}
