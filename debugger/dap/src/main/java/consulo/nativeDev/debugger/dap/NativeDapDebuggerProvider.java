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
import consulo.application.platform.PlatformTransfer;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
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

    /**
     * The launch arguments for a debuggee on {@code platform} - the machine the adapter and the debuggee run on.
     */
    public abstract Map<String, Object> createLaunchArguments(NativeDebugTarget target, Platform platform);

    /**
     * Console commands that make the next step in stop in code without line info (force step into).
     */
    public List<String> getForceStepIntoCommands() {
        return List.of();
    }

    /**
     * Console commands that undo {@link #getForceStepIntoCommands()} once the step has stopped.
     */
    public List<String> getForceStepIntoResetCommands() {
        return List.of();
    }

    /**
     * The installations in the PATH of {@code platform}.
     */
    @Override
    public List<NativeDebuggerInstallation> findInstallations(Platform platform) {
        return findInPath(platform);
    }

    /**
     * The adapter executable to start on {@code platform} - runs off the UI thread, right before the start. An adapter bundled
     * with the IDE is transferred to the platform with its directory - for the local machine that is the path itself.
     */
    public Path prepareExecutable(NativeDebuggerInstallation installation, Platform platform) throws IOException {
        Path executable = installation.executable();
        Path directory = executable.getParent();
        if (directory == null || executable.getFileSystem() == platform.fs().getPath("").getFileSystem()) {
            return executable;
        }
        return PlatformTransfer.getInstance().transfer(directory, platform).resolve(executable.getFileName().toString());
    }

    protected List<NativeDebuggerInstallation> findInPath(Platform platform) {
        List<NativeDebuggerInstallation> installations = new ArrayList<>();
        for (String name : getExecutableNames()) {
            Path executable = platform.findExecutableInPath(name);
            if (executable != null) {
                installations.add(new NativeDebuggerInstallation(getId(), executable));
            }
        }
        return installations;
    }

    /**
     * An adapter of a system property ({@code pathProperty} - a path of {@code platform}), the one bundled with the IDE for the
     * operating system and the architecture of {@code platform}, then the ones in its PATH.
     */
    protected List<NativeDebuggerInstallation> findInstallations(Platform platform, String pathProperty, String bundledName) {
        List<NativeDebuggerInstallation> installations = new ArrayList<>();

        String overridePath = Platform.current().jvm().getRuntimeProperty(pathProperty);
        if (overridePath != null && !overridePath.isEmpty()) {
            addExecutable(installations, platform.fs().getPath(overridePath));
        }

        File pluginPath = PluginManager.getPluginPath(getClass());
        List<String> executableNames = getExecutableNames();
        if (pluginPath != null && !executableNames.isEmpty()) {
            // a file of the IDE - prepareExecutable puts it on the platform
            String directory = platform.os().fileNamePrefix() + "-" + bundledName + platform.jvm().arch().fileNameSuffix();
            String executableName = platform.os().isWindows() ? executableNames.get(0) + ".exe" : executableNames.get(0);
            addExecutable(installations, pluginPath.toPath().resolve(directory).resolve(executableName));
        }

        installations.addAll(findInPath(platform));
        return installations;
    }

    private void addExecutable(List<NativeDebuggerInstallation> installations, Path executable) {
        if (!Files.isRegularFile(executable)) {
            return;
        }
        if (!Files.isExecutable(executable) && executable.getFileSystem() == FileSystems.getDefault()) {
            // an adapter of the IDE plugin - the plugin archive keeps no executable bit
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
