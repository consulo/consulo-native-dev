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

import consulo.execution.ui.CommonProgramParametersLayout;
import consulo.nativeDev.debugger.NativeDebuggerProvider;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.project.Project;
import consulo.ui.ComboBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.dialog.DialogService;
import consulo.ui.util.FormBuilder;

/**
 * @author VISTALL
 * @since 2026-10-10
 */
public class NativeProgramParametersLayout<P extends NativeProgramRunConfiguration> extends CommonProgramParametersLayout<P> {
    private static final String AUTO = "";

    private final Project myProject;
    private final ComboBox<String> myDebugger;

    @RequiredUIAccess
    public NativeProgramParametersLayout(Project project) {
        super(project.getApplication().getInstance(DialogService.class));
        myProject = project;

        ComboBox.Builder<String> debugger = ComboBox.builder();
        debugger.add(AUTO, NativeDevLocalize.nativeApplicationDebuggerAuto());
        project.getApplication().getExtensionPoint(NativeDebuggerProvider.class)
            .forEach(provider -> debugger.add(provider.getId(), provider.getDisplayName()));
        myDebugger = debugger.build();
    }

    @RequiredUIAccess
    @Override
    protected void addAfter(FormBuilder builder) {
        builder.addLabeled(NativeDevLocalize.nativeApplicationDebuggerLabel(), myDebugger);
    }

    @Override
    protected Project getProject() {
        return myProject;
    }

    @RequiredUIAccess
    @Override
    public void reset(P configuration) {
        super.reset(configuration);
        String debugger = configuration.getDebuggerProviderId();
        myDebugger.setValue(debugger == null ? AUTO : debugger);
    }

    @RequiredUIAccess
    @Override
    public void apply(P configuration) {
        super.apply(configuration);
        String debugger = myDebugger.getValue();
        configuration.setDebuggerProviderId(debugger == null || AUTO.equals(debugger) ? null : debugger);
    }
}
