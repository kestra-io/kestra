package org.kestra.core.plugins;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.storages.StorageInterface;

import java.util.*;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@Builder
public class RegisteredPlugin {
    private final ExternalPlugin externalPlugin;
    private final Manifest manifest;
    private final ClassLoader classLoader;
    private final List<Class<? extends Task>> tasks;
    private final List<Class<? extends AbstractTrigger>> triggers;
    private final List<Class<? extends Condition>> conditions;
    private final List<Class<?>> controllers;
    private final List<Class<? extends StorageInterface>> storages;

    public boolean isValid() {
        return tasks.size() > 0 || triggers.size() > 0 || conditions.size() > 0 || controllers.size() > 0 || storages.size() > 0;
    }

    public boolean hasClass(String cls) {
        return allClass()
            .stream()
            .anyMatch(r -> r.getName().equals(cls));
    }

    @SuppressWarnings("rawtypes")
    public Optional<Class> findClass(String cls) {
        return allClass()
            .stream()
            .filter(r -> r.getName().equals(cls))
            .findFirst();
    }

    @SuppressWarnings("rawtypes")
    public Class baseClass(String cls) {
        if (this.getTasks().stream().anyMatch(r -> r.getName().equals(cls))) {
            return Task.class;
        }

        if (this.getTriggers().stream().anyMatch(r -> r.getName().equals(cls))) {
            return AbstractTrigger.class;
        }

        if (this.getConditions().stream().anyMatch(r -> r.getName().equals(cls))) {
            return Condition.class;
        }

        if (this.getStorages().stream().anyMatch(r -> r.getName().equals(cls))) {
            return StorageInterface.class;
        }

        if (this.getTasks().stream().anyMatch(r -> r.getName().equals(cls))) {
            return Task.class;
        }

        throw new IllegalArgumentException("Unable to find base class from '" + cls + "'");
    }

    @SuppressWarnings("rawtypes")
    public List<Class> allClass() {
        List<Class> result = new ArrayList<>();

        result.addAll(Arrays.asList(this.getTasks().toArray(Class[]::new)));
        result.addAll(Arrays.asList(this.getTriggers().toArray(Class[]::new)));
        result.addAll(Arrays.asList(this.getConditions().toArray(Class[]::new)));
        result.addAll(Arrays.asList(this.getControllers().toArray(Class[]::new)));
        result.addAll(Arrays.asList(this.getStorages().toArray(Class[]::new)));

        return result;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();

        if (this.getExternalPlugin() != null) {
            b.append("Found plugin on path: ").append(this.getExternalPlugin().getLocation()).append(" ");
        } else {
            b.append("Core plugin: ");
        }

        if (!this.getTasks().isEmpty()) {
            b.append("[Tasks: ");
            b.append(this.getTasks().stream().map(Class::getName).collect(Collectors.joining(", ")));
            b.append("] ");
        }

        if (!this.getTriggers().isEmpty()) {
            b.append("[Triggers: ");
            b.append(this.getTriggers().stream().map(Class::getName).collect(Collectors.joining(", ")));
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
