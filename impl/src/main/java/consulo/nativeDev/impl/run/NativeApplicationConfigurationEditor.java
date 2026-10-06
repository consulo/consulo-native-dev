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

import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.ui.CommonProgramParametersLayout;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.nativeDev.debugger.NativeDebuggerProvider;
import consulo.project.Project;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.dialog.DialogService;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public class NativeApplicationConfigurationEditor extends SettingsEditor<NativeApplicationConfiguration> {
    private static final String AUTO = "";

    private final Project myProject;
    private @Nullable Layout myLayout;

    public NativeApplicationConfigurationEditor(Project project) {
        myProject = project;
    }

    @RequiredUIAccess
    @Override
    protected Component createUIComponent() {
        Layout layout = new Layout(myProject);
        layout.build();
        myLayout = layout;
        return layout.getComponent();
    }

    @RequiredUIAccess
    @Override
    protected void resetEditorFrom(NativeApplicationConfiguration configuration) {
        Layout layout = myLayout;
        if (layout != null) {
            layout.myExecutable.setValue(configuration.getExecutablePath());
            String debugger = configuration.getDebuggerProviderId();
            layout.myDebugger.setValue(debugger == null ? AUTO : debugger);
            layout.reset(configuration);
        }
    }

    @RequiredUIAccess
    @Override
    protected void applyEditorTo(NativeApplicationConfiguration configuration) throws ConfigurationException {
        Layout layout = myLayout;
        if (layout != null) {
            configuration.setExecutablePath(StringUtil.notNullize(layout.myExecutable.getValue()).trim());
            String debugger = layout.myDebugger.getValue();
            configuration.setDebuggerProviderId(debugger == null || AUTO.equals(debugger) ? null : debugger);
            layout.apply(configuration);
        }
    }

    private static class Layout extends CommonProgramParametersLayout<NativeApplicationConfiguration> {
        private final Project myProject;
        private FileChooserTextBoxBuilder.Controller myExecutable;
        private ComboBox<String> myDebugger;

        @RequiredUIAccess
        private Layout(Project project) {
            super(project.getApplication().getInstance(DialogService.class));
            myProject = project;
            myExecutable = FileChooserTextBoxBuilder.create(project)
                .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
                .dialogTitle(NativeDevLocalize.nativeApplicationExecutableDialogTitle())
                .build();

            ComboBox.Builder<String> debugger = ComboBox.builder();
            debugger.add(AUTO, NativeDevLocalize.nativeApplicationDebuggerAuto());
            project.getApplication().getExtensionPoint(NativeDebuggerProvider.class)
                .forEach(provider -> debugger.add(provider.getId(), provider.getDisplayName()));
            myDebugger = debugger.build();
        }

        @RequiredUIAccess
        @Override
        protected void addBefore(FormBuilder builder) {
            builder.addLabeled(NativeDevLocalize.nativeApplicationExecutableLabel(), myExecutable.getComponent());
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
    }
}
