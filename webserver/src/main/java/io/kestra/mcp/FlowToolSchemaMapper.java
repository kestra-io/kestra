package io.kestra.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Output;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.*;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ListUtils;
import io.kestra.plugin.core.trigger.McpToolTrigger;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class FlowToolSchemaMapper {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    public McpSchema.Tool buildTool(Flow flow, McpToolTrigger toolTrigger) {
        McpToolTrigger.Annotations a = toolTrigger.getAnnotations();
        return McpSchema.Tool.builder()
            .name(toolTrigger.getToolName())
            .description(toolTrigger.getToolDescription())
            .title(toolTrigger.getTitle())
            .annotations(new McpSchema.ToolAnnotations(
                null,
                a.readOnly(),
                a.destructive(),
                a.idempotent(),
                a.openWorld(),
                a.returnDirect()
            ))
            .inputSchema(buildToolInputSchema(
                    ListUtils.emptyOnNull(flow.getInputs())
                )
            )
            .outputSchema(
                MAPPER.convertValue(
                    buildToolOutputSchema(
                        ListUtils.emptyOnNull(flow.getOutputs())
                    ),
                    MAP_TYPE
                )
            )
            .build();
    }

    private McpSchema.JsonSchema buildToolInputSchema(List<Input<?>> inputs) {
        Map<String, Object> toolsSchema = inputs.stream()
            .collect(Collectors.toMap(Input::getId, this::convert));

        return new McpSchema.JsonSchema(
            "object",
            toolsSchema,
            findRequiredInputFields(inputs),
            false,
            null,
            null
        );
    }

    private McpSchema.JsonSchema buildToolOutputSchema(List<Output> outputs) {
        Map<String, Object> toolsSchema = outputs.stream()
            .collect(Collectors.toMap(Output::getId, this::convert));

        return new McpSchema.JsonSchema(
            "object",
            toolsSchema,
            findRequiredOutputFields(outputs),
            false,
            null,
            null
        );
    }

    private List<String> findRequiredOutputFields(List<Output> inputs) {
        return inputs.stream()
            .filter(output -> output.getRequired() == null || output.getRequired())
            .map(Output::getId).toList();
    }

    private List<String> findRequiredInputFields(List<Input<?>> inputs) {
        return inputs.stream()
            .filter((input) -> input.getRequired() != null)
            .filter(Input::getRequired)
            .map(Input::getId).toList();
    }

    public Map<String, Object> convert(Output output) {
        Map<String, Object> outputSchema = new HashMap<>(Map.of(
            "type", getJsonSchemaType(output.getType())
        ));

        if (output.getDescription() != null) {
            outputSchema.put("description", output.getDescription());
        }

        return outputSchema;
    }

    public Map<String, Object> convert(Input<?> input) {
        Map<String, Object> baseSchema = new HashMap<>(Map.of(
            "type", getJsonSchemaType(input.getType())
        ));

        if (input.getDescription() != null) {
            baseSchema.put("description", input.getDescription());
        }

        return switch (input) {
            case ArrayInput i -> toArrayType((ArrayInput) input, baseSchema);
            case BoolInput i -> toBoolType((BoolInput) input, baseSchema);
            case DateInput i -> toDateType((DateInput) input, baseSchema);
            case DateTimeInput i -> toDateTimeType((DateTimeInput) input, baseSchema);
            case DurationInput i -> toDurationTimeType((DurationInput) input, baseSchema);
            case EmailInput i -> toEmailType((EmailInput) input, baseSchema);
            case FileInput i -> toFileType((FileInput) input, baseSchema);
            case FloatInput i -> toFloatType((FloatInput) input, baseSchema);
            case IntInput i -> toIntType((IntInput) input, baseSchema);
            case JsonInput i -> toObjectType(input, baseSchema);
            case MultiselectInput i -> toMultiselectType((MultiselectInput) input, baseSchema);
            case SecretInput i -> toSecretType((SecretInput) input, baseSchema);
            case SelectInput i -> toSelectType((SelectInput) input, baseSchema);
            case StringInput i -> toStringType((StringInput) input, baseSchema);
            case TimeInput i -> toTimeType((TimeInput) input, baseSchema);
            case URIInput i -> toURIType((URIInput) input, baseSchema);
            case YamlInput i -> toObjectType(input, baseSchema);
            default -> throw new IllegalStateException("Unexpected value: " + input);
        };
    }

    private static Map<String, Object> toArrayType(ArrayInput input, Map<String, Object> baseSchema) {
        baseSchema.put(
            "items", buildItemsForArrayInput(input)
        );

        return baseSchema;
    }

    private static Map<String, String> buildItemsForArrayInput(ArrayInput arrayInput) {
        return Map.of(
            "type", getJsonSchemaType(arrayInput.getItemType())
        );
    }

    private static Map<String, Object> toBoolType(BoolInput input, Map<String, Object> baseSchema) {
        return baseSchema;
    }

    private static Map<String, Object> toDateType(DateInput input, Map<String, Object> baseSchema) {
        baseSchema.put(
            "format", "date"
        );

        if (input.getAfter() != null) {
            baseSchema.put("minimum", input.getAfter().toString());
        }

        if (input.getBefore() != null) {
            baseSchema.put("maximum", input.getBefore().toString());
        }

        return baseSchema;
    }

    private static Map<String, Object> toDateTimeType(DateTimeInput input, Map<String, Object> baseSchema) {
        baseSchema.put(
            "format", "date-time"
        );

        if (input.getAfter() != null) {
            baseSchema.put("minimum", input.getAfter().toString());
        }

        if (input.getBefore() != null) {
            baseSchema.put("maximum", input.getBefore().toString());
        }

        return baseSchema;
    }

    private static Map<String, Object> toDurationTimeType(DurationInput input, Map<String, Object> baseSchema) {
        baseSchema.put(
            "format", "duration"
        );

        if (input.getMin() != null) {
            baseSchema.put("minimum", input.getMin().toString());
        }

        if (input.getMax() != null) {
            baseSchema.put("maximum", input.getMax().toString());
        }

        return baseSchema;
    }

    private static Map<String, Object> toEmailType(EmailInput input, Map<String, Object> baseSchema) {
        baseSchema.putAll(Map.of(
            "format", "email",
            "pattern", EmailInput.EMAIL_PATTERN
        ));

        return baseSchema;
    }

    private static Map<String, Object> toFileType(FileInput input, Map<String, Object> baseSchema) {
        baseSchema.put(
            "format", "uri"
        );

        return baseSchema;
    }

    private static Map<String, Object> toFloatType(FloatInput input, Map<String, Object> baseSchema) {
        if (input.getMin() != null) {
            baseSchema.put("minimum", input.getMin());
        }
        if (input.getMax() != null) {
            baseSchema.put("maximum", input.getMax());
        }

        return baseSchema;
    }

    private static Map<String, Object> toIntType(IntInput input, Map<String, Object> baseSchema) {
        if (input.getMin() != null) {
            baseSchema.put("minimum", input.getMin());
        }
        if (input.getMax() != null) {
            baseSchema.put("maximum", input.getMax());
        }

        return baseSchema;
    }


    private Map<String, Object> toObjectType(Input<?> input, Map<String, Object> baseSchema) {
        if (input instanceof JsonInput jsonInput && jsonInput.getJsonSchema() != null && !jsonInput.getJsonSchema().isBlank()) {
            try {
                Map<String, Object> inlineSchema = MAPPER.readValue(jsonInput.getJsonSchema(), MAP_TYPE);
                baseSchema.putAll(inlineSchema);
                return baseSchema;
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON schema for input '" + input.getId() + "': " + e.getMessage(), e);
            }
        }
        baseSchema.put("additionalProperties", true);
        return baseSchema;
    }

    private static Map<String, Object> toMultiselectType(MultiselectInput input, Map<String, Object> baseSchema) {
        baseSchema.putAll(Map.of(
            "items", buildItemsForMultiselectInput(input, input.getValues().stream().map(ValueOption::value).toList()),
            "uniqueItems", true
        ));

        return baseSchema;
    }

    private static Map<String, Object> toStringType(StringInput input, Map<String, Object> baseSchema) {
        if (input.getValidator() != null) {
            baseSchema.put("pattern", input.getValidator());
        }

        return baseSchema;
    }

    private static Map<String, Object> buildItemsForMultiselectInput(MultiselectInput arrayInput, List<String> enumValues) {
        return Map.of(
            "type", getJsonSchemaType(arrayInput.getItemType()),
            "enum", enumValues
        );
    }

    private static Map<String, Object> toSecretType(SecretInput input, Map<String, Object> baseSchema) {
        return baseSchema;
    }

    private static Map<String, Object> toSelectType(SelectInput input, Map<String, Object> baseSchema) {
        baseSchema.put(
            "enum", input.getValues().stream().map(ValueOption::value).toList()
        );

        return baseSchema;
    }

    private static Map<String, Object> toTimeType(TimeInput input, Map<String, Object> baseSchema) {
        baseSchema.put(
            "format", "time"
        );

        if (input.getAfter() != null) {
            baseSchema.put("minimum", input.getAfter().toString());
        }

        if (input.getBefore() != null) {
            baseSchema.put("maximum", input.getBefore().toString());
        }

        return baseSchema;
    }

    private static Map<String, Object> toURIType(URIInput input, Map<String, Object> baseSchema) {
        baseSchema.put(
            "format", "uri"
        );
        return baseSchema;
    }

    private static String getJsonSchemaType(Type type) {
        return switch (type) {
            case STRING, SECRET, EMAIL, DATE, DURATION, TIME, FILE, URI, DATETIME -> "string";
            case JSON, YAML -> "object";
            case SELECT -> "string";
            case INT -> "integer";
            case FLOAT -> "number";
            case BOOL -> "boolean";
            case ARRAY, MULTISELECT -> "array";
        };
    }
}
