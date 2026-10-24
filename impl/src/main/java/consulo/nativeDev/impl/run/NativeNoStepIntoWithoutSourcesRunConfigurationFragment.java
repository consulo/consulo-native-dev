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
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.fragment.StandardRunConfigurationFragmentOrder;
import consulo.execution.configuration.fragment.TagRunConfigurationFragment;
import consulo.localize.LocalizeValue;
import consulo.nativeDev.debugger.run.NativeDebuggerFragmentState;
import consulo.nativeDev.debugger.run.NativeProgramRunConfiguration;
import consulo.nativeDev.localize.NativeDevLocalize;

/**
 * Stepping into code without sources is skipped instead of showing disassembly, kept in {@link NativeDebuggerFragmentState}.
 *
 * @author VISTALL
 * @since 2026-10-23
 */
@ExtensionImpl
public class NativeNoStepIntoWithoutSourcesRunConfigurationFragment implements TagRunConfigurationFragment<RunConfiguration> {
    @Override
    public String getId() {
        return "nativeNoStepIntoWithoutSources";
    }

    @Override
    public LocalizeValue getName() {
        return NativeDevLocalize.nativeApplicationFragmentNoStepIntoWithoutSources();
    }

    @Override
    public LocalizeValue getGroup() {
        return NativeDevLocalize.nativeApplicationFragmentGroupDebugger();
    }

    @Override
    public int getOrder() {
        return StandardRunConfigurationFragmentOrder.REDIRECT_INPUT + 20;
    }

    @Override
    public boolean isApplicable(RunConfiguration configuration) {
        return configuration instanceof NativeProgramRunConfiguration;
    }

    @Override
    public boolean isSet(RunConfiguration target) {
        return !((NativeProgramRunConfiguration) target).isStepIntoWithoutSources();
    }

    @Override
    public void set(RunConfiguration target, boolean value) {
        ((NativeProgramRunConfiguration) target).setStepIntoWithoutSources(!value);
    }
}
