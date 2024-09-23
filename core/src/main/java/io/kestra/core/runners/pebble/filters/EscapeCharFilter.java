package io.kestra.core.runners.pebble.filters;

import io.kestra.core.utils.Enums;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.ArrayList;
import java.util.HashMap;
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
    @SuppressWarnings("unchecked")
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        if (input instanceof Map) {
            return processMap((Map<String, Object>) input, args, self, lineNumber);
        } else if (input instanceof List) {
            return processList((List<Object>) input, args, self, lineNumber);
        } else {
            return escapeString(input.toString(), args, self, lineNumber);
        }
    }

    @SuppressWarnings("unchecked")
    private Object processMap(Map<String, Object> inputMap, Map<String, Object> args, PebbleTemplate self, int lineNumber) {
        Map<String, Object> resultMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : inputMap.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                resultMap.put(entry.getKey(), escapeString((String) value, args, self, lineNumber));
            } else if (value instanceof Map) {
                resultMap.put(entry.getKey(), processMap((Map<String, Object>) value, args, self, lineNumber));
            } else if (value instanceof List<?>) {
                resultMap.put(entry.getKey(), processList((List<Object>) value, args, self, lineNumber));
            } else {
                resultMap.put(entry.getKey(), escapeString(value.toString(), args, self, lineNumber));
            }
        }
        return resultMap;
    }

    @SuppressWarnings("unchecked")
    private Object processList(List<Object> inputList, Map<String, Object> args, PebbleTemplate self, int lineNumber) {
        List<Object> resultList = new ArrayList<>();
        for (Object item : inputList) {
            if (item instanceof String) {
                resultList.add(escapeString((String) item, args, self, lineNumber));
            } else if (item instanceof Map) {
                resultList.add(processMap((Map<String, Object>) item, args, self, lineNumber));
            } else if (item instanceof List<?>) {
                resultList.add(processList((List<Object>) item, args, self, lineNumber));
            } else {
                resultList.add(escapeString(item.toString(), args, self, lineNumber));
            }
        }
        return resultList;
    }

    private String escapeString(String input, Map<String, Object> args, PebbleTemplate self, int lineNumber) {
        return switch (argsToType(args, self, lineNumber)) {
            case SHELL -> escape(input, "'", "'\\''");
            case SINGLE -> escape(input, "'", "\\'");
            case DOUBLE -> escape(input, "\"", "\\\"");
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
