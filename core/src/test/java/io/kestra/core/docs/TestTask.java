package io.kestra.core.docs;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Test Task",
    description = """
        This is a test task that demonstrates all possible documentation features.
        It includes various properties, outputs, metrics, and examples.
        
        ## Features
        - Multiple property types
        - Required and optional properties
        - Dynamic properties
        - Nested objects
        - Metrics
        - Examples
        - Deprecated features
        - Beta features
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Basic usage",
            code = {
                "id: test",
                "type: io.kestra.core.docs.TestTask",
                "stringProp: hello",
                "intProp: 42",
                "boolProp: true",
                "durationProp: PT1H",
                "listProp:",
                "  - item1",
                "  - item2",
                "mapProp:",
                "  key1: value1",
                "  key2: value2",
                "nestedProp:",
                "  name: test",
                "  value: 123"
            }
        ),
        @Example(
            title = "Advanced usage with dynamic properties",
            code = {
                "id: test",
                "type: io.kestra.core.docs.TestTask",
                "stringProp: {{ inputs.string }}",
                "intProp: {{ inputs.number }}",
                "boolProp: {{ inputs.boolean }}",
                "durationProp: {{ inputs.duration }}",
                "listProp: {{ inputs.list }}",
                "mapProp: {{ inputs.map }}",
                "nestedProp:",
                "  name: {{ inputs.name }}",
                "  value: {{ inputs.value }}"
            },
            full = true
        )
    }
)
public class TestTask extends Task implements RunnableTask<TestTask.Output> {
    
    @PluginProperty(dynamic = true)
    @Schema(
        title = "String Property",
        description = "A string property that can be dynamic",
        defaultValue = "default"
    )
    private String stringProp;

    @PluginProperty
    @Schema(
        title = "Integer Property",
        description = "An integer property",
        minimum = "0",
        maximum = "100"
    )
    private Integer intProp;

    @PluginProperty
    @Schema(
        title = "Boolean Property",
        description = "A boolean property",
        defaultValue = "false"
    )
    private Boolean boolProp;

    @PluginProperty
    @Schema(
        title = "Duration Property",
        description = "A duration property",
        defaultValue = "PT1H"
    )
    private Duration durationProp;

    @PluginProperty(dynamic = true)
    @Schema(
        title = "List Property",
        description = "A list property that can be dynamic"
    )
    private List<String> listProp;

    @PluginProperty(dynamic = true)
    @Schema(
        title = "Map Property",
        description = "A map property that can be dynamic"
    )
    private Map<String, String> mapProp;

    @PluginProperty
    @Schema(
        title = "Nested Property",
        description = "A nested object property"
    )
    private NestedObject nestedProp;

    @Override
    public Output run(RunContext runContext) throws Exception {
        return Output.builder()
            .stringOutput(stringProp)
            .intOutput(intProp)
            .boolOutput(boolProp)
            .durationOutput(durationProp)
            .listOutput(listProp)
            .mapOutput(mapProp)
            .nestedOutput(nestedProp)
            .build();
    }

    @Getter
    @Builder
    @ToString
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NestedObject {
        @Schema(
            title = "Name",
            description = "The name of the nested object"
        )
        private String name;

        @Schema(
            title = "Value",
            description = "The value of the nested object",
            minimum = "0"
        )
        private Integer value;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "String Output",
            description = "The string output"
        )
        private String stringOutput;

        @Schema(
            title = "Integer Output",
            description = "The integer output"
        )
        private Integer intOutput;

        @Schema(
            title = "Boolean Output",
            description = "The boolean output"
        )
        private Boolean boolOutput;

        @Schema(
            title = "Duration Output",
            description = "The duration output"
        )
        private Duration durationOutput;

        @Schema(
            title = "List Output",
            description = "The list output"
        )
        private List<String> listOutput;

        @Schema(
            title = "Map Output",
            description = "The map output"
        )
        private Map<String, String> mapOutput;

        @Schema(
            title = "Nested Output",
            description = "The nested object output"
        )
        private NestedObject nestedOutput;
    }
} 