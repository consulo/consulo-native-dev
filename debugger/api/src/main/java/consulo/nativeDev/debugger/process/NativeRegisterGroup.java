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

import consulo.execution.debug.frame.XCompositeNode;
import consulo.execution.debug.frame.XValueChildrenList;
import consulo.execution.debug.frame.XValueGroup;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.nativeDev.debugger.driver.NativeFrame;
import consulo.nativeDev.debugger.driver.NativeVariable;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
class NativeRegisterGroup extends XValueGroup {
    private final NativeDriverDebugProcess myProcess;
    private final NativeFrame myFrame;

    NativeRegisterGroup(NativeDriverDebugProcess process, NativeFrame frame) {
        super(NativeDevLocalize.nativeDebuggerRegisters());
        myProcess = process;
        myFrame = frame;
    }

    @Override
    public void computeChildren(XCompositeNode node) {
        myProcess.getDriver().getRegisters(myFrame).whenCompleteAsync((registers, error) -> {
            if (node.isObsolete()) {
                return;
            }
            if (error != null) {
                node.setErrorMessage(NativeDriverDebugProcess.message(error));
                return;
            }
            XValueChildrenList children = new XValueChildrenList(registers.size());
            for (NativeVariable register : registers) {
                children.add(new NativeValue(myProcess, register, "$" + register.name(), null));
            }
            node.addChildren(children, true);
        }, myProcess.getExecutor());
    }
}
