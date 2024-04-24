package io.kestra.core.models.tasks;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
public class TaskForExecution implements TaskInterface {
    protected String id;

    protected String type;

    public static TaskForExecution of(Task task) {
        return TaskForExecution.builder()
            .id(task.getId())
            .type(task.getType())
            .build();
    }
}
