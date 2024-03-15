package io.kestra.core.runners.pebble;

import java.util.List;
import java.util.Map;

public abstract class AbstractIndent {
    public List<String> getArgumentNames() {
        return List.of("amount", "prefix");
    }

    protected static String prefix(Map<String, Object> args) {
        if (args.containsKey("prefix")) {
            return (String) args.get("prefix");
        } else {
            return " ";
        }
    }

    protected static String getLineSeparator(String input) {
        if (input == null)
            return System.lineSeparator();

        if (input.contains("\r\n"))
            return "\r\n"; // CRLF

        if (input.contains("\n\r"))
            return "\n\r"; // LFCR

        if (input.contains("\n"))
            return "\n"; // LF

        if (input.contains("\r"))
            return "\r"; // CR

        return System.lineSeparator(); // System default
    }
}
