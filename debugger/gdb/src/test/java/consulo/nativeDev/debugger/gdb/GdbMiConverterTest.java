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
import consulo.nativeDev.debugger.driver.NativeBreakpointRequest;
import consulo.nativeDev.debugger.driver.NativeInstruction;
import consulo.nativeDev.debugger.mi.MIParser;
import consulo.nativeDev.debugger.mi.MIRecord;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public class GdbMiConverterTest {
    private static final NativeBreakpointRequest REQUEST = NativeBreakpointRequest.function("square");

    @Test
    public void mi3MultipleLocations() {
        NativeBreakpoint breakpoint = GdbMiConverter.breakpoint(REQUEST, parse("^done,bkpt={number=\"1\",type=\"breakpoint\",disp=\"keep\","
            + "enabled=\"y\",addr=\"<MULTIPLE>\",times=\"0\",original-location=\"square\",locations=["
            + "{number=\"1.1\",enabled=\"y\",addr=\"0x000000000000104c\",func=\"square\",file=\"multi.c\",fullname=\"/work/multi.c\",line=\"3\"},"
            + "{number=\"1.2\",enabled=\"n\",addr=\"0x0000000000001059\",func=\"square\",file=\"multi.c\",fullname=\"/work/multi.c\",line=\"3\"}]}")
            .results(), "bkpt");

        assertEquals("1", breakpoint.id());
        assertEquals(2, breakpoint.locations().size());
        assertEquals("1.2", breakpoint.locations().get(1).id());
        assertEquals(0x1059, breakpoint.locations().get(1).address());
        assertEquals("/work/multi.c", breakpoint.locations().get(1).file());
        assertTrue(breakpoint.locations().get(0).enabled());
        assertFalse(breakpoint.locations().get(1).enabled());
    }

    @Test
    public void mi2MultipleLocations() {
        NativeBreakpoint breakpoint = GdbMiConverter.breakpoint(REQUEST, parse("15^done,bkpt={number=\"2\",type=\"breakpoint\","
            + "disp=\"keep\",enabled=\"y\",addr=\"<MULTIPLE>\",times=\"3\",original-location=\"Customer::Customer\"},"
            + "{number=\"2.1\",enabled=\"y\",addr=\"0x0000000000403efe\",func=\"Customer::Customer(Customer const&)\",file=\"customer.h\","
            + "fullname=\"/tmp/customer.h\",line=\"38\"},"
            + "{number=\"2.2\",enabled=\"y\",addr=\"0x0000000000404707\",func=\"Customer::Customer(std::string, int)\",file=\"customer.cc\","
            + "fullname=\"/tmp/customer.cc\",line=\"35\"}").results(), "bkpt");

        assertEquals("2", breakpoint.id());
        assertEquals(3, breakpoint.hitCount());
        assertEquals(List.of("2.1", "2.2"), breakpoint.locations().stream().map(location -> location.id()).toList());
        assertEquals(35, breakpoint.locations().get(1).line());
    }

    @Test
    public void pendingBreakpoint() {
        NativeBreakpoint breakpoint = GdbMiConverter.breakpoint(REQUEST, parse("^done,bkpt={number=\"3\",type=\"breakpoint\",disp=\"keep\","
            + "enabled=\"y\",addr=\"<PENDING>\",pending=\"square\",times=\"0\",original-location=\"square\"}").results(), "bkpt");

        assertTrue(breakpoint.pending());
        assertTrue(breakpoint.locations().isEmpty());
    }

    @Test
    public void fileVariables() {
        Map<String, List<String>> variables = GdbMiConverter.fileVariables(parse("^done,symbols={debug=["
            + "{filename=\"../src/demo.pas\",fullname=\"/work/src/demo.pas\",symbols=["
            + "{name=\"A\",type=\"TANIMAL\",description=\"static A : TANIMAL;\"},"
            + "{line=\"33\",name=\"S\",type=\"ANSISTRING\",description=\"static S : ANSISTRING;\"},"
            + "{line=\"33\",name=\"S\",type=\"ANSISTRING\",description=\"static S : ANSISTRING;\"}]},"
            + "{filename=\"util.c\",symbols=[{line=\"4\",name=\"counter\",type=\"int\",description=\"static int counter;\"}]}]}")
            .results());

        assertEquals(List.of("A", "S"), variables.get("/work/src/demo.pas"));
        assertEquals(List.of("counter"), variables.get("util.c"));
        assertTrue(GdbMiConverter.fileVariables(parse("^done,symbols={}").results()).isEmpty());
    }

    @Test
    public void escapedValues() {
        MIRecord record = parse("^done,value=\"0x4005d4 \\\"tab\\\\there\\\\n\\\"\",msg=\"\\320\\234\\320\\270\\321\\200 \\\"x\\\"\"");

        assertEquals("0x4005d4 \"tab\\there\\n\"", GdbMiConverter.string(record.results(), "value"));
        assertEquals("Мир \"x\"", GdbMiConverter.string(record.results(), "msg"));
        assertEquals("line\n", GdbMiConverter.stream("line\\n"));
    }

    @Test
    public void sourceDisassembly() {
        List<NativeInstruction> instructions = GdbMiConverter.instructions(parse("^done,asm_insns=["
            + "src_and_asm_line={line=\"5\",file=\"a.c\",fullname=\"/src/a.c\",line_asm_insn=["
            + "{address=\"0x0000000000001139\",func-name=\"main\",offset=\"0\",opcodes=\"55\",inst=\"push   %rbp\"},"
            + "{address=\"0x000000000000113a\",func-name=\"main\",offset=\"1\",opcodes=\"48 89 e5\",inst=\"mov    %rsp,%rbp\"}]},"
            + "src_and_asm_line={line=\"6\",file=\"a.c\",fullname=\"/src/a.c\",line_asm_insn=["
            + "{address=\"0x000000000000113d\",func-name=\"main\",offset=\"4\",opcodes=\"90\",inst=\"nop\"}]}]").results());

        assertEquals(3, instructions.size());
        assertEquals(0x1139, instructions.get(0).address());
        assertEquals("/src/a.c", instructions.get(0).file());
        assertEquals(5, instructions.get(1).line());
        assertEquals("48 89 e5", instructions.get(1).bytes());
        assertEquals(6, instructions.get(2).line());
    }

    @Test
    public void plainDisassembly() {
        List<NativeInstruction> instructions = GdbMiConverter.instructions(parse("^done,asm_insns=["
            + "{address=\"0x00007ffff7d202a0\",func-name=\"usleep\",offset=\"0\",opcodes=\"f3 0f 1e fa\",inst=\"endbr64\"},"
            + "{address=\"0x00007ffff7d202a4\",func-name=\"usleep\",offset=\"4\",opcodes=\"55\",inst=\"push   %rbp\"}]").results());

        assertEquals(2, instructions.size());
        assertEquals("usleep", instructions.get(1).function());
        assertEquals(4, instructions.get(1).offset());
        assertEquals(0, instructions.get(1).line());
    }

    @Test
    public void decodedFileNamesStayIntact() {
        MIRecord record = parse("*stopped,frame={fullname=\"/home/\\320\\234\\320\\270\\321\\200/a b.c\"}");

        assertEquals("/home/\u041c\u0438\u0440/a b.c", GdbMiConverter.string(GdbMiConverter.tuple(record.results(), "frame"), "fullname"));
    }

    @Test
    public void addresses() {
        assertEquals(0x401136L, GdbMiConverter.address("0x401136 <main+16>"));
        assertEquals(0xffffffffff600000L, GdbMiConverter.address("0xffffffffff600000"));
        assertEquals(0L, GdbMiConverter.address("<PENDING>"));
    }

    private static MIRecord parse(String line) {
        MIParser parser = new MIParser("UTF-8");
        parser.setup(line);
        return parser.parse();
    }
}
