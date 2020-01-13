package org.kestra.core.plugins;

import lombok.*;
import org.kestra.core.models.listeners.Condition;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.storages.StorageInterface;

import java.util.List;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@Builder
public class RegisteredPlugin {
    private ExternalPlugin externalPlugin;
    private ClassLoader classLoader;
    private List<Class<? extends Task>> tasks;
    private List<Class<? extends Condition>> conditions;
    private List<Class<?>> controllers;
    private List<Class<? extends StorageInterface>> storages;

    public boolean isValid() {
        return tasks.size() > 0 || conditions.size() > 0 || controllers.size() > 0 || storages.size() > 0;
    }
}
