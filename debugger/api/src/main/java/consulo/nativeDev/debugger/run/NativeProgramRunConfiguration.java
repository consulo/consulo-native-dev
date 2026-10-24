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
package consulo.nativeDev.debugger.run;

import consulo.execution.CommonProgramRunConfigurationParameters;
import consulo.execution.configuration.LegacyRunConfigurationConverter;
import consulo.execution.configuration.fragment.EnvironmentFragmentState;
import consulo.execution.configuration.fragment.ProgramParametersFragmentState;
import consulo.execution.configuration.fragment.WorkingDirectoryFragmentState;
import consulo.nativeDev.debugger.NativeDebuggableRunProfile;
import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersListUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * A configuration starting a native program: program parameters, working directory and environment come from the shared
 * fragments, the debugger from {@link NativeDebuggerFragmentState}.
 *
 * @author VISTALL
 * @since 2026-10-10
 */
public interface NativeProgramRunConfiguration extends CommonProgramRunConfigurationParameters, NativeDebuggableRunProfile {
    /**
     * Converts the program attributes of an old configuration element; call from {@code convertLegacyState}.
     */
    static void convertLegacyProgramState(LegacyRunConfigurationConverter converter) {
        NativeProgramLegacyState old = converter.readXml(NativeProgramLegacyState.class);
        converter.addFragment(new ProgramParametersFragmentState(old.PARAMETERS));
        converter.addFragment(new WorkingDirectoryFragmentState(old.WORKING_DIRECTORY));
        converter.addFragment(new EnvironmentFragmentState(converter.readEnvs(), old.PASS_PARENT_ENVS));
        if (old.DEBUGGER != null || !old.STEP_INTO_WITHOUT_SOURCES) {
            converter.addFragment(new NativeDebuggerFragmentState(old.DEBUGGER, old.STEP_INTO_WITHOUT_SOURCES));
        }
    }

    @Override
    default @Nullable String getDebuggerProviderId() {
        return getFragmentState(NativeDebuggerFragmentState.class).getDebuggerProviderId();
    }

    default void setDebuggerProviderId(@Nullable String debuggerProviderId) {
        getFragmentState(NativeDebuggerFragmentState.class).setDebuggerProviderId(debuggerProviderId);
    }

    default boolean isStepIntoWithoutSources() {
        return getFragmentState(NativeDebuggerFragmentState.class).isStepIntoWithoutSources();
    }

    default void setStepIntoWithoutSources(boolean stepIntoWithoutSources) {
        getFragmentState(NativeDebuggerFragmentState.class).setStepIntoWithoutSources(stepIntoWithoutSources);
    }

    default List<String> getProgramArguments() {
        return ParametersListUtil.parse(StringUtil.notNullize(getProgramParameters()));
    }

    /**
     * The working directory of the program, {@code defaultWorkingDirectory} when none is set.
     */
    default @Nullable Path resolveWorkingDirectory(@Nullable Path defaultWorkingDirectory) {
        String workingDirectory = getWorkingDirectory();
        return StringUtil.isEmptyOrSpaces(workingDirectory) ? defaultWorkingDirectory : Path.of(workingDirectory);
    }

    default GeneralCommandLine createCommandLine(String executable, @Nullable Path defaultWorkingDirectory) {
        GeneralCommandLine commandLine = new GeneralCommandLine(executable);
        commandLine.addParameters(getProgramArguments());
        Path workingDirectory = resolveWorkingDirectory(defaultWorkingDirectory);
        if (workingDirectory != null) {
            commandLine.withWorkDirectory(workingDirectory.toString());
        }
        commandLine.withEnvironment(getEnvs());
        commandLine.withParentEnvironmentType(isPassParentEnvs()
            ? GeneralCommandLine.ParentEnvironmentType.CONSOLE
            : GeneralCommandLine.ParentEnvironmentType.NONE);
        return commandLine;
    }

    default NativeDebugTarget createDebugTarget(Path executable, @Nullable Path defaultWorkingDirectory) {
        return NativeDebugTarget.launch(executable,
            getProgramArguments(),
            resolveWorkingDirectory(defaultWorkingDirectory),
            getEnvs(),
            null).withStepIntoWithoutSources(isStepIntoWithoutSources());
    }
}
