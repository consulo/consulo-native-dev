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
package consulo.nativeDev.perf.internal;

import consulo.localize.LocalizeValue;
import consulo.nativeDev.localize.NativeDevLocalize;

import java.util.List;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public enum PerfCallGraphMode {
    DWARF(NativeDevLocalize.nativeProfilerPerfCallGraphDwarf(), List.of("--call-graph=dwarf")),
    FRAME_POINTERS(NativeDevLocalize.nativeProfilerPerfCallGraphFramePointers(), List.of("--call-graph=fp")),
    LBR(NativeDevLocalize.nativeProfilerPerfCallGraphLbr(), List.of("--call-graph=lbr")),
    NONE(NativeDevLocalize.nativeProfilerPerfCallGraphNone(), List.of());

    private final LocalizeValue myDisplayName;
    private final List<String> myRecordArguments;

    PerfCallGraphMode(LocalizeValue displayName, List<String> recordArguments) {
        myDisplayName = displayName;
        myRecordArguments = recordArguments;
    }

    public LocalizeValue getDisplayName() {
        return myDisplayName;
    }

    public List<String> getRecordArguments() {
        return myRecordArguments;
    }
}
