package io.kestra.mcp;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Output;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.*;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.trigger.McpToolTrigger;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Builder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowToolSchemaMapperTest {

    private final FlowToolSchemaMapper mapper = new FlowToolSchemaMapper();

    @ParameterizedTest
    @FieldSource("inputConversionTestCases")
    void shouldReturnCorrectJsonSchemaWhenConvertingInput(InputConversionTestCase testCase) {
        assertThat(mapper.convert(testCase.input())).isEqualTo(testCase.expectedSchema());
    }

    final static List<InputConversionTestCase> inputConversionTestCases = List.of(
        InputConversionTestCase.builder()
            .input(ArrayInput.builder().type(Type.ARRAY).itemType(Type.STRING).build())
            .expectedSchema(Map.of("type", "array", "items", Map.of("type", "string")))
            .build(),
        InputConversionTestCase.builder()
            .input(BoolInput.builder().type(Type.BOOL).build())
            .expectedSchema(Map.of("type", "boolean"))
            .build(),
        InputConversionTestCase.builder()
            .input(DateInput.builder().type(Type.DATE).build())
            .expectedSchema(Map.of("type", "string", "format", "date"))
            .build(),
        InputConversionTestCase.builder()
            .input(DateInput.builder().type(Type.DATE).after(LocalDate.of(2024, 1, 1)).before(LocalDate.of(2025, 1, 1)).build())
            .expectedSchema(Map.of("type", "string", "format", "date", "minimum", "2024-01-01", "maximum", "2025-01-01"))
            .build(),
        InputConversionTestCase.builder()
            .input(DateTimeInput.builder().type(Type.DATETIME).build())
            .expectedSchema(Map.of("type", "string", "format", "date-time"))
            .build(),
        InputConversionTestCase.builder()
            .input(DateTimeInput.builder().type(Type.DATETIME).after(Instant.ofEpochMilli(0)).before(Instant.ofEpochMilli(1000)).build())
            .expectedSchema(Map.of("type", "string", "format", "date-time", "minimum", "1970-01-01T00:00:00Z", "maximum", "1970-01-01T00:00:01Z"))
            .build(),
        InputConversionTestCase.builder()
            .input(DurationInput.builder().type(Type.DURATION).build())
            .expectedSchema(Map.of("type", "string", "format", "duration"))
            .build(),
        InputConversionTestCase.builder()
            .input(DurationInput.builder().type(Type.DURATION).min(Duration.ofSeconds(1)).max(Duration.ofSeconds(60)).build())
            .expectedSchema(Map.of("type", "string", "format", "duration", "minimum", "PT1S", "maximum", "PT1M"))
            .build(),
        InputConversionTestCase.builder()
            .input(EmailInput.builder().type(Type.EMAIL).build())
            .expectedSchema(Map.of("type", "string", "format", "email", "pattern", EmailInput.EMAIL_PATTERN))
            .build(),
        InputConversionTestCase.builder()
            .input(FileInput.builder().type(Type.FILE).build())
            .expectedSchema(Map.of("type", "string", "format", "uri"))
            .build(),
        InputConversionTestCase.builder()
            .input(FloatInput.builder().type(Type.FLOAT).build())
            .expectedSchema(Map.of("type", "number"))
            .build(),
        InputConversionTestCase.builder()
            .input(FloatInput.builder().type(Type.FLOAT).min(0.0F).max(100.0F).build())
            .expectedSchema(Map.of("type", "number", "minimum", 0.0F, "maximum", 100.0F))
            .build(),
        InputConversionTestCase.builder()
            .input(IntInput.builder().type(Type.INT).build())
            .expectedSchema(Map.of("type", "integer"))
            .build(),
        InputConversionTestCase.builder()
            .input(IntInput.builder().type(Type.INT).min(1).max(10).build())
            .expectedSchema(Map.of("type", "integer", "minimum", 1, "maximum", 10))
            .build(),
        InputConversionTestCase.builder()
            .input(JsonInput.builder().type(Type.JSON).build())
            .expectedSchema(Map.of("type", "object", "additionalProperties", true))
            .build(),
        InputConversionTestCase.builder()
            .input(JsonInput.builder().type(Type.JSON).jsonSchema("""
                {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
                """).build())
            .expectedSchema(Map.of(
                "type", "object",
                "properties", Map.of("name", Map.of("type", "string")),
                "required", List.of("name")
            ))
            .build(),
        InputConversionTestCase.builder()
            .input(MultiselectInput.builder().type(Type.MULTISELECT).itemType(Type.STRING).values(List.of("x", "y")).build())
            .expectedSchema(Map.of("type", "array", "items", Map.of("type", "string", "enum", List.of("x", "y")), "uniqueItems", true))
            .build(),
        InputConversionTestCase.builder()
            .input(SecretInput.builder().type(Type.SECRET).build())
            .expectedSchema(Map.of("type", "string"))
            .build(),
        InputConversionTestCase.builder()
            .input(SelectInput.builder().type(Type.SELECT).values(List.of("a", "b")).build())
            .expectedSchema(Map.of("type", "string", "enum", List.of("a", "b")))
            .build(),
        InputConversionTestCase.builder()
            .input(StringInput.builder().type(Type.STRING).build())
            .expectedSchema(Map.of("type", "string"))
            .build(),
        InputConversionTestCase.builder()
            .input(StringInput.builder().type(Type.STRING).validator("^[a-z]+$").build())
            .expectedSchema(Map.of("type", "string", "pattern", "^[a-z]+$"))
            .build(),
        InputConversionTestCase.builder()
            .input(TimeInput.builder().type(Type.TIME).build())
            .expectedSchema(Map.of("type", "string", "format", "time"))
            .build(),
        InputConversionTestCase.builder()
            .input(TimeInput.builder().type(Type.TIME).after(LocalTime.of(9, 0)).before(LocalTime.of(17, 0)).build())
            .expectedSchema(Map.of("type", "string", "format", "time", "minimum", "09:00", "maximum", "17:00"))
            .build(),
        InputConversionTestCase.builder()
            .input(URIInput.builder().type(Type.URI).build())
            .expectedSchema(Map.of("type", "string", "format", "uri"))
            .build(),
        InputConversionTestCase.builder()
            .input(YamlInput.builder().type(Type.YAML).build())
            .expectedSchema(Map.of("type", "object", "additionalProperties", true))
            .build()
    );

    @Builder
    private record InputConversionTestCase(Input<?> input, Map<String, Object> expectedSchema) {}

    @Test
    void shouldThrowIllegalArgumentExceptionWhenConvertingJsonInputWithInvalidJsonSchema() {
        JsonInput input = JsonInput.builder().type(Type.JSON).jsonSchema("not valid json").build();

        assertThatThrownBy(() -> mapper.convert(input))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid JSON schema for input");
    }

    @Test
    void shouldIncludeDescriptionInSchemaWhenConvertingInputWithDescription() {
        StringInput input = StringInput.builder().type(Type.STRING).description("A test input").build();

        assertThat(mapper.convert(input)).containsEntry("description", "A test input");
    }

    @ParameterizedTest
    @FieldSource("outputConversionTestCases")
    void shouldReturnCorrectJsonSchemaWhenConvertingOutput(OutputConversionTestCase testCase) {
        assertThat(mapper.convert(testCase.output())).isEqualTo(testCase.expectedSchema());
    }

    final static List<OutputConversionTestCase> outputConversionTestCases = List.of(
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.STRING).value("v").build())
            .expectedSchema(Map.of("type", "string"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.STRING).value("v").description("An output").build())
            .expectedSchema(Map.of("type", "string", "description", "An output"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.INT).value("v").build())
            .expectedSchema(Map.of("type", "integer"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.FLOAT).value("v").build())
            .expectedSchema(Map.of("type", "number"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.BOOL).value("v").build())
            .expectedSchema(Map.of("type", "boolean"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.DATETIME).value("v").build())
            .expectedSchema(Map.of("type", "string"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.DATE).value("v").build())
            .expectedSchema(Map.of("type", "string"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.TIME).value("v").build())
            .expectedSchema(Map.of("type", "string"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.DURATION).value("v").build())
            .expectedSchema(Map.of("type", "string"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.FILE).value("v").build())
            .expectedSchema(Map.of("type", "string"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.JSON).value("v").build())
            .expectedSchema(Map.of("type", "object"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.URI).value("v").build())
            .expectedSchema(Map.of("type", "string"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.SECRET).value("v").build())
            .expectedSchema(Map.of("type", "string"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.ARRAY).value("v").build())
            .expectedSchema(Map.of("type", "array"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.MULTISELECT).value("v").build())
            .expectedSchema(Map.of("type", "array"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.YAML).value("v").build())
            .expectedSchema(Map.of("type", "object"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.EMAIL).value("v").build())
            .expectedSchema(Map.of("type", "string"))
            .build(),
        OutputConversionTestCase.builder()
            .output(Output.builder().id("o").type(Type.SELECT).value("v").build())
            .expectedSchema(Map.of("type", "string"))
            .build()
    );

    @Builder
    private record OutputConversionTestCase(Output output, Map<String, Object> expectedSchema) {}

    @Test
    void shouldMapAllHintsCorrectlyWhenBuildingToolWithAnnotations() {
        McpToolTrigger trigger = buildTrigger(new McpToolTrigger.Annotations(true, false, false, true, false));

        McpSchema.Tool tool = mapper.buildTool(buildFlow(List.of()), trigger);

        assertThat(tool.annotations()).isNotNull();
        assertThat(tool.annotations().readOnlyHint()).isTrue();
        assertThat(tool.annotations().openWorldHint()).isFalse();
        assertThat(tool.annotations().destructiveHint()).isFalse();
        assertThat(tool.annotations().idempotentHint()).isTrue();
    }

    @Test
    void shouldSetNameAndTitleWhenBuildingToolWithTriggerMetadata() {
        McpToolTrigger trigger = buildTrigger(new McpToolTrigger.Annotations(false, false, false, false, false));

        McpSchema.Tool tool = mapper.buildTool(buildFlow(List.of()), trigger);

        assertThat(tool.name()).isEqualTo("my-tool");
        assertThat(tool.title()).isEqualTo("My Tool");
    }

    @Test
    void shouldReflectAllInputsInSchemaWhenBuildingToolForFlowWithInputs() {
        List<Input<?>> inputs = List.of(
            StringInput.builder().id("name").type(Type.STRING).required(true).build(),
            IntInput.builder().id("count").type(Type.INT).required(false).build()
        );

        McpSchema.Tool tool = mapper.buildTool(buildFlow(inputs), buildTrigger(new McpToolTrigger.Annotations(false, false, false, false, false)));

        assertThat(tool.inputSchema().properties()).containsKeys("name", "count");
        assertThat(tool.inputSchema().required()).containsExactly("name");
    }

    private McpToolTrigger buildTrigger(McpToolTrigger.Annotations annotations) {
        return McpToolTrigger.builder()
            .id("t").type(McpToolTrigger.class.getName())
            .toolName("my-tool").title("My Tool").toolDescription("desc")
            .annotations(annotations)
            .mcpServer("default")
            .build();
    }

    private Flow buildFlow(List<Input<?>> inputs) {
        return Flow.builder()
            .id(IdUtils.create())
            .namespace("test")
            .tasks(List.of(
                Return.builder()
                    .id("task").type(Return.class.getName())
                    .format(Property.ofValue("test"))
                    .build()
            ))
            .inputs(inputs)
            .build();
    }
}
