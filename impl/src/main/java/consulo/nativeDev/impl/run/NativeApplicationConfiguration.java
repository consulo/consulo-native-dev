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
package consulo.nativeDev.impl.run;

import consulo.execution.configuration.CommandLineState;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.LegacyRunConfigurationConverter;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.RuntimeConfigurationError;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.nativeDev.debugger.run.NativeProgramRunConfiguration;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.nativeDev.profiler.NativeProfilableRunProfile;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilderFactory;
import consulo.process.cmd.GeneralCommandLine;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public class NativeApplicationConfiguration extends RunConfigurationBase
    implements NativeProgramRunConfiguration, NativeProfilableRunProfile {
    public NativeApplicationConfiguration(Project project, ConfigurationFactory factory, String name) {
        super(project, factory, name);
    }

    @Override
    protected Class<NativeApplicationConfigurationOptions> getOptionsClass() {
        return NativeApplicationConfigurationOptions.class;
    }

    @Override
    public NativeApplicationConfigurationOptions getOptions() {
        return (NativeApplicationConfigurationOptions) super.getOptions();
    }

    @Override
    protected void convertLegacyState(LegacyRunConfigurationConverter converter) {
        NativeApplicationConfigurationOptions options = new NativeApplicationConfigurationOptions();
        options.setExecutable(converter.getElement().getAttributeValue("executable"));
        converter.setOptions(options);

        NativeProgramRunConfiguration.convertLegacyProgramState(converter);
    }

    @Override
    public List<RunConfigurationFragment<?>> createFragments() {
        return List.of(new NativeApplicationExecutableFragment());
    }

    public String getExecutablePath() {
        return StringUtil.notNullize(getOptions().getExecutable());
    }

    public void setExecutablePath(String executablePath) {
        getOptions().setExecutable(executablePath);
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationError {
        if (StringUtil.isEmptyOrSpaces(getExecutablePath())) {
            throw new RuntimeConfigurationError(NativeDevLocalize.nativeApplicationExecutableNotSpecified());
        }
    }

    @Override
    public @Nullable RunProfileState getState(Executor executor, ExecutionEnvironment environment) {
        return new CommandLineState(environment) {
            @Override
            protected ProcessHandler startProcess() throws ExecutionException {
                return getProject().getApplication().getInstance(ProcessHandlerBuilderFactory.class)
                    .newBuilder(createCommandLine())
                    .killable()
                    .colored()
                    .build();
            }
        };
    }

    public GeneralCommandLine createCommandLine() {
        return createCommandLine(getExecutablePath(), null);
    }

    @Override
    public NativeDebugTarget createDebugTarget(ExecutionEnvironment environment) throws ExecutionException {
        String executablePath = getExecutablePath();
        if (StringUtil.isEmptyOrSpaces(executablePath)) {
            throw new ExecutionException(NativeDevLocalize.nativeApplicationExecutableNotSpecified().get());
        }
        return createDebugTarget(Path.of(executablePath), null);
    }
}
