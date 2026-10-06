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
import consulo.execution.debug.frame.XNamedValue;
import consulo.execution.debug.frame.XNavigatable;
import consulo.execution.debug.frame.XValueChildrenList;
import consulo.execution.debug.frame.XValueNode;
import consulo.execution.debug.frame.XValuePlace;
import consulo.nativeDev.debugger.NativeSourceNavigationUtil;
import consulo.nativeDev.debugger.driver.NativeVariable;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
class NativeValue extends XNamedValue {
    private static final int PAGE = 100;
    private static final Pattern INDEX = Pattern.compile("\\d+");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final NativeDriverDebugProcess myProcess;
    private final NativeVariable myVariable;
    private final @Nullable String myExpression;
    private final @Nullable NativeStackFrame myFrame;
    private int myOffset;

    NativeValue(NativeDriverDebugProcess process, NativeVariable variable, @Nullable String expression, @Nullable NativeStackFrame frame) {
        super(variable.name());
        myProcess = process;
        myVariable = variable;
        myExpression = expression;
        myFrame = frame;
    }

    @Override
    public void computePresentation(XValueNode node, XValuePlace place) {
        node.setPresentation(null, myVariable.type(), myVariable.value(), myVariable.hasChildren());
    }

    @Override
    public void computeChildren(XCompositeNode node) {
        int from = myOffset;
        myProcess.getDriver().getChildren(myVariable, from, PAGE).whenCompleteAsync((variables, error) -> {
            if (node.isObsolete()) {
                return;
            }
            if (error != null) {
                node.setErrorMessage(NativeDriverDebugProcess.message(error));
                return;
            }
            XValueChildrenList children = new XValueChildrenList(variables.size());
            for (NativeVariable variable : variables) {
                children.add(new NativeValue(myProcess, variable, childExpression(myExpression, variable.name()), myFrame));
            }
            myOffset = from + variables.size();
            boolean more = variables.size() >= PAGE;
            node.addChildren(children, !more);
            if (more) {
                node.tooManyChildren(-1);
            }
        }, myProcess.getExecutor());
    }

    @Override
    public @Nullable String getEvaluationExpression() {
        return myExpression;
    }

    @Override
    public boolean canNavigateToSource() {
        return myExpression != null && myFrame != null;
    }

    @Override
    public void computeSourcePosition(XNavigatable navigatable) {
        NativeStackFrame frame = myFrame;
        String expression = myExpression;
        if (frame == null || expression == null) {
            navigatable.setSourcePosition(null);
            return;
        }
        NativeSourceNavigationUtil.navigateToDeclaration(myProcess.getSession().getProject(),
            frame::getSourcePosition,
            expression,
            navigatable,
            myProcess.getExecutor());
    }

    @Override
    public boolean canNavigateToTypeSource() {
        return myVariable.type() != null && myFrame != null;
    }

    @Override
    public void computeTypeSourcePosition(XNavigatable navigatable) {
        NativeStackFrame frame = myFrame;
        String type = myVariable.type();
        if (frame == null || type == null) {
            navigatable.setSourcePosition(null);
            return;
        }
        NativeSourceNavigationUtil.navigateToType(myProcess.getSession().getProject(),
            frame::getSourcePosition,
            type,
            navigatable,
            myProcess.getExecutor());
    }

    private static @Nullable String childExpression(@Nullable String parent, String name) {
        if (parent == null) {
            return null;
        }
        if (INDEX.matcher(name).matches()) {
            return parent + "[" + name + "]";
        }
        if (IDENTIFIER.matcher(name).matches()) {
            return parent + "." + name;
        }
        return null;
    }
}
