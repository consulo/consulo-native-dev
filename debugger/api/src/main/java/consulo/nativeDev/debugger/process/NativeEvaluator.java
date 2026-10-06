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
package consulo.nativeDev.debugger.process;

import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.breakpoint.XExpression;
import consulo.execution.debug.evaluation.XDebuggerEvaluator;
import consulo.nativeDev.debugger.NativeDebuggerLanguageSupport;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
class NativeEvaluator extends XDebuggerEvaluator {
    private final NativeDriverDebugProcess myProcess;
    private final NativeStackFrame myFrame;

    NativeEvaluator(NativeDriverDebugProcess process, NativeStackFrame frame) {
        myProcess = process;
        myFrame = frame;
    }

    @Override
    public void evaluate(XExpression expression, XEvaluationCallback callback, @Nullable XSourcePosition expressionPosition) {
        NativeDebuggerLanguageSupport support = NativeDebuggerLanguageSupport.forLanguage(expression.getLanguage());
        evaluate(expression.getExpression(), support == null ? null : support.getDebuggerLanguage(), callback);
    }

    @Override
    public void evaluate(String expression, XEvaluationCallback callback, @Nullable XSourcePosition expressionPosition) {
        evaluate(expression, null, callback);
    }

    private void evaluate(String expression, @Nullable String language, XEvaluationCallback callback) {
        myProcess.getDriver().evaluate(myFrame.getFrame(), expression, language).whenCompleteAsync((variable, error) -> {
            if (error != null) {
                callback.errorOccurred(NativeDriverDebugProcess.message(error));
            }
            else {
                callback.evaluated(new NativeValue(myProcess, variable, expression, myFrame));
            }
        }, myProcess.getExecutor());
    }
}
