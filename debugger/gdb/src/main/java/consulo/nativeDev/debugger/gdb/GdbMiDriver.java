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
package consulo.nativeDev.debugger.gdb;

import consulo.nativeDev.debugger.driver.NativeBreakpoint;
import consulo.nativeDev.debugger.driver.NativeBreakpointKind;
import consulo.nativeDev.debugger.driver.NativeBreakpointRequest;
import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.nativeDev.debugger.driver.NativeDebugTargetKind;
import consulo.nativeDev.debugger.driver.NativeDebuggerCapability;
import consulo.nativeDev.debugger.driver.NativeDebuggerDriver;
import consulo.nativeDev.debugger.driver.NativeDebuggerException;
import consulo.nativeDev.debugger.driver.NativeDebuggerKind;
import consulo.nativeDev.debugger.driver.NativeDebuggerListener;
import consulo.nativeDev.debugger.driver.NativeExitEvent;
import consulo.nativeDev.debugger.driver.NativeFrame;
import consulo.nativeDev.debugger.driver.NativeInstruction;
import consulo.nativeDev.debugger.driver.NativeMemoryBlock;
import consulo.nativeDev.debugger.driver.NativeOutputKind;
import consulo.nativeDev.debugger.driver.NativeStepKind;
import consulo.nativeDev.debugger.driver.NativeStopEvent;
import consulo.nativeDev.debugger.driver.NativeStopReason;
import consulo.nativeDev.debugger.driver.NativeThread;
import consulo.nativeDev.debugger.driver.NativeVariable;
import consulo.nativeDev.debugger.mi.GdbUtils;
import consulo.nativeDev.debugger.mi.GdbVersionPeculiarity;
import consulo.nativeDev.debugger.mi.MICommand;
import consulo.nativeDev.debugger.mi.MICommandInjector;
import consulo.nativeDev.debugger.mi.MIRecord;
import consulo.nativeDev.debugger.mi.MITList;
import consulo.nativeDev.debugger.mi.Platform;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilderFactory;
import consulo.process.ProcessOutputTypes;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public class GdbMiDriver implements NativeDebuggerDriver {
    private static final Logger LOG = LoggerFactory.getLogger(GdbMiDriver.class);

    private static final Set<NativeDebuggerCapability> CAPABILITIES =
        Collections.unmodifiableSet(EnumSet.allOf(NativeDebuggerCapability.class));

    private static final Set<String> ACCESS_SPECIFIERS = Set.of("public", "private", "protected");

    private static final GdbVersionPeculiarity.Version UNKNOWN_VERSION = new GdbVersionPeculiarity.Version(12, 0);

    private final ProcessHandlerBuilderFactory myProcessHandlerBuilderFactory;
    private final Path myGdbExecutable;
    private final List<String> myArguments;
    private final NativeDebuggerListener myListener;
    private final Executor myEventExecutor;

    private final Set<CompletableFuture<MIRecord>> myPendingFutures = ConcurrentHashMap.newKeySet();
    private final Map<String, NativeBreakpointRequest> myBreakpointRequests = new ConcurrentHashMap<>();
    private final Set<String> myRunToBreakpointIds = ConcurrentHashMap.newKeySet();
    private final List<String> myVariableObjects = new CopyOnWriteArrayList<>();
    private final AtomicBoolean myPauseRequested = new AtomicBoolean();
    private final AtomicBoolean myTerminated = new AtomicBoolean();
    private final CompletableFuture<Integer> myProcessExit = new CompletableFuture<>();

    private volatile @Nullable ProcessHandler myProcessHandler;
    private volatile @Nullable GdbMiProxy myProxy;
    private volatile @Nullable NativeDebugTarget myTarget;
    private volatile @Nullable GdbVersionPeculiarity myPeculiarity;
    private volatile GdbVersionPeculiarity.Version myVersion = UNKNOWN_VERSION;
    private volatile String myName = "gdb";
    private volatile boolean myRunRequested;
    private volatile @Nullable CompletableFuture<List<String>> myRegisterNames;
    private volatile @Nullable CompletableFuture<Map<String, List<String>>> myFileVariableNames;

    public GdbMiDriver(ProcessHandlerBuilderFactory processHandlerBuilderFactory,
                       Path gdbExecutable,
                       List<String> arguments,
                       NativeDebuggerListener listener,
                       Executor eventExecutor) {
        myProcessHandlerBuilderFactory = processHandlerBuilderFactory;
        myGdbExecutable = gdbExecutable;
        myArguments = List.copyOf(arguments);
        myListener = listener;
        myEventExecutor = eventExecutor;
    }

    @Override
    public String getName() {
        return myName;
    }

    @Override
    public NativeDebuggerKind getKind() {
        return NativeDebuggerKind.GDB;
    }

    @Override
    public Set<NativeDebuggerCapability> getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public CompletableFuture<?> start(NativeDebugTarget target) {
        if (myProcessHandler != null) {
            return CompletableFuture.failedFuture(new NativeDebuggerException("gdb is already started"));
        }
        myTarget = target;
        try {
            launchProcess();
        }
        catch (ExecutionException e) {
            return CompletableFuture.failedFuture(new NativeDebuggerException("Failed to start " + myGdbExecutable + ": " + e.getMessage(), e));
        }

        return send("-gdb-version").thenCompose(record -> {
            initPeculiarity(consoleText(record));
            return CompletableFuture.allOf(
                send("-list-features").thenAccept(peculiarity()::setFeatures),
                send("-gdb-set mi-async on").exceptionallyCompose(e -> send("-gdb-set target-async on")),
                send("-gdb-set pagination off"),
                send("-gdb-set confirm off"),
                ignoreError(send("-enable-pretty-printing"))
            );
        }).thenCompose(o -> loadTarget(target));
    }

    @Override
    public CompletableFuture<NativeBreakpoint> insertBreakpoint(NativeBreakpointRequest request) {
        if (request.kind().isWatchpoint()) {
            return insertWatchpoint(request);
        }

        GdbVersionPeculiarity peculiarity = peculiarity();
        StringBuilder command = new StringBuilder("-break-insert");
        if (request.temporary()) {
            command.append(" -t");
        }
        command.append(peculiarity.breakPendingFlag());
        if (!request.enabled()) {
            command.append(peculiarity.breakDisabledFlag());
        }
        String condition = request.condition();
        if (condition != null && !condition.isBlank()) {
            command.append(" -c ").append(GdbMiQuoteUtil.quote(condition));
        }
        if (request.ignoreCount() > 0) {
            command.append(" -i ").append(request.ignoreCount());
        }
        command.append(' ').append(location(request));

        return send(command.toString()).thenApply(record -> {
            NativeBreakpoint breakpoint = GdbMiConverter.breakpoint(request, record.results(), "bkpt");
            myBreakpointRequests.put(breakpoint.id(), request);
            return breakpoint;
        });
    }

    @Override
    public CompletableFuture<?> removeBreakpoint(NativeBreakpoint breakpoint) {
        myBreakpointRequests.remove(breakpoint.id());
        return send("-break-delete " + breakpoint.id());
    }

    @Override
    public CompletableFuture<?> run() {
        NativeDebugTarget target = target();
        myRunRequested = true;
        return switch (target.kind()) {
            case LAUNCH -> execute("-exec-run");
            case ATTACH, REMOTE -> execute("-exec-continue");
            case CORE -> getThreads().thenAccept(threads -> {
                NativeThread thread = threads.isEmpty() ? null : threads.get(0);
                fire(listener -> listener.onStopped(new NativeStopEvent(NativeStopReason.ENTRY,
                    thread == null ? null : thread.id(),
                    List.of(),
                    thread == null ? null : thread.frame(),
                    null,
                    null)));
            });
        };
    }

    @Override
    public CompletableFuture<?> resume() {
        return execute("-exec-continue");
    }

    @Override
    public CompletableFuture<?> pause() {
        myPauseRequested.set(true);
        return send("-exec-interrupt");
    }

    @Override
    public CompletableFuture<?> step(String threadId, NativeStepKind kind) {
        GdbVersionPeculiarity peculiarity = peculiarity();
        return execute(switch (kind) {
            case OVER -> peculiarity.execNextCommand(threadId);
            case INTO -> peculiarity.execStepCommand(threadId);
            case OUT -> "-exec-finish --thread " + threadId;
            case INSTRUCTION_OVER -> peculiarity.execNextInstCommand(threadId);
            case INSTRUCTION_INTO -> peculiarity.execStepInstCommand(threadId);
        });
    }

    @Override
    public CompletableFuture<?> runToLocation(String file, int line) {
        return send("-break-insert -t " + location(NativeBreakpointRequest.line(file, line))).thenCompose(record -> {
            String id = GdbMiConverter.string(GdbMiConverter.tuple(record.results(), "bkpt"), "number");
            if (id != null) {
                myRunToBreakpointIds.add(id);
            }
            return execute("-exec-continue");
        });
    }

    @Override
    public CompletableFuture<List<NativeThread>> getThreads() {
        return send("-thread-info").thenApply(record -> {
            List<NativeThread> threads = new ArrayList<>();
            for (MITList thread : GdbMiConverter.tuples(record.results().valueOf("threads"))) {
                threads.add(GdbMiConverter.thread(thread));
            }
            return threads;
        });
    }

    @Override
    public CompletableFuture<List<NativeFrame>> getFrames(String threadId, int from, int count) {
        String command = peculiarity().stackListFramesCommand(threadId) + " " + from + " " + (from + count - 1);
        return send(command).thenApply(record -> {
            List<NativeFrame> frames = new ArrayList<>();
            for (MITList frame : GdbMiConverter.tuples(record.results().valueOf("stack"))) {
                frames.add(GdbMiConverter.frame(threadId, frame));
            }
            return frames;
        });
    }

    @Override
    public CompletableFuture<List<NativeVariable>> getVariables(NativeFrame frame) {
        return send("-stack-list-variables" + frameOptions(frame) + " --no-values").thenCompose(record -> {
            List<CompletableFuture<NativeVariable>> variables = new ArrayList<>();
            for (MITList variable : GdbMiConverter.tuples(record.results().valueOf("variables"))) {
                String name = GdbMiConverter.string(variable, "name");
                if (name != null) {
                    variables.add(createVariable(frame, name).exceptionally(e -> errorVariable(name, e)));
                }
            }
            return all(variables);
        });
    }

    @Override
    public CompletableFuture<List<NativeVariable>> getFileVariables(NativeFrame frame) {
        String file = frame.file();
        if (file == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return fileVariableNames().thenCompose(namesByFile -> {
            List<CompletableFuture<NativeVariable>> variables = new ArrayList<>();
            for (String name : namesByFile.getOrDefault(file, List.of())) {
                variables.add(createVariable(frame, name).exceptionally(e -> errorVariable(name, e)));
            }
            return all(variables);
        });
    }

    @Override
    public CompletableFuture<List<NativeVariable>> getRegisters(NativeFrame frame) {
        return registerNames().thenCompose(names -> {
            String command = "-data-list-register-values" + frameOptions(frame) + " --skip-unavailable N";
            return send(command).thenApply(record -> {
                List<NativeVariable> registers = new ArrayList<>();
                for (MITList register : GdbMiConverter.tuples(record.results().valueOf("register-values"))) {
                    int number = GdbMiConverter.integer(register, "number", -1);
                    String value = GdbMiConverter.string(register, "value");
                    if (number < 0 || number >= names.size() || names.get(number).isEmpty() || value == null) {
                        continue;
                    }
                    registers.add(new NativeVariable(names.get(number), null, value, false, null));
                }
                return registers;
            });
        });
    }

    @Override
    public CompletableFuture<List<NativeVariable>> getChildren(NativeVariable variable, int from, int count) {
        String reference = variable.reference();
        if (reference == null || !variable.hasChildren()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return send(peculiarity().listChildrenCommand(reference, from, from + count)).thenCompose(record -> {
            List<CompletableFuture<List<NativeVariable>>> children = new ArrayList<>();
            for (MITList child : GdbMiConverter.tuples(record.results().valueOf("children"))) {
                String name = GdbMiConverter.string(child, "name");
                String expression = GdbMiConverter.string(child, "exp");
                if (name == null || expression == null) {
                    continue;
                }
                String type = GdbMiConverter.string(child, "type");
                if (type == null && ACCESS_SPECIFIERS.contains(expression)) {
                    children.add(getChildren(new NativeVariable(expression, null, "", true, name), 0, count));
                }
                else {
                    children.add(CompletableFuture.completedFuture(List.of(variable(expression, child, name))));
                }
            }
            return all(children).thenApply(lists -> lists.stream().flatMap(List::stream).toList());
        });
    }

    @Override
    public CompletableFuture<NativeVariable> evaluate(NativeFrame frame, String expression, @Nullable String language) {
        if (language == null) {
            return createVariable(frame, expression);
        }
        send("-gdb-set language " + language);
        CompletableFuture<NativeVariable> variable = createVariable(frame, expression);
        ignoreError(send("-gdb-set language auto"));
        return variable;
    }

    @Override
    public CompletableFuture<List<NativeInstruction>> disassemble(long start, long end) {
        String command = "-data-disassemble -s " + GdbMiConverter.hex(start) + " -e " + GdbMiConverter.hex(end) + " -- " + disassembleMode();
        return send(command).thenApply(record -> GdbMiConverter.instructions(record.results()));
    }

    @Override
    public CompletableFuture<List<NativeInstruction>> disassembleFunction(long address) {
        if (myVersion.compareTo(8, 0) < 0) {
            return disassemble(address, address + 256);
        }
        String command = "-data-disassemble -a " + GdbMiConverter.hex(address) + " -- " + disassembleMode();
        return send(command).thenApply(record -> GdbMiConverter.instructions(record.results()));
    }

    @Override
    public CompletableFuture<List<NativeMemoryBlock>> readMemory(long address, int length) {
        return send("-data-read-memory-bytes " + GdbMiConverter.hex(address) + " " + length)
            .thenApply(record -> GdbMiConverter.memory(record.results()));
    }

    @Override
    public CompletableFuture<String> executeCommand(String command) {
        return send("-interpreter-exec console " + GdbMiQuoteUtil.quote(command)).thenApply(GdbMiDriver::consoleText);
    }

    @Override
    public CompletableFuture<?> terminate() {
        ProcessHandler handler = myProcessHandler;
        if (handler == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (!myTerminated.get()) {
            ignoreError(send("-gdb-exit"));
        }
        return myProcessExit.copy().completeOnTimeout(-1, 5, TimeUnit.SECONDS).whenComplete((code, e) -> {
            if (!handler.isProcessTerminated()) {
                handler.destroyProcess();
            }
        });
    }

    void onStopped(MIRecord record) {
        MITList results = record.results();
        String reason = GdbMiConverter.string(results, "reason");
        if (reason != null && reason.startsWith("exited")) {
            onExited(results, reason);
            return;
        }
        if (!myRunRequested) {
            return;
        }

        String threadId = GdbMiConverter.string(results, "thread-id");
        MITList frameTuple = GdbMiConverter.tuple(results, "frame");
        NativeFrame frame = threadId == null || frameTuple == null ? null : GdbMiConverter.frame(threadId, frameTuple);
        boolean pauseRequested = myPauseRequested.getAndSet(false);

        NativeStopReason stopReason;
        List<String> breakpointIds = List.of();
        String signalName = null;
        String description = null;
        if (reason == null) {
            stopReason = pauseRequested ? NativeStopReason.PAUSE : NativeStopReason.UNKNOWN;
        }
        else {
            switch (reason) {
                case "breakpoint-hit" -> {
                    String id = GdbMiConverter.string(results, "bkptno");
                    stopReason = id != null && myRunToBreakpointIds.remove(id) ? NativeStopReason.LOCATION_REACHED : NativeStopReason.BREAKPOINT;
                    breakpointIds = id == null ? List.of() : List.of(id);
                }
                case "watchpoint-trigger", "read-watchpoint-trigger", "access-watchpoint-trigger" -> {
                    stopReason = NativeStopReason.WATCHPOINT;
                    breakpointIds = watchpointIds(results);
                    MITList value = GdbMiConverter.tuple(results, "value");
                    if (value != null) {
                        description = value.toString();
                    }
                }
                case "watchpoint-scope" -> {
                    stopReason = NativeStopReason.WATCHPOINT;
                    String id = GdbMiConverter.string(results, "wpnum");
                    breakpointIds = id == null ? List.of() : List.of(id);
                    description = reason;
                }
                case "end-stepping-range", "function-finished" -> stopReason = NativeStopReason.STEP;
                case "location-reached" -> stopReason = NativeStopReason.LOCATION_REACHED;
                case "signal-received" -> {
                    signalName = GdbMiConverter.string(results, "signal-name");
                    description = GdbMiConverter.string(results, "signal-meaning");
                    boolean interrupt = "SIGINT".equals(signalName) || "0".equals(signalName) || "SIGTRAP".equals(signalName);
                    stopReason = pauseRequested && interrupt ? NativeStopReason.PAUSE : NativeStopReason.SIGNAL;
                }
                default -> {
                    stopReason = NativeStopReason.UNKNOWN;
                    description = reason;
                }
            }
        }

        if (!myRunToBreakpointIds.isEmpty()) {
            for (String id : myRunToBreakpointIds) {
                ignoreError(send("-break-delete " + id));
            }
            myRunToBreakpointIds.clear();
        }

        NativeStopEvent event = new NativeStopEvent(stopReason, threadId, breakpointIds, frame, signalName, description);
        fire(listener -> listener.onStopped(event));
    }

    void onRunning() {
        fire(NativeDebuggerListener::onRunning);
    }

    void onNotification(MIRecord record) {
        MITList results = record.results();
        switch (record.cls()) {
            case "breakpoint-modified" -> {
                String id = GdbMiConverter.string(GdbMiConverter.tuple(results, "bkpt"), "number");
                NativeBreakpointRequest request = id == null ? null : myBreakpointRequests.get(id);
                if (request != null) {
                    NativeBreakpoint breakpoint = GdbMiConverter.breakpoint(request, results, "bkpt");
                    fire(listener -> listener.onBreakpointChanged(breakpoint));
                }
            }
            case "breakpoint-deleted" -> {
                String id = GdbMiConverter.string(results, "id");
                if (id != null) {
                    myBreakpointRequests.remove(id);
                }
            }
            default -> {
            }
        }
    }

    void onOutput(NativeOutputKind kind, String text) {
        fire(listener -> listener.onOutput(kind, text));
    }

    private void onExited(MITList results, String reason) {
        Integer exitCode = null;
        String signalName = null;
        switch (reason) {
            case "exited-normally" -> exitCode = 0;
            case "exited" -> {
                String code = GdbMiConverter.string(results, "exit-code");
                if (code != null) {
                    try {
                        exitCode = Integer.parseInt(code, 8);
                    }
                    catch (NumberFormatException e) {
                        LOG.error("Unexpected gdb exit code: {}", code, e);
                    }
                }
            }
            case "exited-signalled" -> signalName = GdbMiConverter.string(results, "signal-name");
            default -> {
            }
        }
        NativeExitEvent event = new NativeExitEvent(exitCode, signalName);
        fire(listener -> listener.onExited(event));
    }

    private void launchProcess() throws ExecutionException {
        GeneralCommandLine commandLine = new GeneralCommandLine(myGdbExecutable.toString(), "-q", "--interpreter=mi")
            .withParameters(myArguments)
            .withCharset(StandardCharsets.UTF_8)
            .withRedirectErrorStream(true);
        ProcessHandler handler = myProcessHandlerBuilderFactory.newBuilder(commandLine).blockingReader().build();
        OutputStream processInput = handler.getProcessInput();
        if (processInput == null) {
            handler.destroyProcess();
            throw new ExecutionException("gdb has no input stream");
        }
        Writer input = new OutputStreamWriter(processInput, StandardCharsets.UTF_8);
        GdbMiProxy proxy = new GdbMiProxy(new MICommandInjector() {
            @Override
            public void inject(String data) {
                synchronized (input) {
                    try {
                        input.write(data);
                        input.flush();
                    }
                    catch (IOException e) {
                        LOG.warn("Failed to send a command to gdb: {}", data, e);
                    }
                }
            }

            @Override
            public void log(String data) {
            }
        }, this);

        myProcessHandler = handler;
        myProxy = proxy;

        StringBuilder line = new StringBuilder();
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(ProcessEvent event, Key outputType) {
                if (outputType != ProcessOutputTypes.STDOUT) {
                    return;
                }
                String text = event.getText();
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (c == '\n') {
                        processLine(proxy, line.toString());
                        line.setLength(0);
                    }
                    else if (c != '\r') {
                        line.append(c);
                    }
                }
            }

            @Override
            public void processTerminated(ProcessEvent event) {
                if (!line.isEmpty()) {
                    processLine(proxy, line.toString());
                    line.setLength(0);
                }
                onProcessTerminated(event.getExitCode());
            }
        });
        handler.startNotify();
    }

    private static void processLine(GdbMiProxy proxy, String line) {
        try {
            proxy.processLine(line);
        }
        catch (RuntimeException e) {
            LOG.error("Failed to process gdb output: {}", line, e);
        }
    }

    private void onProcessTerminated(int exitCode) {
        myTerminated.set(true);
        NativeDebuggerException error = new NativeDebuggerException("gdb terminated");
        for (CompletableFuture<MIRecord> future : List.copyOf(myPendingFutures)) {
            future.completeExceptionally(error);
        }
        fire(NativeDebuggerListener::onTerminated);
        myProcessExit.complete(exitCode);
    }

    private CompletableFuture<?> loadTarget(NativeDebugTarget target) {
        List<CompletableFuture<?>> commands = new ArrayList<>();
        Path executable = target.executable();
        if (executable != null) {
            commands.add(send("-file-exec-and-symbols " + GdbMiQuoteUtil.quote(executable.toString())));
        }

        switch (target.kind()) {
            case LAUNCH -> {
                boolean windows = Platform.local() == Platform.Windows_x86;
                String arguments = GdbMiQuoteUtil.programArguments(target.arguments(), windows);
                if (target.terminal() == null && !windows) {
                    arguments = arguments.isEmpty() ? "< /dev/null" : arguments + " < /dev/null";
                }
                if (!arguments.isEmpty()) {
                    commands.add(send("-exec-arguments " + arguments));
                }
                Path workingDirectory = target.workingDirectory();
                if (workingDirectory != null) {
                    commands.add(send(peculiarity().environmentCdCommand() + " " + GdbMiQuoteUtil.quote(workingDirectory.toString())));
                }
                for (Map.Entry<String, String> entry : target.environment().entrySet()) {
                    commands.add(send("-gdb-set environment " + entry.getKey() + "=" + entry.getValue()));
                }
                String terminal = target.terminal();
                if (terminal != null) {
                    commands.add(send("-inferior-tty-set " + GdbMiQuoteUtil.quote(terminal)));
                }
            }
            case ATTACH -> commands.add(send("-target-attach " + target.processId()));
            case CORE -> {
                Path coreFile = target.coreFile();
                if (coreFile != null) {
                    commands.add(send("-target-select core " + GdbMiQuoteUtil.quote(coreFile.toString())));
                }
            }
            case REMOTE -> commands.add(send("-target-select remote " + target.remoteAddress()));
        }
        return CompletableFuture.allOf(commands.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<NativeBreakpoint> insertWatchpoint(NativeBreakpointRequest request) {
        StringBuilder command = new StringBuilder("-break-watch");
        if (request.kind() == NativeBreakpointKind.WATCH_READ) {
            command.append(" -r");
        }
        else if (request.kind() == NativeBreakpointKind.WATCH_ACCESS) {
            command.append(" -a");
        }
        command.append(' ').append(GdbMiQuoteUtil.quote(String.valueOf(request.expression())));

        return send(command.toString()).thenCompose(record -> {
            List<String> ids = watchpointIds(record.results());
            if (ids.isEmpty()) {
                throw new NativeDebuggerException("No watchpoint in " + record);
            }
            String id = ids.get(0);
            List<CompletableFuture<?>> options = new ArrayList<>();
            String condition = request.condition();
            if (condition != null && !condition.isBlank()) {
                options.add(send("-break-condition " + id + " " + condition));
            }
            if (request.ignoreCount() > 0) {
                options.add(send("-break-after " + id + " " + request.ignoreCount()));
            }
            if (!request.enabled()) {
                options.add(send("-break-disable " + id));
            }
            myBreakpointRequests.put(id, request);
            return CompletableFuture.allOf(options.toArray(CompletableFuture[]::new))
                .thenApply(o -> new NativeBreakpoint(id, request, false, List.of(), 0));
        });
    }

    private CompletableFuture<NativeVariable> createVariable(NativeFrame frame, String expression) {
        return send("-var-create" + frameOptions(frame) + " - * " + GdbMiQuoteUtil.quote(expression)).thenApply(record -> {
            MITList results = record.results();
            String name = GdbMiConverter.string(results, "name");
            if (name != null) {
                myVariableObjects.add(name);
            }
            return variable(expression, results, name);
        }).exceptionallyCompose(error -> {
            String command = "-data-evaluate-expression" + frameOptions(frame) + " " + GdbMiQuoteUtil.quote(expression);
            return send(command).thenApply(record -> {
                String value = GdbMiConverter.string(record.results(), "value");
                return new NativeVariable(expression, null, value == null ? "" : value, false, null);
            });
        });
    }

    private CompletableFuture<Map<String, List<String>>> fileVariableNames() {
        CompletableFuture<Map<String, List<String>>> names = myFileVariableNames;
        if (names == null) {
            names = send("-symbol-info-variables")
                .thenApply(record -> GdbMiConverter.fileVariables(record.results()))
                .exceptionally(error -> Map.of());
            myFileVariableNames = names;
        }
        return names;
    }

    private CompletableFuture<List<String>> registerNames() {
        CompletableFuture<List<String>> names = myRegisterNames;
        if (names == null) {
            names = send("-data-list-register-names").thenApply(record -> GdbMiConverter.strings(record.results().valueOf("register-names")));
            myRegisterNames = names;
        }
        return names;
    }

    private CompletableFuture<MIRecord> execute(String command) {
        if (!myVariableObjects.isEmpty()) {
            for (String variableObject : myVariableObjects) {
                ignoreError(send("-var-delete " + GdbMiQuoteUtil.quote(variableObject)));
            }
            myVariableObjects.clear();
        }
        return send(command);
    }

    private CompletableFuture<MIRecord> send(String command) {
        GdbMiProxy proxy = myProxy;
        if (proxy == null || myTerminated.get()) {
            return CompletableFuture.failedFuture(new NativeDebuggerException("gdb is not running"));
        }
        CompletableFuture<MIRecord> future = new CompletableFuture<>();
        myPendingFutures.add(future);
        future.whenComplete((record, e) -> myPendingFutures.remove(future));
        proxy.send(new GdbMiCommand(command, future));
        if (myTerminated.get()) {
            future.completeExceptionally(new NativeDebuggerException("gdb terminated"));
        }
        return future;
    }

    private void initPeculiarity(String versionText) {
        String firstLine = versionText.lines().map(String::trim).filter(line -> !line.isEmpty()).findFirst().orElse("gdb");
        myName = firstLine;
        GdbVersionPeculiarity.Version version = UNKNOWN_VERSION;
        try {
            version = GdbUtils.parseVersionString(firstLine);
        }
        catch (RuntimeException e) {
            LOG.error("Failed to parse the gdb version: {}", firstLine, e);
        }
        myVersion = version;
        myPeculiarity = GdbVersionPeculiarity.create(version, Platform.local());
    }

    private GdbVersionPeculiarity peculiarity() {
        GdbVersionPeculiarity peculiarity = myPeculiarity;
        if (peculiarity == null) {
            throw new NativeDebuggerException("gdb is not started");
        }
        return peculiarity;
    }

    private NativeDebugTarget target() {
        NativeDebugTarget target = myTarget;
        if (target == null) {
            throw new NativeDebuggerException("gdb is not started");
        }
        return target;
    }

    private String location(NativeBreakpointRequest request) {
        boolean explicit = myVersion.compareTo(7, 12) >= 0;
        return switch (request.kind()) {
            case LINE -> explicit
                ? "--source " + GdbMiQuoteUtil.quote(String.valueOf(request.file())) + " --line " + request.line()
                : GdbMiQuoteUtil.quote(request.file() + ":" + request.line());
            case FUNCTION -> explicit
                ? "--function " + GdbMiQuoteUtil.quote(String.valueOf(request.function()))
                : GdbMiQuoteUtil.quote(String.valueOf(request.function()));
            case ADDRESS -> "*" + GdbMiConverter.hex(request.address());
            default -> throw new IllegalArgumentException(request.kind().name());
        };
    }

    private int disassembleMode() {
        return myVersion.compareTo(7, 11) >= 0 ? 5 : 2;
    }

    private void fire(Consumer<NativeDebuggerListener> action) {
        myEventExecutor.execute(() -> {
            try {
                action.accept(myListener);
            }
            catch (Throwable e) {
                LOG.error("Native debugger listener failed", e);
            }
        });
    }

    private static String frameOptions(NativeFrame frame) {
        return " --thread " + frame.threadId() + " --frame " + frame.level();
    }

    private static NativeVariable variable(String displayName, MITList variable, @Nullable String reference) {
        String value = GdbMiConverter.string(variable, "value");
        boolean hasChildren = GdbMiConverter.integer(variable, "numchild", 0) > 0 || "1".equals(GdbMiConverter.string(variable, "has_more"));
        return new NativeVariable(displayName, GdbMiConverter.string(variable, "type"), value == null ? "" : value, hasChildren, reference);
    }

    private static NativeVariable errorVariable(String name, Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        return new NativeVariable(name, null, String.valueOf(cause.getMessage()), false, null);
    }

    private static List<String> watchpointIds(MITList results) {
        for (String name : List.of("wpt", "hw-rwpt", "hw-awpt")) {
            String id = GdbMiConverter.string(GdbMiConverter.tuple(results, name), "number");
            if (id != null) {
                return List.of(id);
            }
        }
        return List.of();
    }

    private static String consoleText(MIRecord record) {
        MICommand command = record.command();
        String text = command == null ? null : command.getConsoleStream();
        return text == null ? "" : GdbMiConverter.stream(text);
    }

    private static <T> CompletableFuture<?> ignoreError(CompletableFuture<T> future) {
        return future.exceptionally(e -> null);
    }

    private static <T> CompletableFuture<List<T>> all(List<CompletableFuture<T>> futures) {
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenApply(o -> futures.stream().map(CompletableFuture::join).toList());
    }
}
