package org.kestra.core.services;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.TaskDefault;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.serializers.JacksonMapper;
import org.kestra.core.utils.MapUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TaskDefaultService {
    @Nullable
    @Inject
    protected GlobalDefault globalDefault;

    public <T extends Task> T injectDefaults(T task, Flow flow) {
        Map<String, Object> taskAsMap = JacksonMapper.toMap(task);

        taskAsMap = defaults(task, taskAsMap, flow.getTaskDefaults());
        if (globalDefault != null) {
            taskAsMap = defaults(task, taskAsMap, globalDefault.getDefaults());
        }

        //noinspection unchecked
        return (T) JacksonMapper.toMap(taskAsMap, task.getClass());
    }

    protected <T extends Task> List<TaskDefault> find(T task, List<TaskDefault> defaults) {
        return (defaults == null ? new ArrayList<TaskDefault>() : defaults)
            .stream()
            .filter(t -> t.getType().equals(task.getType()))
            .collect(Collectors.toList());
    }

    protected <T extends Task> Map<String, Object> defaults(T task, Map<String, Object> taskAsMap, List<TaskDefault> defaults) {
        for (TaskDefault current : find(task, defaults)) {
            taskAsMap = defaults(current.getValues(), taskAsMap);
        }

        return taskAsMap;
    }

    protected Map<String, Object> defaults(Map<String, Object> task, Map<String, Object> defaults) {
        return MapUtils.merge(task, defaults);
    }

    @ConfigurationProperties(value = "kestra.tasks")
    @Getter
    public static class GlobalDefault {
        List<TaskDefault> defaults;
    }
}
