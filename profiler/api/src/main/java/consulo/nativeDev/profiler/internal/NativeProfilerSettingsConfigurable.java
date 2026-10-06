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
package consulo.nativeDev.profiler.internal;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.configurable.ApplicationConfigurable;
import consulo.execution.profiler.ProfilerConfigurableIds;
import consulo.execution.profiler.configuration.EditProfilerConfigurationComponent;
import consulo.localize.LocalizeValue;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.nativeDev.profiler.NativeProfilerConfigurableIds;
import jakarta.inject.Inject;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@ExtensionImpl
public class NativeProfilerSettingsConfigurable extends EditProfilerConfigurationComponent implements ApplicationConfigurable {
    @Inject
    public NativeProfilerSettingsConfigurable(Application application) {
        super(application, NativeProfilerConfigurableIds.LANGUAGE_SETTINGS_GROUP, null);
    }

    @Override
    public String getId() {
        return NativeProfilerConfigurableIds.LANGUAGE_SETTINGS_GROUP;
    }

    @Override
    public String getParentId() {
        return ProfilerConfigurableIds.GROUP;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return NativeDevLocalize.nativeProfilerSettingsDisplayName();
    }
}
