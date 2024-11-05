package io.kestra.core.plugins;

import io.kestra.core.app.AppBlockInterface;
import io.kestra.core.app.AppPluginInterface;
import io.kestra.core.models.annotations.PluginSubGroup;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.secret.SecretPluginInterface;
import io.kestra.core.storages.StorageInterface;
import lombok.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwFunction;

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
    private final List<Class<? extends StorageInterface>> storages;
    private final List<Class<? extends SecretPluginInterface>> secrets;
    private final List<Class<? extends TaskRunner>> taskRunners;
    private final List<Class<? extends AppPluginInterface>> apps;
    private final List<Class<? extends AppBlockInterface>> appBlocks;
    private final List<Class<? extends Chart<?>>> charts;
    private final List<Class<? extends DataFilter<?, ?>>> dataFilters;
    private final List<String> guides;
    // Map<lowercasealias, <Alias, Class>>
    private final Map<String, Map.Entry<String, Class<?>>> aliases;

    public boolean isValid() {
        return !tasks.isEmpty() ||
            !triggers.isEmpty() ||
            !conditions.isEmpty() ||
            !storages.isEmpty() ||
            !secrets.isEmpty() ||
            !taskRunners.isEmpty() ||
            !apps.isEmpty()  ||
            !appBlocks.isEmpty() ||
            !charts.isEmpty() ||
            !dataFilters.isEmpty()
        ;
    }

    public boolean hasClass(String cls) {
        return allClass()
            .stream()
            .anyMatch(r -> r.getName().equals(cls)) || aliases.containsKey(cls.toLowerCase());
    }

    @SuppressWarnings("rawtypes")
    public Optional<Class> findClass(String cls) {
        return allClass()
            .stream()
            .filter(r -> r.getName().equals(cls))
            .findFirst()
            .or(() -> Optional.ofNullable(aliases.get(cls.toLowerCase()).getValue()));
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

        if (this.getSecrets().stream().anyMatch(r -> r.getName().equals(cls))) {
            return SecretPluginInterface.class;
        }

        if (this.getTaskRunners().stream().anyMatch(r -> r.getName().equals(cls))) {
            return TaskRunner.class;
        }

        if (this.getCharts().stream().anyMatch(r -> r.getName().equals(cls))) {
            return Chart.class;
        }

        if (this.getDataFilters().stream().anyMatch(r -> r.getName().equals(cls))) {
            return DataFilter.class;
        }

        if(this.getAliases().containsKey(cls.toLowerCase())) {
            // This is a quick-win, but it may trigger an infinite loop ... or not ...
            return baseClass(this.getAliases().get(cls.toLowerCase()).getValue().getName());
        }

        throw new IllegalArgumentException("Unable to find base class from '" + cls + "'");
    }

    @SuppressWarnings("rawtypes")
    public List<Class> allClass() {
        return allClassGrouped()
            .entrySet()
            .stream()
            .flatMap(map -> map.getValue().stream())
            .toList();
    }

    @SuppressWarnings("rawtypes")
    public Map<String, List<Class>> allClassGrouped() {
        Map<String, List<Class>> result = new HashMap<>();

        result.put("tasks", Arrays.asList(this.getTasks().toArray(Class[]::new)));
        result.put("triggers", Arrays.asList(this.getTriggers().toArray(Class[]::new)));
        result.put("conditions", Arrays.asList(this.getConditions().toArray(Class[]::new)));
        result.put("storages", Arrays.asList(this.getStorages().toArray(Class[]::new)));
        result.put("secrets", Arrays.asList(this.getSecrets().toArray(Class[]::new)));
        result.put("task-runners", Arrays.asList(this.getTaskRunners().toArray(Class[]::new)));
        result.put("apps", Arrays.asList(this.getApps().toArray(Class[]::new)));
        result.put("appBlocks", Arrays.asList(this.getAppBlocks().toArray(Class[]::new)));
        result.put("charts", Arrays.asList(this.getCharts().toArray(Class[]::new)));
        result.put("data-filters", Arrays.asList(this.getDataFilters().toArray(Class[]::new)));

        return result;
    }

//    public Map<String, Map<String,List<Class>>> allClassGroupedBySubGroup() {
//
//    }

    public Set<String> subGroupNames() {
        return allClass()
            .stream()
            .map(clazz -> {
                var pluginSubGroup = clazz.getPackage().getDeclaredAnnotation(PluginSubGroup.class);

                // some plugins declare subgroup for main plugins
                if (clazz.getPackageName().length() == this.group().length()) {
                    pluginSubGroup = null;
                }

                if (pluginSubGroup != null && clazz.getPackageName().startsWith(this.group()) ) {
                    return this.group() + "." + clazz.getPackageName().substring(this.group().length() + 1);
                } else {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    public String name() {
        return ObjectUtils.firstNonNull(
            this.getManifest() == null ? "core" : null,
            this.getManifest() != null ? this.getManifest().getMainAttributes().getValue("X-Kestra-Name") : null,
            "core"
        );
    }

    public String path() {
        return ObjectUtils.firstNonNull(
            this.getManifest() != null ? this.getManifest().getMainAttributes().getValue("X-Kestra-Name") : null,
            this.getExternalPlugin() != null ? FilenameUtils.getBaseName(this.getExternalPlugin().getLocation().getPath()) : null,
            "Core"
        );
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

    public String description() {
        return this.getManifest() == null ? null : this.getManifest().getMainAttributes().getValue("X-Kestra-Description");
    }

    public String license() {
        return this.getManifest() == null ? null : this.getManifest().getMainAttributes().getValue("X-Kestra-License");
    }

    public String longDescription() {
        try (var is = this.getClassLoader().getResourceAsStream("doc/" + this.group() + ".md")) {
            if(is != null) {
                return IOUtils.toString(is, StandardCharsets.UTF_8);
            }
        }
        catch (Exception e) {
            // silently fail
        }

        return null;
    }

    public Map<String, String> guides() throws IOException {
        return this.guides
            .stream()
            .map(throwFunction(s -> new AbstractMap.SimpleEntry<>(
                s,
                IOUtils.toString(Objects.requireNonNull(this.getClassLoader().getResourceAsStream("doc/guides/" + s + ".md")), StandardCharsets.UTF_8)
            )))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public String version() {
        return this.getManifest() == null ? null : this.getManifest().getMainAttributes().getValue("X-Kestra-Version");
    }

    @SneakyThrows
    public String icon(Class<?> cls) {
        InputStream resourceAsStream = Stream
            .of(
                this.getClassLoader().getResourceAsStream("icons/" + cls.getName() + ".svg"),
                this.getClassLoader().getResourceAsStream("icons/" + cls.getPackageName() + ".svg")
            )
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

        if (resourceAsStream != null) {
            return Base64.getEncoder().encodeToString(
                IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8)
            );
        }

        return null;
    }

    @SneakyThrows
    public String icon(String iconName) {
        InputStream resourceAsStream = this.getClassLoader().getResourceAsStream("icons/" + iconName + ".svg");
        if (resourceAsStream != null) {
            return Base64.getEncoder().encodeToString(
                IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8)
            );
        }

        return null;
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

        if (!this.getStorages().isEmpty()) {
            b.append("[Storages: ");
            b.append(this.getStorages().stream().map(Class::getName).collect(Collectors.joining(", ")));
            b.append("] ");
        }

        if (!this.getSecrets().isEmpty()) {
            b.append("[Secrets: ");
            b.append(this.getSecrets().stream().map(Class::getName).collect(Collectors.joining(", ")));
            b.append("] ");
        }

        if (!this.getTaskRunners().isEmpty()) {
            b.append("[Task Runners: ");
            b.append(this.getTaskRunners().stream().map(Class::getName).collect(Collectors.joining(", ")));
            b.append("] ");
        }

        if (!this.getCharts().isEmpty()) {
            b.append("[Charts: ");
            b.append(this.getCharts().stream().map(Class::getName).collect(Collectors.joining(", ")));
            b.append("] ");
        }

        if (!this.getDataFilters().isEmpty()) {
            b.append("[DataFilters: ");
            b.append(this.getDataFilters().stream().map(Class::getName).collect(Collectors.joining(", ")));
            b.append("] ");
        }

        if (!this.getAliases().isEmpty()) {
            b.append("[Aliases: ");
            b.append(this.getAliases().values().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            )));
            b.append("] ");
        }

        return b.toString();
    }
}
