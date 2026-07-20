package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kestra.core.docs.*;
import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.EeOnly;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.ui.PluginDistribution;
import io.kestra.core.models.ui.PluginUiManifest;
import io.kestra.core.models.ui.PluginUiModuleWithGroup;
import io.kestra.core.models.ui.TaskWithVersion;
import io.kestra.core.plugins.PluginArtifact;
import io.kestra.core.plugins.PluginAutoInstallDetectResult;
import io.kestra.core.plugins.PluginAutoInstallService;
import io.kestra.core.plugins.PluginCatalogService;
import io.kestra.core.plugins.PluginInstallJob;
import io.kestra.core.plugins.PluginInstallJobRegistry;
import io.kestra.core.plugins.PluginManager;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.PluginSchemaBundleService;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.utils.EditionProvider;
import io.kestra.core.utils.Hashing;
import io.kestra.core.utils.MapUtils;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import io.kestra.webserver.utils.Searchable;

import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;

import static io.kestra.core.models.Plugin.isDeprecated;
import static io.kestra.core.models.Plugin.isInternal;
import static io.kestra.core.utils.Rethrow.throwFunction;

@Controller("/api/v1/plugins/")
public class PluginController {
    private static final String CACHE_DIRECTIVE = "public, max-age=3600";
    private static final String ICON_CACHE_DIRECTIVE = "public, max-age=31536000, immutable";
    // Plugin schemas depend on the set of installed plugins, which can change while the server runs.
    // They must therefore be revalidated on every use (via ETag) instead of being cached blindly, otherwise
    // the editor keeps validating/completing against a stale schema after a plugin is added or removed (#12102).
    private static final String REVALIDATE_CACHE_DIRECTIVE = "no-cache";
    // Merged responses can go stale the moment a plugin finishes auto-installing — a full-hour
    // cache would hide the newly-installed type from the editor for up to an hour afterwards.
    private static final String CATALOG_CACHE_DIRECTIVE = "public, max-age=60";

    @Inject
    protected JsonSchemaGenerator jsonSchemaGenerator;

    @Inject
    protected PluginRegistry pluginRegistry;

    @Inject
    protected JsonSchemaCache jsonSchemaCache;

    @Inject
    protected VersionProvider versionProvider;

    @Inject
    @Named("PLUGIN")
    protected Searchable<Plugin> pluginSearchable;

    @Inject
    protected EditionProvider editionProvider;

    @Inject
    @Named("withIcons")
    protected PluginCatalogService pluginCatalogService;

    @Inject
    protected PluginManager pluginManager;

    @Inject
    protected PluginAutoInstallService pluginAutoInstallService;

    @Inject
    protected PluginInstallJobRegistry pluginInstallJobRegistry;

    @Inject
    protected PluginSchemaBundleService pluginSchemaBundleService;

    @Get(uri = "schemas/{type}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Plugins" },
        summary = "Get the JSON schema for a type",
        description = "The schema will be a [JSON Schema Draft 7](http://json-schema.org/draft-07/schema)"
    )
    public HttpResponse<Map<String, Object>> getSchemasFromType(
        @Parameter(description = "The schema needed") @PathVariable SchemaType type,
        @Parameter(description = "If schema should be an array of requested type") @Nullable @QueryValue(value = "arrayOf", defaultValue = "false") Boolean arrayOf,
        @Parameter(description = "Whether to merge the pre-baked plugin schema bundle for un-installed types") @Nullable @QueryValue(value = "includeCatalog", defaultValue = "false") Boolean includeCatalog,
        @Parameter(hidden = true) @Nullable @Header(HttpHeaders.IF_NONE_MATCH) String ifNoneMatch) {
        if (Boolean.TRUE.equals(includeCatalog)) {
            // Catalog-merged schema: a short browser cache rather than ETag revalidation — the merged
            // response can go stale the moment a plugin finishes auto-installing, so cap it at 60s.
            Map<String, Object> merged = pluginSchemaBundleService.mergeWithBundle(type, jsonSchemaCache.getSchemaForType(type, arrayOf));
            return HttpResponse.ok(merged)
                .header(HttpHeaders.CACHE_CONTROL, CATALOG_CACHE_DIRECTIVE);
        }

        // Non-merged schema: revalidate on every use via ETag so the editor never validates against a
        // stale schema after a plugin is added/removed (#12102).
        final String etag = schemaETag("schema", type, arrayOf);
        if (etag.equals(ifNoneMatch)) {
            return notModified(etag);
        }
        return HttpResponse.ok(jsonSchemaCache.getSchemaForType(type, arrayOf))
            .header(HttpHeaders.ETAG, etag)
            .header(HttpHeaders.CACHE_CONTROL, REVALIDATE_CACHE_DIRECTIVE);
    }

    @Post(uri = "install")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Plugins" },
        summary = "Start async plugin installation",
        description = "Enqueues installation of the specified plugin artifacts and returns a job id " +
            "immediately (HTTP 202). Poll GET /plugins/install/{jobId} for status and per-artifact " +
            "byte-level progress. In distributed (EE) deployments the installation is propagated " +
            "cluster-wide after the job succeeds. Returns 403 when the auto-install feature is disabled " +
            "on this instance."
    )
    @ApiResponse(responseCode = "202", description = "Installation job accepted")
    @ApiResponse(responseCode = "403", description = "Auto-install feature is disabled on this instance")
    public HttpResponse<PluginInstallJob> installPlugins(
        @Valid @Body List<PluginArtifact> artifacts) {
        // Same gate as detectMissingPlugins: without it, any caller could make the server resolve,
        // download and load arbitrary Maven artifacts in-process regardless of the feature flag.
        if (!pluginAutoInstallService.isEnabled()) {
            return HttpResponse.status(HttpStatus.FORBIDDEN);
        }

        UUID jobId = pluginInstallJobRegistry.submit(artifacts);
        PluginInstallJob job = pluginInstallJobRegistry.get(jobId)
            .orElseThrow(() -> new IllegalStateException("Job vanished immediately after submit: " + jobId));
        return HttpResponse.status(HttpStatus.ACCEPTED).body(job);
    }

    @Get(uri = "install/{jobId}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Plugins" },
        summary = "Get plugin installation job status",
        description = "Returns the current state of an async plugin installation job, including " +
            "per-artifact byte-level transfer progress. Returns 404 when the job is unknown or " +
            "has been evicted (jobs are kept for one hour after completion)."
    )
    @ApiResponse(responseCode = "200", description = "Job snapshot")
    @ApiResponse(responseCode = "404", description = "Job not found")
    public HttpResponse<PluginInstallJob> getInstallJob(@PathVariable UUID jobId) {
        return pluginInstallJobRegistry.get(jobId)
            .map(HttpResponse::ok)
            .orElse(HttpResponse.notFound());
    }

    @Post(uri = "auto-install/detect", consumes = MediaType.TEXT_PLAIN)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Plugins" },
        summary = "Detect missing plugins in a flow",
        description = "Parses the provided flow YAML, identifies task and trigger types that are not " +
            "yet registered, and maps them to their Maven artifacts via the plugin catalog. " +
            "Returns an empty result (not an error) when auto-install is disabled or all types are known."
    )
    @ApiResponse(responseCode = "200", description = "Detection result")
    public HttpResponse<PluginAutoInstallDetectResult> detectMissingPlugins(@Body String flowYaml) {
        if (!pluginAutoInstallService.isEnabled()) {
            return HttpResponse.ok(new PluginAutoInstallDetectResult(false, Set.of(), List.of()));
        }

        var missingTypes = pluginAutoInstallService.findMissingTypes(flowYaml);
        var artifacts = missingTypes.stream()
            .map(pluginAutoInstallService::findArtifactForType)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .distinct()
            .toList();

        return HttpResponse.ok(new PluginAutoInstallDetectResult(true, missingTypes, artifacts));
    }

    @Get(uri = "properties/{type}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Plugins" },
        summary = "Get the properties part of the JSON schema for a type",
        description = "The schema will be a [JSON Schema Draft 7](http://json-schema.org/draft-07/schema)"
    )
    public HttpResponse<Map<String, Object>> getPropertiesFromType(
        @Parameter(description = "The schema needed") @PathVariable SchemaType type,
        @Parameter(hidden = true) @Nullable @Header(HttpHeaders.IF_NONE_MATCH) String ifNoneMatch) {
        final String etag = schemaETag("properties", type, false);
        if (etag.equals(ifNoneMatch)) {
            return notModified(etag);
        }
        return HttpResponse.ok(jsonSchemaCache.getPropertiesForType(type))
            .header(HttpHeaders.ETAG, etag)
            .header(HttpHeaders.CACHE_CONTROL, REVALIDATE_CACHE_DIRECTIVE);
    }

    /**
     * Builds a strong ETag for a plugin schema response. The tag changes whenever the installed plugin
     * set changes (via {@link PluginRegistry#hash()}) or the server version changes, so browsers holding a
     * previously cached schema revalidate and receive the fresh one instead of a stale cached copy.
     */
    private String schemaETag(final String kind, final SchemaType type, final boolean arrayOf) {
        return "\"" + kind + "-" + type + "-" + arrayOf + "-" + versionProvider.getVersion() + "-" + pluginRegistry.hash() + "\"";
    }

    private <T> HttpResponse<T> notModified(final String etag) {
        return HttpResponse.<T>notModified()
            .header(HttpHeaders.ETAG, etag)
            .header(HttpHeaders.CACHE_CONTROL, REVALIDATE_CACHE_DIRECTIVE);
    }

    @Get(uri = "inputs")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Plugins" },
        summary = "Get all types for an inputs"
    )
    public List<InputType> getAllInputTypes() throws ClassNotFoundException {
        return Stream.of(Type.values())
            // exclude edition-restricted (@EeOnly) input types (e.g. REUSABLE_INPUTS) from the open-source listing
            .filter(type -> !type.cls().isAnnotationPresent(EeOnly.class))
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
    public PagedResults<Plugin> listPlugins(
        @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "1000") @Max(PageableUtils.MAX_PAGE_SIZE) int size,
        @Parameter(description = "A list of sort fields") @Nullable @QueryValue(value = "sort") List<String> sort,
        @Parameter(description = "A list of query filters", in = ParameterIn.QUERY) @Nullable @QueryFilterFormat(QueryFilter.Resource.PLUGIN) List<QueryFilter> filters) {
        List<Plugin> items = pluginRegistry.plugins()
            .stream()
            .map(p -> Plugin.of(p, null))
            .toList();

        return PagedResults.of(pluginSearchable.filter(items, page, size, sort, filters));
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
            .flatMap(
                registeredPlugin -> registeredPlugin.getTriggers().stream()
                    .filter(c -> !isInternal(c))
                    .filter(c -> !c.getName().startsWith("org.kestra."))
                    .map(c -> toApiTriggerPlugin(registeredPlugin, c))
            )
            .filter(dto -> dto.group() != TriggerPluginCategory.UNKNOWN)
            .sorted(
                Comparator.comparing((ApiTriggerPlugin dto) -> dto.group().ordinal())
                    .thenComparing(ApiTriggerPlugin::name, String.CASE_INSENSITIVE_ORDER)
            )
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
        return HttpResponse.ok(pluginIconsIndex()).header(HttpHeaders.CACHE_CONTROL, CACHE_DIRECTIVE);
    }

    @Get(uri = "icons/{cls}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Plugins" },
        summary = "Get a single plugin icon",
        description = "Lightweight alternative to `GET /plugins/icons` for resolving one icon at a time, " +
            "so callers don't have to download the whole plugin catalog just to render one task icon. " +
            "Not every class has an icon, which is a normal outcome (not every plugin ships one), so this " +
            "always answers 200 with `icon: null` rather than 404 — a bare 404 here would be indistinguishable, " +
            "to the frontend's shared HTTP client, from a genuine routing error and trip its global error page."
    )
    public HttpResponse<PluginIconResponse> getPluginIcon(
        @Parameter(description = "The plugin full class name") @PathVariable String cls) {
        PluginIcon icon = resolvePluginIcon(cls);

        return HttpResponse.ok(new PluginIconResponse(icon)).header(HttpHeaders.CACHE_CONTROL, CACHE_DIRECTIVE);
    }

    @Get(uri = "icons/{cls}/icon.svg", produces = "image/svg+xml")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Plugins" },
        summary = "Get a single plugin icon as a raw SVG",
        description = "Serves the plugin icon as a real, browser-cacheable `image/svg+xml` resource so it can " +
            "be referenced directly from an `<img src>` or CSS `mask-image` instead of being inlined as a data " +
            "URI. Falls back to the Kestra plugin catalog when the class or group isn't locally registered, or " +
            "is registered but ships no bundled icon. Cached indefinitely by the browser — callers append " +
            "`PluginIcon#hash` as a query param so the URL changes whenever the icon's bytes do."
    )
    public HttpResponse<byte[]> getPluginIconSvg(
        @Parameter(description = "The plugin full class name") @PathVariable String cls) {
        PluginIcon icon = resolvePluginIcon(cls);
        if (icon != null && icon.getIcon() != null) {
            return HttpResponse.ok(Base64.getDecoder().decode(icon.getIcon())).header(HttpHeaders.CACHE_CONTROL, ICON_CACHE_DIRECTIVE);
        }

        return pluginCatalogService.icon(cls)
            .<HttpResponse<byte[]>> map(bytes -> HttpResponse.ok(bytes).header(HttpHeaders.CACHE_CONTROL, ICON_CACHE_DIRECTIVE))
            .orElseGet(HttpResponse::notFound);
    }

    private PluginIcon resolvePluginIcon(String cls) {
        PluginIcon icon = pluginIconsIndex().get(cls);
        return icon != null ? icon : loadPluginsIcon().get(cls);
    }

    private static PluginIcon toPluginIcon(String name, Optional<RegisteredPlugin.IconAndMonochrome> icon, boolean flowable) {
        return new PluginIcon(
            name,
            icon.map(RegisteredPlugin.IconAndMonochrome::icon).orElse(null),
            flowable,
            icon.map(RegisteredPlugin.IconAndMonochrome::monochrome).orElse(false),
            icon.map(i -> Hashing.hashToString(i.icon())).orElse(null)
        );
    }

    @Cacheable("default")
    protected Map<String, PluginIcon> pluginIconsIndex() {
        Map<String, PluginIcon> icons = pluginRegistry.plugins()
            .stream()
            .flatMap(plugin ->
            {
                Optional<RegisteredPlugin.IconAndMonochrome> defaultIcon = plugin.iconAndMonochrome("plugin-icon");
                return Stream.of(
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
                            toPluginIcon(e.getSimpleName(), plugin.iconAndMonochrome(e).or(() -> defaultIcon), FlowableTask.class.isAssignableFrom(e))
                        )
                    );
            })
            .filter(entry -> entry.getKey() != null)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a1, a2) -> a1));

        // add aliases
        Map<String, PluginIcon> aliasIcons = pluginRegistry.plugins().stream()
            .flatMap(plugin ->
            {
                Optional<RegisteredPlugin.IconAndMonochrome> defaultIcon = plugin.iconAndMonochrome("plugin-icon");
                return plugin.getAliases().values().stream().map(
                    e -> new AbstractMap.SimpleEntry<>(
                        e.getKey(),
                        toPluginIcon(
                            e.getKey().substring(e.getKey().lastIndexOf('.') + 1),
                            plugin.iconAndMonochrome(e.getValue()).or(() -> defaultIcon),
                            FlowableTask.class.isAssignableFrom(e.getValue())
                        )
                    )
                );
            })
            .filter(entry -> entry.getKey() != null)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a1, a2) -> a1));
        icons.putAll(aliasIcons);

        return icons;
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
                    icons.put(group, toPluginIcon("plugin-icon", plugin.iconAndMonochrome("plugin-icon"), false));
                }

                plugin.subGroupNames().forEach(subgroup ->
                {
                    icons.put(subgroup, toPluginIcon("plugin-icon", plugin.iconAndMonochrome(subgroup), false));
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

        Set<String> groups = pluginTasks.keySet();
        List<RegisteredPlugin> plugins = pluginRegistry.plugins(registeredPlugin -> groups.contains(registeredPlugin.group()));

        Map<String, List<PluginUiModuleWithGroup>> manifest = new HashMap<>();
        for (RegisteredPlugin plugin : plugins) {
            if (!MapUtils.isEmpty(plugin.getPluginUiManifest())) {
                for (String task : pluginTasks.get(plugin.group())) {
                    if (plugin.getPluginUiManifest().containsKey(task)) {
                        manifest.put(
                            task, plugin.getPluginUiManifest().get(task)
                                .stream()
                                .filter(module -> isDistributionAllowed(module.distribution()))
                                .map(
                                    module -> new PluginUiModuleWithGroup(
                                        module.uiModule(), plugin.group(), module.staticInfo(), module.styles(), plugin.getPluginUiSourceHash(), module.distribution()
                                    )
                                )
                                .toList()
                        );
                    }
                }
            }
        }

        return new PluginUiManifest(manifest);
    }

    private boolean isDistributionAllowed(PluginDistribution distribution) {
        if (editionProvider.get() == EditionProvider.Edition.OSS) {
            return distribution != PluginDistribution.EE;
        }
        return true;
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
        return list.stream().map(item ->
        {
            if (item instanceof Map<?, ?> m)
                return (Object) applyAlertReplacementToMap((Map<String, Object>) m);
            if (item instanceof List<?> l)
                return (Object) applyAlertReplacementToList((List<Object>) l);
            return item;
        }).toList();
    }

    public record ApiPluginVersions(
        String type,
        List<String> versions) {
    }

    /**
     * Always-present wrapper around a possibly-absent {@link PluginIcon}, so the response body
     * itself is never null. Micronaut treats a null response body as a 404, which would defeat
     * the purpose of {@link #getPluginIcon} always answering 200.
     */
    public record PluginIconResponse(@Nullable PluginIcon icon) {
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
        Boolean deprecated) {
    }
}
