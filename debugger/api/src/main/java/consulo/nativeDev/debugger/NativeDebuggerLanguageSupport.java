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

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface NativeDebuggerLanguageSupport {
    static List<NativeDebuggerLanguageSupport> all() {
        List<NativeDebuggerLanguageSupport> supports = new ArrayList<>();
        Application.get().getExtensionPoint(NativeDebuggerLanguageSupport.class).forEach(supports::add);
        return supports;
    }

    static @Nullable NativeDebuggerLanguageSupport forLanguage(@Nullable Language language) {
        if (language == null) {
            return null;
        }
        for (NativeDebuggerLanguageSupport support : all()) {
            if (support.getLanguage().equals(language)) {
                return support;
            }
        }
        return null;
    }

    Language getLanguage();

    default @Nullable String getDebuggerLanguage() {
        return null;
    }

    PsiFile createExpressionCodeFragment(Project project, String text, @Nullable PsiElement context, boolean isPhysical);

    @RequiredReadAction
    default @Nullable PsiElement findDeclaration(Project project, PsiElement context, String expression) {
        if (expression.isEmpty()) {
            return null;
        }
        PsiFile fragment = createExpressionCodeFragment(project, expression, context, false);
        for (PsiElement element = fragment.findElementAt(expression.length() - 1); element != null && element != fragment; element = element.getParent()) {
            PsiReference reference = element.getReference();
            if (reference == null) {
                continue;
            }
            if (reference instanceof PsiPolyVariantReference polyReference) {
                for (ResolveResult result : polyReference.multiResolve(false)) {
                    if (result.getElement() != null) {
                        return result.getElement();
                    }
                }
            }
            PsiElement target = reference.resolve();
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    @RequiredReadAction
    default @Nullable PsiElement findTypeDeclaration(Project project, PsiElement context, String typeName) {
        return null;
    }
}
