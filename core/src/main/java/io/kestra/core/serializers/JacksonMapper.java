
package io.kestra.core.serializers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import java.util.Map;
import java.util.TimeZone;

abstract public class JacksonMapper {
    private static final ObjectMapper MAPPER = JacksonMapper.configure(
        new ObjectMapper()
    );

    public static ObjectMapper ofJson() {
        return MAPPER;
    }

    private static final ObjectMapper YAML_MAPPER = JacksonMapper.configure(
        new ObjectMapper(
            new YAMLFactory()
                .configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true)
                .configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)
                .configure(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID, false)
                .configure(YAMLGenerator.Feature.SPLIT_LINES, false)
        )
    );

    public static ObjectMapper ofYaml() {
        return YAML_MAPPER;
    }

    private static final TypeReference<Map<String, Object>> TYPE_REFERENCE = new TypeReference<>() {};

    public static Map<String, Object> toMap(Object object) {
        return MAPPER.convertValue(object, TYPE_REFERENCE);
    }

    public static <T> T toMap(Object map, Class<T> cls) {
        return MAPPER.convertValue(map, cls);
    }

    public static Map<String, Object> toMap(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, TYPE_REFERENCE);
    }

    public static <T> String log(T Object) {
        try {
            return YAML_MAPPER.writeValueAsString(Object);
        } catch (JsonProcessingException ignored) {
            return "Failed to log " + Object.getClass();
        }
    }

    private static final ObjectMapper ION_MAPPER = JacksonMapper
        .configure(
            new IonObjectMapper()
        )
        .setSerializationInclusion(JsonInclude.Include.USE_DEFAULTS)
        .setSerializationInclusion(JsonInclude.Include.ALWAYS);

    public static ObjectMapper ofIon() {
        return ION_MAPPER;
    }

    private static ObjectMapper configure(ObjectMapper mapper) {
        return mapper
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module())
            .registerModule(new ParameterNamesModule())
            .registerModules(new GuavaModule())
            .setTimeZone(TimeZone.getDefault());
    }
}
