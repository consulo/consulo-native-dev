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

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.UnnamedConfigurable;
import consulo.execution.profiler.configuration.ProfilerAttacher;
import consulo.execution.profiler.configuration.ProfilerConfigurationTypeBase;
import consulo.execution.profiler.configuration.ProfilerStarter;
import consulo.execution.profiler.icon.ExecutionProfilerIconGroup;
import consulo.localize.LocalizeValue;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.nativeDev.profiler.NativeProfilerConfigurableIds;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@ExtensionImpl
public class PerfProfilerConfigurationType extends ProfilerConfigurationTypeBase<PerfProfilerConfigurationState> {
    public static final String ID = "native.perf";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return NativeDevLocalize.nativeProfilerPerfName();
    }

    @Override
    public Image getIcon() {
        return ExecutionProfilerIconGroup.profilecpu();
    }

    @Override
    public String getLanguageSettingsGroup() {
        return NativeProfilerConfigurableIds.LANGUAGE_SETTINGS_GROUP;
    }

    @Override
    public PerfProfilerConfigurationState getTemplateState() {
        PerfProfilerConfigurationState state = new PerfProfilerConfigurationState();
        state.setDisplayName(NativeDevLocalize.nativeProfilerPerfName().get());
        return state;
    }

    @Override
    public UnnamedConfigurable createConfigurable(PerfProfilerConfigurationState state) {
        return new PerfProfilerConfigurable(state);
    }

    @Override
    public ProfilerStarter createStarter(PerfProfilerConfigurationState state) {
        return new PerfProfilerStarter();
    }

    @Override
    public @Nullable ProfilerAttacher createAttacher(PerfProfilerConfigurationState state) {
        return null;
    }
}
