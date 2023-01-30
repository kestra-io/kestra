package io.kestra.core.plugins;

import com.github.jknack.handlebars.internal.lang3.ObjectUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.io.FilenameUtils;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.storages.StorageInterface;

import java.nio.file.Path;
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

    private final List<String> guides;

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
        return allClassGrouped()
            .entrySet()
            .stream()
            .flatMap(map -> map.getValue().stream())
            .collect(Collectors.toList());
    }

    @SuppressWarnings("rawtypes")
    public Map<String, List<Class>> allClassGrouped() {
        Map<String, List<Class>> result = new HashMap<>();

        result.put("tasks", Arrays.asList(this.getTasks().toArray(Class[]::new)));
        result.put("triggers", Arrays.asList(this.getTriggers().toArray(Class[]::new)));
        result.put("conditions", Arrays.asList(this.getConditions().toArray(Class[]::new)));
        result.put("controllers", Arrays.asList(this.getControllers().toArray(Class[]::new)));
        result.put("storages", Arrays.asList(this.getStorages().toArray(Class[]::new)));

        return result;
    }

    public String title() {
        return ObjectUtils.firstNonNull(
            this.getManifest() != null ? this.getManifest().getMainAttributes().getValue("X-Kestra-Title") : null,
            this.getExternalPlugin() != null ? FilenameUtils.getBaseName(this.getExternalPlugin().getLocation().getPath()) : null,
            "Core"
        );
    }

    public String group() {
        return this.getManifest() == null ? null : this.getManifest().getMainAttributes().getValue("X-Kestra-Group");
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
