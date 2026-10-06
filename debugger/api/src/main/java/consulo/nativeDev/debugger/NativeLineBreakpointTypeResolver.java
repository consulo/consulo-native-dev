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
package consulo.nativeDev.debugger;

import consulo.annotation.access.RequiredReadAction;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.execution.debug.XDebuggerUtil;
import consulo.execution.debug.breakpoint.XLineBreakpointType;
import consulo.execution.debug.breakpoint.XLineBreakpointTypeResolver;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiWhiteSpace;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public abstract class NativeLineBreakpointTypeResolver implements XLineBreakpointTypeResolver {
    @RequiredReadAction
    @Override
    public @Nullable XLineBreakpointType<?> resolveBreakpointType(Project project, VirtualFile virtualFile, int line) {
        Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
        if (document == null || line < 0 || line >= document.getLineCount()) {
            return null;
        }

        AtomicBoolean code = new AtomicBoolean();
        XDebuggerUtil.getInstance().iterateLine(project, document, line, element -> {
            if (element instanceof PsiWhiteSpace || element instanceof PsiComment || element.getTextLength() == 0) {
                return true;
            }
            code.set(true);
            return false;
        });
        return code.get() ? NativeLineBreakpointType.getInstance() : null;
    }
}
