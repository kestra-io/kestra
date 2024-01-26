package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
import io.kestra.core.storages.InternalStorage;
import io.kestra.core.storages.Storage;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private Map<String, Object> variables;
    private List<AbstractMetricEntry<?>> metrics = new ArrayList<>();
    private RunContextLogger runContextLogger;
    private final List<WorkerTaskResult> dynamicWorkerTaskResult = new ArrayList<>();
    protected transient Path temporaryDirectory;
    private String triggerExecutionId;
    private Storage storage;

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
        this.initLogger(taskRun, task);
        this.initContext(flow, task, execution, taskRun);
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
        this.runContextLogger = new RunContextLogger();
        this.storage = new InternalStorage(
            logger(),
            new StorageContext() {
                @Override
                public URI getContextStorageURI() {
                    return URI.create("");
                }
            },
            storageInterface
        );

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
            this.storage = new InternalStorage(
                logger(),
                StorageContext.forTask(taskRun),
                storageInterface
            );
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

            if (execution.getLabels() != null) {
                builder.put("labels", execution.getLabels()
                    .stream()
                    .map(label -> new AbstractMap.SimpleEntry<>(
                        label.key(),
                        label.value()
                    ))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                );
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
        runContext.storage = this.storage;
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
        StorageContext context = StorageContext.forTrigger(
            triggerContext.getTenantId(),
            triggerContext.getNamespace(),
            triggerContext.getFlowId(),
            triggerExecutionId,
            trigger.getId()
        );
        this.storage = new InternalStorage(
            logger(),
            context,
            storageInterface
        );
        return this;
    }

    @SuppressWarnings("unchecked")
    public RunContext forWorker(ApplicationContext applicationContext, WorkerTask workerTask) {
        this.initBean(applicationContext);

        final TaskRun taskRun = workerTask.getTaskRun();

        this.initLogger(taskRun, workerTask.getTask());

        Map<String, Object> clone = new HashMap<>(this.variables);

        clone.remove("taskrun");
        clone.put("taskrun", this.variables(taskRun));

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
        this.storage = new InternalStorage(logger(), StorageContext.forTask(taskRun), storageInterface);
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

    /**
     * Gets a {@link InputStream} for the given file URI.
     *
     * @param uri   the file URI.
     * @return      the {@link InputStream}.
     * @throws IOException
     * @deprecated use {@link Storage#getFile(URI)}.
     */
    @Deprecated
    public InputStream uriToInputStream(URI uri) throws IOException {
        return this.storage.getFile(uri);
    }

    // for serialization backward-compatibility
    @JsonIgnore
    public URI getStorageOutputPrefix() {
        return storage.getContextBaseURI();
    }

    /**
     * Gets access to the Kestra's storage.
     *
     * @return  a {@link Storage} object.
     */
    public Storage storage() {
        return storage;
    }

    /**
     * Put the temporary file on storage and delete it after.
     *
     * @param file  the temporary file to upload to storage
     * @return      the {@code StorageObject} created
     * @throws IOException If the temporary file can't be read
     *
     * @deprecated use {@link #storage()} and {@link InternalStorage#putFile(File)}.
     */
    @Deprecated
    public URI putTempFile(File file) throws IOException {
        return this.storage.putFile(file);
    }

    /**
     * Put the temporary file on storage and delete it after.
     *
     * @param file the temporary file to upload to storage
     * @param name overwrite file name
     * @return the {@code StorageObject} created
     * @throws IOException If the temporary file can't be read
     *
     * @deprecated use {@link #storage()} and {@link InternalStorage#putFile(File, String)}.
     */
    @Deprecated
    public URI putTempFile(File file, String name) throws IOException {
        return this.storage.putFile(file, name);
    }

    @Deprecated
    public InputStream getTaskStateFile(String state, String name) throws IOException {
        return this.storage.getTaskStateFile(state, name);
    }

    @Deprecated
    public InputStream getTaskStateFile(String state, String name, Boolean isNamespace, Boolean useTaskRun) throws IOException {
        return this.storage.getTaskStateFile(state, name, isNamespace, useTaskRun);
    }

    @Deprecated
    public URI putTaskStateFile(byte[] content, String state, String name) throws IOException {
        return this.storage.putTaskStateFile(content, state, name);
    }

    @Deprecated
    public URI putTaskStateFile(byte[] content, String state, String name, Boolean namespace, Boolean useTaskRun) throws IOException {
        return this.storage.putTaskStateFile(content, state, name, namespace, useTaskRun);
    }

    @Deprecated
    public URI putTaskStateFile(File file, String state, String name) throws IOException {
        return this.storage.putTaskStateFile(file, state, name);
    }

    @Deprecated
    public URI putTaskStateFile(File file, String state, String name, Boolean isNamespace, Boolean useTaskRun) throws IOException {
        return this.storage.putTaskStateFile(file, state, name, isNamespace, useTaskRun);
    }

    @Deprecated
    public boolean deleteTaskStateFile(String state, String name) throws IOException {
        return this.storage.deleteTaskStateFile(state, name);
    }

    @Deprecated
    public boolean deleteTaskStateFile(String state, String name, Boolean isNamespace, Boolean useTaskRun) throws IOException {
        return this.storage.deleteTaskStateFile(state, name, isNamespace, useTaskRun);
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
     * Resolve a path inside the working directory (a.k.a. the tempDir).
     * If the resolved path escapes the working directory, an IllegalArgumentException will be thrown to protect against path traversal security issue.
     * This method is null-friendly: it will return the working directory (a.k.a. the tempDir) if called with a null path.
     */
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

    @SuppressWarnings("unchecked")
    public String tenantId() {
        Map<String, String> flow = (Map<String, String>) this.getVariables().get("flow");
        // normally only tests should not have the flow variable
        return flow != null ? flow.get("tenantId") : null;
    }
}
