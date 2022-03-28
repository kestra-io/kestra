package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.Slugify;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@NoArgsConstructor
public class RunContext {
    private final static ObjectMapper MAPPER = JacksonMapper.ofJson();

    private VariableRenderer variableRenderer;
    private ApplicationContext applicationContext;
    private StorageInterface storageInterface;
    private URI storageOutputPrefix;
    private URI storageExecutionPrefix;
    private String envPrefix;
    private Map<String, Object> variables;
    private List<AbstractMetricEntry<?>> metrics = new ArrayList<>();
    private MetricRegistry meterRegistry;
    private RunContextLogger runContextLogger;
    private Path tempBasedPath;
    protected transient Path temporaryDirectory;

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
        this.initLogger(taskRun);
    }

    /**
     * Only used by {@link io.kestra.core.models.triggers.AbstractTrigger}
     *
     * @param applicationContext the current {@link ApplicationContext}
     */
    public RunContext(ApplicationContext applicationContext, Flow flow, AbstractTrigger trigger) {
        this.initBean(applicationContext);

        this.storageOutputPrefix = this.storageInterface.outputPrefix(flow);
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
        this.envPrefix = applicationContext.getProperty("kestra.variables.env-vars-prefix", String.class, "KESTRA_");
        this.meterRegistry = applicationContext.findBean(MetricRegistry.class).orElseThrow();
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
    private void initLogger(TaskRun taskRun) {
        this.runContextLogger = new RunContextLogger(
            applicationContext.findBean(
                QueueInterface.class,
                Qualifiers.byName(QueueFactoryInterface.WORKERTASKLOG_NAMED)
            ).orElseThrow(),
            LogEntry.of(taskRun)
        );
    }

    @SuppressWarnings("unchecked")
    private void initLogger(Execution execution) {
        this.runContextLogger = new RunContextLogger(
            applicationContext.findBean(
                QueueInterface.class,
                Qualifiers.byName(QueueFactoryInterface.WORKERTASKLOG_NAMED)
            ).orElseThrow(),
            LogEntry.of(execution)
        );
    }

    @SuppressWarnings("unchecked")
    private void initLogger(Flow flow, AbstractTrigger trigger) {
        this.runContextLogger = new RunContextLogger(
            applicationContext.findBean(
                QueueInterface.class,
                Qualifiers.byName(QueueFactoryInterface.WORKERTASKLOG_NAMED)
            ).orElseThrow(),
            LogEntry.of(flow, trigger)
        );
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
            .put("envs", envVariables());

        if (applicationContext.getProperties("kestra.variables.globals").size() > 0) {
            builder.put("globals", applicationContext.getProperties("kestra.variables.globals"));
        }

        if (flow != null) {
            if (flow.getVariables() != null) {
                builder.put("vars", flow.getVariables());
            }
        }

        if (task != null) {
            builder
                .put("task", ImmutableMap.of(
                    "id", task.getId(),
                    "type", task.getType()
                ));
        }

        if (taskRun != null) {
            builder.put("taskrun", this.variables(taskRun));
        }

        if (taskRun != null && execution != null) {
            List<Map<String, Object>> parents = execution.parents(taskRun);

            builder.put("parents", parents);
            if (parents.size() > 0) {
                builder.put("parent", parents.get(0));
            }
        }

        if (flow != null) {
            builder
                .put("flow", ImmutableMap.of(
                    "id", flow.getId(),
                    "namespace", flow.getNamespace(),
                    "revision", flow.getRevision()
                ));
        }

        if (execution != null) {
            builder
                .put("execution", ImmutableMap.of(
                    "id", execution.getId(),
                    "startDate", execution.getState().getStartDate()
                ));

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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, String> envVariables() {
        Map<String, String> result = new HashMap<>(System.getenv());
        result.putAll((Map) System.getProperties());

        return result
            .entrySet()
            .stream()
            .filter(e -> e.getKey().startsWith(this.envPrefix))
            .map(e -> new AbstractMap.SimpleEntry<>(
                e.getKey().substring(this.envPrefix.length()).toLowerCase(),
                e.getValue()
            ))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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

    public RunContext forWorker(ApplicationContext applicationContext, TaskRun taskRun) {
        this.initBean(applicationContext);
        this.initLogger(taskRun);

        HashMap<String, Object> clone = new HashMap<>(this.variables);

        clone.remove("taskrun");
        clone.put("taskrun", this.variables(taskRun));

        this.variables = ImmutableMap.copyOf(clone);
        this.storageExecutionPrefix = URI.create("/" + this.storageInterface.executionPrefix(taskRun));

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

    public Map<String, Object> render(Map<String, Object> inline) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, this.variables);
    }

    public Map<String, Object> render(Map<String, Object> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, mergeVariables(variables));
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
        if (uri.getScheme().equals("kestra")) {
            return this.storageInterface.get(uri);
        }

        throw new IllegalArgumentException("Invalid scheme for uri '" + uri + "'");
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

        return this.storageInterface.put(resolve, new BufferedInputStream(inputStream));
    }

    private URI putTempFile(File file, String prefix, String name) throws IOException {
        URI put = this.putTempFile(new FileInputStream(file), prefix, (name != null ? name : file.getName()));

        boolean delete = file.delete();
        if (!delete) {
            runContextLogger.logger().warn("Failed to delete temporary file");
        }

        return put;
    }

    @SuppressWarnings("unchecked")
    private String taskStateFilePathPrefix(String name) {
        Map<String, String> taskrun = (Map<String, String>) this.getVariables().get("taskrun");

        return "/" + this.storageInterface.statePrefix(
            ((Map<String, String>) this.getVariables().get("flow")).get("namespace"),
            ((Map<String, String>) this.getVariables().get("flow")).get("id"),
            name,
            taskrun != null ? taskrun.getOrDefault("value", null) : null
        );
    }

    public InputStream getTaskStateFile(String state, String name) throws IOException {
        URI uri = URI.create(this.taskStateFilePathPrefix(state));
        URI resolve = uri.resolve(uri.getPath() + "/" + name);

       return this.storageInterface.get(resolve);
    }

    public URI putTaskStateFile(byte[] content, String state, String name) throws IOException {
        return this.putTempFile(
            new ByteArrayInputStream(content),
            this.taskStateFilePathPrefix(state),
            name
        );
    }

    public URI putTaskStateFile(File file, String state, String name) throws IOException {
        return this.putTempFile(
            file,
            this.taskStateFilePathPrefix(state),
            name
        );
    }

    public boolean deleteTaskStateFile(String state, String name) throws IOException {
        URI uri = URI.create(this.taskStateFilePathPrefix(state));
        URI resolve = uri.resolve(uri.getPath() + "/" + name);

        return this.storageInterface.delete(resolve);
    }

    public List<URI> purgeStorageExecution() throws IOException {
        return this.storageInterface.deleteByPrefix(this.storageExecutionPrefix);
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
            builder
                .put(MetricRegistry.TAG_FLOW_ID, ((Map<String, String>) this.variables.get("flow")).get("id"))
                .put(MetricRegistry.TAG_NAMESPACE_ID, ((Map<String, String>) this.variables.get("flow")).get("namespace"));
        }

        if (this.variables.containsKey("task")) {
            builder
                .put(MetricRegistry.TAG_TASK_ID, ((Map<String, String>) this.variables.get("task")).get("id"))
                .put(MetricRegistry.TAG_TASK_TYPE, ((Map<String, String>) this.variables.get("task")).get("type"));
        }

        if (this.variables.containsKey("taskrun")) {
            Map<String, String> taskrun = (Map<String, String>) this.variables.get("taskrun");

            if (taskrun.containsValue("value")) {
                builder.put(MetricRegistry.TAG_VALUE, taskrun.get("value"));
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

    public Path tempFile() throws IOException {
        return this.tempFile(null, null);
    }

    public Path tempFile(String suffix) throws IOException {
        return this.tempFile(null, suffix);
    }

    public Path tempFile(byte[] content) throws IOException {
        return this.tempFile(content, null);
    }

    public Path tempFile(byte[] content, String suffix) throws IOException {
        Path tempFile = Files.createTempFile(this.tempDir(), null, suffix);

        if (content != null) {
            Files.write(tempFile, content);
        }

        return tempFile;
    }

    public void cleanup() {
        try {
            this.cleanTemporaryDirectory();
        } catch (IOException ex) {
            logger().warn("Unable to cleanup worker task", ex);
        }
    }

    public WorkerTask cleanup(WorkerTask workerTask) {
        try {
            this.cleanTemporaryDirectory();
            return MAPPER.readValue(MAPPER.writeValueAsString(workerTask), WorkerTask.class);
        } catch (IOException ex) {
            logger().warn("Unable to cleanup worker task", ex);

            return workerTask;
        }
    }

    private void cleanTemporaryDirectory() throws IOException {
        if (temporaryDirectory != null && temporaryDirectory.toFile().exists()) {
            FileUtils.deleteDirectory(temporaryDirectory.toFile());
            this.temporaryDirectory = null;
        }
    }
}
