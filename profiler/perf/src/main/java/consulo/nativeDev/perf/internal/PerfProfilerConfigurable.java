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
package consulo.nativeDev.perf.internal;

import consulo.configurable.SimpleConfigurableByProperties;
import consulo.disposer.Disposable;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.localize.LocalizeValue;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.IntBox;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.util.FormBuilder;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public class PerfProfilerConfigurable extends SimpleConfigurableByProperties {
    private final PerfProfilerConfigurationState myState;

    public PerfProfilerConfigurable(PerfProfilerConfigurationState state) {
        myState = state;
    }

    @RequiredUIAccess
    @Override
    protected Component createLayout(PropertyBuilder propertyBuilder, Disposable uiDisposable) {
        FileChooserTextBoxBuilder.Controller perfPath = FileChooserTextBoxBuilder.create(null)
            .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
            .dialogTitle(NativeDevLocalize.nativeProfilerPerfSettingsExecutableDialogTitle())
            .uiDisposable(uiDisposable)
            .build();
        TextBox perfPathBox = perfPath.getComponent();
        perfPathBox.setPlaceholder(LocalizeValue.of(PerfUtil.PERF));
        propertyBuilder.add(perfPathBox, myState::getPerfPath, value -> myState.setPerfPath(value == null ? "" : value.trim()));

        IntBox frequencyBox = IntBox.create(PerfProfilerConfigurationState.DEFAULT_SAMPLING_FREQUENCY)
            .withRange(PerfProfilerConfigurationState.MIN_SAMPLING_FREQUENCY, PerfProfilerConfigurationState.MAX_SAMPLING_FREQUENCY);
        propertyBuilder.add(
            frequencyBox,
            myState::getSamplingFrequency,
            value -> myState.setSamplingFrequency(value == null ? PerfProfilerConfigurationState.DEFAULT_SAMPLING_FREQUENCY : value)
        );

        ComboBox<PerfCallGraphMode> callGraphBox = ComboBox.create(PerfCallGraphMode.values());
        callGraphBox.setTextRenderer(mode -> mode == null ? LocalizeValue.empty() : mode.getDisplayName());
        propertyBuilder.add(
            callGraphBox,
            myState::getCallGraphMode,
            mode -> myState.setCallGraphMode(mode == null ? PerfCallGraphMode.DWARF : mode)
        );

        TextBox additionalArgumentsBox = TextBox.create();
        propertyBuilder.add(
            additionalArgumentsBox,
            myState::getAdditionalArguments,
            value -> myState.setAdditionalArguments(value == null ? "" : value.trim())
        );

        CheckBox inlineFramesBox = CheckBox.create(NativeDevLocalize.nativeProfilerPerfSettingsInlineFrames());
        propertyBuilder.add(inlineFramesBox, myState::isInlineFrames, myState::setInlineFrames);

        FormBuilder builder = FormBuilder.create()
            .addLabeled(NativeDevLocalize.nativeProfilerPerfSettingsExecutable(), perfPathBox)
            .addLabeled(NativeDevLocalize.nativeProfilerPerfSettingsFrequency(), frequencyBox)
            .addLabeled(NativeDevLocalize.nativeProfilerPerfSettingsCallGraph(), callGraphBox)
            .addLabeled(NativeDevLocalize.nativeProfilerPerfSettingsAdditionalArguments(), additionalArgumentsBox);
        builder.addBottom(inlineFramesBox);
        return builder.build();
    }
}
