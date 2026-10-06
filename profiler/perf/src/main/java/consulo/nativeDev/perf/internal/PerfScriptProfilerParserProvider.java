/*
 * Copyright 2023 Alexandr Evstigneev
 * Modified by consulo.io
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

import consulo.annotation.component.ExtensionImpl;
import consulo.execution.profiler.ProfilerDumpFileParser;
import consulo.execution.profiler.ProfilerDumpParserProvider;
import consulo.localize.LocalizeValue;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.nativeDev.profiler.NativeSymbolDemangler;
import consulo.project.Project;

@ExtensionImpl
public class PerfScriptProfilerParserProvider implements ProfilerDumpParserProvider {
    @Override
    public String getId() {
        return "perf.script.parser";
    }

    @Override
    public LocalizeValue getName() {
        return NativeDevLocalize.nativeProfilerPerfScriptName();
    }

    @Override
    public String getRequiredFileExtension() {
        return "script";
    }

    @Override
    public ProfilerDumpFileParser createParser(Project project) {
        return new PerfScriptProfilerParser(NativeSymbolDemangler::demangleWithExtensions);
    }
}
