package io.kestra.core.docs;

import io.kestra.core.models.annotations.PluginSubGroup;
import io.kestra.core.plugins.RegisteredPlugin;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

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
    private List<String> taskRunners;
    private List<String> guides;
    private List<PluginSubGroup.PluginCategory> categories;

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
        plugin.categories =  registeredPlugin
            .allClass()
            .stream()
            .map(clazz -> clazz.getPackage().getDeclaredAnnotation(PluginSubGroup.class))
            .filter(Objects::nonNull)
            .flatMap(r -> Arrays.stream(r.categories()))
            .distinct()
            .toList();

        plugin.tasks = filterAndGetClassName(registeredPlugin.getTasks());
        plugin.triggers = filterAndGetClassName(registeredPlugin.getTriggers());
        plugin.conditions = filterAndGetClassName(registeredPlugin.getConditions());
        plugin.storages = filterAndGetClassName(registeredPlugin.getStorages());
        plugin.secrets = filterAndGetClassName(registeredPlugin.getSecrets());
        plugin.taskRunners = filterAndGetClassName(registeredPlugin.getTaskRunners());

        return plugin;
    }

    /**
     * Filters the given list of class all internal Plugin, as well as, all legacy org.kestra classes.
     * Those classes are only filtered from the documentation to ensure backward compatibility.
     *
     * @param list The list of classes?
     * @return  a filtered streams.
     */
    private static List<String> filterAndGetClassName(final List<? extends Class<?>> list) {
        return list
            .stream()
            .filter(not(io.kestra.core.models.Plugin::isInternal))
            .map(Class::getName)
            .filter(c -> !c.startsWith("org.kestra."))
            .toList();
    }
}
