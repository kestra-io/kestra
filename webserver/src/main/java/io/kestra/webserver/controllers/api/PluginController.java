package io.kestra.webserver.controllers.api;

import io.kestra.core.docs.*;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.services.PluginService;
import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.*;
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
    private JsonSchemaGenerator jsonSchemaGenerator;

    @Inject
    private PluginService pluginService;

    @Get(uri = "schemas/{type}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = {"Plugins"},
        summary = "Get all json schemas for a type",
        description = "The schema will be output as [http://json-schema.org/draft-07/schema](Json Schema Draft 7)"
    )
    public HttpResponse<Map<String, Object>> schemas(
        @Parameter(description = "The schema needed") @PathVariable SchemaType type
    ) {
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

    @Get(uri = "inputs")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = {"Plugins"},
        summary = "Get all types for an inputs"
    )
    public List<InputType> inputs() throws ClassNotFoundException {
        return Stream.of(Type.values())
            .map(throwFunction(type -> new InputType(type.name(), type.cls().getName())))
            .collect(Collectors.toList());
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
        return pluginService
            .allPlugins()
            .stream()
            .map(Plugin::of)
            .collect(Collectors.toList());
    }

    @Get(uri = "icons")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Plugins"}, summary = "Get plugins icons")
    public MutableHttpResponse<Map<String, PluginIcon>> icons() {
        Map<String, PluginIcon> icons = pluginService
            .allPlugins()
            .stream()
            .flatMap(plugin -> Stream
                .concat(
                    plugin.getTasks().stream(),
                    Stream.concat(
                        Stream.concat(
                            plugin.getTriggers().stream(),
                            plugin.getConditions().stream()
                        ),
                        plugin.getTaskRunners().stream()
                    )
                )
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
        return HttpResponse.ok(icons).header(HttpHeaders.CACHE_CONTROL, CACHE_DIRECTIVE);
    }

    @Get(uri = "icons/groups")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Plugins"}, summary = "Get plugins icons")
    public MutableHttpResponse<Map<String, PluginIcon>> pluginGroupIcons() {
        Map<String, PluginIcon> icons = pluginService
            .allPlugins()
            .stream()
            .filter(plugin -> plugin.group() != null)
            .collect(Collectors.toMap(
                RegisteredPlugin::group,
                plugin -> new PluginIcon("plugin-icon", plugin.icon("plugin-icon"), false),
                (a1, a2) -> a1
            ));
        return HttpResponse.ok(icons).header(HttpHeaders.CACHE_CONTROL, CACHE_DIRECTIVE);
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
            pluginService.allPlugins(),
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

        Class baseCls = registeredPlugin
            .baseClass(className);

        return ClassPluginDocumentation.of(jsonSchemaGenerator, registeredPlugin, cls, allProperties ? null : baseCls);
    }

    private String alertReplacement(@NonNull String original) {
        // we need to replace the NuxtJS ::alert{type=} :: with the more standard ::: warning :::
        return original.replaceAll("\n::alert\\{type=\"(.*)\"\\}\n", "\n::: $1\n")
            .replaceAll("\n::\n", "\n:::\n");
    }
}
