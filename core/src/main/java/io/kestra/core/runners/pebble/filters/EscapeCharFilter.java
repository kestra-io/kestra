package io.kestra.core.runners.pebble.filters;

import io.kestra.core.utils.Enums;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EscapeCharFilter implements Filter {
    private static final String ARG_NAME = "type";

    private final List<String> argumentNames = new ArrayList<>();

    public EscapeCharFilter() {
        this.argumentNames.add(ARG_NAME);
    }

    @Override
    public List<String> getArgumentNames() {
        return this.argumentNames;
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        final String inputValue = input.toString();

        return switch (argsToType(args, self, lineNumber)) {
            case SHELL -> escape(inputValue, "'", "'\\''");
            case SINGLE -> escape(inputValue, "'", "\\'");
            case DOUBLE -> escape(inputValue, "\"", "\\\"");
        };
    }

    private FilterType argsToType(Map<String, Object> args, PebbleTemplate self, int lineNumber) {
        if (!args.containsKey(ARG_NAME)) {
            throw new PebbleException(null, "The 'escapeChar' filter expects an argument '" + ARG_NAME + "'.", lineNumber, self.getName());
        }

        final String type = (String) args.get(ARG_NAME);

        try {
            return Enums.getForNameIgnoreCase(type, FilterType.class);
        } catch (IllegalArgumentException e) {
            throw new PebbleException(
                null,
                "The 'escapeChar' filter expects the value of '" + ARG_NAME + "' to be either 'single', 'double', or 'shell'.",
                lineNumber,
                self.getName()
            );
        }
    }

    private String escape(String input, String original, String replacement) {
        return input.replace(original, replacement);
    }

    /**
     * Supported escape styles.
     */
    enum FilterType {
        SINGLE,
        DOUBLE,
        SHELL
    }
}
