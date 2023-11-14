package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.Slugify;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwFunction;

@NoArgsConstructor
public class RunContext {
    // Injected
    private ApplicationContext applicationContext;
    private VariableRenderer variableRenderer;
    private StorageInterface storageInterface;
    private MetricRegistry meterRegistry;
    private Path tempBasedPath;
    private RunContextCache runContextCache;

    private URI storageOutputPrefix;
    private URI storageExecutionPrefix;
    private Map<String, Object> variables;
    private List<AbstractMetricEntry<?>> metrics = new ArrayList<>();
    private RunContextLogger runContextLogger;
    private final List<WorkerTaskResult> dynamicWorkerTaskResult = new ArrayList<>();

    protected transient Path temporaryDirectory;

    private String triggerExecutionId;

    /**
     * Only used by {@link io.kestra.core.models.triggers.types.Flow}
     *
     * @param applicationContext the current {@link ApplicationContext}
     * @param flow the current {@link Flow}
     * @param execution the current {@link Execution}
     */
    public RunContext(ApplicationContext applicationContext, Flow flow, Execution execution) {
        this.initBean(applicationContext);
        this.initContext(flow, null, execution, null);
        this.initLogger(execution);
    }

    /**
     * Normal usage
     *
     * @param applicationContext the current {@link ApplicationContext}
     * @param flow the current {@link Flow}
     * @param task the current {@link io.kestra.core.models.tasks.Task}
     * @param execution the current {@link Execution}
     * @param taskRun the current {@link TaskRun}
     */
    public RunContext(ApplicationContext applicationContext, Flow flow, Task task, Execution execution, TaskRun taskRun) {
        this.initBean(applicationContext);
        this.initContext(flow, task, execution, taskRun);
        this.initLogger(taskRun, task);
    }

    /**
     * Only used by {@link io.kestra.core.models.triggers.AbstractTrigger}, then scheduler must call {@link RunContext#forScheduler(TriggerContext, AbstractTrigger)}
     *
     * @param applicationContext the current {@link ApplicationContext}
     */
    public RunContext(ApplicationContext applicationContext, Flow flow, AbstractTrigger trigger) {
        this.initBean(applicationContext);

        this.variables = this.variables(flow, null, null, null, trigger);
        this.initLogger(flow, trigger);
    }

    /**
     * Only used by Unit Test
     *
     * @param applicationContext the current {@link ApplicationContext}
     * @param variables The variable to inject
     */
    @VisibleForTesting
    public RunContext(ApplicationContext applicationContext, Map<String, Object> variables) {
        this.initBean(applicationContext);

        this.variables = new HashMap<>();
        this.variables.putAll(this.variables(null, null, null, null, null));
        this.variables.putAll(variables);

        this.storageOutputPrefix = URI.create("");
        this.runContextLogger = new RunContextLogger();
    }

    protected void initBean(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.variableRenderer = applicationContext.findBean(VariableRenderer.class).orElseThrow();
        this.storageInterface = applicationContext.findBean(StorageInterface.class).orElse(null);
        this.meterRegistry = applicationContext.findBean(MetricRegistry.class).orElseThrow();
        this.runContextCache = applicationContext.findBean(RunContextCache.class).orElseThrow();
        this.tempBasedPath = Path.of(applicationContext
            .getProperty("kestra.tasks.tmp-dir.path", String.class)
            .orElse(System.getProperty("java.io.tmpdir"))
        );
    }

    private void initContext(Flow flow, Task task, Execution execution, TaskRun taskRun) {
        this.variables = this.variables(flow, task, execution, taskRun, null);
        if (taskRun != null && this.storageInterface != null) {
            this.storageOutputPrefix = this.storageInterface.outputPrefix(flow, task, execution, taskRun);
        }
    }

    @SuppressWarnings("unchecked")
    private void initLogger(TaskRun taskRun, Task task) {
        this.runContextLogger = new RunContextLogger(
            applicationContext.findBean(
                QueueInterface.class,
                Qualifiers.byName(QueueFactoryInterface.WORKERTASKLOG_NAMED)
            ).orElseThrow(),
            LogEntry.of(taskRun),
            task.getLogLevel()
        );
    }

    @SuppressWarnings("unchecked")
    private void initLogger(Execution execution) {
        this.runContextLogger = new RunContextLogger(
            applicationContext.findBean(
                QueueInterface.class,
                Qualifiers.byName(QueueFactoryInterface.WORKERTASKLOG_NAMED)
            ).orElseThrow(),
            LogEntry.of(execution),
            null
        );
    }

    @SuppressWarnings("unchecked")
    private void initLogger(TriggerContext triggerContext, AbstractTrigger trigger) {
        this.runContextLogger = new RunContextLogger(
            applicationContext.findBean(
                QueueInterface.class,
                Qualifiers.byName(QueueFactoryInterface.WORKERTASKLOG_NAMED)
            ).orElseThrow(),
            LogEntry.of(triggerContext, trigger),
            trigger.getMinLogLevel()
        );
    }

    @SuppressWarnings("unchecked")
    private void initLogger(Flow flow, AbstractTrigger trigger) {
        this.runContextLogger = new RunContextLogger(
            applicationContext.findBean(
                QueueInterface.class,
                Qualifiers.byName(QueueFactoryInterface.WORKERTASKLOG_NAMED)
            ).orElseThrow(),
            LogEntry.of(flow, trigger),
            trigger.getMinLogLevel()
        );
    }

    @JsonIgnore
    public String getTriggerExecutionId() {
        if (this.triggerExecutionId == null) {
            throw new IllegalStateException("triggerExecutionId is not defined");
        }

        return triggerExecutionId;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    @SuppressWarnings("unused")
    public URI getStorageOutputPrefix() {
        return storageOutputPrefix;
    }

    @JsonIgnore
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    protected Map<String, Object> variables(Flow flow, Task task, Execution execution, TaskRun taskRun, AbstractTrigger trigger) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
            .put("envs", runContextCache.getEnvVars())
            .put("globals", runContextCache.getGlobalVars());

        if (flow != null) {
            if (flow.getVariables() != null) {
                builder.put("vars", flow.getVariables());
            }
        }

        if (task != null) {
            builder.put("task", this.variables(task));
        }

        if (taskRun != null) {
            builder.put("taskrun", this.variables(taskRun));
        }

        if (taskRun != null && execution != null) {
            List<Map<String, Object>> parents = execution.parents(taskRun);

            builder.put("parents", parents);
            if (!parents.isEmpty()) {
                builder.put("parent", parents.get(0));
            }
        }

        if (flow != null) {
            if (flow.getTenantId() == null) {
                builder
                    .put("flow", ImmutableMap.of(
                        "id", flow.getId(),
                        "namespace", flow.getNamespace(),
                        "revision", flow.getRevision()
                    ));
            }
            else {
                builder
                    .put("flow", ImmutableMap.of(
                        "id", flow.getId(),
                        "tenantId", flow.getTenantId(),
                        "namespace", flow.getNamespace(),
                        "revision", flow.getRevision()
                    ));
            }
        }

        if (execution != null) {
            ImmutableMap.Builder<String, Object> executionMap = ImmutableMap.<String, Object>builder()
                .put("id", execution.getId())
                .put("startDate", execution.getState().getStartDate());

            if (execution.getOriginalId() != null) {
                executionMap.put("originalId", execution.getOriginalId());
            }

            builder
                .put("execution", executionMap.build());

            if (execution.getTaskRunList() != null) {
                builder.put("outputs", execution.outputs());
            }

            if (execution.getInputs() != null) {
                builder.put("inputs", execution.getInputs());
            }

            if (execution.getTrigger() != null && execution.getTrigger().getVariables() != null) {
                builder.put("trigger", execution.getTrigger().getVariables());
            }

            if (execution.getVariables() != null) {
                builder.putAll(execution.getVariables());
            }
        }

        if (trigger != null) {
            builder
                .put("trigger", ImmutableMap.of(
                    "id", trigger.getId(),
                    "type", trigger.getType()
                ));
        }

        return builder.build();
    }

    private Map<String, Object> variables(TaskRun taskRun) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
            .put("id", taskRun.getId())
            .put("startDate", taskRun.getState().getStartDate())
            .put("attemptsCount", taskRun.getAttempts() == null ? 0 : taskRun.getAttempts().size());

        if (taskRun.getParentTaskRunId() != null) {
            builder.put("parentId", taskRun.getParentTaskRunId());
        }

        if (taskRun.getValue() != null) {
            builder.put("value", taskRun.getValue());
        }

        if(taskRun.getItems() != null) {
            builder.put("items", taskRun.getItems());
        }

        return builder.build();
    }

    private Map<String, Object> variables(Task task) {
        return ImmutableMap.of(
            "id", task.getId(),
            "type", task.getType()
        );
    }

    @SuppressWarnings("unchecked")
    public RunContext updateVariables(WorkerTaskResult workerTaskResult, TaskRun parent) {
        Map<String, Object> variables = new HashMap<>(this.variables);

        HashMap<String, Object> outputs = this.variables.containsKey("outputs") ?
            new HashMap<>((Map<String, Object>) this.variables.get("outputs")) :
            new HashMap<>();


        Map<String, Object> result = new HashMap<>();
        Map<String, Object> current = result;

        if (variables.containsKey("parents")) {
            for (Map<String, Map<String, String>> t : Lists.reverse((List<Map<String, Map<String, String>>>) variables.get("parents"))) {
                if (t.get("taskrun") != null && t.get("taskrun").get("value") != null) {
                    HashMap<String, Object> item = new HashMap<>();
                    current.put(t.get("taskrun").get("value"), item);
                    current = item;
                }
            }
        }

        if (parent.getValue() != null) {
            HashMap<String, Object> item = new HashMap<>();
            current.put(parent.getValue(), item);
            current = item;
        }

        if (workerTaskResult.getTaskRun().getOutputs() != null) {
            current.putAll(workerTaskResult.getTaskRun().getOutputs());
        }

        outputs.put(workerTaskResult.getTaskRun().getTaskId(), result);

        variables.remove("outputs");
        variables.put("outputs", outputs);

        return this.clone(variables);
    }

    private RunContext clone(Map<String, Object> variables) {
        RunContext runContext = new RunContext();
        runContext.variableRenderer = this.variableRenderer;
        runContext.applicationContext = this.applicationContext;
        runContext.storageInterface = this.storageInterface;
        runContext.storageOutputPrefix = this.storageOutputPrefix;
        runContext.storageExecutionPrefix = this.storageExecutionPrefix;
        runContext.variables = variables;
        runContext.metrics = new ArrayList<>();
        runContext.meterRegistry = this.meterRegistry;
        runContext.runContextLogger = this.runContextLogger;
        runContext.tempBasedPath = this.tempBasedPath;
        runContext.temporaryDirectory = this.temporaryDirectory;

        return runContext;
    }

    public RunContext forScheduler(TriggerContext triggerContext, AbstractTrigger trigger) {
        this.triggerExecutionId = IdUtils.create();
        this.storageOutputPrefix = this.storageInterface.outputPrefix(triggerContext, trigger, triggerExecutionId);

        return this;
    }

    public RunContext forWorker(ApplicationContext applicationContext, WorkerTask workerTask) {
        this.initBean(applicationContext);
        this.initLogger(workerTask.getTaskRun(), workerTask.getTask());

        Map<String, Object> clone = new HashMap<>(this.variables);

        clone.remove("taskrun");
        clone.put("taskrun", this.variables(workerTask.getTaskRun()));

        clone.remove("task");
        clone.put("task", this.variables(workerTask.getTask()));

        if (clone.containsKey("workerTaskrun") && ((Map<String, Object>) clone.get("workerTaskrun")).containsKey("value")) {
            Map<String, Object> workerTaskrun = ((Map<String, Object>) clone.get("workerTaskrun"));
            Map<String, Object> taskrun = new HashMap<>((Map<String, Object>) clone.get("taskrun"));

            taskrun.put("value", workerTaskrun.get("value"));

            clone.remove("taskrun");
            clone.put("taskrun", taskrun);
        }

        this.variables = ImmutableMap.copyOf(clone);
        this.storageExecutionPrefix = URI.create("/" + this.storageInterface.executionPrefix(workerTask.getTaskRun()));

        return this;
    }

    public RunContext forWorker(ApplicationContext applicationContext, WorkerTrigger workerTrigger) {
        this.initBean(applicationContext);
        this.initLogger(workerTrigger.getTriggerContext(), workerTrigger.getTrigger());

        // Mutability hack to update the triggerExecutionId for each evaluation on the worker
        return forScheduler(workerTrigger.getTriggerContext(), workerTrigger.getTrigger());
    }

    public RunContext forWorkerDirectory(ApplicationContext applicationContext, WorkerTask workerTask) {
        forWorker(applicationContext, workerTask);

        Map<String, Object> clone = new HashMap<>(this.variables);

        clone.put("workerTaskrun", clone.get("taskrun"));

        this.variables = ImmutableMap.copyOf(clone);

        return this;
    }

    public String render(String inline) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, this.variables);
    }

    public String render(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, mergeVariables(variables));
    }

    public List<String> render(List<String> inline) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, this.variables);
    }

    public List<String> render(List<String> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, mergeVariables(variables));
    }

    public Set<String> render(Set<String> inline) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, this.variables);
    }

    public Set<String> render(Set<String> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, mergeVariables(variables));
    }

    public Map<String, Object> render(Map<String, Object> inline) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, this.variables);
    }

    public Map<String, Object> render(Map<String, Object> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, mergeVariables(variables));
    }

    public Map<String, String> renderMap(Map<String, String> inline) throws IllegalVariableEvaluationException {
        return inline
            .entrySet()
            .stream()
            .map(throwFunction(entry -> new AbstractMap.SimpleEntry<>(
                this.render(entry.getKey(), mergeVariables(variables)),
                this.render(entry.getValue(), mergeVariables(variables))
            )))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, Object> mergeVariables(Map<String, Object> variables) {
        return Stream
            .concat(this.variables.entrySet().stream(), variables.entrySet().stream())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (o, o2) -> o2
            ));
    }

    public org.slf4j.Logger logger() {
        return runContextLogger.logger();
    }

    public InputStream uriToInputStream(URI uri) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("Invalid internal storage uri, got null");
        }

        if (uri.getScheme() == null) {
            throw new IllegalArgumentException("Invalid internal storage uri, got uri '" + uri + "'");
        }

        if (uri.getScheme().equals("kestra")) {
            return this.storageInterface.get(getTenantId(), uri);
        }

        throw new IllegalArgumentException("Invalid internal storage scheme, got uri '" + uri + "'");
    }

    /**
     * Put the temporary file on storage and delete it after.
     *
     * @param file the temporary file to upload to storage
     * @return the {@code StorageObject} created
     * @throws IOException If the temporary file can't be read
     */
    public URI putTempFile(File file) throws IOException {
        return this.putTempFile(file, this.storageOutputPrefix.toString(), (String) null);
    }

    /**
     * Put the temporary file on storage and delete it after.
     *
     * @param file the temporary file to upload to storage
     * @param name overwrite file name
     * @return the {@code StorageObject} created
     * @throws IOException If the temporary file can't be read
     */
    public URI putTempFile(File file, String name) throws IOException {
        return this.putTempFile(file, this.storageOutputPrefix.toString(), name);
    }

    /**
     * Put the temporary file on storage and delete it after.
     * This method is meant to be used by polling triggers, the name of the destination file is derived from the
     * executionId and the trigger passed as parameters.
     *
     * @param file the temporary file to upload to storage
     * @param executionId overwrite file name
     * @param trigger the trigger
     * @return the {@code StorageObject} created
     * @throws IOException If the temporary file can't be read
     */
    public URI putTempFile(File file, String executionId, AbstractTrigger trigger) throws IOException {
        return this.putTempFile(
            file,
            this.storageOutputPrefix.toString() + "/" + String.join(
                "/",
                Arrays.asList(
                    "executions",
                    executionId,
                    "trigger",
                    Slugify.of(trigger.getId())
                )
            ),
            (String) null
        );
    }

    private URI putTempFile(InputStream inputStream, String prefix, String name) throws IOException {
        URI uri = URI.create(prefix);
        URI resolve = uri.resolve(uri.getPath() + "/" + name);

        return this.storageInterface.put(getTenantId(), resolve, new BufferedInputStream(inputStream));
    }

    private URI putTempFile(File file, String prefix, String name) throws IOException {
        try (InputStream fileInput = new FileInputStream(file)) {
            return this.putTempFile(fileInput, prefix, (name != null ? name : file.getName()));
        } finally {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                runContextLogger.logger().warn("Failed to delete temporary file '{}'", file.toPath(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String taskStateFilePathPrefix(String name, Boolean isNamespace, Boolean useTaskRun) {
        Map<String, String> taskrun = (Map<String, String>) this.getVariables().get("taskrun");
        Map<String, String> flow = (Map<String, String>) this.getVariables().get("flow");

        return "/" + this.storageInterface.statePrefix(
            flow.get("namespace"),
            isNamespace ? null : flow.get("id"),
            name,
            taskrun != null && useTaskRun ? taskrun.getOrDefault("value", null) : null
        );
    }

    public InputStream getTaskStateFile(String state, String name) throws IOException {
        return this.getTaskStateFile(state, name, false, true);
    }


    public InputStream getTaskStateFile(String state, String name, Boolean isNamespace, Boolean useTaskRun) throws IOException {
        URI uri = URI.create(this.taskStateFilePathPrefix(state, isNamespace, useTaskRun));
        URI resolve = uri.resolve(uri.getPath() + "/" + name);

       return this.storageInterface.get(getTenantId(), resolve);
    }

    public URI putTaskStateFile(byte[] content, String state, String name) throws IOException {
        return this.putTaskStateFile(content, state, name, false, true);
    }

    public URI putTaskStateFile(byte[] content, String state, String name, Boolean namespace, Boolean useTaskRun) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(content)) {
            return this.putTempFile(
                inputStream,
                this.taskStateFilePathPrefix(state, namespace, useTaskRun),
                name
            );
        }
    }

    public URI putTaskStateFile(File file, String state, String name) throws IOException {
        return this.putTaskStateFile(file, state, name, false, true);
    }

    public URI putTaskStateFile(File file, String state, String name, Boolean isNamespace, Boolean useTaskRun) throws IOException {
        return this.putTempFile(
            file,
            this.taskStateFilePathPrefix(state, isNamespace, useTaskRun),
            name
        );
    }

    public boolean deleteTaskStateFile(String state, String name) throws IOException {
        return this.deleteTaskStateFile(state, name, false, true);
    }

    public boolean deleteTaskStateFile(String state, String name, Boolean isNamespace, Boolean useTaskRun) throws IOException {
        URI uri = URI.create(this.taskStateFilePathPrefix(state, isNamespace, useTaskRun));
        URI resolve = uri.resolve(uri.getPath() + "/" + name);

        return this.storageInterface.delete(getTenantId(), resolve);
    }

    /**
     * Get from the internal storage the cache file corresponding to this task.
     * If the cache file didn't exist, an empty Optional is returned.
     *
     * @param namespace the flow namespace
     * @param flowId the flow identifier
     * @param taskId the task identifier
     * @param value optional, the task run value
     *
     * @return an Optional with the cache input stream or empty.
     */
    public Optional<InputStream> getTaskCacheFile(String namespace, String flowId, String taskId, String value) throws IOException {
        URI uri = URI.create("/" + this.storageInterface.cachePrefix(namespace, flowId, taskId, value) + "/cache.zip");
        return this.storageInterface.exists(getTenantId(), uri) ? Optional.of(this.storageInterface.get(getTenantId(), uri)) : Optional.empty();
    }

    public Optional<Long> getTaskCacheFileLastModifiedTime(String namespace, String flowId, String taskId, String value) throws IOException {
        URI uri = URI.create("/" + this.storageInterface.cachePrefix(namespace, flowId, taskId, value) + "/cache.zip");
        return this.storageInterface.exists(getTenantId(), uri) ? Optional.of(this.storageInterface.lastModifiedTime(getTenantId(), uri)) : Optional.empty();
    }

    /**
     * Put into the internal storage the cache file corresponding to this task.
     *
     * @param file the cache as a ZIP archive
     * @param namespace the flow namespace
     * @param flowId the flow identifier
     * @param taskId the task identifier
     * @param value optional, the task run value
     *
     * @return the URI of the file inside the internal storage.
     */
    public URI putTaskCacheFile(File file, String namespace, String flowId, String taskId, String value) throws IOException {
        return this.putTempFile(
            file,
            "/" + this.storageInterface.cachePrefix(namespace, flowId, taskId, value),
            "cache.zip"
        );
    }

    public Optional<Boolean> deleteTaskCacheFile(String namespace, String flowId, String taskId, String value) throws IOException {
        URI uri = URI.create("/" + this.storageInterface.cachePrefix(namespace, flowId, taskId, value) + "/cache.zip");
        return this.storageInterface.exists(getTenantId(), uri) ? Optional.of(this.storageInterface.delete(getTenantId(), uri)) : Optional.empty();
    }

    public List<URI> purgeStorageExecution() throws IOException {
        return this.storageInterface.deleteByPrefix(getTenantId(), this.storageExecutionPrefix);
    }

    public List<AbstractMetricEntry<?>> metrics() {
        return this.metrics;
    }

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
        ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String>builder();

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
        String clsName = values.remove(values.size() - 1);
        values.add(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, clsName));

        return String.join(".", values);
    }

    public void dynamicWorkerResult(List<WorkerTaskResult> workerTaskResults) {
        dynamicWorkerTaskResult.addAll(workerTaskResults);
    }

    public List<WorkerTaskResult> dynamicWorkerResults() {
        return dynamicWorkerTaskResult;
    }

    public synchronized Path tempDir() {
        return this.tempDir(true);
    }

    public synchronized Path tempDir(boolean create) {
        if (this.temporaryDirectory == null) {
            this.temporaryDirectory = tempBasedPath.resolve(IdUtils.create());
        }

        if (create && !this.temporaryDirectory.toFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            this.temporaryDirectory.toFile().mkdirs();
        }

        return this.temporaryDirectory;
    }

    /**
     * @deprecated use {@link #tempFile(String)} instead
     */
    @Deprecated
    public Path tempFile() throws IOException {
        return this.tempFile(null, null);
    }

    public Path tempFile(String extension) throws IOException {
        return this.tempFile(null, extension);
    }

    /**
     * @deprecated use {@link #tempFile(byte[], String)} instead
     */
    @Deprecated
    public Path tempFile(byte[] content) throws IOException {
        return this.tempFile(content, null);
    }

    public Path tempFile(byte[] content, String extension) throws IOException {
        Path tempFile = Files.createTempFile(this.tempDir(), null, extension);

        if (content != null) {
            Files.write(tempFile, content);
        }

        return tempFile;
    }

    /**
     * Get the file extension including the '.' to be used with the various methods that took a suffix.
     * @param fileName the name of the file
     * @return the file extension including the '.' or null
     */
    public String fileExtension(String fileName) {
        String extension = FilenameUtils.getExtension(fileName);
        return StringUtils.isEmpty(extension) ? null : "." + extension;
    }

    public void cleanup() {
        try {
            this.cleanTemporaryDirectory();
        } catch (IOException ex) {
            logger().warn("Unable to cleanup worker task", ex);
        }
    }

    private void cleanTemporaryDirectory() throws IOException {
        if (temporaryDirectory != null && temporaryDirectory.toFile().exists()) {
            FileUtils.deleteDirectory(temporaryDirectory.toFile());
            this.temporaryDirectory = null;
        }
    }

    private String getTenantId() {
        Map<String, String> flow = (Map<String, String>) this.getVariables().get("flow");
        // normally only tests should not have the flow variable
        return flow != null ? flow.get("tenantId") : null;
    }
}
