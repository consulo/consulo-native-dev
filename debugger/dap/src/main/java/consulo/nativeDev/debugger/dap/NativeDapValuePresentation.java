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
package consulo.nativeDev.debugger.dap;

import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.frame.XNavigatable;
import consulo.execution.debug.frame.XStackFrame;
import consulo.execution.debugger.dap.protocol.DAP;
import consulo.execution.debugger.dap.protocol.Variable;
import consulo.execution.debugger.dap.value.DefaultDAPValuePresentation;
import consulo.nativeDev.debugger.NativeSourceNavigationUtil;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
class NativeDapValuePresentation extends DefaultDAPValuePresentation {
    private final NativeDapDebugProcess myProcess;

    NativeDapValuePresentation(NativeDapDebugProcess process) {
        myProcess = process;
    }

    @Override
    public boolean canNavigateToSource(Variable variable) {
        return super.canNavigateToSource(variable) || variable.evaluateName != null;
    }

    @Override
    public void computeSourcePosition(DAP dap, XNavigatable navigatable, Variable variable) {
        if (super.canNavigateToSource(variable) || variable.evaluateName == null) {
            super.computeSourcePosition(dap, navigatable, variable);
            return;
        }
        NativeSourceNavigationUtil.navigateToDeclaration(myProcess.getSession().getProject(),
            this::currentPosition,
            variable.evaluateName,
            navigatable,
            myProcess.getNavigationExecutor());
    }

    @Override
    public boolean canNavigateToTypeSource(Variable variable) {
        return variable.type != null && !variable.type.isBlank();
    }

    @Override
    public void computeTypeSourcePosition(XNavigatable navigatable, Variable variable) {
        if (variable.type == null) {
            navigatable.setSourcePosition(null);
            return;
        }
        NativeSourceNavigationUtil.navigateToType(myProcess.getSession().getProject(),
            this::currentPosition,
            variable.type,
            navigatable,
            myProcess.getNavigationExecutor());
    }

    private @Nullable XSourcePosition currentPosition() {
        XStackFrame frame = myProcess.getSession().getCurrentStackFrame();
        return frame == null ? null : frame.getSourcePosition();
    }
}
