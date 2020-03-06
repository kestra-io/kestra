package org.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import lombok.NoArgsConstructor;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.metrics.MetricRegistry;
import org.kestra.core.models.executions.AbstractMetricEntry;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.serializers.JacksonMapper;
import org.kestra.core.storages.StorageInterface;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@NoArgsConstructor
public class RunContext {
    private static VariableRenderer variableRenderer = new VariableRenderer();
    private ApplicationContext applicationContext;
    private StorageInterface storageInterface;
    private URI storageOutputPrefix;
    private String envPrefix;
    private Map<String, Object> variables;
    private List<AbstractMetricEntry<?>> metrics = new ArrayList<>();
    private MetricRegistry meterRegistry;
    private RunContextLogger runContextLogger;


    public RunContext(ApplicationContext applicationContext, Flow flow, Execution execution) {
        this.init(applicationContext);
        this.init(flow, null, execution, null);
    }

    public RunContext(ApplicationContext applicationContext, Flow flow, ResolvedTask task, Execution execution, TaskRun taskRun) {
        this.init(applicationContext);
        this.init(flow, task, execution, taskRun);
    }

    @VisibleForTesting
    public RunContext(ApplicationContext applicationContext, Map<String, Object> variables) {
        this.init(applicationContext);

        this.storageOutputPrefix = URI.create("");
        this.variables = variables;
        this.runContextLogger = new RunContextLogger("flow.unitest");
    }

    private void init(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.storageInterface = applicationContext.findBean(StorageInterface.class).orElse(null);
        this.envPrefix = applicationContext.getProperty("kestra.variables.env-vars-prefix", String.class, "KESTRA_");
        this.meterRegistry = applicationContext.findBean(MetricRegistry.class).orElseThrow();
        this.runContextLogger = new RunContextLogger();
    }

    private void init(Flow flow, ResolvedTask task, Execution execution, TaskRun taskRun) {
        this.variables = this.variables(flow, task, execution, taskRun);
        this.runContextLogger = new RunContextLogger("flow." + flow.getId() + (taskRun != null ? "." + taskRun.getTaskId() : ""));
        if (taskRun != null) {
            this.storageOutputPrefix = StorageInterface.outputPrefix(flow, task, execution, taskRun);
        }
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

    private Map<String, Object> variables(Flow flow, ResolvedTask resolvedTask, Execution execution, TaskRun taskRun) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
            .put("envs", envVariables());

        if (applicationContext.getProperties("kestra.variables.globals").size() > 0) {
            builder.put("globals", applicationContext.getProperties("kestra.variables.globals"));
        }

        if (resolvedTask != null && flow.isListenerTask(resolvedTask.getTask().getId())) {
            builder
                .put("flow", JacksonMapper.toMap(flow))
                .put("execution", JacksonMapper.toMap(execution));

        } else {
            builder
                .put("flow", ImmutableMap.of(
                    "id", flow.getId(),
                    "namespace", flow.getNamespace()
                ))
                .put("execution", ImmutableMap.of(
                    "id", execution.getId(),
                    "startDate", execution.getState().getStartDate()
                ));
        }

        if (resolvedTask != null) {
            builder
                .put("task", ImmutableMap.of(
                    "id", resolvedTask.getTask().getId(),
                    "type", resolvedTask.getTask().getType()
                ));
        }

        if (taskRun != null) {
            builder.put("taskrun", this.variables(taskRun));
        }

        if (execution.getTaskRunList() != null) {
            builder.put("outputs", execution.outputs());
        }

        if (execution.getInputs() != null) {
            builder.put("inputs", execution.getInputs());
        }

        if (flow.getVariables() != null) {
            builder.put("vars", flow.getVariables());
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
        this.init(applicationContext);

        HashMap<String, Object> clone = new HashMap<>(this.variables);

        clone.remove("taskrun");
        clone.put("taskrun", this.variables(taskRun));

        this.variables = ImmutableMap.copyOf(clone);

        return this;
    }

    public String render(String inline) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, this.variables);
    }

    public List<String> render(List<String> inline) throws IllegalVariableEvaluationException {
        return variableRenderer.render(inline, this.variables);
    }

    public String render(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return variableRenderer.render(
            inline,
            Stream
                .concat(this.variables.entrySet().stream(), variables.entrySet().stream())
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue
                ))
        );
    }

    public org.slf4j.Logger logger(Class<?> cls) {
        return runContextLogger.logger(cls);
    }

    public List<LogEntry> logs() {
        return runContextLogger.logs();
    }

    public InputStream uriToInputStream(URI uri) throws FileNotFoundException {
        if (uri.getScheme().equals("kestra")) {
            return this.storageInterface.get(uri);
        }

        if (uri.getScheme().equals("file")) {
            return new FileInputStream(uri.toString());
        }

        if (uri.getScheme().equals("http")) {
            try {
                return uri.toURL().openStream();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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
        URI uri = URI.create(this.storageOutputPrefix.toString());
        URI resolve = uri.resolve(uri.getPath() + "/" + file.getName());

        URI put = this.storageInterface.put(resolve, new FileInputStream(file));

        boolean delete = file.delete();
        if (!delete) {
            runContextLogger.logger(RunContext.class).warn("Failed to delete temporary file");
        }

        return put;
    }

    public List<AbstractMetricEntry<?>> metrics() {
        return this.metrics;
    }

    public RunContext metric(AbstractMetricEntry<?> metricEntry) {
        metricEntry.register(this.meterRegistry, this.metricPrefix(), this.metricsTags());
        this.metrics.add(metricEntry);

        return this;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> metricsTags() {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String>builder()
            .put(MetricRegistry.TAG_FLOW_ID, ((Map<String, String>) this.variables.get("flow")).get("id"))
            .put(MetricRegistry.TAG_NAMESPACE_ID, ((Map<String, String>) this.variables.get("flow")).get("namespace"));

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
}
