package io.kestra.core.runners.pebble.filters;

import java.util.List;
import java.util.Map;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;
import tools.jackson.datatype.guava.GuavaModule;

public class YamlFilter implements Filter {

    // java.time and parameter-names support are embedded in jackson-databind 3.x, no explicit module registration needed.
    // YAMLGenerator.Feature split into YAMLReadFeature/YAMLWriteFeature in v3; USE_PLATFORM_LINE_BREAKS has no v3
    // equivalent (removed from the API) - harmless since YAML consumers don't distinguish \n from \r\n.
    // WRITE_DURATIONS_AS_TIMESTAMPS defaults to false in v3 (was true in v2); keep it enabled to preserve the
    // existing numeric Duration representation used across the codebase (see JacksonMapper.java).
    private static final ObjectMapper MAPPER = YAMLMapper.builder()
        .configure(YAMLWriteFeature.MINIMIZE_QUOTES, true)
        .configure(YAMLWriteFeature.WRITE_DOC_START_MARKER, false)
        .configure(YAMLWriteFeature.USE_NATIVE_TYPE_ID, false)
        .configure(YAMLWriteFeature.SPLIT_LINES, false)
        .configure(YAMLWriteFeature.INDENT_ARRAYS, true)
        .configure(YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS, false)
        .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS, true)
        .addModule(new GuavaModule())
        .build();

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
        } catch (JacksonException e) {
            throw new PebbleException(e, "Unable to transform to yaml value '" + input + "' with type '" + input.getClass().getName() + "'", lineNumber, self.getName());
        }
    }
}
