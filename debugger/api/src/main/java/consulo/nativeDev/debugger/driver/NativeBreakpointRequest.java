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
package consulo.nativeDev.debugger.driver;

import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public record NativeBreakpointRequest(NativeBreakpointKind kind,
                                      @Nullable String file,
                                      int line,
                                      @Nullable String function,
                                      long address,
                                      @Nullable String expression,
                                      @Nullable String condition,
                                      int ignoreCount,
                                      boolean enabled,
                                      boolean temporary) {
    public static NativeBreakpointRequest line(String file, int line) {
        return new NativeBreakpointRequest(NativeBreakpointKind.LINE, file, line, null, 0, null, null, 0, true, false);
    }

    public static NativeBreakpointRequest function(String function) {
        return new NativeBreakpointRequest(NativeBreakpointKind.FUNCTION, null, 0, function, 0, null, null, 0, true, false);
    }

    public static NativeBreakpointRequest address(long address) {
        return new NativeBreakpointRequest(NativeBreakpointKind.ADDRESS, null, 0, null, address, null, null, 0, true, false);
    }

    public static NativeBreakpointRequest watch(NativeBreakpointKind kind, String expression) {
        if (!kind.isWatchpoint()) {
            throw new IllegalArgumentException(kind.name());
        }
        return new NativeBreakpointRequest(kind, null, 0, null, 0, expression, null, 0, true, false);
    }

    public NativeBreakpointRequest withCondition(@Nullable String condition) {
        return new NativeBreakpointRequest(kind, file, line, function, address, expression, condition, ignoreCount, enabled, temporary);
    }

    public NativeBreakpointRequest withIgnoreCount(int ignoreCount) {
        return new NativeBreakpointRequest(kind, file, line, function, address, expression, condition, ignoreCount, enabled, temporary);
    }

    public NativeBreakpointRequest withEnabled(boolean enabled) {
        return new NativeBreakpointRequest(kind, file, line, function, address, expression, condition, ignoreCount, enabled, temporary);
    }

    public NativeBreakpointRequest withTemporary(boolean temporary) {
        return new NativeBreakpointRequest(kind, file, line, function, address, expression, condition, ignoreCount, enabled, temporary);
    }
}
