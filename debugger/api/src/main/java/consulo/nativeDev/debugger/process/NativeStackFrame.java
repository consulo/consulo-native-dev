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

import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.XSourcePositionFactory;
import consulo.execution.debug.evaluation.XDebuggerEvaluator;
import consulo.execution.debug.frame.XCompositeNode;
import consulo.execution.debug.frame.XStackFrame;
import consulo.execution.debug.frame.XValueChildrenList;
import consulo.nativeDev.debugger.driver.NativeDebuggerCapability;
import consulo.nativeDev.debugger.driver.NativeDebuggerDriver;
import consulo.nativeDev.debugger.driver.NativeFrame;
import consulo.nativeDev.debugger.driver.NativeVariable;
import consulo.ui.ex.ColoredTextContainer;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.util.lang.Pair;
import consulo.util.lang.lazy.LazyValue;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
class NativeStackFrame extends XStackFrame {
    private final NativeDriverDebugProcess myProcess;
    private final NativeFrame myFrame;
    private final Supplier<@Nullable XSourcePosition> myPosition;

    NativeStackFrame(NativeDriverDebugProcess process, NativeFrame frame) {
        myProcess = process;
        myFrame = frame;
        myPosition = LazyValue.nullable(this::findPosition);
    }

    NativeFrame getFrame() {
        return myFrame;
    }

    @Override
    public Object getEqualityObject() {
        return myFrame.threadId() + ":" + myFrame.level() + ":" + myFrame.address();
    }

    @Override
    public @Nullable XSourcePosition getSourcePosition() {
        return myPosition.get();
    }

    @Override
    public @Nullable XDebuggerEvaluator getEvaluator() {
        return new NativeEvaluator(myProcess, this);
    }

    @Override
    public void customizePresentation(ColoredTextContainer component) {
        String function = myFrame.function();
        component.append(function == null ? "0x" + Long.toHexString(myFrame.address()) : function, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        String file = myFrame.file();
        if (file != null && myFrame.line() > 0) {
            int slash = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
            component.append(" " + file.substring(slash + 1) + ":" + myFrame.line(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
            String module = myFrame.module();
            if (module != null) {
                component.append(" in " + module, SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        }
    }

    @Override
    public void computeChildren(XCompositeNode node) {
        NativeDebuggerDriver driver = myProcess.getDriver();
        CompletableFuture<List<NativeVariable>> fileVariables = driver.getFileVariables(myFrame).exceptionally(error -> List.of());
        driver.getVariables(myFrame).thenCombine(fileVariables, Pair::create).whenCompleteAsync((variables, error) -> {
            if (node.isObsolete()) {
                return;
            }
            if (error != null) {
                node.setErrorMessage(NativeDriverDebugProcess.message(error));
                return;
            }
            List<NativeVariable> locals = variables.getFirst();
            List<NativeVariable> globals = variables.getSecond();
            XValueChildrenList children = new XValueChildrenList(locals.size());
            for (NativeVariable variable : locals) {
                children.add(new NativeValue(myProcess, variable, variable.name(), this));
            }
            if (!globals.isEmpty()) {
                children.addTopGroup(new NativeFileVariableGroup(myProcess, this, globals, locals.isEmpty()));
            }
            if (driver.getCapabilities().contains(NativeDebuggerCapability.REGISTERS)) {
                children.addBottomGroup(new NativeRegisterGroup(myProcess, myFrame));
            }
            node.addChildren(children, true);
        }, myProcess.getExecutor());
    }

    private @Nullable XSourcePosition findPosition() {
        String path = myFrame.file();
        if (path == null || myFrame.line() <= 0) {
            return null;
        }
        return ReadAction.compute(() -> {
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
            if (file == null) {
                return null;
            }
            return Application.get().getInstance(XSourcePositionFactory.class).createPosition(file, myFrame.line() - 1);
        });
    }
}
