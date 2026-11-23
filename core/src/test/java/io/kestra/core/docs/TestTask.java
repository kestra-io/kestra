package io.kestra.core.docs;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Metric;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.enums.MonacoLanguages;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Hidden
@Schema(
    title = "Documentation test task",
    description = "A task fixture containing every feature rendered by the Markdown documentation generator."
)
@Plugin(
    beta = true,
    examples = {
        @Example(
            title = "Minimal example",
            code = {
                "legacyDynamicString: value"
            }
        ),
        @Example(
            title = "Full example",
            code = {
                "id: full_example",
                "type: io.kestra.core.docs.TestTask",
                "legacyDynamicString: value"
            },
            full = true
        )
    },
    metrics = {
        @Metric(name = "records", type = "counter", unit = "records", description = "Number of processed records."),
        @Metric(name = "duration", type = "timer")
    }
)
public class TestTask extends Task implements RunnableTask<TestTask.TestOutput> {
    @PluginProperty(
        dynamic = true,
        beta = true,
        internalStorageURI = true,
        group = "advanced",
        language = MonacoLanguages.YAML,
        secret = true,
        index = 1
    )
    @Schema(title = "Legacy dynamic string", description = "A required old-style dynamic string.")
    @NotBlank
    @Size(min = 2, max = 10)
    @Pattern(regexp = "[a-z]+")
    private String legacyDynamicString;

    @PluginProperty
    @Schema(title = "Legacy non-dynamic string", description = "An old-style non-dynamic string.")
    private String legacyNonDynamicString;

    @Builder.Default
    @Schema(title = "Boolean with default", description = "An optional boolean.")
    private Property<Boolean> booleanWithDefault = Property.ofValue(false);

    @Schema(title = "Items", description = "A bounded list of strings.")
    @Size(min = 1, max = 3)
    private Property<List<String>> items;

    @Schema(title = "Named objects", description = "Nested objects keyed by name.")
    private Property<Map<String, NestedObject>> namedObjects;

    @Schema(title = "Mode", description = "An enumerated mode.")
    private Property<Mode> mode;

    @Schema(title = "Formatted value", description = "A value with an explicit format.", format = "email")
    private Property<String> formattedValue;

    @Deprecated
    @Schema(title = "Deprecated property", description = "A deprecated option.")
    private Property<String> deprecatedProperty;

    @Override
    public TestOutput run(RunContext runContext) {
        return TestOutput.builder()
            .value(legacyDynamicString)
            .build();
    }

    enum Mode {
        FIRST,
        SECOND
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class NestedObject {
        @NotBlank
        @Schema(title = "Nested name", description = "The required nested name.")
        private Property<String> name;

        @Builder.Default
        @Schema(title = "Nested value", description = "An optional nested value.")
        private Property<String> value = Property.ofValue("nested-default");

        @Schema(title = "Inclusive integer", description = "An integer with inclusive bounds.")
        @Min(1)
        @Max(100)
        private Integer inclusiveInteger;

        @Schema(title = "Exclusive decimal", description = "A decimal with exclusive bounds.")
        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax(value = "1", inclusive = false)
        private BigDecimal exclusiveDecimal;
    }

    @Builder
    @Getter
    static class TestOutput implements Output {
        @NotNull
        @Schema(title = "Output value", description = "The generated value.")
        private String value;

        @Schema(title = "Output count", description = "The generated count.", defaultValue = "0")
        private Integer count;
    }
}
