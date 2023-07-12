package io.kestra.core.plugins;

import jakarta.inject.Singleton;
import lombok.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@Singleton
public class PluginRegistry {
    private List<RegisteredPlugin> plugins;
    private Map<String, RegisteredPlugin> pluginsByClass;
    @Setter
    private Runnable cacheCleaner;

    public PluginRegistry() {
        this.plugins = new ArrayList<>();
        this.pluginsByClass = new HashMap<>();
    }

    public Optional<RegisteredPlugin> find(String name) {
        if (pluginsByClass.containsKey(name)) {
            return Optional.of(pluginsByClass.get(name));
        }

        return Optional.empty();
    }

    public void addPlugin(RegisteredPlugin registeredPlugin) {
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

        if(this.cacheCleaner != null){
            this.cacheCleaner.run();
        }
    }
}
