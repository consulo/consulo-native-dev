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

import consulo.application.progress.ProgressIndicator;
import consulo.execution.profiler.BaseCallStackElement;
import consulo.execution.profiler.CollapsedProfilerDumpWriter;
import consulo.execution.profiler.FileBasedProfilerProcess;
import consulo.execution.profiler.NewCallTreeOnlyProfilerData;
import consulo.execution.profiler.ProfilerDumpFileParsingResult;
import consulo.execution.profiler.ProfilerDumpWriter;
import consulo.execution.profiler.ProfilerState;
import consulo.execution.profiler.Success;
import consulo.execution.profiler.configuration.ProfilerConfigurationState;
import consulo.execution.profiler.model.ThreadInfo;
import consulo.logging.Logger;
import consulo.nativeDev.profiler.NativeTargetProcess;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilderFactory;
import consulo.project.Project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public class PerfProfilerProcess extends FileBasedProfilerProcess<NativeTargetProcess> {
    private static final Logger LOG = Logger.getInstance(PerfProfilerProcess.class);

    private final ProcessHandlerBuilderFactory myProcessHandlerBuilderFactory;
    private final PerfProfilerConfigurationState myState;
    private final String myPerfPath;
    private final long myAttachedTimestamp;

    public PerfProfilerProcess(Project project,
                               ProcessHandlerBuilderFactory processHandlerBuilderFactory,
                               NativeTargetProcess targetProcess,
                               PerfProfilerConfigurationState state,
                               String perfPath,
                               ProcessHandler processHandler,
                               File recording) {
        super(project, targetProcess, recording);
        myProcessHandlerBuilderFactory = processHandlerBuilderFactory;
        myState = state;
        myPerfPath = perfPath;
        myAttachedTimestamp = System.currentTimeMillis();
        initTargetProcessLifecycleListener(processHandler);
    }

    @Override
    public long getAttachedTimestamp() {
        return myAttachedTimestamp;
    }

    @Override
    public ProfilerConfigurationState getProfilerConfiguration() {
        return myState;
    }

    @Override
    protected ProfilerState readPreparedDump(File file, ProgressIndicator indicator) {
        try {
            ProfilerDumpFileParsingResult result = PerfUtil.readRecording(
                myProcessHandlerBuilderFactory,
                myPerfPath,
                file,
                myState.isInlineFrames(),
                indicator
            );
            ProfilerDumpWriter dumpWriter = null;
            if (result instanceof Success success && success.getData() instanceof NewCallTreeOnlyProfilerData data) {
                dumpWriter = new CollapsedProfilerDumpWriter(
                    data.getBuilder(),
                    getTargetProcess().getFullName(),
                    myAttachedTimestamp,
                    BaseCallStackElement::fullName,
                    ThreadInfo::getName
                );
            }
            return asProfilerState(result, dumpWriter);
        }
        finally {
            deleteRecording(file);
        }
    }

    private static void deleteRecording(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        }
        catch (IOException e) {
            LOG.warn("Can't delete the perf recording " + file, e);
        }
    }
}
