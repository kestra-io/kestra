package io.kestra.core.runners.pebble.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.YAMLException;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.List;
import java.util.Map;

public class YamlFunction implements Function {
    final static ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Object> TYPE_REFERENCE = new TypeReference<>() {};

    public List<String> getArgumentNames() {
        return List.of("yaml");
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey("yaml")) {
            throw new PebbleException(null, "The 'yaml' function expects an argument 'yaml'.", lineNumber, self.getName());
        }

        if (!(args.get("yaml") instanceof String)) {
            throw new PebbleException(null, "The 'yaml' function expects an argument 'yaml' with type string.", lineNumber, self.getName());
        }

        String yaml = (String) args.get("yaml");

        try {
            return MAPPER.readValue(yaml, TYPE_REFERENCE);
        } catch (YAMLException e) {
            throw new PebbleException(null, "Invalid yaml: " + e.getMessage(), lineNumber, self.getName());
        } catch (JsonMappingException e) {
            throw new PebbleException(null, "Invalid yaml: " + e.getMessage(), lineNumber, self.getName());
        } catch (JsonProcessingException e) {
            throw new PebbleException(null, "Invalid yaml: " + e.getMessage(), lineNumber, self.getName());
        }
    }
}
