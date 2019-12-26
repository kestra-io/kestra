
package org.kestra.core.serializers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

abstract public class JacksonMapper {
    private static ObjectMapper jsonMapper = JacksonMapper.configure(
        new ObjectMapper()
    );

    private static ObjectMapper yamlMapper = JacksonMapper.configure(
        new ObjectMapper(
            new YAMLFactory()
                .configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true)
                .configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)
        )
    );

    public static ObjectMapper ofJson() {
        return jsonMapper;
    }

    public static ObjectMapper ofYaml() {
        return yamlMapper;
    }

    public static <T> String log(T Object) {
        try {
            return yamlMapper.writeValueAsString(Object);
        } catch (JsonProcessingException ignored) {
            return "Failed to log " + Object.getClass();
        }
    }

    private static ObjectMapper configure(ObjectMapper mapper) {
        return mapper
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .registerModule(new JavaTimeModule())
            .registerModule(new ParameterNamesModule());
    }
}
