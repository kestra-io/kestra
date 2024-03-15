package io.kestra.core.runners.pebble.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.List;
import java.util.Map;

public class YamlFilter implements Filter {

    private static final ObjectMapper MAPPER = new ObjectMapper(
        new YAMLFactory()
            .configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true)
            .configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)
            .configure(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID, false)
            .configure(YAMLGenerator.Feature.SPLIT_LINES, false)
            .configure(YAMLGenerator.Feature.INDENT_ARRAYS, true)
            .configure(YAMLGenerator.Feature.USE_PLATFORM_LINE_BREAKS, true)
            .configure(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS, false)
        )
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .registerModule(new JavaTimeModule())
        .registerModule(new Jdk8Module())
        .registerModule(new ParameterNamesModule())
        .registerModules(new GuavaModule());

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
            throw new PebbleException(e, "Unable to transform to yaml value '" + input +  "' with type '" + input.getClass().getName() + "'", lineNumber, self.getName());
        }
    }
}
