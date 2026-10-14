package io.kestra.core.tasks.test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;

import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Emits a nested map holding an explicit null, as data-transform tasks do when configured to keep nulls.
 * The null is map <em>content</em>; compare {@link NullOutputTask}, whose null is a bean <em>property</em>.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Plugin
public class NullContentOutputTask extends Task implements RunnableTask<NullContentOutputTask.Output> {

    @Override
    public Output run(RunContext runContext) throws Exception {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("a", 1);
        record.put("b", null);

        return Output.builder().records(List.of(record)).build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private List<Object> records;
    }
}
