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

import consulo.nativeDev.debugger.NativeDebuggerInstallation;
import consulo.nativeDev.debugger.NativeDebuggerProvider;

import java.util.List;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public interface NativeDebuggerSetup {
    NativeDebuggerSetup EMPTY = new NativeDebuggerSetup() {
    };

    default List<String> getSourceLanguages() {
        return List.of();
    }

    /**
     * @param debugger which debugger runs the target - its {@link NativeDebuggerProvider#getFamilyId() family} says which
     *                 commands it takes, its id what is special about it
     */
    default List<String> getInitCommands(NativeDebuggerProvider debugger) {
        return List.of();
    }

    /**
     * Debuggers shipped with the toolchain the target was built by, most preferred first. "Auto" tries them before anything
     * found on the machine - only they are sure to understand the language and runtime of the target (e.g. the LLDB of a
     * Swift toolchain knows Swift types, a stock LLDB does not). A debugger chosen explicitly keeps its own lookup.
     */
    default List<NativeDebuggerInstallation> getToolchainDebuggers() {
        return List.of();
    }
}
