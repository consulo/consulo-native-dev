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
package consulo.nativeDev.perf.internal;

import consulo.execution.profiler.BaseCallStackElement;
import consulo.execution.profiler.NewCallTreeOnlyProfilerData;
import consulo.execution.profiler.ProfilerDumpFileParsingResult;
import consulo.execution.profiler.Stack;
import consulo.execution.profiler.Success;
import consulo.execution.profiler.model.NativeCall;
import consulo.execution.profiler.model.NativeThread;
import consulo.nativeDev.profiler.NavigatableNativeCall;
import org.jspecify.annotations.NullUnmarked;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@NullUnmarked
public class PerfScriptProfilerParserTest {
    @Test
    public void testRustDwarfRecording() throws Exception {
        List<Stack<BaseCallStackElement>> stacks = parse("rust-dwarf.script");
        assertEquals(3, stacks.size());

        Stack<BaseCallStackElement> first = stacks.get(0);
        assertThread(first, 41234, "rdemo-41234");
        assertEquals(4, first.getFrames().size());
        assertCall(first.getFrames().get(0), "libc.so.6", "", "__libc_start_call_main");
        assertCall(first.getFrames().get(1), "rdemo", "core::ops::function::FnOnce", "call_once");
        assertCall(first.getFrames().get(2), "rdemo", "rdemo", "main");
        assertCall(first.getFrames().get(3), "rdemo", "rdemo", "square");
        assertEquals("rdemo`rdemo::square", first.getFrames().get(3).fullName());
        assertTrue(first.getFrames().get(3) instanceof NavigatableNativeCall);
        assertEquals(1, first.getValue());

        Stack<BaseCallStackElement> second = stacks.get(1);
        assertSame(first.getFrames().get(3), second.getFrames().get(3));
        assertSame(first.getThread(), second.getThread());

        Stack<BaseCallStackElement> third = stacks.get(2);
        assertThread(third, 41240, "tokio-runtime-w-41240");
        assertCall(third.getFrames().get(1), "rdemo", "<alloc::vec::Vec<T> as core::ops::drop::Drop>", "drop");
    }

    @Test
    public void testHeadersFramesAndMissingTrailingBlankLine() throws Exception {
        List<Stack<BaseCallStackElement>> stacks = parse("mixed-no-trailing-blank.script");
        assertEquals(2, stacks.size());

        Stack<BaseCallStackElement> kernel = stacks.get(0);
        assertThread(kernel, 4243, "Web Content-4243");
        assertEquals(4, kernel.getFrames().size());
        assertCall(kernel.getFrames().get(0), "", "", "square");
        assertCall(kernel.getFrames().get(1), "a.out", "std::vector<int, std::allocator<int> >", "push_back(int const&)");
        assertCall(kernel.getFrames().get(2), "[unknown]", "", "[unknown]");
        assertCall(kernel.getFrames().get(3), "[kernel.kallsyms]", "", "native_write_msr");
        assertTrue(kernel.getFrames().get(0) instanceof NavigatableNativeCall);
        assertTrue(kernel.getFrames().get(1) instanceof NavigatableNativeCall);
        assertFalse(kernel.getFrames().get(2) instanceof NavigatableNativeCall);
        assertFalse(kernel.getFrames().get(3) instanceof NavigatableNativeCall);

        Stack<BaseCallStackElement> java = stacks.get(1);
        assertThread(java, 88707, "java-88707");
        assertCall(java.getFrames().get(0), "perf-88428.map", "", "Interpreter+0x1234 7f00aa");
        assertCall(java.getFrames().get(1), "", "", "FileBasedIndexImpl$$Lambda$4423/0x0000000801d35368.run");
        assertFalse(java.getFrames().get(0) instanceof NavigatableNativeCall);
        assertFalse(java.getFrames().get(1) instanceof NavigatableNativeCall);
    }

    @Test
    public void testMangledFramesAreDemangled() {
        PerfScriptProfilerParser parser = new PerfScriptProfilerParser(
            symbol -> symbol.startsWith("_ZN5rdemo6square") ? "rdemo::square" : symbol
        );
        parser.consumeLine("           rdemo 41234 12345.678901:    1001001 cpu-clock:u: ");
        parser.consumeLine("\t    55d4c3a1b2c3 _ZN5rdemo6square17h0123456789abcdefE+0x13 (/home/user/rdemo/target/release/rdemo)");
        parser.consumeLine("\t    55d4c3a1b3d4 main+0x44 (/home/user/rdemo/target/release/rdemo)");
        parser.consumeLine("");
        ProfilerDumpFileParsingResult result = parser.createResult();
        assertTrue(result instanceof Success);
        Stack<BaseCallStackElement> stack = ((NewCallTreeOnlyProfilerData) ((Success) result).getData()).getBuilder().getAllStacks()
            .iterator().next();
        assertCall(stack.getFrames().get(0), "rdemo", "", "main");
        assertCall(stack.getFrames().get(1), "rdemo", "rdemo", "square");
    }

    @Test
    public void testBadLinesAreCountedAndKept() {
        PerfScriptProfilerParser parser = new PerfScriptProfilerParser();
        parser.consumeLine("failed to open perf.data: No such file or directory");
        parser.consumeLine("\t    55d4c3a1b2c3 rdemo::square+0x13 (/tmp/rdemo)");
        assertEquals(2, parser.getBadLines());
        assertEquals("failed to open perf.data: No such file or directory", parser.getBadLineSamples().get(0));
    }

    private static List<Stack<BaseCallStackElement>> parse(String fixture) throws URISyntaxException, IOException {
        URL resource = PerfScriptProfilerParserTest.class.getResource("/perf/" + fixture);
        assertNotNull(fixture, resource);
        PerfScriptProfilerParser parser = new PerfScriptProfilerParser();
        Files.readAllLines(Path.of(resource.toURI())).forEach(parser::consumeLine);
        ProfilerDumpFileParsingResult result = parser.createResult();
        assertTrue(String.valueOf(result), result instanceof Success);
        NewCallTreeOnlyProfilerData data = (NewCallTreeOnlyProfilerData) ((Success) result).getData();
        List<Stack<BaseCallStackElement>> stacks = new ArrayList<>();
        data.getBuilder().getAllStacks().forEach(stacks::add);
        return stacks;
    }

    private static void assertThread(Stack<BaseCallStackElement> stack, long id, String name) {
        NativeThread thread = (NativeThread) stack.getThread();
        assertEquals(id, thread.getId());
        assertEquals(name, thread.getName());
    }

    private static void assertCall(BaseCallStackElement frame, String library, String className, String method) {
        assertTrue(frame.getClass() + ": " + frame.fullName(), frame instanceof NativeCall);
        NativeCall call = (NativeCall) frame;
        assertEquals(library, call.getLibrary());
        assertEquals(className, call.getClassName());
        assertEquals(method, call.getMethodOrFunction());
    }
}
