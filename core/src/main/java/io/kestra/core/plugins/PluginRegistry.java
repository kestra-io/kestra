package io.kestra.core.plugins;

import jakarta.inject.Singleton;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@EqualsAndHashCode
@ToString
@Singleton
@Slf4j
public class PluginRegistry {
    private final List<RegisteredPlugin> plugins = new CopyOnWriteArrayList<>();
    private final Map<String, RegisteredPlugin> pluginsByClass = new ConcurrentHashMap<>();
    @Setter
    private Runnable cacheCleaner;

    public Optional<RegisteredPlugin> find(String name) {
        if (pluginsByClass.containsKey(name)) {
            return Optional.of(pluginsByClass.get(name));
        }

        return Optional.empty();
    }

    public void addPlugin(RegisteredPlugin registeredPlugin) {
        log.info("Adding plugin '{}'", registeredPlugin.getExternalPlugin().getLocation().getFile());

        this.plugins.add(registeredPlugin);
        this.pluginsByClass.putAll(
            Stream.of(registeredPlugin)
                .flatMap(plugin -> Stream.of(
                        plugin.getTasks()
                            .stream()
                            .map(r -> new AbstractMap.SimpleEntry<>(r.getName(), plugin)),
                        plugin.getTriggers()
                            .stream()
                            .map(r -> new AbstractMap.SimpleEntry<>(r.getName(), plugin)),
                        plugin.getConditions()
                            .stream()
                            .map(r -> new AbstractMap.SimpleEntry<>(r.getName(), plugin)),
                        plugin.getControllers()
                            .stream()
                            .map(r -> new AbstractMap.SimpleEntry<>(r.getName(), plugin))
                    ).flatMap(i -> i)
                ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a1, a2) -> a1))
        );

        cleanCache();
    }

    public void removePlugin(String filePath) {
        log.info("Remove plugin '{}'", filePath);

        this.plugins
            .stream()
            .filter(plugin -> plugin.getExternalPlugin().getLocation().getFile().equals(filePath))
            .findFirst()
            .ifPresent(plugin -> {
                 plugin.close();

                 this.plugins.remove(plugin);
            });

        this.pluginsByClass
            .entrySet()
            .removeIf(entry -> entry.getValue().getExternalPlugin().getLocation().getFile().equals(filePath));

        cleanCache();
    }

    private void cleanCache() {
        if (this.cacheCleaner != null) {
            this.cacheCleaner.run();
        }
    }
}
