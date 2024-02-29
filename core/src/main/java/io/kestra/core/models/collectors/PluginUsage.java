package io.kestra.core.models.collectors;

import io.kestra.core.contexts.KestraApplicationContext;
import io.kestra.core.plugins.PluginRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuperBuilder
@Getter
@Jacksonized
@Introspected
public class PluginUsage {
    private final Map<String, String> manifest;

    public static List<PluginUsage> of(ApplicationContext applicationContext) {
        if (!(applicationContext instanceof KestraApplicationContext)) {
            return Collections.emptyList();
        }

        KestraApplicationContext context = (KestraApplicationContext) applicationContext;
        PluginRegistry pluginRegistry = context.getPluginRegistry();

        if (pluginRegistry == null) {
            return List.of();
        }

        return pluginRegistry.getPlugins()
            .stream()
            .map(registeredPlugin -> PluginUsage.builder()
                .manifest(registeredPlugin
                    .getManifest()
                    .getMainAttributes()
                    .entrySet()
                    .stream()
                    .map(e -> new AbstractMap.SimpleEntry<>(
                        e.getKey().toString(),
                        e.getValue().toString()
                    ))
                    .filter(e -> e.getKey().startsWith("X-Kestra"))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                )
                .build()
            )
            .collect(Collectors.toList());
    }
}
