package org.kestra.core.plugins;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.kestra.core.models.listeners.Condition;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.storages.StorageInterface;

import java.util.List;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@Builder
public class RegisteredPlugin {
    private ExternalPlugin externalPlugin;
    private Manifest manifest;
    private ClassLoader classLoader;
    private List<Class<? extends Task>> tasks;
    private List<Class<? extends Condition>> conditions;
    private List<Class<?>> controllers;
    private List<Class<? extends StorageInterface>> storages;

    public boolean isValid() {
        return tasks.size() > 0 || conditions.size() > 0 || controllers.size() > 0 || storages.size() > 0;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();

        b.append("Found plugin on path: ").append(this.getExternalPlugin().getLocation()).append(" ");

        if (!this.getTasks().isEmpty()) {
            b.append("[Tasks: ");
            b.append(this.getTasks().stream().map(Class::getName).collect(Collectors.joining(", ")));
            b.append("] ");
        }

        if (!this.getConditions().isEmpty()) {
            b.append("[Conditions: ");
            b.append(this.getConditions().stream().map(Class::getName).collect(Collectors.joining(", ")));
            b.append("] ");
        }

        if (!this.getControllers().isEmpty()) {
            b.append("[Controllers: ");
            b.append(this.getControllers().stream().map(Class::getName).collect(Collectors.joining(", ")));
            b.append("] ");
        }

        if (!this.getStorages().isEmpty()) {
            b.append("[Storages: ");
            b.append(this.getStorages().stream().map(Class::getName).collect(Collectors.joining(", ")));
            b.append("] ");
        }

        return b.toString();
    }
}
