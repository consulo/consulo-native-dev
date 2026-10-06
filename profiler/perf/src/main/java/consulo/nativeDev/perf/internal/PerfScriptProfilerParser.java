/*
 * Copyright 2023 Alexandr Evstigneev
 * Modified by consulo.io
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

import consulo.application.progress.ProgressIndicator;
import consulo.execution.profiler.BaseCallStackElement;
import consulo.execution.profiler.DummyCallTreeBuilder;
import consulo.execution.profiler.Failure;
import consulo.execution.profiler.LineByLineParser;
import consulo.execution.profiler.NewCallTreeOnlyProfilerData;
import consulo.execution.profiler.ProfilerDumpFileParser;
import consulo.execution.profiler.ProfilerDumpFileParsingResult;
import consulo.execution.profiler.Success;
import consulo.execution.profiler.model.CantBeParsedCall;
import consulo.execution.profiler.model.NativeCall;
import consulo.execution.profiler.model.NativeThread;
import consulo.execution.profiler.model.ThreadInfo;
import consulo.execution.profiler.ui.NativeCallStackElementRenderer;
import consulo.logging.Logger;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.nativeDev.profiler.NavigatableNativeCall;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for the output of `perf script -i intput.data`.
 * To create a script following command sequence should be run:
 * <pre>
 * # Start java process with -XX:+PreserveFramePointer -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints
 *
 * # sample the app
 * # Following samples with 500 hz, $PID or $TID for 60 seconds, recording output to the perf.data
 * sudo perf record -F 500 -p $PID [-t $TID] -g -o perf.data -- sleep 60
 *
 * # create a map for java calls and perf addresses, JIT stuff.  *BEFORE finishing the process*. Alternatively run process with -XX:+DumpPerfMapAtExit
 * jcmd $PID Compiler.perfmap
 *
 * # create plain text report with
 * perf script -i perf.data > perf.script
 * </pre>
 */
public class PerfScriptProfilerParser extends LineByLineParser implements ProfilerDumpFileParser {
    private static final Logger LOG = Logger.getInstance(PerfScriptProfilerParser.class);

    private static final int KEPT_BAD_LINES = 5;

    /**
     * Captures: address, name and file parts.
     * <pre>
     * f1918c0ec04 void com.intellij.util.indexing.FileBasedIndexImpl$$Lambda$4423/0x0000000801d35368.run()+0xc4 (/tmp/perf-88428.map)
     * </pre>
     */
    private static final Pattern STACK_LINE_PATTERN = Pattern.compile("^\\s+(\\S+)\\s+(.+)\\s+\\(([^)]+)\\)$");

    private static final Pattern INLINED_FRAME_PATTERN = Pattern.compile("^(.+)\\s+\\(inlined\\)$");

    /**
     * Captures class and method name parts from.
     * <pre>
     * com.intellij.psi.tree.IElementType org.jetbrains.plugins.ruby.ruby.lang.parser.parsing.controlStructures.Case.parse(org.jetbrains.plugins.ruby.ruby.lang.parser.parsingUtils.RBuilder)+0x202c
     * </pre>
     */
    private static final Pattern JAVA_FRAME_PATTERN = Pattern.compile("^(?:[^.]+\\.)*([^.]+\\.[^.]+)\\(");

    private static final Pattern HEADER_PATTERN =
        Pattern.compile("^\\s*(\\S.*?)\\s+(?:\\d+/)?(\\d+)\\s+(?:\\[\\d+]\\s+)?(\\d+(?:\\.\\d+)?):");

    /**
     * These are frames where mappings are missing from the JVM
     */
    private static final String UNKNOWN_FRAME_PREFIX = "Interpreter+0x";

    private static final String MAPPING_FILE_SUFFIX = ".map";

    private final Function<String, String> myDemangler;
    private final DummyCallTreeBuilder<BaseCallStackElement> myCallTreeBuilder = new DummyCallTreeBuilder<>();
    private final Map<String, String> myStringInterner = new HashMap<>();
    private final Map<Long, ThreadInfo> myThreadInfoInterner = new HashMap<>();
    private final Map<String, BaseCallStackElement> myLineInterner = new HashMap<>();
    private final Map<BaseCallStackElement, BaseCallStackElement> myFrameInterner = new HashMap<>();
    private final List<String> myBadLineSamples = new ArrayList<>();

    private @Nullable PerfThreadState myCurrentThreadState;
    private long myStackCount;

    public PerfScriptProfilerParser() {
        this(Function.identity());
    }

    public PerfScriptProfilerParser(Function<String, String> demangler) {
        myDemangler = demangler;
    }

    @Override
    public ProfilerDumpFileParsingResult parse(File file, ProgressIndicator indicator) {
        try (InputStream stream = Files.newInputStream(file.toPath())) {
            readFromStream(stream, indicator);
        }
        catch (IOException e) {
            LOG.warn("Can't read perf script output " + file, e);
            return new Failure(e.getMessage());
        }
        return createResult();
    }

    public ProfilerDumpFileParsingResult createResult() {
        flush();
        endStack();
        if (myStackCount == 0) {
            if (getBadLines() == 0) {
                return new Failure(NativeDevLocalize.nativeProfilerPerfNoSamples().get());
            }
            String reason = NativeDevLocalize.nativeProfilerPerfBadLines(getBadLines()).get();
            return new Failure(reason + "\n" + String.join("\n", myBadLineSamples));
        }
        return new Success(new NewCallTreeOnlyProfilerData(myCallTreeBuilder, NativeCallStackElementRenderer.INSTANCE));
    }

    public List<String> getBadLineSamples() {
        return Collections.unmodifiableList(myBadLineSamples);
    }

    @Override
    public void consumeLine(String line) {
        if (line.isEmpty()) {
            // end of stacktrace
            endStack();
            return;
        }

        PerfThreadState threadState = myCurrentThreadState;
        if (threadState == null) {
            // beginning of the new stacktrace
            computeThread(line);
            return;
        }

        // stack frame
        BaseCallStackElement frame = myLineInterner.get(line);
        if (frame == null) {
            frame = computeFrame(line);
            myLineInterner.put(line, frame);
        }
        threadState.myStack.add(frame);
    }

    private void endStack() {
        PerfThreadState threadState = myCurrentThreadState;
        if (threadState == null) {
            return;
        }
        List<BaseCallStackElement> rootFirst = new ArrayList<>(threadState.myStack);
        Collections.reverse(rootFirst);
        myCallTreeBuilder.addStack(threadState.myThreadInfo, rootFirst, 1);
        myStackCount++;
        myCurrentThreadState = null;
    }

    /**
     * Attempts to compute a single stack element from the perf output line.
     * Currently supported:
     * <pre>
     * f1918c0ec04 void com.intellij.util.indexing.FileBasedIndexImpl$$Lambda$4423/0x0000000801d35368.run()+0xc4 (/tmp/perf-88428.map)
     * </pre>
     */
    private BaseCallStackElement computeFrame(String line) {
        Matcher matcher = STACK_LINE_PATTERN.matcher(line);
        if (!matcher.matches()) {
            String trimmedLine = intern(line.trim());
            Matcher inlined = INLINED_FRAME_PATTERN.matcher(trimmedLine);
            if (inlined.matches()) {
                NativeCall call = NativeCall.read(inlined.group(1));
                if (call != null) {
                    return nativeFrame("", call, true);
                }
            }
            return internFrame(new CantBeParsedCall(trimmedLine));
        }
        String address = matcher.group(1);
        String symbol = matcher.group(2);
        String file = matcher.group(3);

        boolean isUnknownFile = symbol.startsWith(UNKNOWN_FRAME_PREFIX);
        boolean isMappingFile = file.endsWith(MAPPING_FILE_SUFFIX);
        String library = isUnknownFile || !isMappingFile ? intern(libraryName(file)) : "";

        if (isUnknownFile) {
            return internFrame(new NativeCall(library, "", intern(symbol + " " + address)));
        }

        String frameName = symbol;
        if (isMappingFile) {
            Matcher javaFrame = JAVA_FRAME_PATTERN.matcher(symbol);
            if (javaFrame.lookingAt()) {
                frameName = javaFrame.group(1);
            }
        }

        NativeCall call = NativeCall.read(frameName);
        if (call == null) {
            return internFrame(new CantBeParsedCall(intern(line.trim())));
        }
        return nativeFrame(library, call, !isMappingFile && !library.startsWith("["));
    }

    private BaseCallStackElement nativeFrame(String library, NativeCall call, boolean navigatable) {
        NativeCall symbol = call;
        if (navigatable && symbol.getClassName().isEmpty()) {
            String demangled = myDemangler.apply(symbol.getMethodOrFunction());
            NativeCall demangledCall = demangled.equals(symbol.getMethodOrFunction()) ? null : NativeCall.read(demangled);
            if (demangledCall != null) {
                symbol = demangledCall;
            }
        }
        String className = intern(symbol.getClassName());
        String method = intern(symbol.getMethodOrFunction());
        if (navigatable) {
            return internFrame(new NavigatableNativeCall(library, className, method));
        }
        return internFrame(new NativeCall(library, className, method));
    }

    /**
     * Accepts following headers
     * <pre>
     * Indexing   88428/88707   14182.263881:         16 cycles:P:
     * Indexing   88707   14182.263881:         16 cycles:P:
     * </pre>
     */
    private void computeThread(String line) {
        if (line.startsWith("\t")) {
            // bad line from bad stackframe
            badLine("Skipping line (starts with tab): ", line);
            return;
        }
        Matcher matcher = HEADER_PATTERN.matcher(line);
        if (!matcher.lookingAt()) {
            badLine("Stack header expected to have at least 3 fields: Name, Pid/Tid and time: ", line);
            return;
        }

        String threadName = matcher.group(1);
        long tid;
        long time;
        try {
            tid = Long.parseLong(matcher.group(2));
            String textTime = matcher.group(3);
            time = textTime.contains(".") ? (long) (Double.parseDouble(textTime) * 1000000) : Long.parseLong(textTime);
        }
        catch (NumberFormatException e) {
            badLine("Unable to parse stack time: ", line);
            return;
        }

        ThreadInfo threadInfo = myThreadInfoInterner.computeIfAbsent(tid, id -> new NativeThread(id, intern(threadName + "-" + id)));
        myCurrentThreadState = new PerfThreadState(threadInfo, time);
    }

    private void badLine(String reason, String line) {
        LOG.debug(reason + line);
        setBadLines(getBadLines() + 1);
        if (myBadLineSamples.size() < KEPT_BAD_LINES) {
            myBadLineSamples.add(line);
        }
    }

    private static String libraryName(String file) {
        if (file.startsWith("[")) {
            return file;
        }
        int slash = file.lastIndexOf('/');
        return slash < 0 ? file : file.substring(slash + 1);
    }

    private String intern(String value) {
        String interned = myStringInterner.putIfAbsent(value, value);
        return interned == null ? value : interned;
    }

    private BaseCallStackElement internFrame(BaseCallStackElement frame) {
        BaseCallStackElement interned = myFrameInterner.putIfAbsent(frame, frame);
        return interned == null ? frame : interned;
    }

    private static final class PerfThreadState {
        private final ThreadInfo myThreadInfo;
        private final long myTime;
        private final List<BaseCallStackElement> myStack = new ArrayList<>();

        private PerfThreadState(ThreadInfo threadInfo, long time) {
            myThreadInfo = threadInfo;
            myTime = time;
        }
    }
}
