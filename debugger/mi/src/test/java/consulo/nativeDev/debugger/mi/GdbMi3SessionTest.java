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
package consulo.nativeDev.debugger.mi;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Parses a real gdb 17.2 session started with {@code --interpreter=mi3}: a multi-location breakpoint on an inlined
 * function, a plain line breakpoint, two stops, the stack and the variables of the first stop.
 *
 * @author VISTALL
 * @since 2026-10-06
 */
public class GdbMi3SessionTest {
    @Test
    public void everyRecordParses() throws IOException {
        List<MIRecord> records = records();
        assertEquals(49, records.size());
        for (MIRecord record : records) {
            assertTrue(record.toString(), "^*+=~@&".indexOf(record.type()) >= 0);
        }
    }

    @Test
    public void multiLocationBreakpointKeepsMi3Locations() throws IOException {
        MITList bkpt = first(records(), '^', "done", "bkpt").results().valueOf("bkpt").asTuple();
        assertEquals("1", bkpt.getConstValue("number"));
        assertEquals("<MULTIPLE>", bkpt.getConstValue("addr"));

        MITList locations = bkpt.valueOf("locations").asList();
        assertEquals(2, locations.size());
        String[] numbers = {"1.1", "1.2"};
        for (int i = 0; i < numbers.length; i++) {
            MITList location = ((MIValue) locations.get(i)).asTuple();
            assertEquals(numbers[i], location.getConstValue("number"));
            assertEquals("square", location.getConstValue("func"));
            assertEquals("/work/multi.c", location.getConstValue("fullname"));
            assertEquals("2", location.getConstValue("line"));
        }
    }

    @Test
    public void lineBreakpointHasSingleAddress() throws IOException {
        List<MIRecord> bkpts = all(records(), '^', "done", "bkpt");
        MITList bkpt = bkpts.get(1).results().valueOf("bkpt").asTuple();
        assertEquals("2", bkpt.getConstValue("number"));
        assertEquals("0x0000000000001046", bkpt.getConstValue("addr"));
        assertEquals("6", bkpt.getConstValue("line"));
    }

    @Test
    public void stopsCarryBreakpointAndLocationNumbers() throws IOException {
        List<MIRecord> stops = all(records(), '*', "stopped", null);
        assertEquals(2, stops.size());

        MITList first = stops.get(0).results();
        assertEquals("breakpoint-hit", first.getConstValue("reason"));
        assertEquals("2", first.getConstValue("bkptno"));
        MITList frame = first.valueOf("frame").asTuple();
        assertEquals("main", frame.getConstValue("func"));
        assertEquals(2, frame.valueOf("args").asList().size());

        MITList second = stops.get(1).results();
        assertEquals("1", second.getConstValue("bkptno"));
        assertEquals("1", second.getConstValue("locno"));
        assertEquals("square", second.valueOf("frame").asTuple().getConstValue("func"));
    }

    @Test
    public void stackAndVariablesOfFirstStop() throws IOException {
        List<MIRecord> records = records();
        MITList stack = first(records, '^', "done", "stack").results().valueOf("stack").asList();
        assertEquals(1, stack.size());
        MITList frame = ((MIResult) stack.get(0)).value().asTuple();
        assertEquals("0", frame.getConstValue("level"));
        assertEquals("main", frame.getConstValue("func"));

        MITList variables = first(records, '^', "done", "variables").results().valueOf("variables").asList();
        String[][] expected = {{"argc", "1"}, {"argv", "<optimized out>"}, {"a", "4"}, {"b", "9"}};
        assertEquals(expected.length, variables.size());
        for (int i = 0; i < expected.length; i++) {
            MITList variable = ((MIValue) variables.get(i)).asTuple();
            assertEquals(expected[i][0], variable.getConstValue("name"));
            assertEquals(expected[i][1], variable.getConstValue("value"));
        }
    }

    private static List<MIRecord> records() throws IOException {
        List<MIRecord> records = new ArrayList<>();
        try (InputStream stream = GdbMi3SessionTest.class.getResourceAsStream("/gdb-17.2-mi3-session.txt")) {
            assertNotNull(stream);
            for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isEmpty() || line.startsWith("(gdb)")) {
                    continue;
                }
                MIParser parser = new MIParser("UTF-8");
                parser.setup(line);
                records.add(parser.parse());
            }
        }
        return records;
    }

    private static MIRecord first(List<MIRecord> records, char type, String cls, String result) {
        return all(records, type, cls, result).get(0);
    }

    private static List<MIRecord> all(List<MIRecord> records, char type, String cls, String result) {
        List<MIRecord> found = new ArrayList<>();
        for (MIRecord record : records) {
            if (record.type() == type && cls.equals(record.cls()) && (result == null || record.results().valueOf(result) != null)) {
                found.add(record);
            }
        }
        assertTrue("no " + type + cls + " record with " + result, !found.isEmpty());
        return found;
    }
}
