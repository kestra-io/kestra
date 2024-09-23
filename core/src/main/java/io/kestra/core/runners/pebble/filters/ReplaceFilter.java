package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReplaceFilter implements Filter {

    public static final String FILTER_NAME = "replace";

    private static final String ARGUMENT_PAIRS = "replace_pairs";
    private static final String ARGUMENT_REGEXP = "regexp";

    private final static List<String> ARGS = List.of(ARGUMENT_PAIRS, ARGUMENT_REGEXP);

    @Override
    public List<String> getArgumentNames() {
        return ARGS;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        if (args.get(ARGUMENT_PAIRS) == null) {
            throw new PebbleException(null,
                MessageFormat.format("The argument ''{0}'' is required.", ARGUMENT_PAIRS), lineNumber,
                self.getName()
            );
        }

        final boolean regexp = args.containsKey(ARGUMENT_REGEXP) ? (Boolean) args.get(ARGUMENT_REGEXP) : false;
        Map<?, ?> replacePair = (Map<?, ?>) args.get(ARGUMENT_PAIRS);

        if (input instanceof Map) {
            return processMap((Map<String, Object>) input, replacePair, regexp);
        } else if (input instanceof List) {
            return processList((List<Object>) input, replacePair, regexp);
        } else {
            return processString(input.toString(), replacePair, regexp);
        }
    }

    @SuppressWarnings("unchecked")
    private Object processMap(Map<String, Object> inputMap, Map<?, ?> replacePair, boolean regexp) {
        Map<String, Object> resultMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : inputMap.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                resultMap.put(entry.getKey(), processString((String) value, replacePair, regexp));
            } else if (value instanceof Map) {
                resultMap.put(entry.getKey(), processMap((Map<String, Object>) value, replacePair, regexp));
            } else if (value instanceof List<?>) {
                resultMap.put(entry.getKey(), processList((List<Object>) value, replacePair, regexp));
            } else {
                resultMap.put(entry.getKey(), processString(value.toString(), replacePair, regexp));
            }
        }
        return resultMap;
    }

    @SuppressWarnings("unchecked")
    private Object processList(List<Object> inputList, Map<?, ?> replacePair, boolean regexp) {
        List<Object> resultList = new ArrayList<>();
        for (Object item : inputList) {
            if (item instanceof String) {
                resultList.add(processString((String) item, replacePair, regexp));
            } else if (item instanceof Map) {
                resultList.add(processMap((Map<String, Object>) item, replacePair, regexp));
            } else if (item instanceof List<?>) {
                resultList.add(processList((List<Object>) item, replacePair, regexp));
            } else {
                resultList.add(processString(item.toString(), replacePair, regexp));
            }
        }
        return resultList;
    }

    private String processString(String input, Map<?, ?> replacePair, boolean regexp) {
        String data = input;
        for (Map.Entry<?, ?> entry : replacePair.entrySet()) {
            if (regexp) {
                data = data.replaceAll(entry.getKey().toString(), entry.getValue().toString());
            } else {
                data = data.replace(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        return data;
    }
}
