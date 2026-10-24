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

import com.dslplatform.json.CompiledJson;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.Attribute;
import org.jspecify.annotations.Nullable;

/**
 * The debugger a {@link NativeProgramRunConfiguration} is debugged with, {@code null} for the first one able to debug it.
 *
 * @author VISTALL
 * @since 2026-10-23
 */
@CompiledJson
public class NativeDebuggerFragmentState {
    private @Nullable String myDebuggerProviderId;
    private boolean myStepIntoWithoutSources = true;

    public NativeDebuggerFragmentState() {
    }

    public NativeDebuggerFragmentState(@Nullable String debuggerProviderId, boolean stepIntoWithoutSources) {
        setDebuggerProviderId(debuggerProviderId);
        myStepIntoWithoutSources = stepIntoWithoutSources;
    }

    @Attribute("debuggerProviderId")
    public @Nullable String getDebuggerProviderId() {
        return myDebuggerProviderId;
    }

    public void setDebuggerProviderId(@Nullable String debuggerProviderId) {
        myDebuggerProviderId = StringUtil.nullize(debuggerProviderId);
    }

    @Attribute("stepIntoWithoutSources")
    public boolean isStepIntoWithoutSources() {
        return myStepIntoWithoutSources;
    }

    public void setStepIntoWithoutSources(boolean stepIntoWithoutSources) {
        myStepIntoWithoutSources = stepIntoWithoutSources;
    }
}
