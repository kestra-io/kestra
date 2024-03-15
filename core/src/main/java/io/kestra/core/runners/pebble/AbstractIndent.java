package io.kestra.core.runners.pebble;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

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

    protected Object abstractApply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber, String indentType) throws PebbleException {
        if (input == null) {
            return null;
        }
        if (input.toString().isEmpty()) {
            return input.toString();
        }

        if (!args.containsKey("amount")) {
            throw new PebbleException(null, String.format("The '%s' filter expects an integer as argument 'amount'.", indentType), lineNumber, self.getName());
        }

        int amount = ((Long) args.get("amount")).intValue();
        if (!(amount >= 0)) {
            throw new PebbleException(null, String.format("The '%s' filter expects a positive integer >=0 as argument 'amount'.", indentType), lineNumber, self.getName());
        }

        String prefix = prefix(args);
        String newLine = getLineSeparator(input.toString());

        if (indentType.equals("indent"))
            // indent filter adds N amount of spaces to each line except for the first one (assuming the first line is already indented in place)
            return input.toString().replace(newLine, newLine + prefix.repeat(amount));
        else if (indentType.equals("nindent")) {
            // nindent filter adds a newline to the string and indents each line by defined amount of spaces
            return (newLine + input).replace(newLine, newLine + prefix.repeat(amount));
        }
        throw new PebbleException(null, String.format("Unknow indent type '%s'.", indentType), lineNumber, self.getName());

    }
}
