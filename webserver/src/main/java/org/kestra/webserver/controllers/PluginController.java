package org.kestra.webserver.controllers;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.validation.Validated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.kestra.core.contexts.KestraApplicationContext;
import org.kestra.core.docs.DocumentationGenerator;
import org.kestra.core.docs.PluginDocumentation;
import org.kestra.core.plugins.PluginRegistry;
import org.kestra.core.plugins.PluginScanner;
import org.kestra.core.plugins.RegisteredPlugin;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;

@Validated
@Controller("/api/v1/plugins/")
public class PluginController {
    @Inject
    private ApplicationContext applicationContext;

    @Get
    public List<Plugin> search() throws HttpStatusException {
        return plugins()
            .stream()
            .map(Plugin::of)
            .collect(Collectors.toList());
    }

    @SuppressWarnings("rawtypes")
    @Get(uri = "{cls}")
    public Doc pluginDocumentation(String cls) throws HttpStatusException, IOException {
        PluginDocumentation pluginDocumentation = pluginDocumentation(plugins(), cls);

        return new Doc(
            pluginDocumentation,
            DocumentationGenerator.render(pluginDocumentation)
        );
    }

    private List<RegisteredPlugin> plugins() {
        if (!(applicationContext instanceof KestraApplicationContext)) {
            throw new RuntimeException("Invalid ApplicationContext");
        }

        KestraApplicationContext context = (KestraApplicationContext) applicationContext;
        PluginRegistry pluginRegistry = context.getPluginRegistry();

        List<RegisteredPlugin> plugins = new ArrayList<>();
        if (pluginRegistry != null) {
            plugins = new ArrayList<>(pluginRegistry.getPlugins());
        }

        PluginScanner corePluginScanner = new PluginScanner(PluginController.class.getClassLoader());
        plugins.add(corePluginScanner.scan());

        return plugins;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private PluginDocumentation<?> pluginDocumentation(List<RegisteredPlugin> plugins, String className) throws IOException {
        RegisteredPlugin registeredPlugin = plugins
            .stream()
            .filter(r -> r.hasClass(className))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("Class '" + className + "' doesn't exists "));

        Class cls = registeredPlugin
            .findClass(className)
            .orElseThrow(() -> new NoSuchElementException("Class '" + className + "' doesn't exists "));


        return PluginDocumentation.of(registeredPlugin, cls);
    }

    @NoArgsConstructor
    @Data
    public static class Plugin {
        private Map<String, String> manifest;
        private List<String> tasks;
        private List<String> triggers;
        private List<String> conditions;
        private List<String> controllers;
        private List<String> storages;

        public static Plugin of(RegisteredPlugin registeredPlugin) {
            Plugin plugin = new Plugin();

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

            plugin.tasks = className(registeredPlugin.getTasks().toArray(Class[]::new));
            plugin.triggers = className(registeredPlugin.getTriggers().toArray(Class[]::new));
            plugin.conditions = className(registeredPlugin.getConditions().toArray(Class[]::new));
            plugin.controllers = className(registeredPlugin.getControllers().toArray(Class[]::new));
            plugin.storages = className(registeredPlugin.getStorages().toArray(Class[]::new));

            return plugin;
        }

        @SuppressWarnings("rawtypes")
        private static <T> List<String> className(Class[] classes) {
            return Arrays.stream(classes)
                .map(Class::getName)
                .collect(Collectors.toList());
        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Doc {
        PluginDocumentation<?> details;
        String markdown;
    }
}
