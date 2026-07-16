package io.kestra.core.runners.pebble.functions;

import java.util.List;
import java.util.Map;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

public class YamlFunction implements KestraFunction {
    public static final String NAME = "yaml";
    final static ObjectMapper MAPPER = YAMLMapper.builder()
        .findAndAddModules()
        .build();
    private static final TypeReference<Object> TYPE_REFERENCE = new TypeReference<>() {
    };

    public List<String> getArgumentNames() {
        return List.of("yaml");
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        return Map.of("yaml", "inputs.yamlInput");
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
        } catch (JacksonException e) {
            throw new PebbleException(null, "Invalid yaml: " + e.getMessage(), lineNumber, self.getName());
        }
    }
}
