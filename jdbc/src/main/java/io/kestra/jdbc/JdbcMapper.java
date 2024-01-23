package io.kestra.jdbc;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.kestra.core.serializers.JacksonMapper;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public abstract class JdbcMapper {
    private static final DateTimeFormatter INSTANT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter ZONED_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static ObjectMapper MAPPER;

    public static ObjectMapper of() {
        if (MAPPER == null) {
            MAPPER = JacksonMapper.ofJson(false).copy();

            final SimpleModule module = new SimpleModule();
            module.addSerializer(Instant.class, new JsonSerializer<>() {
                @Override
                public void serialize(Instant instant, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                    jsonGenerator.writeString(INSTANT_FORMATTER.format(instant));
                }
            });

            module.addSerializer(ZonedDateTime.class, new JsonSerializer<>() {
                @Override
                public void serialize(ZonedDateTime instant, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                    jsonGenerator.writeString(ZONED_DATE_TIME_FORMATTER.format(instant));
                }
            });

            MAPPER.registerModule(module);
        }

        return MAPPER;
    }
}
