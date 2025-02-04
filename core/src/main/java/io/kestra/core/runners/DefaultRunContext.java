package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.Storage;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.VersionProvider;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static io.kestra.core.utils.MapUtils.mergeWithNullableValues;
import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * Default and mutable implementation of {@link RunContext}.
 */
@Introspected
public class DefaultRunContext extends RunContext {
    // Injected manually inside init(ApplicationContext)
    private ApplicationContext applicationContext;
    private VariableRenderer variableRenderer;
    private MetricRegistry meterRegistry;
    private VersionProvider version;
    private KVStoreService kvStoreService;
    private Optional<String> secretKey;
    private WorkingDir workingDir;

    private Map<String, Object> variables;
    private List<AbstractMetricEntry<?>> metrics = new ArrayList<>();
    private RunContextLogger logger;
    private final List<WorkerTaskResult> dynamicWorkerTaskResult = new ArrayList<>();
    private String triggerExecutionId;
    private Storage storage;
    private Map<String, Object> pluginConfiguration;
    private List<String> secretInputs;
    private String traceParent;

    // those are only used to validate dynamic properties inside the RunContextProperty
    private Task task;
    private AbstractTrigger trigger;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);


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

    /**
     * {@inheritDoc}
     */
    @Override
    @JsonInclude
    public List<String> getSecretInputs() {
        return secretInputs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @JsonInclude
    public String getTraceParent() {
        return traceParent;
    }

    @Override
    public void setTraceParent(String traceParent) {
        this.traceParent = traceParent;
    }

    @JsonIgnore
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @JsonIgnore
    Task getTask() {
        return task;
    }

    @JsonIgnore
    AbstractTrigger getTrigger() {
        return trigger;
    }

    void init(final ApplicationContext applicationContext) {
        if (isInitialized.compareAndSet(false, true)) {
            this.applicationContext = applicationContext;

            // init beans
            if (this.workingDir == null) {
                // we only init the workingDir if not already init for the WorkingDirectory task to keep the same working directory
                this.workingDir = applicationContext.getBean(WorkingDirFactory.class).createWorkingDirectory();
            }
            this.variableRenderer = applicationContext.getBean(VariableRenderer.class);
            this.meterRegistry = applicationContext.getBean(MetricRegistry.class);
            this.version = applicationContext.getBean(VersionProvider.class);
            this.kvStoreService = applicationContext.getBean(KVStoreService.class);
            this.secretKey = applicationContext.getProperty("kestra.encryption.secret-key", String.class);
        }
    }

    void setVariables(final Map<String, Object> variables) {
        this.variables = Collections.unmodifiableMap(variables);
    }

    void setStorage(final Storage storage) {
        this.storage = storage;
    }

    void setLogger(final RunContextLogger logger) {
        this.logger = logger;

        // this is used when a run context is re-hydrated so we need to add again the secrets from the inputs
        if (!ListUtils.isEmpty(secretInputs) && getVariables().containsKey("inputs")) {
            Map<String, Object> inputs = (Map<String, Object>) getVariables().get("inputs");
            for (String secretInput : secretInputs) {
                String secret = (String) inputs.get(secretInput);
                if (secret != null) {
                    logger.usedSecret(secret);
                }
            }
        }
    }

    void setPluginConfiguration(final Map<String, Object> pluginConfiguration) {
        this.pluginConfiguration = pluginConfiguration;
    }

    void setTriggerExecutionId(final String triggerExecutionId) {
        this.triggerExecutionId = triggerExecutionId;
    }

    void setTask(final Task task) {
        this.task = task;
    }

    void setTrigger(final AbstractTrigger trigger) {
        this.trigger = trigger;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public DefaultRunContext clone() {
        DefaultRunContext runContext = new DefaultRunContext();
        runContext.variables = new HashMap<>(this.variables);
        runContext.workingDir = this.workingDir;
        runContext.logger = this.logger;
        runContext.metrics = new ArrayList<>();
        runContext.storage = this.storage;
        runContext.pluginConfiguration = this.pluginConfiguration;
        runContext.secretInputs = this.secretInputs;
        if (this.isInitialized()) {
            //Inject all services
            runContext.init(applicationContext);
        }
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
    public <T> RunContextProperty<T> render(Property<T> inline) {
        return new RunContextProperty<>(inline, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public String render(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, mergeWithNullableValues(this.variables, decryptVariables(variables)));
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
        return variableRenderer.render(inline, mergeWithNullableValues(this.variables, decryptVariables(variables)));
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
        return variableRenderer.render(inline, mergeWithNullableValues(this.variables, decryptVariables(variables)));
    }

    @Override
    public Map<String, Object> render(Map<String, Object> inline) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, this.variables);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> render(Map<String, Object> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, mergeWithNullableValues(this.variables, decryptVariables(variables)));
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

        Map<String, Object> allVariables = mergeWithNullableValues(this.variables, decryptVariables(variables));
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

    /**
     * {@inheritDoc}
     */
    @Override
    public URI logFileURI() {
        if (logger.getLogFile() != null) {
            try {
                logger.closeLogFile();
                String logName = "log-" + RandomStringUtils.secure().nextAlphanumeric(5).toLowerCase() + ".txt";
                Path logFile = this.workingDir.createFile(logName);
                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(logFile))) {
                    Files.copy(logger.getLogFile().toPath(), out);
                }
                URI logFileURI = this.storage.putFile(logFile.toFile());
                if (!logger.getLogFile().delete()) {
                    logger().warn("Unable to delete the log file {}", logger.getLogFile().toPath());
                }
                return logFileURI;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
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

    private Map<String, Object> decryptVariables(Map<String, Object> variables) {
        if (secretKey.isPresent()) {
            final Secret secret = new Secret(secretKey, logger);
            return secret.decrypt(variables);
        }
        return variables;
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
            logger().warn("Unable to cleanup worker task", ex);
        }

        logger.resetMDC();
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
        return flow == null ? new FlowInfo(null, null, null, null) : new FlowInfo(
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
        return this.isInitialized() ? version.getVersion() : null;
    }

    @Override
    public KVStore namespaceKv(String namespace) {
        return kvStoreService.get(this.flowInfo().tenantId(), namespace, this.flowInfo().namespace());
    }

    @Override
    public boolean isInitialized() {
        return isInitialized.get();
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
        private RunContextLogger logger;
        private KVStoreService kvStoreService;
        private List<String> secretInputs;
        private Task task;
        private AbstractTrigger trigger;

        /**
         * Builds the new {@link DefaultRunContext} object.
         *
         * @return a new {@link DefaultRunContext} object.
         */
        public DefaultRunContext build() {
            DefaultRunContext context = new DefaultRunContext();
            context.applicationContext = applicationContext;
            context.variableRenderer = variableRenderer;
            context.meterRegistry = meterRegistry;
            context.variables = Optional.ofNullable(variables).map(ImmutableMap::copyOf).orElse(ImmutableMap.of());
            context.pluginConfiguration = Optional.ofNullable(pluginConfiguration).map(ImmutableMap::copyOf).orElse(ImmutableMap.of());
            context.logger = logger;
            context.secretKey = secretKey;
            context.workingDir = workingDir;
            context.storage = storage;
            context.triggerExecutionId = triggerExecutionId;
            context.kvStoreService = kvStoreService;
            context.secretInputs = secretInputs;
            context.task = task;
            context.trigger = trigger;
            return context;
        }
    }
}
