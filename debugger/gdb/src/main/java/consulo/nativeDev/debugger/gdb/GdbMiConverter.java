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
import consulo.nativeDev.debugger.driver.NativeBreakpointLocation;
import consulo.nativeDev.debugger.driver.NativeBreakpointRequest;
import consulo.nativeDev.debugger.driver.NativeFrame;
import consulo.nativeDev.debugger.driver.NativeInstruction;
import consulo.nativeDev.debugger.driver.NativeMemoryBlock;
import consulo.nativeDev.debugger.driver.NativeThread;
import consulo.nativeDev.debugger.mi.MIResult;
import consulo.nativeDev.debugger.mi.MITList;
import consulo.nativeDev.debugger.mi.MITListItem;
import consulo.nativeDev.debugger.mi.MIValue;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public final class GdbMiConverter {
    private GdbMiConverter() {
    }

    public static @Nullable String string(@Nullable MITList tuple, String name) {
        if (tuple == null) {
            return null;
        }
        MIValue value = tuple.valueOf(name);
        if (value == null || !value.isConst()) {
            return null;
        }
        return unescape(value.asConst().value());
    }

    public static String stream(String text) {
        return unescape(text);
    }

    public static String unescape(String value) {
        if (value.indexOf('\\') < 0) {
            return value;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());
        int i = 0;
        while (i < value.length()) {
            int c = value.codePointAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                writeUtf8(bytes, c);
                i += Character.charCount(c);
                continue;
            }

            char next = value.charAt(i + 1);
            if (next >= '0' && next <= '7') {
                int end = i + 1;
                int code = 0;
                while (end < value.length() && end < i + 4 && value.charAt(end) >= '0' && value.charAt(end) <= '7') {
                    code = code * 8 + (value.charAt(end) - '0');
                    end++;
                }
                bytes.write(code);
                i = end;
                continue;
            }

            writeUtf8(bytes, switch (next) {
                case 'n' -> '\n';
                case 't' -> '\t';
                case 'r' -> '\r';
                case 'a' -> '\u0007';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'v' -> '\u000B';
                case 'e' -> '\u001B';
                default -> next;
            });
            i += 2;
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    public static int integer(@Nullable MITList tuple, String name, int defaultValue) {
        String value = string(tuple, name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static long address(@Nullable String value) {
        if (value == null) {
            return 0;
        }
        String text = value.trim();
        int space = text.indexOf(' ');
        if (space > 0) {
            text = text.substring(0, space);
        }
        if (!text.startsWith("0x") && !text.startsWith("0X")) {
            return 0;
        }
        try {
            return Long.parseUnsignedLong(text.substring(2), 16);
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String hex(long address) {
        return "0x" + Long.toHexString(address);
    }

    public static @Nullable MITList tuple(@Nullable MITList tuple, String name) {
        if (tuple == null) {
            return null;
        }
        MIValue value = tuple.valueOf(name);
        return value != null && value.isTList() ? value.asTuple() : null;
    }

    public static List<MITList> tuples(@Nullable MIValue list) {
        if (list == null || !list.isTList()) {
            return List.of();
        }
        List<MITList> result = new ArrayList<>();
        for (MITListItem item : list.asList()) {
            MIValue value = item instanceof MIResult itemResult ? itemResult.value() : (MIValue) item;
            if (value.isTList()) {
                result.add(value.asTuple());
            }
        }
        return result;
    }

    public static List<String> strings(@Nullable MIValue list) {
        if (list == null || !list.isTList()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (MITListItem item : list.asList()) {
            MIValue value = item instanceof MIResult itemResult ? itemResult.value() : (MIValue) item;
            result.add(value.isConst() ? unescape(value.asConst().value()) : "");
        }
        return result;
    }

    public static NativeFrame frame(String threadId, MITList frame) {
        return new NativeFrame(threadId,
            integer(frame, "level", 0),
            address(string(frame, "addr")),
            string(frame, "func"),
            sourceFile(frame),
            integer(frame, "line", 0),
            string(frame, "from"));
    }

    public static NativeThread thread(MITList thread) {
        String id = string(thread, "id");
        String threadId = id == null ? "" : id;
        MITList frame = tuple(thread, "frame");
        return new NativeThread(threadId,
            string(thread, "name"),
            string(thread, "target-id"),
            !"running".equals(string(thread, "state")),
            frame == null ? null : frame(threadId, frame));
    }

    public static NativeBreakpoint breakpoint(NativeBreakpointRequest request, MITList results, String resultName) {
        List<MITList> tuples = new ArrayList<>();
        for (MITListItem item : results) {
            if (item instanceof MIResult result && result.matches(resultName) && result.value().isTList()) {
                tuples.add(result.value().asTuple());
            }
        }
        if (tuples.isEmpty()) {
            throw new IllegalArgumentException("No " + resultName + " in " + results);
        }

        MITList main = tuples.get(0);
        String number = string(main, "number");
        String id = number == null ? "" : number;
        String addr = string(main, "addr");
        boolean pending = string(main, "pending") != null || "<PENDING>".equals(addr);

        List<NativeBreakpointLocation> locations = new ArrayList<>();
        MIValue mi3Locations = main.valueOf("locations");
        if (mi3Locations != null) {
            for (MITList location : tuples(mi3Locations)) {
                locations.add(location(location, id));
            }
        }
        else if (tuples.size() > 1) {
            for (MITList location : tuples.subList(1, tuples.size())) {
                locations.add(location(location, id));
            }
        }
        else if (!pending && !"<MULTIPLE>".equals(addr)) {
            locations.add(location(main, id));
        }

        return new NativeBreakpoint(id, request, pending, List.copyOf(locations), integer(main, "times", 0));
    }

    public static List<NativeInstruction> instructions(MITList results) {
        List<NativeInstruction> instructions = new ArrayList<>();
        MIValue asm = results.valueOf("asm_insns");
        if (asm == null || !asm.isTList()) {
            return instructions;
        }
        for (MITListItem item : asm.asList()) {
            if (item instanceof MIResult result && result.matches("src_and_asm_line") && result.value().isTList()) {
                MITList line = result.value().asTuple();
                String file = sourceFile(line);
                int lineNumber = integer(line, "line", 0);
                for (MITList instruction : tuples(line.valueOf("line_asm_insn"))) {
                    instructions.add(instruction(instruction, file, lineNumber));
                }
            }
            else {
                MIValue value = item instanceof MIResult result ? result.value() : (MIValue) item;
                if (value.isTList()) {
                    instructions.add(instruction(value.asTuple(), null, 0));
                }
            }
        }
        return instructions;
    }

    public static List<NativeMemoryBlock> memory(MITList results) {
        List<NativeMemoryBlock> blocks = new ArrayList<>();
        for (MITList block : tuples(results.valueOf("memory"))) {
            String contents = string(block, "contents");
            if (contents == null) {
                continue;
            }
            long begin = address(string(block, "begin"));
            long offset = address(string(block, "offset"));
            blocks.add(new NativeMemoryBlock(begin + offset, HexFormat.of().parseHex(contents)));
        }
        return blocks;
    }

    private static NativeInstruction instruction(MITList instruction, @Nullable String file, int line) {
        String opcodes = string(instruction, "opcodes");
        String text = string(instruction, "inst");
        return new NativeInstruction(address(string(instruction, "address")),
            string(instruction, "func-name"),
            integer(instruction, "offset", 0),
            opcodes == null ? "" : opcodes,
            text == null ? "" : text,
            file,
            line);
    }

    private static NativeBreakpointLocation location(MITList location, String breakpointId) {
        String number = string(location, "number");
        return new NativeBreakpointLocation(number == null ? breakpointId : number,
            address(string(location, "addr")),
            string(location, "func"),
            sourceFile(location),
            integer(location, "line", 0),
            !"n".equals(string(location, "enabled")));
    }

    private static void writeUtf8(ByteArrayOutputStream bytes, int codePoint) {
        if (codePoint < 0x80) {
            bytes.write(codePoint);
        }
        else {
            bytes.writeBytes(Character.toString(codePoint).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static @Nullable String sourceFile(MITList tuple) {
        String fullName = string(tuple, "fullname");
        return fullName != null ? fullName : string(tuple, "file");
    }
}
