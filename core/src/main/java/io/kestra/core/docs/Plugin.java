package io.kestra.core.docs;

import io.kestra.core.models.annotations.PluginSubGroup;
import io.kestra.core.plugins.RegisteredPlugin;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

@NoArgsConstructor
@Data
public class Plugin {
    private String name;
    private String title;
    private String description;
    private String license;
    private String longDescription;
    private String group;
    private String version;
    private Map<String, String> manifest;
    private List<String> guides;
    private List<String> aliases;
    private List<PluginElementMetadata> tasks;
    private List<PluginElementMetadata> triggers;
    private List<PluginElementMetadata> conditions;
    private List<PluginElementMetadata> controllers;
    private List<PluginElementMetadata> storages;
    private List<PluginElementMetadata> secrets;
    private List<PluginElementMetadata> taskRunners;
    private List<PluginElementMetadata> apps;
    private List<PluginElementMetadata> appBlocks;
    private List<PluginElementMetadata> charts;
    private List<PluginElementMetadata> dataFilters;
    private List<PluginElementMetadata> dataFiltersKPI;
    private List<PluginElementMetadata> logExporters;
    private List<PluginElementMetadata> additionalPlugins;
    private List<PluginSubGroup.PluginCategory> categories;
    private String subGroup;

    public static Plugin of(RegisteredPlugin registeredPlugin, @Nullable String subgroup) {
        Plugin plugin = new Plugin();
        plugin.name = registeredPlugin.name();
        PluginSubGroup subGroupInfos = null;
        if (subgroup == null) {
            plugin.title = registeredPlugin.title();
        } else {
            subGroupInfos = registeredPlugin.allClass().stream()
                .filter(c -> c.getPackageName().contains(subgroup))
                .min(Comparator.comparingInt(a -> a.getPackageName().length()))
                .map(clazz -> clazz.getPackage().getDeclaredAnnotation(PluginSubGroup.class))
                .orElseThrow();
            plugin.title = !subGroupInfos.title().isEmpty() ? subGroupInfos.title() : subgroup.substring(subgroup.lastIndexOf('.') + 1);
        }
        plugin.group = registeredPlugin.group();
        plugin.description = subGroupInfos != null && !subGroupInfos.description().isEmpty() ? subGroupInfos.description() : registeredPlugin.description();
        plugin.license = registeredPlugin.license();
        plugin.longDescription = registeredPlugin.longDescription();
        plugin.version = registeredPlugin.version();
        plugin.guides = registeredPlugin.getGuides();
        plugin.aliases = registeredPlugin.getAliases().values().stream().map(Map.Entry::getKey).toList();
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
        plugin.categories = subGroupInfos != null ?
            Arrays.stream(subGroupInfos.categories()).toList() :
            registeredPlugin
                .allClass()
                .stream()
                .map(clazz -> clazz.getPackage().getDeclaredAnnotation(PluginSubGroup.class))
                .filter(Objects::nonNull)
                .flatMap(r -> Arrays.stream(r.categories()))
                .distinct()
                .toList();

        plugin.subGroup = subgroup;

        Predicate<Class<?>> packagePredicate = c -> subgroup == null || c.getPackageName().equals(subgroup);
        plugin.tasks = filterAndGetTypeWithMetadata(registeredPlugin.getTasks(), packagePredicate);
        plugin.triggers = filterAndGetTypeWithMetadata(registeredPlugin.getTriggers(), packagePredicate);
        plugin.conditions = filterAndGetTypeWithMetadata(registeredPlugin.getConditions(), packagePredicate);
        plugin.storages = filterAndGetTypeWithMetadata(registeredPlugin.getStorages(), packagePredicate);
        plugin.secrets = filterAndGetTypeWithMetadata(registeredPlugin.getSecrets(), packagePredicate);
        plugin.taskRunners = filterAndGetTypeWithMetadata(registeredPlugin.getTaskRunners(), packagePredicate);
        plugin.apps = filterAndGetTypeWithMetadata(registeredPlugin.getApps(), packagePredicate);
        plugin.appBlocks = filterAndGetTypeWithMetadata(registeredPlugin.getAppBlocks(), packagePredicate);
        plugin.charts = filterAndGetTypeWithMetadata(registeredPlugin.getCharts(), packagePredicate);
        plugin.dataFilters = filterAndGetTypeWithMetadata(registeredPlugin.getDataFilters(), packagePredicate);
        plugin.dataFiltersKPI = filterAndGetTypeWithMetadata(registeredPlugin.getDataFiltersKPI(), packagePredicate);
        plugin.logExporters = filterAndGetTypeWithMetadata(registeredPlugin.getLogExporters(), packagePredicate);
        plugin.additionalPlugins = filterAndGetTypeWithMetadata(registeredPlugin.getAdditionalPlugins(), packagePredicate);

        return plugin;
    }

    /**
     * Filters the given list of class all internal Plugin, as well as, all legacy org.kestra classes.
     * Those classes are only filtered from the documentation to ensure backward compatibility.
     *
     * @param list              The list of classes?
     * @return a filtered streams.
     */
    private static List<PluginElementMetadata> filterAndGetTypeWithMetadata(final List<? extends Class<?>> list, Predicate<Class<?>> clazzFilter) {
        return list
            .stream()
            .filter(not(io.kestra.core.models.Plugin::isInternal))
            .filter(clazzFilter)
            .filter(c -> !c.getName().startsWith("org.kestra."))
            .map(c -> {
                Schema schema = c.getAnnotation(Schema.class);

                var title = Optional.ofNullable(schema).map(Schema::title).filter(t -> !t.isEmpty()).orElse(null);
                var description = Optional.ofNullable(schema).map(Schema::description).filter(d -> !d.isEmpty()).orElse(null);
                var deprecated = io.kestra.core.models.Plugin.isDeprecated(c) ? true : null;

                return new PluginElementMetadata(c.getName(), deprecated, title, description);
            })
            .toList();
    }

    public record PluginElementMetadata(String cls, Boolean deprecated, String title, String description) {}
}
