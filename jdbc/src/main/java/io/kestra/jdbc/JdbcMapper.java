package io.kestra.jdbc;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import io.kestra.core.serializers.JacksonMapper;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

public abstract class JdbcMapper {
    private static final DateTimeFormatter INSTANT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
        .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter ZONED_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final ObjectMapper MAPPER = init();

    public static ObjectMapper of() {
        return MAPPER;
    }

    private static ObjectMapper init() {
        final SimpleModule module = new SimpleModule();
        module.addSerializer(Instant.class, new ValueSerializer<>() {
            @Override
            public void serialize(Instant instant, JsonGenerator jsonGenerator, SerializationContext serializationContext) throws JacksonException {
                jsonGenerator.writeString(INSTANT_FORMATTER.format(instant));
            }
        });

        module.addSerializer(ZonedDateTime.class, new ValueSerializer<>() {
            @Override
            public void serialize(ZonedDateTime instant, JsonGenerator jsonGenerator, SerializationContext serializationContext) throws JacksonException {
                jsonGenerator.writeString(ZONED_DATE_TIME_FORMATTER.format(instant));
            }
        });

        return JacksonMapper.ofJson(false).rebuild()
            .addModule(module)
            .build();
    }
}
