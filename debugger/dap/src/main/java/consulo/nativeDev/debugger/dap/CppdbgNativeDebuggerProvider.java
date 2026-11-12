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

import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.nativeDev.debugger.NativeDebuggerInstallation;
import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.platform.Platform;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@ExtensionImpl
public class CppdbgNativeDebuggerProvider extends NativeDapDebuggerProvider {

    public static final String ID = "cppdbg";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.of("cppdbg (Windows DbgEng)");
    }

    @Override
    public int getPriority(Platform platform) {
        return 110;
    }

    /**
     * DbgEng exists on windows only - a cppdbg of the PATH or the one bundled with the IDE.
     */
    @Override
    public List<NativeDebuggerInstallation> findInstallations(Platform platform) {
        if (!platform.os().isWindows()) {
            return List.of();
        }
        return findInstallations(platform, "cppdbg.path", "cppdbg");
    }

    @Override
    protected List<String> getExecutableNames() {
        return List.of("cppdbg");
    }

    @Override
    public List<String> getCommandLine(Path executable, NativeDebugTarget target) {
        return List.of(executable.toString());
    }

    @Override
    public Map<String, Object> createLaunchArguments(NativeDebugTarget target, Platform platform) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        Path executable = target.executable();
        if (executable != null) {
            arguments.put("program", executable.toString());
        }
        arguments.put("args", target.arguments());
        Path workingDirectory = target.workingDirectory();
        if (workingDirectory != null) {
            arguments.put("cwd", workingDirectory.toString());
        }
        arguments.put("stopOnEntry", Boolean.FALSE);
        return arguments;
    }
}
