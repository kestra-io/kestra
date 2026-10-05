package io.kestra.core.models.flows.input;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.path.NodePath;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.serializers.JacksonMapper;

import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
                Set<ManualConstraintViolation<JsonInput>> violations = errors.stream()
                    .map(error -> ManualConstraintViolation.of(
                        "it must match the json schema: " + error.getMessage(),
                        this,
                        JsonInput.class,
                        locationOf(error),
                        input
                    ))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
                throw ManualConstraintViolation.toConstraintViolationException(violations);
            }
        } catch (ConstraintViolationException e) {
            // The violations above are a RuntimeException, so without this they are caught below and rewrapped into
            // one "Invalid JSON schema" error, losing both their locations and the fact that the schema is fine.
            throw e;
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

    /**
     * Renders where a schema error sits inside the value, as {@code id.a[0].b}. Built from the path elements rather
     * than {@link NodePath#toString()}, whose notation follows the schema's {@code PathType}.
     */
    private String locationOf(Error error) {
        NodePath location = error.getInstanceLocation();
        StringBuilder path = new StringBuilder(getId());

        for (int i = 0; i < location.getNameCount(); i++) {
            Object element = location.getElement(i);
            if (element instanceof Integer index) {
                path.append('[').append(index).append(']');
            } else {
                path.append('.').append(element);
            }
        }

        return path.toString();
    }
}
