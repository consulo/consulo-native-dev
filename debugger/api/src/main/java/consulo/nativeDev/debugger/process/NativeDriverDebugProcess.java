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

import consulo.application.ReadAction;
import consulo.execution.debug.XDebugProcess;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.breakpoint.XBreakpointHandler;
import consulo.execution.debug.breakpoint.XBreakpointProperties;
import consulo.execution.debug.breakpoint.XExpression;
import consulo.execution.debug.breakpoint.XLineBreakpoint;
import consulo.execution.debug.evaluation.XDebuggerEditorsProvider;
import consulo.execution.debug.frame.XExecutionStack;
import consulo.execution.debug.frame.XSuspendContext;
import consulo.execution.debug.ui.XDebugTabLayouter;
import consulo.execution.ui.console.ConsoleView;
import consulo.execution.ui.console.TextConsoleBuilderFactory;
import consulo.execution.ui.layout.PlaceInGrid;
import consulo.execution.ui.layout.RunnerLayoutUi;
import consulo.logging.Logger;
import consulo.nativeDev.debugger.NativeDebugProcess;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.nativeDev.debugger.NativeDebuggerLanguageSupport;
import consulo.nativeDev.debugger.NativeDebuggerEditorsProvider;
import consulo.nativeDev.debugger.driver.NativeBreakpoint;
import consulo.nativeDev.debugger.driver.NativeBreakpointRequest;
import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.nativeDev.debugger.driver.NativeDebuggerCapability;
import consulo.nativeDev.debugger.driver.NativeDebuggerDriver;
import consulo.nativeDev.debugger.driver.NativeDebuggerListener;
import consulo.nativeDev.debugger.driver.NativeExitEvent;
import consulo.nativeDev.debugger.driver.NativeFrame;
import consulo.nativeDev.debugger.driver.NativeInstruction;
import consulo.nativeDev.debugger.driver.NativeMemoryBlock;
import consulo.nativeDev.debugger.driver.NativeOutputKind;
import consulo.nativeDev.debugger.driver.NativeStepKind;
import consulo.nativeDev.debugger.driver.NativeStopEvent;
import consulo.nativeDev.debugger.driver.NativeStopReason;
import consulo.process.ProcessHandler;
import consulo.process.ProcessOutputTypes;
import consulo.ui.NotificationType;
import consulo.ui.ex.content.Content;
import consulo.util.concurrent.AsyncResult;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public class NativeDriverDebugProcess extends XDebugProcess implements NativeDebugProcess, NativeDebuggerListener {
    private static final Logger LOG = Logger.getInstance(NativeDriverDebugProcess.class);

    private final NativeDebugTarget myTarget;
    private final ExecutorService myExecutor;
    private final NativeDebuggerDriver myDriver;
    private final NativeDebugProcessHandler myProcessHandler;
    private final NativeDebuggerConsoleHandler myConsoleHandler;
    private final XBreakpointHandler<?>[] myBreakpointHandlers;

    private final Map<XLineBreakpoint<?>, CompletableFuture<NativeBreakpoint>> myBreakpoints = new ConcurrentHashMap<>();
    private final Map<String, XLineBreakpoint<?>> myBreakpointsById = new ConcurrentHashMap<>();
    private final List<CompletableFuture<NativeBreakpoint>> myInitialBreakpoints = new CopyOnWriteArrayList<>();
    private volatile boolean myStarted;

    public NativeDriverDebugProcess(XDebugSession session,
                              NativeDebugTarget target,
                              ExecutorService executor,
                              Function<NativeDebuggerListener, NativeDebuggerDriver> driverFactory) {
        super(session);
        myTarget = target;
        myExecutor = executor;
        myDriver = driverFactory.apply(this);
        myProcessHandler = new NativeDebugProcessHandler(this, target);
        myConsoleHandler = new NativeDebuggerConsoleHandler(this);
        myBreakpointHandlers = new XBreakpointHandler<?>[]{new NativeLineBreakpointHandler(this)};
    }

    public NativeDebuggerDriver getDriver() {
        return myDriver;
    }

    public ExecutorService getExecutor() {
        return myExecutor;
    }

    @Override
    public void start() {
        myDriver.start(myTarget).thenComposeAsync(o -> {
            ReadAction.run(() -> getSession().initBreakpoints());
            myStarted = true;
            CompletableFuture<?>[] breakpoints = myInitialBreakpoints.stream()
                .map(future -> future.handle((breakpoint, error) -> breakpoint))
                .toArray(CompletableFuture[]::new);
            myInitialBreakpoints.clear();
            return CompletableFuture.allOf(breakpoints);
        }, myExecutor).thenCompose(o -> myDriver.run()).whenCompleteAsync((o, error) -> {
            if (error != null) {
                String message = message(error);
                myConsoleHandler.notifyTextAvailable(message + "\n", ProcessOutputTypes.STDERR);
                getSession().reportError(message);
                myProcessHandler.notifyExited(-1);
            }
        }, myExecutor);
    }

    @Override
    public String getDebuggerName() {
        return myDriver.getName();
    }

    @Override
    public Set<NativeDebuggerCapability> getCapabilities() {
        return myDriver.getCapabilities();
    }

    @Override
    public CompletableFuture<String> executeCommand(String command) {
        return myDriver.executeCommand(command);
    }

    @Override
    public CompletableFuture<List<NativeInstruction>> disassemble(long start, long end) {
        return myDriver.disassemble(start, end);
    }

    @Override
    public CompletableFuture<List<NativeMemoryBlock>> readMemory(long address, int length) {
        return myDriver.readMemory(address, length);
    }

    @Override
    public void sessionInitialized() {
        getSession().setPauseActionSupported(myDriver.getCapabilities().contains(NativeDebuggerCapability.PAUSE));
    }

    @Override
    public boolean checkCanInitBreakpoints() {
        return false;
    }

    @Override
    public XBreakpointHandler<?>[] getBreakpointHandlers() {
        return myBreakpointHandlers;
    }

    @Override
    public XDebuggerEditorsProvider getEditorsProvider() {
        return NativeDebuggerEditorsProvider.INSTANCE;
    }

    @Override
    protected @Nullable ProcessHandler doGetProcessHandler() {
        return myProcessHandler;
    }

    @Override
    public XDebugTabLayouter createTabLayouter() {
        return new XDebugTabLayouter() {
            @Override
            public void registerAdditionalContent(RunnerLayoutUi ui) {
                ConsoleView console = TextConsoleBuilderFactory.getInstance().createBuilder(getSession().getProject()).getConsole();
                console.attachToProcess(myConsoleHandler);
                myConsoleHandler.startNotify();
                Content content = ui.createContent("NativeDebuggerConsole", console, myDriver.getName(), null);
                content.setCloseable(false);
                ui.addContent(content, 0, PlaceInGrid.bottom, false);
            }
        };
    }

    @Override
    public void startPausing() {
        report(myDriver.pause());
    }

    @Override
    public void startStepOver(@Nullable XSuspendContext context) {
        step(context, NativeStepKind.OVER);
    }

    @Override
    public void startStepInto(@Nullable XSuspendContext context) {
        step(context, NativeStepKind.INTO);
    }

    @Override
    public void startForceStepInto(@Nullable XSuspendContext context) {
        step(context, NativeStepKind.INTO);
    }

    @Override
    public void startStepOut(@Nullable XSuspendContext context) {
        step(context, NativeStepKind.OUT);
    }

    @Override
    public void resume(@Nullable XSuspendContext context) {
        report(myDriver.resume());
    }

    @Override
    public void runToPosition(XSourcePosition position, @Nullable XSuspendContext context) {
        report(myDriver.runToLocation(position.getFile().getPath(), position.getLine() + 1));
    }

    @Override
    public void stop() {
        myDriver.terminate();
    }

    @Override
    public AsyncResult<Void> stopAsync() {
        AsyncResult<Void> result = AsyncResult.undefined();
        myDriver.terminate().whenComplete((o, error) -> {
            myConsoleHandler.notifyExited();
            myExecutor.shutdown();
            result.setDone();
        });
        return result;
    }

    void registerBreakpoint(XLineBreakpoint<XBreakpointProperties> breakpoint) {
        String path = VirtualFileUtil.urlToPath(breakpoint.getFileUrl());
        XExpression condition = breakpoint.getConditionExpression();
        NativeBreakpointRequest request = NativeBreakpointRequest.line(path, breakpoint.getLine() + 1)
            .withCondition(condition == null ? null : condition.getExpression())
            .withTemporary(breakpoint.isTemporary());

        CompletableFuture<NativeBreakpoint> future = myDriver.insertBreakpoint(request);
        myBreakpoints.put(breakpoint, future);
        if (!myStarted) {
            myInitialBreakpoints.add(future);
        }
        future.whenCompleteAsync((nativeBreakpoint, error) -> {
            if (error != null) {
                myBreakpoints.remove(breakpoint, future);
                getSession().setBreakpointInvalid(breakpoint, message(error));
                return;
            }
            myBreakpointsById.put(nativeBreakpoint.id(), breakpoint);
            updatePresentation(breakpoint, nativeBreakpoint);
        }, myExecutor);
    }

    void unregisterBreakpoint(XLineBreakpoint<XBreakpointProperties> breakpoint) {
        CompletableFuture<NativeBreakpoint> future = myBreakpoints.remove(breakpoint);
        if (future == null) {
            return;
        }
        future.thenCompose(nativeBreakpoint -> {
            myBreakpointsById.remove(nativeBreakpoint.id());
            return myDriver.removeBreakpoint(nativeBreakpoint);
        }).exceptionally(error -> {
            LOG.warn("Failed to remove breakpoint " + breakpoint, error);
            return null;
        });
    }

    @Override
    public void onStopped(NativeStopEvent event) {
        myDriver.getThreads().thenAcceptAsync(threads -> {
            NativeSuspendContext context = new NativeSuspendContext(this, threads, event.threadId());
            XLineBreakpoint<?> breakpoint = event.reason() == NativeStopReason.BREAKPOINT ? findBreakpoint(event) : null;
            if (breakpoint != null) {
                String log = evaluateLog(breakpoint, event.frame());
                if (!getSession().breakpointReached(breakpoint, log, context)) {
                    report(myDriver.resume());
                }
                return;
            }

            getSession().positionReached(context);
            if (event.reason() == NativeStopReason.SIGNAL) {
                String description = event.description();
                String signal = event.signalName() + (description == null ? "" : ", " + description);
                getSession().reportMessage(NativeDevLocalize.nativeDebuggerSignalReceived(signal).get(), NotificationType.WARNING);
            }
        }, myExecutor).exceptionally(error -> {
            LOG.error("Failed to process a native debugger stop", error);
            return null;
        });
    }

    @Override
    public void onExited(NativeExitEvent event) {
        Integer exitCode = event.exitCode();
        if (event.signalName() != null) {
            myProcessHandler.notifyTextAvailable("\n" + NativeDevLocalize.nativeDebuggerSignalTerminated(event.signalName()).get() + "\n",
                ProcessOutputTypes.SYSTEM);
        }
        myProcessHandler.notifyExited(exitCode == null ? -1 : exitCode);
    }

    @Override
    public void onTerminated() {
        myProcessHandler.notifyExited(-1);
    }

    @Override
    public void onBreakpointChanged(NativeBreakpoint breakpoint) {
        XLineBreakpoint<?> lineBreakpoint = myBreakpointsById.get(breakpoint.id());
        if (lineBreakpoint != null) {
            updatePresentation(lineBreakpoint, breakpoint);
        }
    }

    @Override
    public void onOutput(NativeOutputKind kind, String text) {
        switch (kind) {
            case TARGET -> myProcessHandler.notifyTextAvailable(text, ProcessOutputTypes.STDOUT);
            case CONSOLE -> myConsoleHandler.notifyTextAvailable(text, ProcessOutputTypes.STDOUT);
            case LOG -> myConsoleHandler.notifyTextAvailable(text, ProcessOutputTypes.SYSTEM);
        }
    }

    CompletableFuture<?> terminateDriver() {
        return myDriver.terminate();
    }

    private void updatePresentation(XLineBreakpoint<?> breakpoint, NativeBreakpoint nativeBreakpoint) {
        if (!nativeBreakpoint.pending()) {
            getSession().setBreakpointVerified(breakpoint);
        }
    }

    private @Nullable XLineBreakpoint<?> findBreakpoint(NativeStopEvent event) {
        for (String id : event.breakpointIds()) {
            XLineBreakpoint<?> breakpoint = myBreakpointsById.get(id);
            if (breakpoint != null) {
                return breakpoint;
            }
        }
        return null;
    }

    private @Nullable String evaluateLog(XLineBreakpoint<?> breakpoint, @Nullable NativeFrame frame) {
        XExpression expression = breakpoint.getLogExpressionObject();
        if (expression == null || frame == null) {
            return null;
        }
        try {
            NativeDebuggerLanguageSupport support = NativeDebuggerLanguageSupport.forLanguage(expression.getLanguage());
            String language = support == null ? null : support.getDebuggerLanguage();
            return myDriver.evaluate(frame, expression.getExpression(), language).join().value();
        }
        catch (CompletionException e) {
            return message(e);
        }
    }

    private void step(@Nullable XSuspendContext context, NativeStepKind kind) {
        XExecutionStack stack = context == null ? null : context.getActiveExecutionStack();
        if (stack instanceof NativeExecutionStack nativeStack) {
            report(myDriver.step(nativeStack.getThreadId(), kind));
        }
        else {
            report(myDriver.resume());
        }
    }

    private void report(CompletableFuture<?> future) {
        future.whenCompleteAsync((o, error) -> {
            if (error != null) {
                getSession().reportError(message(error));
            }
        }, myExecutor);
    }

    static String message(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? cause.toString() : message;
    }
}
