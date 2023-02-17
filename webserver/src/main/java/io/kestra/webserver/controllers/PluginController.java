package io.kestra.webserver.controllers;

import io.kestra.core.docs.ClassPluginDocumentation;
import io.kestra.core.docs.DocumentationGenerator;
import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.services.PluginService;
import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Validated
@Controller("/api/v1/plugins/")
public class PluginController {
    @Inject
    private JsonSchemaGenerator jsonSchemaGenerator;

    @Inject
    private PluginService pluginService;

    @Get(uri = "schemas/{type}")
    @Operation(
        tags = {"Plugins"},
        summary = "Get all json schemas for a type",
        description = "The schema will be output as [http://json-schema.org/draft-07/schema](Json Schema Draft 7)"
    )
    public HttpResponse<Map<String, Object>> schemas(@PathVariable SchemaType type) {
        return HttpResponse.ok()
            .body(this.schemasCache(type))
            .header("Cache-Control", "public, max-age=3600");
    }

    @Cacheable("default")
    protected Map<String, Object> schemasCache(SchemaType type) {
        if (type == SchemaType.flow) {
            return jsonSchemaGenerator.schemas(Flow.class);
        } else if (type == SchemaType.template) {
            return jsonSchemaGenerator.schemas(Template.class);
        } else if (type == SchemaType.task) {
            return jsonSchemaGenerator.schemas(Task.class);
        } else if (type == SchemaType.trigger) {
            return jsonSchemaGenerator.schemas(AbstractTrigger.class);
        } else {
            throw new IllegalArgumentException("Invalid type " + type);
        }
    }

    public enum SchemaType {
        flow,
        template,
        task,
        trigger
    }

    @Get
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Plugins"}, summary = "Get list of plugins")
    public List<Plugin> search() {
        return pluginService
            .allPlugins()
            .stream()
            .map(Plugin::of)
            .collect(Collectors.toList());
    }

    @Get(uri = "icons")
    @Operation(tags = {"Plugins"}, summary = "Get plugins icons")
    public Map<String, PluginIcon> icons() {
        return pluginService
            .allPlugins()
            .stream()
            .flatMap(plugin -> Stream
                .concat(
                    plugin.getTasks().stream(),
                    Stream.concat(
                        plugin.getTriggers().stream(),
                        plugin.getConditions().stream()
                    )
                )
                .map(e -> new AbstractMap.SimpleEntry<>(
                    e.getName(),
                    new PluginIcon(
                        e.getSimpleName(),
                        DocumentationGenerator.icon(plugin, e),
                        FlowableTask.class.isAssignableFrom(e)
                    )
                ))
            )
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a1, a2) -> a1));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Get(uri = "{cls}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Plugins"}, summary = "Get plugin documentation")
    public Doc pluginDocumentation(
        @Parameter(description = "The plugin full class name") @PathVariable String cls
    ) throws IOException {
        ClassPluginDocumentation classPluginDocumentation = pluginDocumentation(
            pluginService.allPlugins(),
            cls
        );

        return new Doc(
            DocumentationGenerator.render(classPluginDocumentation),
            new Schema(
                classPluginDocumentation.getPropertiesSchema(),
                classPluginDocumentation.getOutputsSchema(),
                classPluginDocumentation.getDefs()
            )
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ClassPluginDocumentation<?> pluginDocumentation(List<RegisteredPlugin> plugins, String className)  {
        RegisteredPlugin registeredPlugin = plugins
            .stream()
            .filter(r -> r.hasClass(className))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("Class '" + className + "' doesn't exists "));

        Class cls = registeredPlugin
            .findClass(className)
            .orElseThrow(() -> new NoSuchElementException("Class '" + className + "' doesn't exists "));

        Class baseCls = registeredPlugin
            .baseClass(className);

        return ClassPluginDocumentation.of(jsonSchemaGenerator, registeredPlugin, cls, baseCls);
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

            plugin.tasks = className(filter(registeredPlugin.getTasks()).toArray(Class[]::new));
            plugin.triggers = className(filter(registeredPlugin.getTriggers()).toArray(Class[]::new));
            plugin.conditions = className(filter(registeredPlugin.getConditions()).toArray(Class[]::new));
            plugin.controllers = className(filter(registeredPlugin.getControllers()).toArray(Class[]::new));
            plugin.storages = className(filter(registeredPlugin.getStorages()).toArray(Class[]::new));

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

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Doc {
        String markdown;
        Schema schema;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class PluginIcon {
        String name;
        String icon;
        Boolean flowable;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Schema {
        private Map<String, Object> properties;
        private Map<String, Object> outputs;
        private Map<String, Object> definitions;
    }
}
