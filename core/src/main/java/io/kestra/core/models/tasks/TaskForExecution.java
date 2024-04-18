package io.kestra.core.models.tasks;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@Builder
@Getter
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class TaskForExecution implements TaskInterface {
    @NotNull
    @NotBlank
    @Pattern(regexp="^[a-zA-Z0-9][a-zA-Z0-9_-]*")
    protected String id;

    @NotNull
    @NotBlank
    @Pattern(regexp="\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*")
    protected String type;

    public static TaskForExecution of(Task task) {
        return TaskForExecution.builder()
            .id(task.getId())
            .type(task.getType())
            .build();
    }
}
