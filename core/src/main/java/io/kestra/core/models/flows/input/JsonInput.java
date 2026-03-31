package io.kestra.core.models.flows.input;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.serializers.JacksonMapper;
import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public class JsonInput extends Input<Object> {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistry.withDialect(Dialects.getDraft202012());
    private static final ConcurrentHashMap<String, Schema> SCHEMA_CACHE = new ConcurrentHashMap<>();

    @io.swagger.v3.oas.annotations.media.Schema(title = "A JSON schema used to validate the input value.")
    String jsonSchema;

    @Override
    public void validate(Object input) throws ConstraintViolationException {
        if (jsonSchema == null || jsonSchema.isBlank()) {
            return;
        }

        try {
            JsonNode schemaNode = MAPPER.readTree(jsonSchema);
            Schema schema = getOrCreateSchema(schemaNode);

            JsonNode inputNode = (input instanceof String s) ? MAPPER.readTree(s) : MAPPER.valueToTree(input);
            List<Error> errors = schema.validate(inputNode);

            if (!errors.isEmpty()) {
                throw ManualConstraintViolation.toConstraintViolationException(
                    "it must match the json schema: " + errors,
                    this,
                    JsonInput.class,
                    getId(),
                    input
                );
            }
        } catch (JsonProcessingException e) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "Invalid JSON content or schema: " + e.getMessage(),
                this,
                JsonInput.class,
                getId(),
                input
            );
        } catch (RuntimeException e) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "Invalid JSON schema: " + e.getMessage(),
                this,
                JsonInput.class,
                getId(),
                jsonSchema
            );
        }
    }

    private static Schema getOrCreateSchema(JsonNode schemaNode) {
        String cacheKey = schemaNode.has("$id") ? schemaNode.get("$id").asText() : Integer.toString(schemaNode.hashCode());
        return SCHEMA_CACHE.computeIfAbsent(cacheKey, key -> SCHEMA_REGISTRY.getSchema(schemaNode));
    }
}
