package io.kestra.plugin.templates;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
@Schema(
    title = "Short description for this task",
    description = "Full description of this task"
)
@Plugin(
    examples = {
        @Example(code = "format: top"),
        @Example(full = true, title = "My 2nd example", code = "format: 2nd")
    }
)
public class ExampleTask extends AbstractTask implements ExampleInterface {
    @Schema(title = "a property childOutput")
    @PluginProperty(dynamic = true)
    protected PropertyChildInput childInput;

    @PluginProperty
    private ExampleEnum exampleEnum;

    @PluginProperty(dynamic = true)
    @NotNull
    @Schema(
        title = "The source of the published data.",
        description = "Can be an internal storage URI, a list of Example, or a single Example.",
        anyOf = {String.class, Example[].class, Example.class}
    )
    private Object from;

    private String example;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String render = runContext.render(format);
        runContext.logger().debug(render);

        return Output.builder()
            .childOutput(OutputChild.builder()
                .value(StringUtils.reverse(render))
                .outputChildMap(Map.of(render, OutputMap.builder().code(render.length()).display(render).build()))
                .build())
            .build();
    }

    @Builder
    @Getter
    public static class PropertyChildInput {
        @Schema(title = "List of string")
        @Valid
        @NotEmpty
        protected final List<String> list;

        @Schema(title = "List of string")
        @Valid
        @NotEmpty
        protected final List<PropertyChildSubInput> listObject;

        protected final PropertyChildEnum childEnum;

        @Schema(title = "List of string")
        @PluginProperty(dynamic = true, additionalProperties = Float.class)
        @Valid
        @NotEmpty
        protected final Map<String, Float> map;
    }

    @Builder
    @Getter
    public static class PropertyChildSubInput {
        private final String name;
        private final PropertyChildSubInput child;
    }

    public enum PropertyChildEnum {
        VALUE_1,
        VALUE_2
    }
}
