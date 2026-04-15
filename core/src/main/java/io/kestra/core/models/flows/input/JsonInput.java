package io.kestra.core.models.flows.input;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
    private static final ObjectMapper FASTERXML_MAPPER = JacksonMapper.ofJson();
    private static final JsonMapper TOOLS_MAPPER = JsonMapper.builder().build();
    private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistry.withDialect(Dialects.getDraft202012());

    @io.swagger.v3.oas.annotations.media.Schema(title = "A JSON schema used to validate the input value.")
    String jsonSchema;

    @Override
    public void validate(Object input) throws ConstraintViolationException {
        if (jsonSchema == null || jsonSchema.isBlank()) {
            return;
        }

        try {
            final JsonNode schemaNode = TOOLS_MAPPER.readTree(jsonSchema);
            Schema schema = SCHEMA_REGISTRY.getSchema(schemaNode);

            String inputJson = (input instanceof String s) ? s : FASTERXML_MAPPER.writeValueAsString(input);
            JsonNode inputNode = TOOLS_MAPPER.readTree(inputJson);
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
        } catch (JsonProcessingException | JacksonException e) {
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
}
