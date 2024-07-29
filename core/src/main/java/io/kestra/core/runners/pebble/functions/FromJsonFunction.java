package io.kestra.core.runners.pebble.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.serializers.JacksonMapper;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.List;
import java.util.Map;

public class FromJsonFunction implements Function {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    public List<String> getArgumentNames() {
        return List.of("json");
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey("json")) {
            throw new PebbleException(null, "The 'fromJson' function expects an argument 'json'.", lineNumber, self.getName());
        }

        if (args.get("json") == null) {
            return null;
        }

        if (!(args.get("json") instanceof String)) {
            throw new PebbleException(null, "The 'fromJson' function expects an argument 'json' with type string.", lineNumber, self.getName());
        }

        String json = (String) args.get("json");;

        try {
            return MAPPER.readValue(json, JacksonMapper.OBJECT_TYPE_REFERENCE);
        } catch (JsonProcessingException e) {
            throw new PebbleException(null, "Invalid json: " + e.getMessage(), lineNumber, self.getName());
        }
    }
}
