package io.kestra.core.models.property;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.event.Level;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Plugin
public class DynamicPropertyExampleTask extends Task implements RunnableTask<DynamicPropertyExampleTask.Output> {
    @NotNull
    private Property<Integer> number;

    @NotNull
    private Property<String> string;

    @NotNull
    @Builder.Default
    private Property<String> withDefault = Property.of("Default Value");

    @NotNull
    @Builder.Default
    private Property<Level> level = Property.of(Level.INFO);

    @NotNull
    private Property<Duration> someDuration;

    @NotNull
    private Property<List<String>> items;

    @NotNull
    private Property<Map<String, String>> properties;


    @Override
    public Output run(RunContext runContext) throws Exception {
        String value = String.format(
                "%s - %s - %s - %s",
                string.as(runContext, String.class),
                number.as(runContext, Integer.class),
                withDefault.as(runContext, String.class),
                someDuration.as(runContext, Duration.class)
            );

        Level level = this.level.as(runContext, Level.class);

        List<String> list = items.asList(runContext, String.class);

        Map<String, String> map = properties.asMap(runContext, String.class, String.class);

        return Output.builder()
            .value(value)
            .level(level)
            .list(list)
            .map(map)
            .build();
    }

    @SuperBuilder(toBuilder = true)
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String value;
        private Level level;
        private List<String> list;
        private Map<String, String> map;
    }
}