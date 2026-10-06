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
package consulo.nativeDev.profiler;

import consulo.application.Application;
import consulo.execution.DefaultExecutionResult;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.configuration.RunProfile;
import consulo.execution.executor.Executor;
import consulo.execution.executor.ExecutorGroup;
import consulo.execution.executor.ExecutorRegistry;
import consulo.execution.profiler.DefaultProfilerExecutorGroup;
import consulo.execution.profiler.ProfilerConfigurationExtension;
import consulo.execution.profiler.ProfilerExecutorSettings;
import consulo.execution.profiler.ProfilerProcess;
import consulo.execution.profiler.ProfilerToolWindowManager;
import consulo.execution.profiler.SimpleProfilerLaunchContext;
import consulo.execution.profiler.configuration.ProfilerConfigurationState;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.RunContentBuilder;
import consulo.execution.ui.RunContentDescriptor;
import consulo.execution.ui.console.ConsoleView;
import consulo.execution.ui.console.TextConsoleBuilderFactory;
import consulo.logging.Logger;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilderFactory;
import consulo.process.cmd.GeneralCommandLine;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public final class NativeProfilerLauncher {
    private static final Logger LOG = Logger.getInstance(NativeProfilerLauncher.class);

    private NativeProfilerLauncher() {
    }

    public static @Nullable ProfilerExecutorSettings findSettings(Executor executor) {
        if (ExecutorGroup.getGroupIfProxy(executor) instanceof DefaultProfilerExecutorGroup profilerGroup) {
            return profilerGroup.getRegisteredSettings(executor.getId());
        }
        return null;
    }

    public static boolean canRun(String executorId, RunProfile profile) {
        if (!(profile instanceof NativeProfilableRunProfile) || !(profile instanceof RunConfigurationBase configuration)) {
            return false;
        }
        Executor executor = ExecutorRegistry.getInstance().getExecutorById(executorId);
        ProfilerExecutorSettings settings = executor == null ? null : findSettings(executor);
        return settings != null && settings.canRun(profile) && !findExtensions(configuration, settings.getState()).isEmpty();
    }

    public static @Nullable RunContentDescriptor execute(ExecutionEnvironment environment, GeneralCommandLine commandLine)
        throws ExecutionException {
        Project project = environment.getProject();
        ProfilerExecutorSettings settings = findSettings(environment.getExecutor());
        if (settings == null || !(environment.getRunProfile() instanceof RunConfigurationBase configuration)) {
            throw new ExecutionException(NativeDevLocalize.nativeProfilerNoConfiguration(environment.getRunProfile().getName()).get());
        }
        ProfilerConfigurationState state = settings.getState();
        List<ProfilerConfigurationExtension> extensions = findExtensions(configuration, state);
        SimpleProfilerLaunchContext context = new SimpleProfilerLaunchContext(
            project,
            environment.getExecutor(),
            environment.getRunnerSettings()
        ).withCommandLine(commandLine);
        for (ProfilerConfigurationExtension extension : extensions) {
            extension.patch(configuration, state, context);
        }

        ProcessHandler handler = project.getApplication().getInstance(ProcessHandlerBuilderFactory.class)
            .newBuilder(commandLine)
            .killable()
            .colored()
            .build();
        ConsoleView console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        console.attachToProcess(handler);

        for (ProfilerConfigurationExtension extension : extensions) {
            ProfilerProcess<?> process;
            try {
                process = extension.attachToProcess(configuration, handler, state, context);
            }
            catch (RuntimeException e) {
                LOG.error("Profiler extension " + extension + " failed to attach to " + configuration.getName(), e);
                continue;
            }
            if (process != null) {
                ProfilerToolWindowManager.getInstance(project).addProfilerProcessTab(process, true);
                break;
            }
        }

        return new RunContentBuilder(new DefaultExecutionResult(console, handler), environment)
            .showRunContent(environment.getContentToReuse());
    }

    private static List<ProfilerConfigurationExtension> findExtensions(RunConfigurationBase configuration,
                                                                       ProfilerConfigurationState state) {
        List<ProfilerConfigurationExtension> extensions = new ArrayList<>();
        Application.get().getExtensionPoint(ProfilerConfigurationExtension.class).forEach(extension -> {
            if (extension.isApplicableFor(configuration) && extension.isEnabledFor(configuration, state)) {
                extensions.add(extension);
            }
        });
        return extensions;
    }
}
