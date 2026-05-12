package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kestra.core.docs.*;
import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.ui.PluginUiManifest;
import io.kestra.core.models.ui.PluginUiModuleWithGroup;
import io.kestra.core.models.ui.TaskWithVersion;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;

import static io.kestra.core.models.Plugin.isDeprecated;
import static io.kestra.core.models.Plugin.isInternal;
import static io.kestra.core.utils.Rethrow.throwFunction;

@Controller("/api/v1/plugins/")
public class PluginController {
    private static final String CACHE_DIRECTIVE = "public, max-age=3600";

    @Inject
    protected JsonSchemaGenerator jsonSchemaGenerator;

    @Inject
    protected PluginRegistry pluginRegistry;

    @Inject
    protected JsonSchemaCache jsonSchemaCache;

    @Get(uri = "schemas/{type}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Plugins" },
        summary = "Get the JSON schema for a type",
        description = "The schema will be a [JSON Schema Draft 7](http://json-schema.org/draft-07/schema)"
    )
    public HttpResponse<Map<String, Object>> getSchemasFromType(
        @Parameter(description = "The schema needed") @PathVariable SchemaType type,
        @Parameter(description = "If schema should be an array of requested type") @Nullable @QueryValue(value = "arrayOf", defaultValue = "false") Boolean arrayOf) {
        return HttpResponse.ok()
            .body(jsonSchemaCache.getSchemaForType(type, arrayOf))
            .header(HttpHeaders.CACHE_CONTROL, CACHE_DIRECTIVE);
    }

    @Get(uri = "properties/{type}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Plugins" },
        summary = "Get the properties part of the JSON schema for a type",
        description = "The schema will be a [JSON Schema Draft 7](http://json-schema.org/draft-07/schema)"
    )
    public HttpResponse<Map<String, Object>> getPropertiesFromType(
        @Parameter(description = "The schema needed") @PathVariable SchemaType type) {
        return HttpResponse.ok()
            .body(jsonSchemaCache.getPropertiesForType(type))
            .header(HttpHeaders.CACHE_CONTROL, CACHE_DIRECTIVE);
    }

    @Get(uri = "inputs")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Plugins" },
        summary = "Get all types for an inputs"
    )
    public List<InputType> getAllInputTypes() throws ClassNotFoundException {
        return Stream.of(Type.values())
            .map(throwFunction(type -> new InputType(type.name(), type.cls().getName())))
            .toList();
    }

    @Get(uri = "inputs/{type}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Plugins" },
        summary = "Get the JSON schema for an input type",
        description = "The schema will be a [JSON Schema Draft 7](http://json-schema.org/draft-07/schema)"
    )
    public MutableHttpResponse<DocumentationWithSchema> getSchemaFromInputType(
        @Parameter(description = "The schema needed") @PathVariable Type type) throws IOException {
        ClassInputDocumentation classInputDocumentation = this.inputDocumentation(type);

        return HttpResponse.ok()
            .body(
                new DocumentationWithSchema(
                    alertReplacement(DocumentationGenerator.render(classInputDocumentation)),
                    new Schema(
                        classInputDocumentation.getPropertiesSchema(),
                        null,
                        classInputDocumentation.getDefs()
                    )
                )
            )
            .header(HttpHeaders.CACHE_CONTROL, CACHE_DIRECTIVE);
    }

    @Cacheable("default")
    protected ClassInputDocumentation inputDocumentation(Type type) {
        Class<? extends Input<?>> inputCls = type.cls();

        return ClassInputDocumentation.of(jsonSchemaGenerator, inputCls);
    }

    @Get
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Plugins" }, summary = "Get list of plugins")
    public List<Plugin> listPlugins() {
        return pluginRegistry.plugins()
            .stream()
            .map(p -> Plugin.of(p, null))
            .toList();
    }

    @Get(uri = "triggers")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Plugins" },
        summary = "Get list of trigger plugins grouped by category",
        description = "Feeds the 'Add Trigger' catalog UI. Returns one entry per non-internal, non-deprecated " +
            "trigger class, classified as core (bundled with Kestra Core), realtime (implements " +
            "RealtimeTriggerInterface) or app (implements PollingTriggerInterface)."
    )
    public PagedResults<ApiTriggerPlugin> listTriggerPlugins() {
        List<ApiTriggerPlugin> all = pluginRegistry.plugins().stream()
            .flatMap(registeredPlugin -> registeredPlugin.getTriggers().stream()
                .filter(c -> !isInternal(c))
                .filter(c -> !c.getName().startsWith("org.kestra."))
                .map(c -> toApiTriggerPlugin(registeredPlugin, c))
            )
            .filter(dto -> dto.group() != TriggerPluginCategory.UNKNOWN)
            .sorted(Comparator.comparing((ApiTriggerPlugin dto) -> dto.group().ordinal())
                .thenComparing(ApiTriggerPlugin::name, String.CASE_INSENSITIVE_ORDER))
            .toList();

        return PagedResults.of(new ArrayListTotal<>(all, all.size()));
    }

    private ApiTriggerPlugin toApiTriggerPlugin(RegisteredPlugin registeredPlugin, Class<? extends AbstractTrigger> triggerClass) {
        io.swagger.v3.oas.annotations.media.Schema schema = triggerClass.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
        String title = triggerClass.getSimpleName();
        String description = schema != null && !schema.description().isEmpty() ? schema.description() : null;
        Boolean deprecated = isDeprecated(triggerClass) ? Boolean.TRUE : null;

        return new ApiTriggerPlugin(
            triggerClass.getName(),
            title,
            description,
            TriggerPluginCategory.classify(registeredPlugin, triggerClass),
            isEnterpriseEdition(registeredPlugin, triggerClass),
            triggerClass.getName(),
            deprecated
        );
    }

    /**
     * A trigger is classified as Enterprise Edition when either the owning plugin's manifest marks
     * the module as EE (via the {@code X-Kestra-License} attribute) or the class lives in an EE
     * package. EE classes show up under several package shapes depending on where they're housed:
     * {@code io.kestra.ee.*} and {@code io.kestra.plugin.ee.*} for bundled EE modules, plus any
     * external plugin that carves out an {@code .ee.} namespace (for example
     * {@code io.kestra.plugin.kestra.ee.assets}). The package fallback matters because uber-jars
     * strip module-level manifests, so the license attribute alone isn't reliable.
     */
    protected boolean isEnterpriseEdition(RegisteredPlugin registeredPlugin, Class<?> triggerClass) {
        String license = registeredPlugin.license();
        if (license != null && license.toUpperCase(Locale.ROOT).contains("EE")) {
            return true;
        }

        String packageName = triggerClass.getPackageName();
        return packageName.startsWith("io.kestra.ee.")
            || packageName.startsWith("io.kestra.plugin.ee.")
            || packageName.contains(".ee.");
    }

    @Get(uri = "icons")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Plugins" }, summary = "Get plugins icons")
    public MutableHttpResponse<Map<String, PluginIcon>> getPluginIcons() {
        Map<String, PluginIcon> icons = pluginRegistry.plugins()
            .stream()
            .flatMap(
                plugin -> Stream.of(
                    plugin.getTasks().stream(),
                    plugin.getTriggers().stream(),
                    plugin.getTaskRunners().stream(),
                    plugin.getLogExporters().stream(),
                    plugin.getApps().stream(),
                    plugin.getAppBlocks().stream(),
                    plugin.getAdditionalPlugins().stream()
                )
                    .flatMap(i -> i)
                    .map(
                        e -> new AbstractMap.SimpleEntry<>(
                            e.getName(),
                            new PluginIcon(
                                e.getSimpleName(),
                                plugin.icon(e),
                                FlowableTask.class.isAssignableFrom(e)
                            )
                        )
                    )
            )
            .filter(entry -> entry.getKey() != null)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a1, a2) -> a1));

        // add aliases
        Map<String, PluginIcon> aliasIcons = pluginRegistry.plugins().stream()
            .flatMap(
                plugin -> plugin.getAliases().values().stream().map(
                    e -> new AbstractMap.SimpleEntry<>(
                        e.getKey(),
                        new PluginIcon(
                            e.getKey().substring(e.getKey().lastIndexOf('.') + 1),
                            plugin.icon(e.getValue()),
                            FlowableTask.class.isAssignableFrom(e.getValue())
                        )
                    )
                )
            )
            .filter(entry -> entry.getKey() != null)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a1, a2) -> a1));
        icons.putAll(aliasIcons);

        return HttpResponse.ok(icons).header(HttpHeaders.CACHE_CONTROL, CACHE_DIRECTIVE);
    }

    @Get(uri = "icons/groups")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Plugins" }, summary = "Get plugins icons")
    public MutableHttpResponse<Map<String, PluginIcon>> getPluginGroupIcons() {
        Map<String, PluginIcon> icons = loadPluginsIcon();

        return HttpResponse.ok(icons).header(HttpHeaders.CACHE_CONTROL, CACHE_DIRECTIVE);
    }

    @Cacheable("default")
    protected Map<String, PluginIcon> loadPluginsIcon() {
        Map<String, PluginIcon> icons = new HashMap<>();

        pluginRegistry.plugins().stream()
            .filter(plugin -> plugin.group() != null)
            .forEach(plugin ->
            {
                String group = plugin.group();
                if (group != null) {
                    icons.put(group, new PluginIcon("plugin-icon", plugin.icon("plugin-icon"), false));
                }

                plugin.subGroupNames().forEach(subgroup ->
                {
                    icons.put(subgroup, new PluginIcon("plugin-icon", plugin.icon(subgroup), false));
                });
            });

        return icons;
    }

    @Get(uri = "{cls}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Plugins" }, summary = "Get plugin documentation")
    public HttpResponse<DocumentationWithSchema> getPluginDocumentation(
        @Parameter(description = "The plugin full class name") @PathVariable String cls,
        @Parameter(description = "Include all the properties") @QueryValue(value = "all", defaultValue = "false") Boolean allProperties) throws IOException {
        return getPluginDocumentationFromVersion(cls, null, allProperties);
    }

    @Get(uri = "{cls}/versions/{version}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Plugins" }, summary = "Get plugin documentation")
    public HttpResponse<DocumentationWithSchema> getPluginDocumentationFromVersion(
        @Parameter(description = "The plugin type") @PathVariable String cls,
        @Parameter(description = "The plugin version") @PathVariable String version,
        @Parameter(description = "Include all the properties") @QueryValue(value = "all", defaultValue = "false") Boolean allProperties) throws IOException {

        ClassPluginDocumentation<?> classPluginDocumentation = buildPluginDocumentation(cls, version, allProperties);

        var doc = alertReplacement(DocumentationGenerator.render(classPluginDocumentation));

        return HttpResponse.ok()
            .body(
                new DocumentationWithSchema(
                    doc,
                    new Schema(
                        applyAlertReplacementToMap(classPluginDocumentation.getPropertiesSchema()),
                        applyAlertReplacementToMap(classPluginDocumentation.getOutputsSchema()),
                        applyAlertReplacementToMap(classPluginDocumentation.getDefs())
                    )
                )
            )
            .header(HttpHeaders.CACHE_CONTROL, CACHE_DIRECTIVE);
    }

    @Get(uri = "{cls}/versions")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Plugins" },
        summary = "Get all versions for a plugin"
    )
    public HttpResponse<ApiPluginVersions> getPluginVersions(
        @Parameter(description = "The plugin type") @PathVariable String cls) {
        return HttpResponse.ok(new ApiPluginVersions(cls, pluginRegistry.getAllVersionsForType(cls)));
    }

    @Get("/groups/subgroups")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Plugins" }, summary = "Get plugins group by subgroups")
    public List<Plugin> getPluginBySubgroups() {
        return Stream.concat(
            pluginRegistry.plugins()
                .stream()
                .map(p -> Plugin.of(p, null)),
            pluginRegistry.plugins()
                .stream()
                .flatMap(
                    p -> p.subGroupNames()
                        .stream()
                        .map(subgroup -> Plugin.of(p, subgroup))
                )
        )
            .distinct()
            .toList();
    }

    @Post("/pluginUiManifest")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Plugins" }, summary = "Get plugins group by subgroups")
    public PluginUiManifest getPluginUiManifest(@Body List<TaskWithVersion> taskWithVersions) {
        Map<String, List<String>> pluginTasks = new HashMap<>();
        for (TaskWithVersion t : taskWithVersions) {
            pluginRegistry.findMetadataByIdentifier(
                getPluginIdentifier(t.cls(), t.version())
            ).ifPresent(
                meta -> pluginTasks.computeIfAbsent(meta.group(), k -> new ArrayList<>()).add(t.cls())
            );
        }

        if (pluginTasks.isEmpty()) {
            throw new NotFoundException();
        }

        Set<String> groups = pluginTasks.keySet();
        List<RegisteredPlugin> plugins = pluginRegistry.plugins(registeredPlugin -> groups.contains(registeredPlugin.group()));

        if (ListUtils.isEmpty(plugins)) {
            throw new NotFoundException();
        }

        Map<String, List<PluginUiModuleWithGroup>> manifest = new HashMap<>();
        for (RegisteredPlugin plugin : plugins) {
            if (!MapUtils.isEmpty(plugin.getPluginUiManifest())) {
                for (String task : pluginTasks.get(plugin.group())) {
                    if (plugin.getPluginUiManifest().containsKey(task)) {
                        manifest.put(
                            task, plugin.getPluginUiManifest().get(task)
                                .stream()
                                .map(module -> new PluginUiModuleWithGroup(module.uiModule(), plugin.group(), module.staticInfo(), module.styles()))
                                .toList()
                        );
                    }
                }
            }
        }

        return new PluginUiManifest(manifest);
    }

    @Get(value = "/{group}/pluginUi/{path:.*}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Plugins" }, summary = "Get plugins group by subgroups")
    public HttpResponse<StreamedFile> getPluginUi(
        @Parameter(description = "The plugin group") @PathVariable String group,
        @Parameter(description = "The file path") @PathVariable String path) {
        if (path.contains("..") || path.startsWith("/") || path.startsWith("\\") || path.contains("\0")) {
            return HttpResponse.badRequest();
        }

        RegisteredPlugin plugin = pluginRegistry.plugins(p -> p.group().equals(group))
            .stream()
            .findFirst()
            .orElseThrow(NotFoundException::new);

        String resourcePath = path.startsWith("/") ? "plugin-ui" + path : "plugin-ui/" + path;

        InputStream in = plugin.getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            throw new NotFoundException();
        }

        MediaType mediaType = MediaType
            .forExtension(NameUtils.extension(resourcePath))
            .orElse(MediaType.APPLICATION_OCTET_STREAM_TYPE);

        StreamedFile streamedFile = new StreamedFile(in, mediaType);

        //todo add front cache later
        return HttpResponse.ok(streamedFile);
    }

    protected ClassPluginDocumentation<?> buildPluginDocumentation(String className, String version, Boolean allProperties) {
        return pluginRegistry.findMetadataByIdentifier(getPluginIdentifier(className, version))
            .map(metadata -> ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, version, allProperties))
            .orElseThrow(() -> new NoSuchElementException("Class '" + className + "' doesn't exists "));
    }

    protected String getPluginIdentifier(final String type, final String version) {
        return type;
    }

    /**
     * Converts Nuxt-content-style two-colon alert directives to the three-colon remark-directive
     * container syntax that KsMarkdown expects.
     * <p>
     * {@code ::alert{type="info"}} → {@code :::alert{type="info"}}
     * {@code ::} (closing) → {@code :::}
     */
    private String alertReplacement(@NonNull String original) {
        return original
            .replaceAll("(?m)^::alert\\{type=\"(.*?)\"\\}$", ":::alert{type=\"$1\"}")
            .replaceAll("(?m)^::$", ":::");
    }

    /**
     * Recursively walks a JSON-schema map and applies {@link #alertReplacement} to every
     * {@code "description"} string value so that plugin property descriptions authored in
     * Nuxt-content syntax render correctly in the UI via KsMarkdown.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> applyAlertReplacementToMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>(map);
        for (String key : result.keySet().toArray(new String[0])) {
            Object value = result.get(key);
            if ("description".equals(key) && value instanceof String s) {
                result.put(key, alertReplacement(s));
            } else if (value instanceof Map<?, ?> m) {
                result.put(key, applyAlertReplacementToMap((Map<String, Object>) m));
            } else if (value instanceof List<?> l) {
                result.put(key, applyAlertReplacementToList((List<Object>) l));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> applyAlertReplacementToList(List<Object> list) {
        return list.stream().map(item -> {
            if (item instanceof Map<?, ?> m) return (Object) applyAlertReplacementToMap((Map<String, Object>) m);
            if (item instanceof List<?> l) return (Object) applyAlertReplacementToList((List<Object>) l);
            return item;
        }).toList();
    }

    public record ApiPluginVersions(
        String type,
        List<String> versions) {
    }

    /**
     * Lightweight descriptor of a trigger plugin class for the "Add Trigger" catalog UI.
     *
     * @param type fully qualified class name (for example {@code io.kestra.plugin.core.trigger.Schedule})
     * @param name human-readable name (Schema#title if set, otherwise simple class name)
     * @param description one-line description from the plugin @Schema
     * @param group category bucket ({@code core}, {@code realtime}, or {@code app})
     * @param ee true when the trigger is only available in Enterprise Edition (bundled with EE core, or shipped by a plugin distributed under an Enterprise license)
     * @param icon icon key resolvable via {@code GET /api/v1/plugins/icons}
     * @param deprecated whether the trigger is deprecated
     */
    public record ApiTriggerPlugin(
        String type,
        String name,
        String description,
        TriggerPluginCategory group,
        boolean ee,
        String icon,
        Boolean deprecated
    ) {
    }
}
