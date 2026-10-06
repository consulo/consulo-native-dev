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

import consulo.application.ReadAction;
import consulo.execution.profiler.model.NativeCall;
import consulo.language.psi.NavigatablePsiElement;
import consulo.project.Project;

import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public class NavigatableNativeCall extends NativeCall {
    public NavigatableNativeCall(String library, String className, String methodOrFunction) {
        super(library, className, methodOrFunction);
    }

    @Override
    public boolean isNavigatable() {
        return true;
    }

    @Override
    public NavigatablePsiElement[] calcNavigatables(Project project) {
        if (project.isDisposed()) {
            return NavigatablePsiElement.EMPTY_ARRAY;
        }
        return ReadAction.compute(() -> {
            if (project.isDisposed()) {
                return NavigatablePsiElement.EMPTY_ARRAY;
            }
            List<NavigatablePsiElement> navigatables = new ArrayList<>();
            project.getApplication().getExtensionPoint(NavigatableSymbolSearcher.class).forEach(searcher -> {
                for (NavigatablePsiElement element : searcher.findNavigatableSymbols(this, project)) {
                    if (element.isValid()) {
                        navigatables.add(element);
                    }
                }
            });
            return navigatables.toArray(NavigatablePsiElement.EMPTY_ARRAY);
        });
    }
}
