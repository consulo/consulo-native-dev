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
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.nativeDev.debugger.run.NativeProgramParametersLayout;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public class NativeApplicationConfigurationEditor extends SettingsEditor<NativeApplicationConfiguration> {
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
            layout.reset(configuration);
        }
    }

    @RequiredUIAccess
    @Override
    protected void applyEditorTo(NativeApplicationConfiguration configuration) throws ConfigurationException {
        Layout layout = myLayout;
        if (layout != null) {
            configuration.setExecutablePath(StringUtil.notNullize(layout.myExecutable.getValue()).trim());
            layout.apply(configuration);
        }
    }

    private static class Layout extends NativeProgramParametersLayout<NativeApplicationConfiguration> {
        private final FileChooserTextBoxBuilder.Controller myExecutable;

        @RequiredUIAccess
        private Layout(Project project) {
            super(project);
            myExecutable = FileChooserTextBoxBuilder.create(project)
                .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
                .dialogTitle(NativeDevLocalize.nativeApplicationExecutableDialogTitle())
                .build();
        }

        @RequiredUIAccess
        @Override
        protected void addBefore(FormBuilder builder) {
            builder.addLabeled(NativeDevLocalize.nativeApplicationExecutableLabel(), myExecutable.getComponent());
        }
    }
}
