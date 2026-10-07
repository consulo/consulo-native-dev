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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@ExtensionImpl
public class CodeLldbNativeDebuggerProvider extends NativeDapDebuggerProvider {
    public static final String ID = "codelldb";

    private static final String EXTENSION_PREFIX = "vadimcn.vscode-lldb-";
    private static final List<String> EXTENSION_DIRECTORIES = List.of(".vscode/extensions", ".vscode-oss/extensions", ".vscode-insiders/extensions");
    private static final List<String> SYSTEM_ADAPTERS = List.of("/usr/lib/codelldb/adapter/codelldb", "/opt/codelldb/adapter/codelldb");

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getFamilyId() {
        return LLDB_FAMILY;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.of("CodeLLDB");
    }

    @Override
    public int getPriority() {
        return Platform.current().os().isMac() ? 90 : 60;
    }

    @Override
    protected List<String> getExecutableNames() {
        return List.of("codelldb");
    }

    @Override
    public List<NativeDebuggerInstallation> findInstallations() {
        boolean windows = Platform.current().os().isWindows();
        String adapterName = windows ? "codelldb.exe" : "codelldb";

        Set<Path> adapters = new LinkedHashSet<>();
        for (NativeDebuggerInstallation installation : super.findInstallations()) {
            adapters.add(realPath(installation.executable()));
        }
        if (!windows) {
            for (String adapter : SYSTEM_ADAPTERS) {
                Path path = Path.of(adapter);
                if (Files.isExecutable(path)) {
                    adapters.add(realPath(path));
                }
            }
        }

        Path home = Platform.current().user().homePath();
        for (String directory : EXTENSION_DIRECTORIES) {
            Path extensions = home.resolve(directory);
            if (!Files.isDirectory(extensions)) {
                continue;
            }
            try (Stream<Path> children = Files.list(extensions)) {
                children.filter(child -> child.getFileName().toString().startsWith(EXTENSION_PREFIX))
                    .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                    .map(child -> child.resolve("adapter").resolve(adapterName))
                    .filter(Files::isExecutable)
                    .forEach(adapter -> adapters.add(realPath(adapter)));
            }
            catch (IOException ignored) {
            }
        }

        List<NativeDebuggerInstallation> installations = new ArrayList<>(adapters.size());
        for (Path adapter : adapters) {
            installations.add(new NativeDebuggerInstallation(ID, adapter));
        }
        return installations;
    }

    @Override
    public List<String> getCommandLine(Path executable, NativeDebugTarget target) {
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
        if (!target.environment().isEmpty()) {
            arguments.put("env", target.environment());
        }
        arguments.put("stopOnEntry", false);
        List<String> sourceLanguages = target.setup().getSourceLanguages();
        if (!sourceLanguages.isEmpty()) {
            arguments.put("sourceLanguages", sourceLanguages);
        }
        List<String> initCommands = target.setup().getInitCommands(this);
        if (!initCommands.isEmpty()) {
            arguments.put("initCommands", initCommands);
        }
        return arguments;
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        }
        catch (IOException e) {
            return path;
        }
    }
}
