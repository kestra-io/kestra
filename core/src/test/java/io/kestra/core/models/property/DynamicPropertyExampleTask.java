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

    @NotNull
    private Data<Message> data;


    @Override
    public Output run(RunContext runContext) throws Exception {
        String value = String.format(
            "%s - %s - %s - %s",
            runContext.render(string).as(String.class).orElseThrow(),
            runContext.render(number).as(Integer.class).orElseThrow(),
            runContext.render(withDefault).as(String.class).orElseThrow(),
            runContext.render(someDuration).as(Duration.class).orElseThrow()
        );

        Level level =runContext.render(this.level).as(Level.class).orElseThrow();

        List<String> list = runContext.render(items).asList(String.class);

        Map<String, String> map = runContext.render(properties).asMap(String.class, String.class);

        List<Message> outputMessages = data.flux(runContext, Message.class, message -> Message.fromMap(message))
            .collectList()
            .block();

        return Output.builder()
            .value(value)
            .level(level)
            .list(list)
            .map(map)
            .messages(outputMessages)
            .build();
    }

    @SuperBuilder(toBuilder = true)
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String value;
        private Level level;
        private List<String> list;
        private Map<String, String> map;
        private List<Message> messages;
    }

    @SuperBuilder(toBuilder = true)
    @Getter
    @NoArgsConstructor
    public static class Message {
        private Object key;
        private Object value;

        private static Message fromMap(Map<String, Object> map) {
            return Message.builder()
                .key(map.get("key"))
                .value(map.get("value"))
                .build();
        }
    }
}