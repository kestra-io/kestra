package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.Storage;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.utils.VersionProvider;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
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

    @Inject
    private KVStoreService kvStoreService;

    @Value("${kestra.encryption.secret-key}")
    private Optional<String> secretKey;

    private Map<String, Object> variables;
    private List<AbstractMetricEntry<?>> metrics = new ArrayList<>();
    private Supplier<Logger> logger;
    private final List<WorkerTaskResult> dynamicWorkerTaskResult = new ArrayList<>();
    private String triggerExecutionId;
    private Storage storage;
    private Map<String, Object> pluginConfiguration;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private WorkingDir workingDir;

    /**
     * Creates a new {@link DefaultRunContext} instance.
     */
    public DefaultRunContext() {}

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
            if (this.workingDir == null) {
                this.workingDir = applicationContext.getBean(WorkingDirFactory.class).createWorkingDirectory();
            }
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

    void setWorkingDir(final WorkingDir workingDir) {
        this.workingDir = workingDir;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public DefaultRunContext clone() {
        DefaultRunContext runContext = new DefaultRunContext();
        runContext.variableRenderer = this.variableRenderer;
        runContext.applicationContext = this.applicationContext;
        runContext.storageInterface = this.storageInterface;
        runContext.storage = this.storage;
        runContext.variables = new HashMap<>(this.variables);
        runContext.metrics = new ArrayList<>();
        runContext.meterRegistry = this.meterRegistry;
        runContext.workingDir = this.workingDir;
        runContext.logger = this.logger;
        runContext.pluginConfiguration = this.pluginConfiguration;
        runContext.version = version;
        runContext.isInitialized.set(this.isInitialized.get());
        return runContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String render(String inline) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, this.variables);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object renderTyped(String inline) throws IllegalVariableEvaluationException {
        return variableRenderer.renderTyped(inline, this.variables);
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
    public WorkingDir workingDir() {
        return workingDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup() {
        try {
           workingDir.cleanup();
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

    @Override
    public KVStore namespaceKv(String namespace) {
        return kvStoreService.get(tenantId(), namespace, this.flowInfo().namespace());
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
        private WorkingDir workingDir;
        private Storage storage;
        private String triggerExecutionId;
        private Supplier<Logger> logger;
        private KVStoreService kvStoreService;

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
            context.workingDir = workingDir;
            context.storage = storage;
            context.triggerExecutionId = triggerExecutionId;
            context.kvStoreService = kvStoreService;
            return context;
        }
    }
}
