package io.kestra.core.models.tasks;

import io.kestra.core.models.annotations.PluginProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class TaskDepend {
    @NotNull
    @PluginProperty
    private Task task;

    @PluginProperty
    private List<String> dependsOn;

}
