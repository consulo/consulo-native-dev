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
import consulo.nativeDev.debugger.driver.NativeVariable;
import consulo.nativeDev.localize.NativeDevLocalize;

import java.util.List;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
class NativeFileVariableGroup extends XValueGroup {
    private final NativeDriverDebugProcess myProcess;
    private final NativeStackFrame myFrame;
    private final List<NativeVariable> myVariables;
    private final boolean myAutoExpand;

    NativeFileVariableGroup(NativeDriverDebugProcess process, NativeStackFrame frame, List<NativeVariable> variables, boolean autoExpand) {
        super(NativeDevLocalize.nativeDebuggerGlobals());
        myProcess = process;
        myFrame = frame;
        myVariables = variables;
        myAutoExpand = autoExpand;
    }

    @Override
    public boolean isAutoExpand() {
        return myAutoExpand;
    }

    @Override
    public void computeChildren(XCompositeNode node) {
        XValueChildrenList children = new XValueChildrenList(myVariables.size());
        for (NativeVariable variable : myVariables) {
            children.add(new NativeValue(myProcess, variable, variable.name(), myFrame));
        }
        node.addChildren(children, true);
    }
}
