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

import consulo.nativeDev.debugger.driver.NativeDebuggerException;
import consulo.nativeDev.debugger.mi.MICommand;
import consulo.nativeDev.debugger.mi.MIRecord;
import consulo.nativeDev.debugger.mi.MIUserInteraction;

import java.util.concurrent.CompletableFuture;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
class GdbMiCommand extends MICommand {
    private final CompletableFuture<MIRecord> myFuture;

    GdbMiCommand(String command, CompletableFuture<MIRecord> future) {
        super(0, command);
        myFuture = future;
    }

    @Override
    protected void onDone(MIRecord record) {
        complete(record);
    }

    @Override
    protected void onRunning(MIRecord record) {
        complete(record);
    }

    @Override
    protected void onError(MIRecord record) {
        finish();
        String message = record.isEmpty() ? null : GdbMiConverter.string(record.results(), "msg");
        if (message == null) {
            message = record.toString();
        }
        myFuture.completeExceptionally(new NativeDebuggerException(message));
    }

    @Override
    protected void onExit(MIRecord record) {
        complete(record);
    }

    @Override
    protected void onStopped(MIRecord record) {
    }

    @Override
    protected void onOther(MIRecord record) {
        if (record.type() == '^') {
            complete(record);
        }
    }

    @Override
    protected void onUserInteraction(MIUserInteraction ui) {
    }

    private void complete(MIRecord record) {
        finish();
        myFuture.complete(record);
    }
}
