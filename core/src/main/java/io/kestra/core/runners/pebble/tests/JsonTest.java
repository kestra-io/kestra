package io.kestra.core.runners.pebble.tests;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.serializers.JacksonMapper;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Test;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class JsonTest implements Test {
    @Override
    public List<String> getArgumentNames() {
        return null;
    }

    @Override
    public boolean apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return false;
        }
        if (input instanceof Map || input instanceof List) {
            return true;
        }
        if (!(input instanceof String stringValue)) {
            return false;
        }
        try {
            JacksonMapper.ofJson().readTree(stringValue);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
