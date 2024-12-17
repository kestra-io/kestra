package io.kestra.webserver.controllers.api;

import io.kestra.core.docs.*;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.PluginDefault;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Validated
@Controller("/api/v1/plugins/")
public class PluginController {
    private static final String CACHE_DIRECTIVE = "public, max-age=3600";

    @Inject
    protected JsonSchemaGenerator jsonSchemaGenerator;

    @Inject
    private PluginRegistry pluginRegistry;

    @Get(uri = "schemas/{type}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = {"Plugins"},
        summary = "Get all json schemas for a type",
        description = "The schema will be output as [http://json-schema.org/draft-07/schema](Json Schema Draft 7)"
    )
    public HttpResponse<Map<String, Object>> schemas(
        @Parameter(description = "The schema needed") @PathVariable SchemaType type,
        @Parameter(description = "If schema should be an array of requested type") @Nullable @QueryValue(value = "arrayOf", defaultValue = "false") Boolean arrayOf
    ) {
        return HttpResponse.ok()
            .body(this.schemasCache(type, arrayOf))
            .header(HttpHeaders.CACHE_CONTROL, CACHE_DIRECTIVE);
    }

    @Cacheable("default")
    protected Map<String, Object> schemasCache(SchemaType type, boolean arrayOf) {
        if (type == SchemaType.flow) {
            return jsonSchemaGenerator.schemas(Flow.class, arrayOf);
        } else if (type == SchemaType.template) {
            return jsonSchemaGenerator.schemas(Template.class, arrayOf);
        } else if (type == SchemaType.task) {
            return jsonSchemaGenerator.schemas(Task.class, arrayOf);
        } else if (type == SchemaType.trigger) {
            return jsonSchemaGenerator.schemas(AbstractTrigger.class, arrayOf);
        } else if (type == SchemaType.plugindefault) {
            return jsonSchemaGenerator.schemas(PluginDefault.class, arrayOf);
        } else if (type == SchemaType.dashboard) {
            return jsonSchemaGenerator.schemas(Dashboard.class, arrayOf);
        } else {
            throw new IllegalArgumentException("Invalid type " + type);
        }
    }

    @Get(uri = "inputs")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = {"Plugins"},
        summary = "Get all types for an inputs"
    )
    public List<InputType> inputs() throws ClassNotFoundException {
        return Stream.of(Type.values())
            .map(throwFunction(type -> new InputType(type.name(), type.cls().getName())))
            .toList();
    }

    @Get(uri = "inputs/{type}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = {"Plugins"},
        summary = "Get all json schemas for a type",
        description = "The schema will be output as [http://json-schema.org/draft-07/schema](Json Schema Draft 7)"
    )
    public MutableHttpResponse<DocumentationWithSchema> inputSchemas(
        @Parameter(description = "The schema needed") @PathVariable Type type
    ) throws ClassNotFoundException, IOException {
        ClassInputDocumentation classInputDocumentation = this.inputDocumentation(type);

        return HttpResponse.ok()
            .body(new DocumentationWithSchema(
                alertReplacement(DocumentationGenerator.render(classInputDocumentation)),
                new Schema(
                    classInputDocumentation.getPropertiesSchema(),
                    null,
                    classInputDocumentation.getDefs()
                )
            ))
            .header(HttpHeaders.CACHE_CONTROL, CACHE_DIRECTIVE);
    }

    @Cacheable("default")
    protected ClassInputDocumentation inputDocumentation(Type type) throws ClassNotFoundException {
        Class<? extends Input<?>> inputCls = type.cls();

        return ClassInputDocumentation.of(jsonSchemaGenerator, inputCls);
    }

    @Get
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Plugins"}, summary = "Get list of plugins")
    public List<Plugin> search() {
        return pluginRegistry.plugins()
            .stream()
            .map(p -> Plugin.of(p, null))
            .toList();
    }

    @Get(uri = "icons")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Plugins"}, summary = "Get plugins icons")
    public MutableHttpResponse<Map<String, PluginIcon>> icons() {
        Map<String, PluginIcon> icons = pluginRegistry.plugins()
            .stream()
            .flatMap(plugin -> Stream.of(
                    plugin.getTasks().stream(),
                    plugin.getTriggers().stream(),
                    plugin.getConditions().stream(),
                    plugin.getTaskRunners().stream()
                )
                .flatMap(i -> i)
                .map(e -> new AbstractMap.SimpleEntry<>(
                    e.getName(),
                    new PluginIcon(
                        e.getSimpleName(),
                        plugin.icon(e),
                        FlowableTask.class.isAssignableFrom(e)
                    )
                ))
            )
            .filter(entry -> entry.getKey() != null)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a1, a2) -> a1));

        // add aliases
        Map<String, PluginIcon> aliasIcons = pluginRegistry.plugins().stream()
            .flatMap(plugin -> plugin.getAliases().values().stream().map(e -> new AbstractMap.SimpleEntry<>(
                e.getKey(),
                new PluginIcon(
                    e.getKey().substring(e.getKey().lastIndexOf('.') + 1),
                    plugin.icon(e.getValue()),
                    FlowableTask.class.isAssignableFrom(e.getValue())
                ))))
            .filter(entry -> entry.getKey() != null)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a1, a2) -> a1));
        icons.putAll(aliasIcons);

        return HttpResponse.ok(icons).header(HttpHeaders.CACHE_CONTROL, CACHE_DIRECTIVE);
    }

    @Get(uri = "icons/groups")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Plugins"}, summary = "Get plugins icons")
    public MutableHttpResponse<Map<String, PluginIcon>> pluginGroupIcons() {
        Map<String, PluginIcon> icons = loadPluginsIcon();

        return HttpResponse.ok(icons).header(HttpHeaders.CACHE_CONTROL, CACHE_DIRECTIVE);
    }

    @Cacheable("default")
    protected Map<String, PluginIcon> loadPluginsIcon() {
        Map<String, PluginIcon> icons = new HashMap<>();

        pluginRegistry.plugins().stream()
            .filter(plugin -> plugin.group() != null)
            .forEach(plugin -> {
                String group = plugin.group();
                if (group != null) {
                    icons.put(group, new PluginIcon("plugin-icon", plugin.icon("plugin-icon"), false));
                }

                plugin.subGroupNames().forEach(subgroup -> {
                    icons.put(subgroup, new PluginIcon("plugin-icon", plugin.icon(subgroup), false));
                });
            });

        return icons;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Get(uri = "{cls}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Plugins"}, summary = "Get plugin documentation")
    public HttpResponse<DocumentationWithSchema> pluginDocumentation(
        @Parameter(description = "The plugin full class name") @PathVariable String cls,
        @Parameter(description = "Include all the properties") @QueryValue(value = "all", defaultValue = "false") Boolean allProperties
    ) throws IOException {
        ClassPluginDocumentation classPluginDocumentation = pluginDocumentation(
            pluginRegistry.plugins(),
            cls,
            allProperties
        );

        var doc = alertReplacement(DocumentationGenerator.render(classPluginDocumentation));

        return HttpResponse.ok()
            .body(new DocumentationWithSchema(
                doc,
                new Schema(
                    classPluginDocumentation.getPropertiesSchema(),
                    classPluginDocumentation.getOutputsSchema(),
                    classPluginDocumentation.getDefs()
                )
            ))
            .header(HttpHeaders.CACHE_CONTROL, CACHE_DIRECTIVE);
    }

    @Get("/groups/subgroups")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Plugins"}, summary = "Get plugins group by subgroups")
    public List<Plugin> subgroups() {
        return Stream.concat(
                pluginRegistry.plugins()
                    .stream()
                    .map(p -> Plugin.of(p, null)),
                pluginRegistry.plugins()
                    .stream()
                    .flatMap(p -> p.subGroupNames()
                        .stream()
                        .map(subgroup -> Plugin.of(p, subgroup))
                    )
            )
            .distinct()
            .toList();
    }


    @SuppressWarnings({"rawtypes", "unchecked"})
    @Cacheable("default")
    protected ClassPluginDocumentation<?> pluginDocumentation(List<RegisteredPlugin> plugins, String className, Boolean allProperties) {
        RegisteredPlugin registeredPlugin = plugins
            .stream()
            .filter(r -> r.hasClass(className))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("Class '" + className + "' doesn't exists "));

        Class cls = registeredPlugin
            .findClass(className)
            .orElseThrow(() -> new NoSuchElementException("Class '" + className + "' doesn't exists "));

        Class baseCls = registeredPlugin.baseClass(className);
        if(registeredPlugin.getAliases().containsKey(className.toLowerCase())) {
            return ClassPluginDocumentation.of(jsonSchemaGenerator, registeredPlugin, cls, allProperties ? null : baseCls, className);
        } else {
            return ClassPluginDocumentation.of(jsonSchemaGenerator, registeredPlugin, cls, allProperties ? null : baseCls);
        }
    }

    private String alertReplacement(@NonNull String original) {
        // we need to replace the NuxtJS ::alert{type=} :: with the more standard ::: warning :::
        return original.replaceAll("\n::alert\\{type=\"(.*)\"\\}\n", "\n::: $1\n")
            .replace("\n::\n", "\n:::\n");
    }
}
