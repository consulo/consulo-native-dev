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

import consulo.annotation.component.ExtensionImpl;
import consulo.disposer.Disposable;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.execution.configuration.fragment.StandardRunConfigurationFragmentOrder;
import consulo.localize.LocalizeValue;
import consulo.nativeDev.debugger.NativeDebuggerProvider;
import consulo.nativeDev.debugger.run.NativeDebuggerFragmentState;
import consulo.nativeDev.debugger.run.NativeProgramRunConfiguration;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.project.Project;
import consulo.ui.ComboBox;
import consulo.ui.annotation.RequiredUIAccess;

/**
 * The debugger of a {@link NativeProgramRunConfiguration}; owns {@link NativeDebuggerFragmentState}.
 *
 * @author VISTALL
 * @since 2026-10-23
 */
@ExtensionImpl
public class NativeDebuggerRunConfigurationFragment implements RunConfigurationFragment<RunConfiguration> {
    private static final String AUTO = "";

    @Override
    public String getId() {
        return "nativeDebugger";
    }

    @Override
    public LocalizeValue getName() {
        return NativeDevLocalize.nativeApplicationFragmentDebugger();
    }

    @Override
    public LocalizeValue getGroup() {
        return NativeDevLocalize.nativeApplicationFragmentGroupDebugger();
    }

    @Override
    public int getOrder() {
        return StandardRunConfigurationFragmentOrder.REDIRECT_INPUT + 10;
    }

    @Override
    public boolean isApplicable(RunConfiguration configuration) {
        return configuration instanceof NativeProgramRunConfiguration;
    }

    @Override
    public boolean isVisibleByDefault(RunConfiguration target) {
        return ((NativeProgramRunConfiguration) target).getDebuggerProviderId() != null;
    }

    @Override
    public void applyHidden(RunConfiguration target) {
        ((NativeProgramRunConfiguration) target).setDebuggerProviderId(null);
    }

    @Override
    public Class<NativeDebuggerFragmentState> getStateClass() {
        return NativeDebuggerFragmentState.class;
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<RunConfiguration> createEditor(Project project, Disposable parent) {
        ComboBox.Builder<String> builder = ComboBox.builder();
        builder.add(AUTO, NativeDevLocalize.nativeApplicationDebuggerAuto());
        project.getApplication().getExtensionPoint(NativeDebuggerProvider.class)
            .forEach(provider -> builder.add(provider.getId(), provider.getDisplayName()));
        ComboBox<String> debugger = builder.build();

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(NativeDevLocalize.nativeApplicationDebuggerLabel(), debugger);
            }

            @RequiredUIAccess
            @Override
            public void reset(RunConfiguration target) {
                String debuggerProviderId = ((NativeProgramRunConfiguration) target).getDebuggerProviderId();
                debugger.setValue(debuggerProviderId == null ? AUTO : debuggerProviderId);
            }

            @RequiredUIAccess
            @Override
            public void apply(RunConfiguration target) {
                ((NativeProgramRunConfiguration) target).setDebuggerProviderId(debugger.getValue());
            }
        };
    }
}
