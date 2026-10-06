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

import consulo.execution.CommonProgramRunConfigurationParameters;
import consulo.execution.configuration.CommandLineState;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.RuntimeConfigurationError;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.nativeDev.debugger.NativeDebuggableRunProfile;
import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.nativeDev.profiler.NativeProfilableRunProfile;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilderFactory;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersListUtil;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import org.jdom.Element;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public class NativeApplicationConfiguration extends RunConfigurationBase
    implements CommonProgramRunConfigurationParameters, NativeDebuggableRunProfile, NativeProfilableRunProfile {
    private String myExecutablePath = "";
    private @Nullable String myProgramParameters;
    private @Nullable String myWorkingDirectory;
    private Map<String, String> myEnvs = new LinkedHashMap<>();
    private boolean myPassParentEnvs = true;
    private @Nullable String myDebuggerProviderId;

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
    public void setProgramParameters(@Nullable String value) {
        myProgramParameters = value;
    }

    @Override
    public @Nullable String getProgramParameters() {
        return myProgramParameters;
    }

    @Override
    public void setWorkingDirectory(@Nullable String value) {
        myWorkingDirectory = value;
    }

    @Override
    public @Nullable String getWorkingDirectory() {
        return myWorkingDirectory;
    }

    @Override
    public void setEnvs(Map<String, String> envs) {
        myEnvs = new LinkedHashMap<>(envs);
    }

    @Override
    public Map<String, String> getEnvs() {
        return myEnvs;
    }

    @Override
    public void setPassParentEnvs(boolean passParentEnvs) {
        myPassParentEnvs = passParentEnvs;
    }

    @Override
    public boolean isPassParentEnvs() {
        return myPassParentEnvs;
    }

    @Override
    public @Nullable String getDebuggerProviderId() {
        return myDebuggerProviderId;
    }

    public void setDebuggerProviderId(@Nullable String debuggerProviderId) {
        myDebuggerProviderId = debuggerProviderId;
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
        GeneralCommandLine commandLine = new GeneralCommandLine(myExecutablePath);
        commandLine.addParameters(ParametersListUtil.parse(StringUtil.notNullize(myProgramParameters)));
        String workingDirectory = myWorkingDirectory;
        if (!StringUtil.isEmptyOrSpaces(workingDirectory)) {
            commandLine.withWorkDirectory(workingDirectory);
        }
        commandLine.withEnvironment(myEnvs);
        commandLine.withParentEnvironmentType(myPassParentEnvs
            ? GeneralCommandLine.ParentEnvironmentType.CONSOLE
            : GeneralCommandLine.ParentEnvironmentType.NONE);
        return commandLine;
    }

    @Override
    public NativeDebugTarget createDebugTarget(ExecutionEnvironment environment) throws ExecutionException {
        if (StringUtil.isEmptyOrSpaces(myExecutablePath)) {
            throw new ExecutionException(NativeDevLocalize.nativeApplicationExecutableNotSpecified().get());
        }
        String workingDirectory = myWorkingDirectory;
        return NativeDebugTarget.launch(Path.of(myExecutablePath),
            ParametersListUtil.parse(StringUtil.notNullize(myProgramParameters)),
            StringUtil.isEmptyOrSpaces(workingDirectory) ? null : Path.of(workingDirectory),
            myEnvs,
            null);
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
        super.readExternal(element);
        myExecutablePath = StringUtil.notNullize(element.getAttributeValue("executable"));
        myProgramParameters = element.getAttributeValue("parameters");
        myWorkingDirectory = element.getAttributeValue("working-directory");
        myPassParentEnvs = !"false".equals(element.getAttributeValue("pass-parent-envs"));
        myDebuggerProviderId = element.getAttributeValue("debugger");
        myEnvs = new LinkedHashMap<>();
        for (Element env : element.getChildren("env")) {
            String name = env.getAttributeValue("name");
            if (name != null) {
                myEnvs.put(name, StringUtil.notNullize(env.getAttributeValue("value")));
            }
        }
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
        super.writeExternal(element);
        element.setAttribute("executable", myExecutablePath);
        if (myProgramParameters != null) {
            element.setAttribute("parameters", myProgramParameters);
        }
        if (myWorkingDirectory != null) {
            element.setAttribute("working-directory", myWorkingDirectory);
        }
        if (!myPassParentEnvs) {
            element.setAttribute("pass-parent-envs", "false");
        }
        if (myDebuggerProviderId != null) {
            element.setAttribute("debugger", myDebuggerProviderId);
        }
        for (Map.Entry<String, String> entry : myEnvs.entrySet()) {
            Element env = new Element("env");
            env.setAttribute("name", entry.getKey());
            env.setAttribute("value", entry.getValue());
            element.addContent(env);
        }
    }
}
