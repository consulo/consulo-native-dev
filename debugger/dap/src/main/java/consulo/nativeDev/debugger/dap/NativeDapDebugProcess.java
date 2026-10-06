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

import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.breakpoint.XLineBreakpointType;
import consulo.execution.debug.evaluation.XDebuggerEditorsProvider;
import consulo.execution.debug.frame.XStackFrame;
import consulo.execution.debugger.dap.DAPDebugProcess;
import consulo.execution.debugger.dap.DAPStackFrame;
import consulo.execution.debugger.dap.protocol.Capabilities;
import consulo.execution.debugger.dap.protocol.DAP;
import consulo.execution.debugger.dap.protocol.DAPFactory;
import consulo.execution.debugger.dap.protocol.DisassembleArguments;
import consulo.execution.debugger.dap.protocol.DisassembledInstruction;
import consulo.execution.debugger.dap.protocol.EvaluateArguments;
import consulo.execution.debugger.dap.protocol.EvaluateContext;
import consulo.execution.debugger.dap.protocol.ReadMemoryArguments;
import consulo.execution.debugger.dap.value.DAPValuePresentation;
import consulo.execution.ui.console.ConsoleViewContentType;
import consulo.nativeDev.debugger.NativeDebugProcess;
import consulo.nativeDev.debugger.NativeDebuggerEditorsProvider;
import consulo.nativeDev.debugger.NativeDebuggerInstallation;
import consulo.nativeDev.debugger.NativeLineBreakpointType;
import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.nativeDev.debugger.driver.NativeDebuggerCapability;
import consulo.nativeDev.debugger.driver.NativeDebuggerException;
import consulo.nativeDev.debugger.driver.NativeInstruction;
import consulo.nativeDev.debugger.driver.NativeMemoryBlock;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilderFactory;
import consulo.process.ProcessOutputTypes;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public class NativeDapDebugProcess extends DAPDebugProcess implements NativeDebugProcess {
    private static final long ADAPTER_EXIT_TIMEOUT_MS = 2000;

    private final NativeDebugTarget myTarget;
    private final NativeDebuggerInstallation myInstallation;
    private final NativeDapDebuggerProvider myProvider;

    private volatile @Nullable ProcessHandler myAdapterHandler;

    public NativeDapDebugProcess(XDebugSession session,
                                 NativeDebugTarget target,
                                 NativeDebuggerInstallation installation,
                                 NativeDapDebuggerProvider provider) {
        super(session);
        myTarget = target;
        myInstallation = installation;
        myProvider = provider;
    }

    @Override
    protected XLineBreakpointType<?> getLineBreakpointType() {
        return NativeLineBreakpointType.getInstance();
    }

    @Override
    protected DAP createDAP(DAPFactory factory) {
        GeneralCommandLine commandLine = new GeneralCommandLine(myProvider.getCommandLine(myInstallation.executable(), myTarget))
            .withCharset(StandardCharsets.UTF_8);
        Path workingDirectory = myTarget.workingDirectory();
        if (workingDirectory != null) {
            commandLine.withWorkDirectory(workingDirectory.toFile());
        }
        ProcessHandler handler;
        try {
            handler = getSession().getProject().getApplication().getInstance(ProcessHandlerBuilderFactory.class)
                .newBuilder(commandLine)
                .blockingReader()
                .build();
        }
        catch (ExecutionException e) {
            throw new NativeDebuggerException("Failed to start " + myInstallation.executable() + ": " + e.getMessage(), e);
        }
        myAdapterHandler = handler;
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(ProcessEvent event, Key outputType) {
                if (outputType == ProcessOutputTypes.STDERR) {
                    getSession().getConsoleView().print(event.getText(), ConsoleViewContentType.LOG_ERROR_OUTPUT);
                }
            }
        });
        return factory.createProcessDAP(handler);
    }

    @Override
    protected String getAdapterId() {
        return myProvider.getId();
    }

    @Override
    protected CompletableFuture<?> startDebuggee(DAP dap) {
        return dap.request("launch", myProvider.createLaunchArguments(myTarget), Object.class);
    }

    @Override
    protected DAPValuePresentation createPresentation() {
        return new NativeDapValuePresentation(this);
    }

    Executor getNavigationExecutor() {
        return getExecutor();
    }

    @Override
    public XDebuggerEditorsProvider getEditorsProvider() {
        return NativeDebuggerEditorsProvider.INSTANCE;
    }

    @Override
    protected void stopImpl() {
        super.stopImpl();
        ProcessHandler handler = myAdapterHandler;
        if (handler != null && !handler.waitFor(ADAPTER_EXIT_TIMEOUT_MS)) {
            handler.destroyProcess();
        }
    }

    @Override
    public String getDebuggerName() {
        return myProvider.getDisplayName().get();
    }

    @Override
    public Set<NativeDebuggerCapability> getCapabilities() {
        Capabilities capabilities = getAdapterCapabilities();
        Set<NativeDebuggerCapability> result = EnumSet.of(NativeDebuggerCapability.PAUSE,
            NativeDebuggerCapability.RUN_TO_LOCATION,
            NativeDebuggerCapability.CONSOLE_COMMANDS);
        if (Boolean.TRUE.equals(capabilities.supportsConditionalBreakpoints)) {
            result.add(NativeDebuggerCapability.CONDITIONAL_BREAKPOINTS);
        }
        if (Boolean.TRUE.equals(capabilities.supportsHitConditionalBreakpoints)) {
            result.add(NativeDebuggerCapability.HIT_COUNT_BREAKPOINTS);
        }
        if (Boolean.TRUE.equals(capabilities.supportsFunctionBreakpoints)) {
            result.add(NativeDebuggerCapability.FUNCTION_BREAKPOINTS);
        }
        if (Boolean.TRUE.equals(capabilities.supportsInstructionBreakpoints)) {
            result.add(NativeDebuggerCapability.ADDRESS_BREAKPOINTS);
        }
        if (Boolean.TRUE.equals(capabilities.supportsDataBreakpoints)) {
            result.add(NativeDebuggerCapability.WATCHPOINTS);
        }
        if (Boolean.TRUE.equals(capabilities.supportsSteppingGranularity)) {
            result.add(NativeDebuggerCapability.INSTRUCTION_STEPPING);
        }
        if (Boolean.TRUE.equals(capabilities.supportsDisassembleRequest)) {
            result.add(NativeDebuggerCapability.DISASSEMBLE);
        }
        if (Boolean.TRUE.equals(capabilities.supportsReadMemoryRequest)) {
            result.add(NativeDebuggerCapability.READ_MEMORY);
        }
        return result;
    }

    @Override
    public CompletableFuture<String> executeCommand(String command) {
        Integer frameId = null;
        XStackFrame frame = getSession().getCurrentStackFrame();
        if (frame instanceof DAPStackFrame dapFrame) {
            frameId = dapFrame.getFrameId();
        }
        return getDAP().evaluate(new EvaluateArguments(command, frameId, EvaluateContext.REPL)).thenApply(result -> result.result);
    }

    @Override
    public CompletableFuture<List<NativeInstruction>> disassemble(long start, long end) {
        if (!getCapabilities().contains(NativeDebuggerCapability.DISASSEMBLE)) {
            return CompletableFuture.failedFuture(new NativeDebuggerException(NativeDevLocalize.nativeDebuggerDoesNotSupportDisassembly(getDebuggerName()).get()));
        }
        int count = (int) Math.max(1, Math.min(4096, (end - start) / 2));
        DisassembleArguments arguments = new DisassembleArguments(hex(start), count);
        arguments.resolveSymbols = true;
        return getDAP().disassemble(arguments).thenApply(result -> {
            List<NativeInstruction> instructions = new ArrayList<>();
            if (result.instructions == null) {
                return instructions;
            }
            for (DisassembledInstruction instruction : result.instructions) {
                long address = parseAddress(instruction.address);
                if (address >= end) {
                    break;
                }
                instructions.add(new NativeInstruction(address,
                    instruction.symbol,
                    0,
                    instruction.instructionBytes == null ? "" : instruction.instructionBytes,
                    instruction.instruction == null ? "" : instruction.instruction,
                    instruction.location == null ? null : instruction.location.path,
                    instruction.line == null ? 0 : instruction.line));
            }
            return instructions;
        });
    }

    @Override
    public CompletableFuture<List<NativeMemoryBlock>> readMemory(long address, int length) {
        if (!getCapabilities().contains(NativeDebuggerCapability.READ_MEMORY)) {
            return CompletableFuture.failedFuture(new NativeDebuggerException(NativeDevLocalize.nativeDebuggerDoesNotSupportMemory(getDebuggerName()).get()));
        }
        return getDAP().readMemory(new ReadMemoryArguments(hex(address), length)).thenApply(result -> {
            if (result.data == null) {
                return List.of();
            }
            return List.of(new NativeMemoryBlock(parseAddress(result.address), Base64.getDecoder().decode(result.data)));
        });
    }

    private static String hex(long address) {
        return "0x" + Long.toHexString(address);
    }

    private static long parseAddress(@Nullable String address) {
        if (address == null) {
            return 0;
        }
        String text = address.startsWith("0x") || address.startsWith("0X") ? address.substring(2) : address;
        try {
            return Long.parseUnsignedLong(text, 16);
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }
}
