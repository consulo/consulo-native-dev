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

import consulo.container.plugin.PluginManager;
import consulo.execution.debug.XDebugProcess;
import consulo.execution.debug.XDebugSession;
import consulo.nativeDev.debugger.NativeDebuggerInstallation;
import consulo.nativeDev.debugger.NativeDebuggerProvider;
import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.platform.Platform;
import consulo.process.PathEnvironmentVariableUtil;

import java.io.File;
import java.nio.file.Files;
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

    public abstract List<String> getCommandLine(Path executable, NativeDebugTarget target);

    public abstract Map<String, Object> createLaunchArguments(NativeDebugTarget target);

    @Override
    public List<NativeDebuggerInstallation> findInstallations() {
        return findInPath();
    }

    protected List<NativeDebuggerInstallation> findInPath() {
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

    protected List<NativeDebuggerInstallation> findInstallations(String pathProperty, String bundledName) {
        Platform platform = Platform.current();
        List<NativeDebuggerInstallation> installations = new ArrayList<>();

        String overridePath = platform.jvm().getRuntimeProperty(pathProperty);
        if (overridePath != null && !overridePath.isEmpty()) {
            addExecutable(installations, Path.of(overridePath));
        }

        File pluginPath = PluginManager.getPluginPath(getClass());
        List<String> executableNames = getExecutableNames();
        if (pluginPath != null && !executableNames.isEmpty()) {
            String directory = platform.os().fileNamePrefix() + "-" + bundledName + platform.jvm().arch().fileNameSuffix();
            String executableName = platform.os().isWindows() ? executableNames.get(0) + ".exe" : executableNames.get(0);
            addExecutable(installations, pluginPath.toPath().resolve(directory).resolve(executableName));
        }

        installations.addAll(findInPath());
        return installations;
    }

    private void addExecutable(List<NativeDebuggerInstallation> installations, Path executable) {
        if (!Files.isRegularFile(executable)) {
            return;
        }
        if (!Files.isExecutable(executable)) {
            //noinspection ResultOfMethodCallIgnored
            executable.toFile().setExecutable(true, false);
        }
        installations.add(new NativeDebuggerInstallation(getId(), executable));
    }

    @Override
    public XDebugProcess createProcess(XDebugSession session, NativeDebugTarget target, NativeDebuggerInstallation installation) {
        return new NativeDapDebugProcess(session, target, installation, this);
    }
}
