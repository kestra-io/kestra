
package org.floworc.core.serializers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

abstract public class JacksonMapper {
    public static ObjectMapper ofJson() {
        return JacksonMapper.configure(
            new ObjectMapper()
        );
    }

    public static ObjectMapper ofYaml() {
        return JacksonMapper.configure(
            new ObjectMapper(new YAMLFactory())
        );
    }

    private static ObjectMapper configure(ObjectMapper mapper) {
        return mapper
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .registerModule(new JavaTimeModule())
            .registerModule(new ParameterNamesModule());
    }
}
