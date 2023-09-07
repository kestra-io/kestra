package io.kestra.core.docs;

import io.kestra.core.plugins.RegisteredPlugin;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@NoArgsConstructor
@Data
public class Plugin {
    private String name;
    private String title;
    private String description;
    private String longDescription;
    private String group;
    private String version;
    private Map<String, String> manifest;
    private List<String> tasks;
    private List<String> triggers;
    private List<String> conditions;
    private List<String> controllers;
    private List<String> storages;
    private List<String> secrets;
    private List<String> guides;

    public static Plugin of(RegisteredPlugin registeredPlugin) {
        Plugin plugin = new Plugin();
        plugin.name = registeredPlugin.name();
        plugin.title = registeredPlugin.title();
        plugin.group = registeredPlugin.group();
        plugin.description = registeredPlugin.description();
        plugin.longDescription = registeredPlugin.longDescription();
        plugin.version = registeredPlugin.version();
        plugin.guides = registeredPlugin.getGuides();

        plugin.manifest = registeredPlugin
            .getManifest()
            .getMainAttributes()
            .entrySet()
            .stream()
            .map(e -> new AbstractMap.SimpleEntry<>(
                e.getKey().toString(),
                e.getValue().toString()
            ))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        plugin.tasks = className(filter(registeredPlugin.getTasks()).toArray(Class[]::new));
        plugin.triggers = className(filter(registeredPlugin.getTriggers()).toArray(Class[]::new));
        plugin.conditions = className(filter(registeredPlugin.getConditions()).toArray(Class[]::new));
        plugin.controllers = className(filter(registeredPlugin.getControllers()).toArray(Class[]::new));
        plugin.storages = className(filter(registeredPlugin.getStorages()).toArray(Class[]::new));
        plugin.secrets = className(filter(registeredPlugin.getSecrets()).toArray(Class[]::new));

        return plugin;
    }

    /**
     * we filter from documentation all legacy org.kestra code ...
     * we do it only on docs to avoid remove backward compatibility everywhere (worker, executor...)
     */
    private static <T extends Class<?>> Stream<T> filter(List<T> list) {
        return list
            .stream()
            .filter(s -> !s.getName().startsWith("org.kestra."));
    }

    @SuppressWarnings("rawtypes")
    private static <T> List<String> className(Class[] classes) {
        return Arrays.stream(classes)
            .map(Class::getName)
            .collect(Collectors.toList());
    }
}
