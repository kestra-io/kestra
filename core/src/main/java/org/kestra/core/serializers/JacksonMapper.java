
package org.kestra.core.serializers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import java.util.Map;

abstract public class JacksonMapper {
    private static ObjectMapper jsonMapper = JacksonMapper.configure(
        new ObjectMapper()
    );

    public static ObjectMapper ofJson() {
        return jsonMapper;
    }

    private static ObjectMapper yamlMapper = JacksonMapper.configure(
        new ObjectMapper(
            new YAMLFactory()
                .configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true)
                .configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)
        )
    );

    public static ObjectMapper ofYaml() {
        return yamlMapper;
    }

    private static final ObjectMapper mapper = JacksonMapper.configure(
        new ObjectMapper()
    );

    private static TypeReference<Map<String, Object>> typeReference = new TypeReference<>() {};

    public static Map<String, Object> toMap(Object object) {
        return mapper.convertValue(object, typeReference);
    }

    public static <T> T toMap(Map<String, Object> map, Class<T> cls) {
        return mapper.convertValue(map, cls);
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
