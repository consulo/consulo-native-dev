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

import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.execution.debug.XDebuggerUtil;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.XSourcePositionFactory;
import consulo.execution.debug.frame.XNavigatable;
import consulo.language.Language;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public final class NativeSourceNavigationUtil {
    private NativeSourceNavigationUtil() {
    }

    public static void navigateToDeclaration(Project project,
                                             Supplier<@Nullable XSourcePosition> context,
                                             String expression,
                                             XNavigatable navigatable,
                                             Executor executor) {
        navigate(project, context, navigatable, executor, (support, element) -> support.findDeclaration(project, element, expression.trim()));
    }

    public static void navigateToType(Project project,
                                      Supplier<@Nullable XSourcePosition> context,
                                      String typeName,
                                      XNavigatable navigatable,
                                      Executor executor) {
        navigate(project, context, navigatable, executor, (support, element) -> support.findTypeDeclaration(project, element, typeName.trim()));
    }

    private static void navigate(Project project,
                                 Supplier<@Nullable XSourcePosition> context,
                                 XNavigatable navigatable,
                                 Executor executor,
                                 BiFunction<NativeDebuggerLanguageSupport, PsiElement, @Nullable PsiElement> lookup) {
        executor.execute(() -> {
            XSourcePosition position = context.get();
            if (position == null) {
                navigatable.setSourcePosition(null);
                return;
            }
            navigatable.setSourcePosition(ReadAction.compute(() -> {
                if (project.isDisposed()) {
                    return null;
                }
                Language language = position.getFile().getFileType() instanceof LanguageFileType type ? type.getLanguage() : null;
                NativeDebuggerLanguageSupport support = NativeDebuggerLanguageSupport.forLanguage(language);
                if (support == null) {
                    return null;
                }
                PsiElement contextElement = XDebuggerUtil.getInstance().findContextElement(position.getFile(), position.getOffset(), project);
                if (contextElement == null) {
                    return null;
                }
                PsiElement target = lookup.apply(support, contextElement);
                if (target == null) {
                    return null;
                }
                return Application.get().getInstance(XSourcePositionFactory.class).createPositionByElement(target.getNavigationElement());
            }));
        });
    }
}
