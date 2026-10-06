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
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.breakpoint.XExpression;
import consulo.execution.debug.evaluation.XDebuggerEditorsProviderBase;
import consulo.language.Language;
import consulo.language.file.LanguageFileType;
import consulo.language.plain.PlainTextFileType;
import consulo.language.plain.PlainTextLanguage;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.project.Project;
import consulo.virtualFileSystem.fileType.FileType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public class NativeDebuggerEditorsProvider extends XDebuggerEditorsProviderBase {
    public static final NativeDebuggerEditorsProvider INSTANCE = new NativeDebuggerEditorsProvider();

    @Override
    public FileType getFileType() {
        return PlainTextFileType.INSTANCE;
    }

    @Override
    public Collection<Language> getSupportedLanguages(Project project, @Nullable XSourcePosition sourcePosition) {
        List<Language> languages = new ArrayList<>();
        Language preferred = sourcePosition == null ? null : languageOf(sourcePosition.getFile().getFileType());
        for (NativeDebuggerLanguageSupport support : NativeDebuggerLanguageSupport.all()) {
            Language language = support.getLanguage();
            if (language.equals(preferred)) {
                languages.add(0, language);
            }
            else {
                languages.add(language);
            }
        }
        return languages;
    }

    @Override
    public Collection<Language> getSupportedLanguages(@Nullable PsiElement context) {
        List<Language> languages = new ArrayList<>();
        Language preferred = context == null ? null : context.getLanguage();
        for (NativeDebuggerLanguageSupport support : NativeDebuggerLanguageSupport.all()) {
            Language language = support.getLanguage();
            if (language.equals(preferred)) {
                languages.add(0, language);
            }
            else {
                languages.add(language);
            }
        }
        return languages;
    }

    @RequiredReadAction
    @Override
    protected PsiFile createExpressionCodeFragment(Project project, XExpression expression, @Nullable PsiElement context, boolean isPhysical) {
        NativeDebuggerLanguageSupport support = NativeDebuggerLanguageSupport.forLanguage(expression.getLanguage());
        if (support != null) {
            return support.createExpressionCodeFragment(project, expression.getExpression(), context, isPhysical);
        }
        return createExpressionCodeFragment(project, expression.getExpression(), context, isPhysical);
    }

    @RequiredReadAction
    @Override
    protected PsiFile createExpressionCodeFragment(Project project, String text, @Nullable PsiElement context, boolean isPhysical) {
        NativeDebuggerLanguageSupport support = context == null ? null : NativeDebuggerLanguageSupport.forLanguage(context.getLanguage());
        if (support != null) {
            return support.createExpressionCodeFragment(project, text, context, isPhysical);
        }
        return PsiFileFactory.getInstance(project).createFileFromText("expression.txt", PlainTextLanguage.INSTANCE, text, isPhysical, false);
    }

    private static @Nullable Language languageOf(FileType fileType) {
        return fileType instanceof LanguageFileType languageFileType ? languageFileType.getLanguage() : null;
    }
}
