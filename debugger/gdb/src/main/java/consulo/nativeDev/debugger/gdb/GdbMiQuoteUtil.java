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

import java.util.List;
import java.util.regex.Pattern;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public final class GdbMiQuoteUtil {
    private static final Pattern SHELL_SAFE = Pattern.compile("[A-Za-z0-9_\\-./=:,@+%^]+");

    private GdbMiQuoteUtil() {
    }

    public static String quote(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 2);
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(c);
            }
        }
        builder.append('"');
        return builder.toString();
    }

    public static String programArguments(List<String> arguments, boolean windows) {
        StringBuilder builder = new StringBuilder();
        for (String argument : arguments) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(windows ? windowsArgument(argument) : shellArgument(argument));
        }
        return builder.toString();
    }

    private static String shellArgument(String argument) {
        if (SHELL_SAFE.matcher(argument).matches()) {
            return argument;
        }
        return "'" + argument.replace("'", "'\\''") + "'";
    }

    private static String windowsArgument(String argument) {
        if (!argument.isEmpty() && argument.chars().noneMatch(c -> c == ' ' || c == '\t' || c == '"')) {
            return argument;
        }

        StringBuilder builder = new StringBuilder("\"");
        int backslashes = 0;
        for (int i = 0; i < argument.length(); i++) {
            char c = argument.charAt(i);
            if (c == '\\') {
                backslashes++;
                continue;
            }
            if (c == '"') {
                builder.append("\\".repeat(backslashes * 2 + 1));
            }
            else {
                builder.append("\\".repeat(backslashes));
            }
            backslashes = 0;
            builder.append(c);
        }
        builder.append("\\".repeat(backslashes * 2));
        builder.append('"');
        return builder.toString();
    }
}
