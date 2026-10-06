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
import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.nativeDev.debugger.driver.NativeDebuggerKind;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@ExtensionImpl
public class GdbDapNativeDebuggerProvider extends NativeDapDebuggerProvider {
    public static final String ID = "gdb-dap";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.of("GDB (DAP)");
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    protected List<String> getExecutableNames() {
        return List.of("gdb");
    }

    @Override
    public List<String> getCommandLine(Path executable, NativeDebugTarget target) {
        List<String> commandLine = new ArrayList<>(List.of(executable.toString(), "-q", "-i=dap"));
        for (String command : target.setup().getInitCommands(NativeDebuggerKind.GDB)) {
            commandLine.add("-ex");
            commandLine.add(command);
        }
        return commandLine;
    }

    @Override
    public Map<String, Object> createLaunchArguments(NativeDebugTarget target) {
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
        if (!target.environment().isEmpty()) {
            arguments.put("env", target.environment());
        }
        return arguments;
    }
}
