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

import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.nativeDev.debugger.driver.NativeBreakpoint;
import consulo.nativeDev.debugger.driver.NativeBreakpointKind;
import consulo.nativeDev.debugger.driver.NativeBreakpointRequest;
import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.nativeDev.debugger.driver.NativeDebuggerException;
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
import consulo.process.ProcessHandlerBuilderFactory;
import consulo.test.light.LightApplicationBuilder;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jspecify.annotations.NullUnmarked;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@NullUnmarked
public class GdbMiDriverTest {
    private static final long TIMEOUT = 30;

    private static Path ourSource;
    private static Path ourExecutable;
    private static final Map<String, Integer> ourLines = new HashMap<>();

    private final BlockingQueue<Object> myEvents = new LinkedBlockingQueue<>();
    private ExecutorService myExecutor;
    private Disposable myRootDisposable;
    private ProcessHandlerBuilderFactory myProcessHandlerBuilderFactory;
    private GdbMiDriver myDriver;

    @BeforeClass
    public static void compile() throws Exception {
        Assume.assumeTrue("gdb is not installed", runs("gdb", "--version"));
        Assume.assumeTrue("gcc is not installed", runs("gcc", "--version"));

        Path directory = Files.createTempDirectory("gdb-mi-driver");
        ourSource = directory.resolve("sample.c");
        try (InputStream stream = GdbMiDriverTest.class.getResourceAsStream("/sample.c")) {
            assertNotNull(stream);
            Files.copy(stream, ourSource);
        }
        ourExecutable = directory.resolve("sample");
        Process gcc = new ProcessBuilder("gcc", "-g", "-O0", "-pthread", "-o", ourExecutable.toString(), ourSource.toString())
            .inheritIO()
            .start();
        assertEquals(0, gcc.waitFor());

        List<String> lines = Files.readAllLines(ourSource);
        Pattern marker = Pattern.compile("/\\* ([A-Z_]+) \\*/");
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = marker.matcher(lines.get(i));
            if (matcher.find()) {
                ourLines.put(matcher.group(1), i + 1);
            }
        }
    }

    @Before
    public void setUp() {
        myExecutor = Executors.newSingleThreadExecutor();
        myRootDisposable = Disposable.newDisposable("GdbMiDriverTest");
        Application application = LightApplicationBuilder.create(myRootDisposable).build();
        myProcessHandlerBuilderFactory = application.getInstance(ProcessHandlerBuilderFactory.class);
    }

    @After
    public void tearDown() throws Exception {
        if (myDriver != null) {
            myDriver.terminate().get(TIMEOUT, TimeUnit.SECONDS);
        }
        myExecutor.shutdownNow();
        Disposer.dispose(myRootDisposable);
    }

    @Test
    public void lineBreakpointStopsWithFramesAndVariables() throws Exception {
        start(List.of());
        NativeBreakpoint breakpoint = get(myDriver.insertBreakpoint(line("LOOP")));
        assertFalse(breakpoint.pending());
        assertEquals(1, breakpoint.locations().size());
        assertEquals(line("LOOP").line(), breakpoint.locations().get(0).line());

        get(myDriver.run());
        NativeStopEvent stop = awaitStop();
        assertEquals(NativeStopReason.BREAKPOINT, stop.reason());
        assertEquals(List.of(breakpoint.id()), stop.breakpointIds());
        NativeFrame frame = stop.frame();
        assertNotNull(frame);
        assertEquals("main", frame.function());
        assertEquals(line("LOOP").line(), frame.line());
        assertTrue(String.valueOf(frame.file()), String.valueOf(frame.file()).endsWith("sample.c"));

        List<NativeFrame> frames = get(myDriver.getFrames(stop.threadId(), 0, 20));
        assertEquals("main", frames.get(0).function());

        Map<String, NativeVariable> variables = byName(get(myDriver.getVariables(frame)));
        assertTrue(variables.keySet().toString(), variables.keySet().containsAll(List.of("argc", "argv", "p", "values", "i")));
        assertEquals("1", variables.get("argc").value());

        NativeVariable point = variables.get("p");
        assertTrue(point.hasChildren());
        assertEquals("struct point", point.type());
        Map<String, NativeVariable> fields = byName(get(myDriver.getChildren(point, 0, 100)));
        assertEquals("3", fields.get("x").value());
        assertEquals("4", fields.get("y").value());

        List<NativeVariable> elements = get(myDriver.getChildren(variables.get("values"), 0, 100));
        assertEquals(List.of("1", "2", "3"), elements.stream().map(NativeVariable::value).toList());

        assertEquals("25", get(myDriver.evaluate(frame, "square(5)", null)).value());
        assertEquals("7", get(myDriver.evaluate(frame, "p.x + p.y", null)).value());
        try {
            get(myDriver.evaluate(frame, "no_such_variable", null));
            fail();
        }
        catch (NativeDebuggerException e) {
            assertEquals("No symbol \"no_such_variable\" in current context.", e.getMessage());
        }
    }

    @Test
    public void evaluationLanguage() throws Exception {
        start(List.of());
        get(myDriver.insertBreakpoint(line("LOOP")));
        get(myDriver.run());
        NativeFrame frame = awaitStop().frame();

        assertEquals("true", get(myDriver.evaluate(frame, "2 = 2", "pascal")).value());
        assertEquals("1", get(myDriver.evaluate(frame, "2 == 2", null)).value());
        assertTrue(get(myDriver.executeCommand("show language")).contains("auto"));
    }

    @Test
    public void stepping() throws Exception {
        start(List.of());
        get(myDriver.insertBreakpoint(line("LOOP")));
        get(myDriver.run());
        NativeStopEvent stop = awaitStop();

        get(myDriver.step(stop.threadId(), NativeStepKind.INTO));
        NativeStopEvent into = awaitStop();
        assertEquals(NativeStopReason.STEP, into.reason());
        assertEquals("square", into.frame().function());

        get(myDriver.step(into.threadId(), NativeStepKind.OVER));
        NativeStopEvent over = awaitStop();
        assertEquals(NativeStopReason.STEP, over.reason());
        assertEquals("square", over.frame().function());
        assertEquals(line("SQUARE").line() + 1, over.frame().line());

        get(myDriver.step(over.threadId(), NativeStepKind.OUT));
        NativeStopEvent out = awaitStop();
        assertEquals(NativeStopReason.STEP, out.reason());
        assertEquals("main", out.frame().function());

        get(myDriver.step(out.threadId(), NativeStepKind.INSTRUCTION_INTO));
        NativeStopEvent instruction = awaitStop();
        assertEquals(NativeStopReason.STEP, instruction.reason());
        assertNotEquals(out.frame().address(), instruction.frame().address());
    }

    @Test
    public void conditionalFunctionBreakpoint() throws Exception {
        start(List.of());
        NativeBreakpoint breakpoint = get(myDriver.insertBreakpoint(NativeBreakpointRequest.function("square").withCondition("value == 3")));
        get(myDriver.run());
        NativeStopEvent stop = awaitStop();
        assertEquals(NativeStopReason.BREAKPOINT, stop.reason());
        assertEquals(List.of(breakpoint.id()), stop.breakpointIds());
        assertEquals("3", get(myDriver.evaluate(stop.frame(), "value", null)).value());
    }

    @Test
    public void ignoreCountAndDisabledBreakpoints() throws Exception {
        start(List.of());
        get(myDriver.insertBreakpoint(line("AFTER_LOOP").withEnabled(false)));
        NativeBreakpoint loop = get(myDriver.insertBreakpoint(line("LOOP").withIgnoreCount(2)));
        get(myDriver.run());
        NativeStopEvent stop = awaitStop();
        assertEquals(List.of(loop.id()), stop.breakpointIds());
        assertEquals("2", get(myDriver.evaluate(stop.frame(), "i", null)).value());

        get(myDriver.removeBreakpoint(loop));
        get(myDriver.resume());
        assertEquals(Integer.valueOf(14), awaitExit().exitCode());
    }

    @Test
    public void exitCode() throws Exception {
        start(List.of());
        get(myDriver.run());
        assertEquals(Integer.valueOf(14), awaitExit().exitCode());
    }

    @Test
    public void programArguments() throws Exception {
        start(List.of("a b", "it's", "c"));
        get(myDriver.insertBreakpoint(line("LOOP")));
        get(myDriver.run());
        NativeFrame frame = awaitStop().frame();
        assertEquals("4", get(myDriver.evaluate(frame, "argc", null)).value());
        assertEndsWith("\"a b\"", get(myDriver.evaluate(frame, "argv[1]", null)).value());
        assertEndsWith("\"it's\"", get(myDriver.evaluate(frame, "argv[2]", null)).value());
    }

    @Test
    public void pauseInterruptsRunningProgram() throws Exception {
        start(List.of("spin"));
        get(myDriver.run());
        Thread.sleep(500);
        get(myDriver.pause());
        NativeStopEvent stop = awaitStop();
        assertEquals(NativeStopReason.PAUSE, stop.reason());

        List<NativeThread> threads = get(myDriver.getThreads());
        assertTrue(threads.toString(), threads.size() >= 2);
        assertTrue(threads.stream().allMatch(NativeThread::stopped));
    }

    @Test
    public void breakpointInsertedWhileRunning() throws Exception {
        start(List.of("spin"));
        get(myDriver.run());
        Thread.sleep(300);
        NativeBreakpoint breakpoint = get(myDriver.insertBreakpoint(line("WORKER")));
        NativeStopEvent stop = awaitStop();
        assertEquals(NativeStopReason.BREAKPOINT, stop.reason());
        assertEquals(List.of(breakpoint.id()), stop.breakpointIds());
        assertEquals("worker", stop.frame().function());
    }

    @Test
    public void runToLocation() throws Exception {
        start(List.of());
        NativeBreakpoint loop = get(myDriver.insertBreakpoint(line("LOOP")));
        get(myDriver.run());
        awaitStop();
        get(myDriver.removeBreakpoint(loop));

        get(myDriver.runToLocation(ourSource.toString(), line("AFTER_LOOP").line()));
        NativeStopEvent stop = awaitStop();
        assertEquals(NativeStopReason.LOCATION_REACHED, stop.reason());
        assertEquals(line("AFTER_LOOP").line(), stop.frame().line());
    }

    @Test
    public void watchpoint() throws Exception {
        start(List.of());
        get(myDriver.insertBreakpoint(line("LOOP")));
        get(myDriver.run());
        awaitStop();

        NativeBreakpoint watchpoint = get(myDriver.insertBreakpoint(NativeBreakpointRequest.watch(NativeBreakpointKind.WATCH_WRITE, "total")));
        get(myDriver.resume());
        NativeStopEvent stop = awaitStop();
        assertEquals(NativeStopReason.WATCHPOINT, stop.reason());
        assertEquals(List.of(watchpoint.id()), stop.breakpointIds());
    }

    @Test
    public void pendingBreakpoint() throws Exception {
        start(List.of());
        NativeBreakpoint breakpoint = get(myDriver.insertBreakpoint(NativeBreakpointRequest.function("no_such_function")));
        assertTrue(breakpoint.pending());
        assertTrue(breakpoint.locations().isEmpty());
    }

    @Test
    public void disassemblyMemoryRegistersAndConsole() throws Exception {
        start(List.of());
        get(myDriver.insertBreakpoint(line("AFTER_LOOP")));
        get(myDriver.run());
        NativeFrame frame = awaitStop().frame();
        assertNotEquals(0, frame.address());

        List<NativeInstruction> function = get(myDriver.disassembleFunction(frame.address()));
        assertTrue(function.stream().anyMatch(instruction -> instruction.address() == frame.address()
            && instruction.line() == line("AFTER_LOOP").line()
            && !instruction.bytes().isEmpty()
            && "main".equals(instruction.function())));
        List<NativeInstruction> range = get(myDriver.disassemble(frame.address(), frame.address() + 16));
        assertFalse(range.isEmpty());

        String pointer = get(myDriver.evaluate(frame, "&total", null)).value();
        Matcher matcher = Pattern.compile("0x[0-9a-f]+").matcher(pointer);
        assertTrue(pointer, matcher.find());
        List<NativeMemoryBlock> memory = get(myDriver.readMemory(GdbMiConverter.address(matcher.group()), 4));
        assertEquals(1, memory.size());
        assertEquals(14, memory.get(0).contents()[0]);

        List<NativeVariable> registers = get(myDriver.getRegisters(frame));
        assertFalse(registers.isEmpty());

        String line = get(myDriver.executeCommand("info line main"));
        assertTrue(line, line.startsWith("Line ") && line.contains("\"" + ourSource + "\"") && line.endsWith("\n"));
    }

    @Test
    public void programOutputWithoutTerminal() throws Exception {
        List<String> output = new CopyOnWriteArrayList<>();
        myDriver = new GdbMiDriver(myProcessHandlerBuilderFactory, Path.of("gdb"), List.of("-nx"), new NativeDebuggerListener() {
            @Override
            public void onExited(NativeExitEvent event) {
                myEvents.add(event);
            }

            @Override
            public void onOutput(NativeOutputKind kind, String text) {
                if (kind == NativeOutputKind.TARGET) {
                    output.add(text);
                }
            }
        }, myExecutor);
        get(myDriver.start(NativeDebugTarget.launch(Path.of("/bin/sh"), List.of("-c", "read line; echo \"read:[$line] it's\""), null, Map.of(), null)));
        get(myDriver.run());
        assertEquals(Integer.valueOf(0), awaitExit().exitCode());
        assertTrue(output.toString(), String.join("", output).contains("read:[] it's"));
    }

    private void start(List<String> arguments) throws Exception {
        myDriver = new GdbMiDriver(myProcessHandlerBuilderFactory, Path.of("gdb"), List.of("-nx"), new NativeDebuggerListener() {
            @Override
            public void onStopped(NativeStopEvent event) {
                myEvents.add(event);
            }

            @Override
            public void onExited(NativeExitEvent event) {
                myEvents.add(event);
            }
        }, myExecutor);
        get(myDriver.start(NativeDebugTarget.launch(ourExecutable, arguments, ourSource.getParent(), Map.of(), "/dev/null")));
        assertTrue(myDriver.getName(), myDriver.getName().contains("gdb"));
    }

    private NativeStopEvent awaitStop() throws InterruptedException {
        Object event = myEvents.poll(TIMEOUT, TimeUnit.SECONDS);
        if (event instanceof NativeStopEvent stop) {
            return stop;
        }
        throw new AssertionError("Expected a stop, got " + event);
    }

    private NativeExitEvent awaitExit() throws InterruptedException {
        Object event = myEvents.poll(TIMEOUT, TimeUnit.SECONDS);
        if (event instanceof NativeExitEvent exit) {
            return exit;
        }
        throw new AssertionError("Expected an exit, got " + event);
    }

    private static void assertEndsWith(String suffix, String value) {
        assertTrue(value, value.endsWith(suffix));
    }

    private static NativeBreakpointRequest line(String marker) {
        Integer line = ourLines.get(marker);
        assertNotNull(marker, line);
        return NativeBreakpointRequest.line(ourSource.toString(), line);
    }

    private static Map<String, NativeVariable> byName(List<NativeVariable> variables) {
        Map<String, NativeVariable> result = new HashMap<>();
        for (NativeVariable variable : variables) {
            result.putIfAbsent(variable.name(), variable);
        }
        return result;
    }

    private static <T> T get(CompletableFuture<T> future) throws Exception {
        try {
            return future.get(TIMEOUT, TimeUnit.SECONDS);
        }
        catch (ExecutionException e) {
            if (e.getCause() instanceof NativeDebuggerException debuggerException) {
                throw debuggerException;
            }
            throw e;
        }
    }

    private static boolean runs(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        }
        catch (IOException e) {
            return false;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
