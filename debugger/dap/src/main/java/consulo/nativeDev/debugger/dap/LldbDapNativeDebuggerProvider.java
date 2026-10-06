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
import consulo.platform.Platform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@ExtensionImpl
public class LldbDapNativeDebuggerProvider extends NativeDapDebuggerProvider {
    public static final String ID = "lldb-dap";

    private static final int NEWEST_LLVM = 25;
    private static final int OLDEST_LLVM = 14;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.of("LLDB (lldb-dap)");
    }

    @Override
    public int getPriority() {
        return Platform.current().os().isMac() ? 100 : 50;
    }

    @Override
    protected List<String> getExecutableNames() {
        List<String> names = new ArrayList<>();
        names.add("lldb-dap");
        for (int version = NEWEST_LLVM; version >= OLDEST_LLVM; version--) {
            names.add("lldb-dap-" + version);
        }
        names.add("lldb-vscode");
        for (int version = NEWEST_LLVM; version >= OLDEST_LLVM; version--) {
            names.add("lldb-vscode-" + version);
        }
        return names;
    }

    @Override
    public List<String> getCommandLine(Path executable) {
        return List.of(executable.toString());
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
        Map<String, String> environment = new LinkedHashMap<>(Platform.current().os().environmentVariables());
        environment.putAll(target.environment());
        List<String> env = new ArrayList<>(environment.size());
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            env.add(entry.getKey() + "=" + entry.getValue());
        }
        arguments.put("env", env);
        arguments.put("stopOnEntry", false);
        return arguments;
    }
}
