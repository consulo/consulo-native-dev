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
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.profiler.ProfilerConfigurationExtension;
import consulo.execution.profiler.ProfilerLaunchContext;
import consulo.execution.profiler.ProfilerProcess;
import consulo.execution.profiler.configuration.ProfilerConfigurationState;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.nativeDev.profiler.NativeProfilableRunProfile;
import consulo.nativeDev.profiler.NativeTargetProcess;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilderFactory;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersListUtil;
import consulo.util.dataholder.Key;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@ExtensionImpl
public class PerfProfilerConfigurationExtension implements ProfilerConfigurationExtension {
    private static final Key<File> OUTPUT_FILE = Key.create("PerfProfilerConfigurationExtension.outputFile");
    private static final Key<String> PERF_PATH = Key.create("PerfProfilerConfigurationExtension.perfPath");

    private final ProcessHandlerBuilderFactory myProcessHandlerBuilderFactory;

    @Inject
    public PerfProfilerConfigurationExtension(ProcessHandlerBuilderFactory processHandlerBuilderFactory) {
        myProcessHandlerBuilderFactory = processHandlerBuilderFactory;
    }

    @Override
    public boolean isApplicableFor(RunConfigurationBase configuration) {
        return configuration instanceof NativeProfilableRunProfile;
    }

    @Override
    public boolean isEnabledFor(RunConfigurationBase configuration, ProfilerConfigurationState state) {
        return state instanceof PerfProfilerConfigurationState;
    }

    @Override
    public void patch(RunConfigurationBase configuration, ProfilerConfigurationState state, ProfilerLaunchContext context)
        throws ExecutionException {
        GeneralCommandLine commandLine = context.getCommandLine();
        if (!(state instanceof PerfProfilerConfigurationState perfState) || commandLine == null) {
            return;
        }
        String perf = PerfUtil.findPerf(perfState.getPerfPath());
        if (perf == null) {
            throw new ExecutionException(NativeDevLocalize.nativeProfilerPerfNotFound().get());
        }
        String kernelError = PerfUtil.checkKernel();
        if (kernelError != null) {
            throw new ExecutionException(kernelError);
        }
        File outputFile;
        try {
            outputFile = Files.createTempFile("consulo-perf-", ".data").toFile();
        }
        catch (IOException e) {
            throw new ExecutionException(NativeDevLocalize.nativeProfilerPerfOutputFileFailed(PerfUtil.messageOf(e)).get(), e);
        }

        List<String> record = new ArrayList<>();
        record.add("record");
        record.add("--freq=" + perfState.getSamplingFrequency());
        record.addAll(perfState.getCallGraphMode().getRecordArguments());
        record.addAll(ParametersListUtil.parse(perfState.getAdditionalArguments()));
        record.add("-o");
        record.add(outputFile.getPath());
        record.add("--");
        record.add(commandLine.getExePath());
        commandLine.getParametersList().prependAll(record.toArray(String[]::new));
        commandLine.setExePath(perf);

        context.putUserData(OUTPUT_FILE, outputFile);
        context.putUserData(PERF_PATH, perf);
    }

    @Override
    public @Nullable ProfilerProcess<?> attachToProcess(
        RunConfigurationBase configuration,
        ProcessHandler handler,
        ProfilerConfigurationState state,
        ProfilerLaunchContext context
    ) {
        File outputFile = context.getUserData(OUTPUT_FILE);
        String perf = context.getUserData(PERF_PATH);
        if (!(state instanceof PerfProfilerConfigurationState perfState) || outputFile == null || perf == null) {
            return null;
        }
        long id = handler.getId();
        return new PerfProfilerProcess(
            context.getProject(),
            myProcessHandlerBuilderFactory,
            new NativeTargetProcess(configuration.getName(), id > 0 ? (int) id : 0),
            perfState,
            perf,
            handler,
            outputFile
        );
    }
}
