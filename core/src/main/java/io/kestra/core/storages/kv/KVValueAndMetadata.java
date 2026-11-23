package io.kestra.core.storages.kv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.kestra.core.serializers.JacksonMapper;

import jakarta.annotation.Nullable;

/**
 * Class wrapping a value and metadata for K/V entry.
 *
 * @param metadata
 * @param value
 */
public record KVValueAndMetadata(@Nullable KVMetadata metadata, @Nullable Object value) {

    /**
     * Wraps a value given as text, the way the KV API receives it: a single Ion (or JSON) value is kept typed, so
     * {@code 1}, {@code true}, {@code "quoted"}, {@code 2021-09-01} or {@code {"a": 1}} become a number, a boolean, a
     * string, a date or an object, and any other text is kept as a string.
     */
    public static KVValueAndMetadata ofText(@Nullable final KVMetadata metadata, final String text) {
        try (JsonParser parser = JacksonMapper.ofIon().createParser(text)) {
            JsonNode node = JacksonMapper.ofIon().readTree(parser);
            if (parser.nextToken() != null) {
                throw new JsonParseException(parser, "Trailing content after the first Ion value");
            }
            return new KVValueAndMetadata(metadata, node);
        } catch (JsonProcessingException e) {
            return new KVValueAndMetadata(metadata, text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Map<String, String> metadataAsMap() {
        return Optional.ofNullable(metadata).map(KVMetadata::toMap).orElse(null);
    }
}
