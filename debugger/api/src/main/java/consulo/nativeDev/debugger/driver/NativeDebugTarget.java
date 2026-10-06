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
package consulo.nativeDev.debugger.driver;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public record NativeDebugTarget(NativeDebugTargetKind kind,
                                @Nullable Path executable,
                                List<String> arguments,
                                @Nullable Path workingDirectory,
                                Map<String, String> environment,
                                @Nullable String terminal,
                                long processId,
                                @Nullable Path coreFile,
                                @Nullable String remoteAddress,
                                NativeDebuggerSetup setup) {
    public static NativeDebugTarget launch(Path executable,
                                           List<String> arguments,
                                           @Nullable Path workingDirectory,
                                           Map<String, String> environment,
                                           @Nullable String terminal) {
        return new NativeDebugTarget(NativeDebugTargetKind.LAUNCH,
            executable,
            List.copyOf(arguments),
            workingDirectory,
            Map.copyOf(environment),
            terminal,
            0,
            null,
            null,
            NativeDebuggerSetup.EMPTY);
    }

    public static NativeDebugTarget attach(long processId, @Nullable Path executable) {
        return new NativeDebugTarget(NativeDebugTargetKind.ATTACH, executable, List.of(), null, Map.of(), null, processId, null, null, NativeDebuggerSetup.EMPTY);
    }

    public static NativeDebugTarget core(Path executable, Path coreFile) {
        return new NativeDebugTarget(NativeDebugTargetKind.CORE, executable, List.of(), null, Map.of(), null, 0, coreFile, null, NativeDebuggerSetup.EMPTY);
    }

    public static NativeDebugTarget remote(@Nullable Path executable, String address) {
        return new NativeDebugTarget(NativeDebugTargetKind.REMOTE, executable, List.of(), null, Map.of(), null, 0, null, address, NativeDebuggerSetup.EMPTY);
    }

    public NativeDebugTarget withSetup(NativeDebuggerSetup setup) {
        return new NativeDebugTarget(kind,
            executable,
            arguments,
            workingDirectory,
            environment,
            terminal,
            processId,
            coreFile,
            remoteAddress,
            setup);
    }
}
