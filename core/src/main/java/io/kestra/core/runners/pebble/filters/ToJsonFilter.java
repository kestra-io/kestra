package io.kestra.core.runners.pebble.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.serializers.JacksonMapper;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.List;
import java.util.Map;

public class ToJsonFilter implements Filter {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Override
    public List<String> getArgumentNames() {
        return null;
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return "null";
        }

        try {
            return MAPPER.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new PebbleException(e, "Unable to transform to json value '" + input +  "' with type '" + input.getClass().getName() + "'", lineNumber, self.getName());
        }
    }
}
