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

import consulo.util.xml.serializer.annotation.Attribute;
import org.jspecify.annotations.Nullable;

/**
 * The attributes a native program configuration wrote before the fragment model, read by
 * {@link NativeProgramRunConfiguration#convertLegacyProgramState}.
 *
 * @author VISTALL
 * @since 2026-10-23
 */
public class NativeProgramLegacyState {
    @Attribute("parameters")
    public @Nullable String PARAMETERS;

    @Attribute("working-directory")
    public @Nullable String WORKING_DIRECTORY;

    @Attribute("pass-parent-envs")
    public boolean PASS_PARENT_ENVS = true;

    @Attribute("debugger")
    public @Nullable String DEBUGGER;

    @Attribute("step-into-without-sources")
    public boolean STEP_INTO_WITHOUT_SOURCES = true;
}
