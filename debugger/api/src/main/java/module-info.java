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

import org.jspecify.annotations.NullMarked;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@NullMarked
module consulo.nativeDev.debugger.api {
    requires transitive consulo.nativeDev.api;
    requires transitive consulo.execution.debug.api;
    requires transitive consulo.execution.api;
    requires transitive consulo.process.api;
    requires consulo.code.editor.api;
    requires consulo.document.api;
    requires consulo.language.api;
    requires consulo.application.api;
    requires consulo.localize.api;
    requires consulo.ui.api;
    requires consulo.ui.ex.api;
    requires consulo.util.lang;
    requires consulo.util.concurrent;
    requires consulo.virtual.file.system.api;
    requires consulo.logging.api;

    exports consulo.nativeDev.debugger;
    exports consulo.nativeDev.debugger.driver;
    exports consulo.nativeDev.debugger.process;
    exports consulo.nativeDev.debugger.run;
}
