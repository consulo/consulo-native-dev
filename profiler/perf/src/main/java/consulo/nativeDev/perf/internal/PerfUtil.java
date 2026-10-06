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

import consulo.application.progress.EmptyProgressIndicator;
import consulo.application.progress.ProgressIndicator;
import consulo.execution.profiler.Failure;
import consulo.execution.profiler.ProfilerDumpFileParsingResult;
import consulo.logging.Logger;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.nativeDev.profiler.NativeSymbolDemangler;
import consulo.process.ExecutionException;
import consulo.process.PathEnvironmentVariableUtil;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilderFactory;
import consulo.process.ProcessOutputTypes;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public final class PerfUtil {
    private static final Logger LOG = Logger.getInstance(PerfUtil.class);

    public static final String PERF = "perf";

    private static final Path PERF_EVENT_PARANOID = Path.of("/proc/sys/kernel/perf_event_paranoid");

    private static final int MAX_USABLE_PARANOID = 2;

    private static final long CANCEL_CHECK_INTERVAL_MS = 100;

    private PerfUtil() {
    }

    static String messageOf(Exception e) {
        return Objects.requireNonNullElse(e.getMessage(), e.toString());
    }

    private static Failure scriptFailed(Exception e) {
        return new Failure(NativeDevLocalize.nativeProfilerPerfScriptFailed(messageOf(e)).get());
    }

    public static @Nullable String findPerf(String configuredPath) {
        if (!configuredPath.isBlank()) {
            return Files.isRegularFile(Path.of(configuredPath)) ? configuredPath : null;
        }
        File perf = PathEnvironmentVariableUtil.findInPath(PERF);
        return perf == null ? null : perf.getPath();
    }

    public static @Nullable String checkKernel() {
        String value;
        try {
            value = Files.readString(PERF_EVENT_PARANOID).trim();
        }
        catch (IOException e) {
            return null;
        }
        try {
            if (Integer.parseInt(value) > MAX_USABLE_PARANOID) {
                return NativeDevLocalize.nativeProfilerPerfParanoid(value).get();
            }
        }
        catch (NumberFormatException e) {
            LOG.warn("Unexpected kernel.perf_event_paranoid value: " + value);
        }
        return null;
    }

    public static ProfilerDumpFileParsingResult readRecording(ProcessHandlerBuilderFactory processHandlerBuilderFactory,
                                                              String perfPath,
                                                              File recording,
                                                              boolean inlineFrames,
                                                              ProgressIndicator indicator) {
        GeneralCommandLine commandLine = new GeneralCommandLine(perfPath, "script", "-i", recording.getPath())
            .withCharset(StandardCharsets.UTF_8)
            .withRedirectErrorStream(true);
        if (!inlineFrames) {
            commandLine.addParameter("--no-inline");
        }

        ProcessHandler handler;
        try {
            handler = processHandlerBuilderFactory.newBuilder(commandLine).build();
        }
        catch (ExecutionException e) {
            return scriptFailed(e);
        }

        PerfScriptProfilerParser parser = new PerfScriptProfilerParser(NativeSymbolDemangler::demangleWithExtensions);
        ProgressIndicator readerIndicator = new EmptyProgressIndicator();
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(ProcessEvent event, Key outputType) {
                if (outputType == ProcessOutputTypes.STDOUT) {
                    parser.consumeText(event.getText(), readerIndicator);
                }
            }
        });
        handler.startNotify();

        while (!handler.waitFor(CANCEL_CHECK_INTERVAL_MS)) {
            if (indicator.isCanceled()) {
                handler.destroyProcess();
                indicator.checkCanceled();
            }
        }

        ProfilerDumpFileParsingResult result = parser.createResult();
        Integer exitCode = handler.getExitCode();
        if (result instanceof Failure && exitCode != null && exitCode != 0) {
            return new Failure(NativeDevLocalize.nativeProfilerPerfScriptFailed(String.join("\n", parser.getBadLineSamples())).get());
        }
        return result;
    }
}
