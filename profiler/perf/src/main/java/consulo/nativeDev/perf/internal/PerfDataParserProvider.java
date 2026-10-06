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

import consulo.annotation.component.ExtensionImpl;
import consulo.execution.profiler.Failure;
import consulo.execution.profiler.ProfilerDumpFileParser;
import consulo.execution.profiler.ProfilerDumpParserProvider;
import consulo.localize.LocalizeValue;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.process.ProcessHandlerBuilderFactory;
import consulo.project.Project;
import jakarta.inject.Inject;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@ExtensionImpl
public class PerfDataParserProvider implements ProfilerDumpParserProvider {
    private final ProcessHandlerBuilderFactory myProcessHandlerBuilderFactory;

    @Inject
    public PerfDataParserProvider(ProcessHandlerBuilderFactory processHandlerBuilderFactory) {
        myProcessHandlerBuilderFactory = processHandlerBuilderFactory;
    }

    @Override
    public String getId() {
        return "perf.data";
    }

    @Override
    public LocalizeValue getName() {
        return NativeDevLocalize.nativeProfilerPerfDataName();
    }

    @Override
    public String getRequiredFileExtension() {
        return "data";
    }

    @Override
    public ProfilerDumpFileParser createParser(Project project) {
        return (file, indicator) -> {
            String perf = PerfUtil.findPerf("");
            if (perf == null) {
                return new Failure(NativeDevLocalize.nativeProfilerPerfNotFound().get());
            }
            return PerfUtil.readRecording(myProcessHandlerBuilderFactory, perf, file, false, indicator);
        };
    }
}
