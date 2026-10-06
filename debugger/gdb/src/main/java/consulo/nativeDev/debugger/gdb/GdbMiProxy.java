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

import consulo.nativeDev.debugger.driver.NativeOutputKind;
import consulo.nativeDev.debugger.mi.MICommandInjector;
import consulo.nativeDev.debugger.mi.MIProxy;
import consulo.nativeDev.debugger.mi.MIRecord;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
class GdbMiProxy extends MIProxy {
    private final GdbMiDriver myDriver;

    GdbMiProxy(MICommandInjector injector, GdbMiDriver driver) {
        super(injector, "(gdb)", "UTF-8");
        myDriver = driver;
    }

    @Override
    public boolean processLine(String line) {
        if (!isRecord(line)) {
            myDriver.onOutput(NativeOutputKind.TARGET, line + "\n");
            return true;
        }
        return super.processLine(line);
    }

    @Override
    protected void execAsyncOutput(MIRecord record) {
        if (record.token() != 0) {
            dispatch(record);
            return;
        }

        if ("stopped".equals(record.cls())) {
            clearMessages();
            myDriver.onStopped(record);
        }
        else if ("running".equals(record.cls())) {
            clearMessages();
            myDriver.onRunning();
        }
    }

    @Override
    protected void notifyAsyncOutput(MIRecord record) {
        if (record.token() != 0) {
            dispatch(record);
            return;
        }

        myDriver.onNotification(record);
    }

    @Override
    protected void consoleStreamOutput(MIRecord record) {
        super.consoleStreamOutput(record);
        myDriver.onOutput(NativeOutputKind.CONSOLE, GdbMiConverter.stream(record.stream()));
    }

    @Override
    protected void targetStreamOutput(MIRecord record) {
        myDriver.onOutput(NativeOutputKind.TARGET, GdbMiConverter.stream(record.stream()));
    }

    @Override
    protected void logStreamOutput(MIRecord record) {
        super.logStreamOutput(record);
        myDriver.onOutput(NativeOutputKind.LOG, GdbMiConverter.stream(record.stream()));
    }

    @Override
    protected void errorBadLine(String data) {
        myDriver.onOutput(NativeOutputKind.TARGET, data + "\n");
    }

    private static boolean isRecord(String line) {
        if (line.trim().equals("(gdb)")) {
            return true;
        }
        int i = 0;
        while (i < line.length() && Character.isDigit(line.charAt(i))) {
            i++;
        }
        return i < line.length() && "^*+=~@&".indexOf(line.charAt(i)) >= 0;
    }
}
