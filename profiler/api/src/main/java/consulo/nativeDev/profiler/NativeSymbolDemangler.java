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
package consulo.nativeDev.profiler;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface NativeSymbolDemangler {
    static String demangleWithExtensions(String symbol) {
        String demangled = Application.get()
            .getExtensionPoint(NativeSymbolDemangler.class)
            .computeSafeIfAny(demangler -> demangler.demangle(symbol));
        return demangled == null ? symbol : demangled;
    }

    @Nullable String demangle(String symbol);
}
