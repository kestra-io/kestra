package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.storages.Storage;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.VersionProvider;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.kestra.core.utils.MapUtils.mergeWithNullableValues;
import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * Default and mutable implementation of {@link RunContext}.
 */
@Introspected
public class DefaultRunContext extends RunContext {
    // Injected
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private VariableRenderer variableRenderer;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private MetricRegistry meterRegistry;

    @Inject
    private Provider<VersionProvider> version;

    @Value("${kestra.encryption.secret-key}")
    private Optional<String> secretKey;

    private Path tempBasedPath;
    private Map<String, Object> variables;
    private List<AbstractMetricEntry<?>> metrics = new ArrayList<>();
    private Supplier<Logger> logger;
    private final List<WorkerTaskResult> dynamicWorkerTaskResult = new ArrayList<>();

    protected transient Path tempDir;
    private String triggerExecutionId;
    private Storage storage;
    private Map<String, Object> pluginConfiguration;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    /**
     * ContextID is used to resolved local temporary directory.
     */
    private final String contextId;

    /**
     * Creates a new {@link DefaultRunContext} instance.
     */
    public DefaultRunContext() {
        this(IdUtils.create());
    }

    /**
     * Creates a new {@link DefaultRunContext} instance.
     *
     * @param contextId a context ID.
     */
    private DefaultRunContext(final String contextId) {
        this.contextId = contextId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @JsonIgnore
    public String getTriggerExecutionId() {
        if (this.triggerExecutionId == null) {
            throw new IllegalStateException("triggerExecutionId is not defined");
        }
        return triggerExecutionId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @JsonInclude
    public Map<String, Object> getVariables() {
        return variables;
    }

    @JsonIgnore
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    void init(final ApplicationContext applicationContext) {
        if (isInitialized.compareAndSet(false, true)) {
            this.applicationContext = applicationContext;
            this.applicationContext.inject(this);
            this.tempBasedPath = Path.of(applicationContext
                .getProperty("kestra.tasks.tmp-dir.path", String.class)
                .orElse(System.getProperty("java.io.tmpdir"))
            );
        }
    }

    void setVariables(final Map<String, Object> variables) {
        this.variables = Collections.unmodifiableMap(variables);
    }

    void setStorage(final Storage storage) {
        this.storage = storage;
    }

    void setLogger(final Supplier<Logger> logger) {
        this.logger = logger;
    }

    void setPluginConfiguration(final Map<String, Object> pluginConfiguration) {
        this.pluginConfiguration = pluginConfiguration;
    }

    void setTriggerExecutionId(final String triggerExecutionId) {
        this.triggerExecutionId = triggerExecutionId;
    }

    void setTempDir(final Path tempDir) {
        this.tempDir = tempDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultRunContext clone() {
        DefaultRunContext runContext = new DefaultRunContext(this.contextId);
        runContext.variableRenderer = this.variableRenderer;
        runContext.applicationContext = this.applicationContext;
        runContext.storageInterface = this.storageInterface;
        runContext.storage = this.storage;
        runContext.variables = new HashMap<>(this.variables);
        runContext.metrics = new ArrayList<>();
        runContext.meterRegistry = this.meterRegistry;
        runContext.tempBasedPath = this.tempBasedPath;
        runContext.tempDir = this.tempDir;
        runContext.logger = this.logger;
        runContext.pluginConfiguration = this.pluginConfiguration;
        return runContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String render(String inline) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, this.variables);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String render(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, mergeWithNullableValues(this.variables, variables));
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> render(List<String> inline) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, this.variables);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<String> render(List<String> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, mergeWithNullableValues(this.variables, variables));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> render(Set<String> inline) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, this.variables);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public Set<String> render(Set<String> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, mergeWithNullableValues(this.variables, variables));
    }

    @Override
    public Map<String, Object> render(Map<String, Object> inline) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, this.variables);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> render(Map<String, Object> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, mergeWithNullableValues(this.variables, variables));
    }

    @Override
    public Map<String, String> renderMap(Map<String, String> inline) throws IllegalVariableEvaluationException {
        return renderMap(inline, Collections.emptyMap());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> renderMap(Map<String, String> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        if (inline == null) {
            return null;
        }

        Map<String, Object> allVariables = mergeWithNullableValues(this.variables, variables);
        return inline
            .entrySet()
            .stream()
            .map(throwFunction(entry -> new AbstractMap.SimpleEntry<>(
                this.render(entry.getKey(), allVariables),
                this.render(entry.getValue(), allVariables)
            )))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String decrypt(String encrypted) throws GeneralSecurityException {
        return new Secret(secretKey, this::logger).decrypt(encrypted);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String encrypt(String plaintext) throws GeneralSecurityException {
        return new Secret(secretKey, this::logger).encrypt(plaintext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Logger logger() {
        return logger.get();
    }

    // for serialization backward-compatibility
    @Override
    @JsonIgnore
    public URI getStorageOutputPrefix() {
        return storage.getContextBaseURI();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Storage storage() {
        return storage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AbstractMetricEntry<?>> metrics() {
        return this.metrics;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> RunContext metric(AbstractMetricEntry<T> metricEntry) {
        int index = this.metrics.indexOf(metricEntry);

        if (index >= 0) {
            @SuppressWarnings("unchecked")
            AbstractMetricEntry<T> current = (AbstractMetricEntry<T>) this.metrics.get(index);
            current.increment(metricEntry.getValue());
        } else {
            this.metrics.add(metricEntry);
        }

        try {
            metricEntry.register(this.meterRegistry, this.metricPrefix(), this.metricsTags());
        } catch (IllegalArgumentException e) {
            // https://github.com/micrometer-metrics/micrometer/issues/877
            // https://github.com/micrometer-metrics/micrometer/issues/2399
            if (!e.getMessage().contains("Collector already registered")) {
                throw e;
            }
        }

        return this;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> metricsTags() {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

        if (this.variables.containsKey("flow")) {
            var flowVars = ((Map<String, String>) this.variables.get("flow"));
            builder
                .put(MetricRegistry.TAG_FLOW_ID, flowVars.get("id"))
                .put(MetricRegistry.TAG_NAMESPACE_ID, flowVars.get("namespace"));
            if (flowVars.containsKey("tenantId")) {
                builder.put(MetricRegistry.TAG_TENANT_ID, flowVars.get("tenantId"));
            }
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private String metricPrefix() {
        if (!this.variables.containsKey("task")) {
            return null;
        }

        List<String> values = new ArrayList<>(Arrays.asList(((Map<String, String>) this.variables.get("task")).get("type").split("\\.")));
        String clsName = values.removeLast();
        values.add(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, clsName));

        return String.join(".", values);
    }

    @Override
    public void dynamicWorkerResult(List<WorkerTaskResult> workerTaskResults) {
        dynamicWorkerTaskResult.addAll(workerTaskResults);
    }

    @Override
    public List<WorkerTaskResult> dynamicWorkerResults() {
        return dynamicWorkerTaskResult;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path tempDir() {
        return tempDir(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Path tempDir(boolean create) {
        if (this.tempDir == null) {
            this.tempDir = tempBasedPath.resolve(contextId);
        }

        if (create && !this.tempDir.toFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            this.tempDir.toFile().mkdirs();
        }

        return this.tempDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path resolve(Path path) {
        if (path == null) {
            return tempDir();
        }

        if (path.toString().contains(".." + File.separator)) {
            throw new IllegalArgumentException("The path to resolve must be a relative path inside the current working directory");
        }

        Path baseDir = tempDir();
        Path resolved = baseDir.resolve(path).toAbsolutePath();

        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("The path to resolve must be a relative path inside the current working directory");
        }

        return resolved;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path tempFile() throws IOException {
        return this.tempFile(null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path tempFile(String extension) throws IOException {
        return this.tempFile(null, extension);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path tempFile(byte[] content) throws IOException {
        return this.tempFile(content, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path tempFile(byte[] content, String extension) throws IOException {
        Path tempFile = Files.createTempFile(this.tempDir(), null, extension);

        if (content != null) {
            Files.write(tempFile, content);
        }

        return tempFile;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path file(String filename) throws IOException {
        return this.file(null, filename);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path file(byte[] content, String filename) throws IOException {
        Path newFilePath = this.resolve(Path.of(filename));
        Files.createDirectories(newFilePath.getParent());
        Path file = Files.createFile(newFilePath);

        if (content != null) {
            Files.write(file, content);
        }

        return file;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String fileExtension(String fileName) {
        String extension = FilenameUtils.getExtension(fileName);
        return StringUtils.isEmpty(extension) ? null : "." + extension;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup() {
        try {
            if (tempDir != null && Files.exists(tempDir)) {
                FileUtils.deleteDirectory(tempDir.toFile());
                this.tempDir = null;
            }
        } catch (IOException ex) {
            new RunContextLogger().logger().warn("Unable to cleanup worker task", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public String tenantId() {
        Map<String, String> flow = (Map<String, String>) this.getVariables().get("flow");
        // normally only tests should not have the flow variable
        return flow != null ? flow.get("tenantId") : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public FlowInfo flowInfo() {
        Map<String, Object> flow = (Map<String, Object>) this.getVariables().get("flow");
        // normally only tests should not have the flow variable
        return flow == null ? null : new FlowInfo(
            (String) flow.get("tenantId"),
            (String) flow.get("namespace"),
            (String) flow.get("id"),
            (Integer) flow.get("revision")
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> pluginConfiguration(final String name) {
        Objects.requireNonNull(name,"Cannot get plugin configuration from null name");
        return Optional.ofNullable((T)pluginConfiguration.get(name));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> pluginConfigurations() {
        return pluginConfiguration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String version() {
        return isInitialized.get() ? version.get().getVersion() : null;
    }

    /**
     * Builder class for constructing new {@link DefaultRunContext} objects.
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @With
    public static class Builder {
        private ApplicationContext applicationContext;
        private VariableRenderer variableRenderer;
        private StorageInterface storageInterface;
        private MetricRegistry meterRegistry;
        private Map<String, Object> variables;
        private List<WorkerTaskResult> dynamicWorkerResults;
        private Map<String, Object> pluginConfiguration;
        private Optional<String> secretKey = Optional.empty();
        private Path tempBasedPath;
        private Storage storage;
        private String triggerExecutionId;
        private Supplier<Logger> logger;

        /**
         * Builds the new {@link DefaultRunContext} object.
         *
         * @return a new {@link DefaultRunContext} object.
         */
        public DefaultRunContext build() {
            DefaultRunContext context = new DefaultRunContext();
            context.applicationContext = applicationContext;
            context.variableRenderer = variableRenderer;
            context.storageInterface = storageInterface;
            context.meterRegistry = meterRegistry;
            context.variables = Optional.ofNullable(variables).map(ImmutableMap::copyOf).orElse(ImmutableMap.of());
            context.pluginConfiguration = Optional.ofNullable(pluginConfiguration).map(ImmutableMap::copyOf).orElse(ImmutableMap.of());
            context.logger = logger;
            context.secretKey = secretKey;
            context.tempBasedPath = tempBasedPath;
            context.storage = storage;
            context.triggerExecutionId = triggerExecutionId;
            return context;
        }
    }
}
