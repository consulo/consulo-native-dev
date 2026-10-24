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

import consulo.disposer.Disposable;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.localize.LocalizeValue;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.StringUtil;

/**
 * @author VISTALL
 * @since 2026-10-23
 */
class NativeApplicationExecutableFragment implements RunConfigurationFragment<NativeApplicationConfiguration> {
    @Override
    public String getId() {
        return "nativeExecutable";
    }

    @Override
    public LocalizeValue getName() {
        return NativeDevLocalize.nativeApplicationFragmentExecutable();
    }

    @Override
    public boolean isRemovable() {
        return false;
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<NativeApplicationConfiguration> createEditor(Project project, Disposable parent) {
        FileChooserTextBoxBuilder builder = FileChooserTextBoxBuilder.create(project);
        builder.fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
        builder.dialogTitle(NativeDevLocalize.nativeApplicationExecutableDialogTitle());
        builder.uiDisposable(parent);
        FileChooserTextBoxBuilder.Controller executable = builder.build();

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(NativeDevLocalize.nativeApplicationExecutableLabel(), executable.getComponent());
            }

            @RequiredUIAccess
            @Override
            public void reset(NativeApplicationConfiguration target) {
                executable.setValue(target.getExecutablePath(), false);
            }

            @RequiredUIAccess
            @Override
            public void apply(NativeApplicationConfiguration target) {
                target.setExecutablePath(StringUtil.notNullize(executable.getValue()).trim());
            }
        };
    }
}
