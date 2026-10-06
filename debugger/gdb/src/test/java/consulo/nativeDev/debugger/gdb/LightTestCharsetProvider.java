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
package consulo.nativeDev.debugger.gdb;

import consulo.annotation.component.ComponentProfiles;
import consulo.annotation.component.ServiceImpl;
import consulo.process.DefaultCharsetProvider;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@ServiceImpl(profiles = ComponentProfiles.LIGHT_TEST)
public class LightTestCharsetProvider implements DefaultCharsetProvider {
    @Override
    public Charset getDefaultCharset() {
        return StandardCharsets.UTF_8;
    }
}
