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

import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersListUtil;
import consulo.util.lang.StringUtil;
import org.jdom.Element;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2026-10-10
 */
public final class NativeProgramSettings {
    private static final String ATTR_PARAMETERS = "parameters";
    private static final String ATTR_WORKING_DIRECTORY = "working-directory";
    private static final String ATTR_PASS_PARENT_ENVS = "pass-parent-envs";
    private static final String ATTR_DEBUGGER = "debugger";
    private static final String ELEMENT_ENV = "env";
    private static final String ATTR_ENV_NAME = "name";
    private static final String ATTR_ENV_VALUE = "value";

    private @Nullable String myProgramParameters;
    private @Nullable String myWorkingDirectory;
    private Map<String, String> myEnvs = new LinkedHashMap<>();
    private boolean myPassParentEnvs = true;
    private @Nullable String myDebuggerProviderId;

    public @Nullable String getProgramParameters() {
        return myProgramParameters;
    }

    public void setProgramParameters(@Nullable String programParameters) {
        myProgramParameters = programParameters;
    }

    public List<String> getProgramArguments() {
        return ParametersListUtil.parse(StringUtil.notNullize(myProgramParameters));
    }

    public @Nullable String getWorkingDirectory() {
        return myWorkingDirectory;
    }

    public void setWorkingDirectory(@Nullable String workingDirectory) {
        myWorkingDirectory = workingDirectory;
    }

    public @Nullable Path getWorkingDirectoryPath() {
        String workingDirectory = myWorkingDirectory;
        return StringUtil.isEmptyOrSpaces(workingDirectory) ? null : Path.of(workingDirectory);
    }

    public Map<String, String> getEnvs() {
        return myEnvs;
    }

    public void setEnvs(Map<String, String> envs) {
        myEnvs = new LinkedHashMap<>(envs);
    }

    public boolean isPassParentEnvs() {
        return myPassParentEnvs;
    }

    public void setPassParentEnvs(boolean passParentEnvs) {
        myPassParentEnvs = passParentEnvs;
    }

    public @Nullable String getDebuggerProviderId() {
        return myDebuggerProviderId;
    }

    public void setDebuggerProviderId(@Nullable String debuggerProviderId) {
        myDebuggerProviderId = debuggerProviderId;
    }

    public GeneralCommandLine createCommandLine(String executable, @Nullable Path defaultWorkingDirectory) {
        GeneralCommandLine commandLine = new GeneralCommandLine(executable);
        commandLine.addParameters(getProgramArguments());
        Path workingDirectory = resolveWorkingDirectory(defaultWorkingDirectory);
        if (workingDirectory != null) {
            commandLine.withWorkDirectory(workingDirectory.toString());
        }
        commandLine.withEnvironment(myEnvs);
        commandLine.withParentEnvironmentType(myPassParentEnvs
            ? GeneralCommandLine.ParentEnvironmentType.CONSOLE
            : GeneralCommandLine.ParentEnvironmentType.NONE);
        return commandLine;
    }

    public NativeDebugTarget createDebugTarget(Path executable, @Nullable Path defaultWorkingDirectory) {
        return NativeDebugTarget.launch(executable,
            getProgramArguments(),
            resolveWorkingDirectory(defaultWorkingDirectory),
            myEnvs,
            null);
    }

    public NativeProgramSettings copy() {
        NativeProgramSettings copy = new NativeProgramSettings();
        copy.myProgramParameters = myProgramParameters;
        copy.myWorkingDirectory = myWorkingDirectory;
        copy.myEnvs = new LinkedHashMap<>(myEnvs);
        copy.myPassParentEnvs = myPassParentEnvs;
        copy.myDebuggerProviderId = myDebuggerProviderId;
        return copy;
    }

    public void readExternal(Element element) {
        myProgramParameters = element.getAttributeValue(ATTR_PARAMETERS);
        myWorkingDirectory = element.getAttributeValue(ATTR_WORKING_DIRECTORY);
        myPassParentEnvs = !"false".equals(element.getAttributeValue(ATTR_PASS_PARENT_ENVS));
        myDebuggerProviderId = element.getAttributeValue(ATTR_DEBUGGER);
        myEnvs = new LinkedHashMap<>();
        for (Element env : element.getChildren(ELEMENT_ENV)) {
            String name = env.getAttributeValue(ATTR_ENV_NAME);
            if (name != null) {
                myEnvs.put(name, StringUtil.notNullize(env.getAttributeValue(ATTR_ENV_VALUE)));
            }
        }
    }

    public void writeExternal(Element element) {
        if (myProgramParameters != null) {
            element.setAttribute(ATTR_PARAMETERS, myProgramParameters);
        }
        if (myWorkingDirectory != null) {
            element.setAttribute(ATTR_WORKING_DIRECTORY, myWorkingDirectory);
        }
        if (!myPassParentEnvs) {
            element.setAttribute(ATTR_PASS_PARENT_ENVS, "false");
        }
        if (myDebuggerProviderId != null) {
            element.setAttribute(ATTR_DEBUGGER, myDebuggerProviderId);
        }
        for (Map.Entry<String, String> entry : myEnvs.entrySet()) {
            Element env = new Element(ELEMENT_ENV);
            env.setAttribute(ATTR_ENV_NAME, entry.getKey());
            env.setAttribute(ATTR_ENV_VALUE, entry.getValue());
            element.addContent(env);
        }
    }

    private @Nullable Path resolveWorkingDirectory(@Nullable Path defaultWorkingDirectory) {
        Path workingDirectory = getWorkingDirectoryPath();
        return workingDirectory != null ? workingDirectory : defaultWorkingDirectory;
    }
}
