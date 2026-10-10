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
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.RuntimeConfigurationError;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.nativeDev.debugger.run.NativeProgramRunConfiguration;
import consulo.nativeDev.debugger.run.NativeProgramSettings;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.nativeDev.profiler.NativeProfilableRunProfile;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilderFactory;
import consulo.process.cmd.GeneralCommandLine;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import org.jdom.Element;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public class NativeApplicationConfiguration extends RunConfigurationBase
    implements NativeProgramRunConfiguration, NativeProfilableRunProfile {
    private String myExecutablePath = "";
    private NativeProgramSettings myProgramSettings = new NativeProgramSettings();

    public NativeApplicationConfiguration(Project project, ConfigurationFactory factory, String name) {
        super(project, factory, name);
    }

    public String getExecutablePath() {
        return myExecutablePath;
    }

    public void setExecutablePath(String executablePath) {
        myExecutablePath = executablePath;
    }

    @Override
    public NativeProgramSettings getProgramSettings() {
        return myProgramSettings;
    }

    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new NativeApplicationConfigurationEditor(getProject());
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationError {
        if (StringUtil.isEmptyOrSpaces(myExecutablePath)) {
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
        return myProgramSettings.createCommandLine(myExecutablePath, null);
    }

    @Override
    public NativeDebugTarget createDebugTarget(ExecutionEnvironment environment) throws ExecutionException {
        if (StringUtil.isEmptyOrSpaces(myExecutablePath)) {
            throw new ExecutionException(NativeDevLocalize.nativeApplicationExecutableNotSpecified().get());
        }
        return myProgramSettings.createDebugTarget(Path.of(myExecutablePath), null);
    }

    @Override
    public RunConfiguration clone() {
        NativeApplicationConfiguration clone = (NativeApplicationConfiguration) super.clone();
        clone.myProgramSettings = myProgramSettings.copy();
        return clone;
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
        super.readExternal(element);
        myExecutablePath = StringUtil.notNullize(element.getAttributeValue("executable"));
        myProgramSettings.readExternal(element);
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
        super.writeExternal(element);
        element.setAttribute("executable", myExecutablePath);
        myProgramSettings.writeExternal(element);
    }
}
