package io.kestra.core.runners.pebble.tests;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Test;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import io.kestra.core.serializers.JacksonMapper;

import java.util.List;
import java.util.Map;

public class JsonTest implements Test {
    @Override
    public List<String> getArgumentNames() {
        return null;
    }

    @Override
    public boolean apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        try {
            JacksonMapper.ofJson().readTree((String) input);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
