package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.kestra.core.encryption.EncryptionService;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.Plugin;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.property.PropertyContext;
import io.kestra.core.storages.Storage;
import io.kestra.core.storages.kv.KVStore;
import org.slf4j.Logger;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@JsonSerialize(using = RunContextSerializer.class)
public interface RunContext extends PropertyContext {

    /**
     * Returns the trigger execution id attached to this context.
     *
     * @return the string id.
     * @throws IllegalStateException if trigger execution id is defined.
     */
    @JsonIgnore
    String getTriggerExecutionId();

    /**
     * Returns an immutable {@link Map} containing all the variables attached to this context.
     *
     * @return The map variables.
     */
    @JsonInclude
    Map<String, Object> getVariables();

    /**
     * Returns the list of inputs of type SECRET.
     */
    @JsonInclude
    List<String> getSecretInputs();

    /**
     * OpenTelemetry trace parent
     */
    @JsonInclude
    String getTraceParent();

    void setTraceParent(String traceParent);

    String render(String inline) throws IllegalVariableEvaluationException;

    Object renderTyped(String inline) throws IllegalVariableEvaluationException;

    String render(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException;

    <T> RunContextProperty<T> render(Property<T> inline);

    List<String> render(List<String> inline) throws IllegalVariableEvaluationException;

    List<String> render(List<String> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException;

    Set<String> render(Set<String> inline) throws IllegalVariableEvaluationException;

    Set<String> render(Set<String> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException;

    Map<String, Object> render(Map<String, Object> inline) throws IllegalVariableEvaluationException;

    Map<String, Object> render(Map<String, Object> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException;

    Map<String, String> renderMap(Map<String, String> inline) throws IllegalVariableEvaluationException;

    Map<String, String> renderMap(Map<String, String> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException;

    /**
     * Validate a bean using Jakarta Bean Validation.
     */
    <T> void validate(T bean);

    String decrypt(String encrypted) throws GeneralSecurityException;

    /**
     * Encrypt a plaintext string using the {@link EncryptionService} and the default encryption key.
     * If the key is not configured, it will log a WARNING and return the plaintext string as is.
     */
    String encrypt(String plaintext) throws GeneralSecurityException;

    /**
     * Gets the {@link Logger} attached to this {@link RunContext}.
     *
     * @return the {@link Logger}.
     */
    Logger logger();

    /**
     * Gets the log file URI inside the internal storage.
     * Only populated if the task or trigger is configured to log o a file.<br>
     *
     * Warning: this method can be called only once for an attempt.
     */
    URI logFileURI();

    /**
     * Gets access to the Kestra's internal storage.
     *
     * @return a {@link Storage} object.
     */
    Storage storage();

    List<AbstractMetricEntry<?>> metrics();

    <T> RunContext metric(AbstractMetricEntry<T> metricEntry);

    void dynamicWorkerResult(List<WorkerTaskResult> workerTaskResults);

    List<WorkerTaskResult> dynamicWorkerResults();

    /**
     * Gets access to the working directory.
     *
     * @return The {@link WorkingDir}.
     */
    WorkingDir workingDir();

    /**
     * Cleanup any temporary resources, files created through this context.
     * Also reset logs MDC so the logger should not be used after this point.
     */
    void cleanup();


    TaskRunInfo taskRunInfo();

    FlowInfo flowInfo();

    /**
     * Returns the value of the specified configuration property for the plugin type
     * associated to the current task or trigger.
     *
     * @param name the configuration property name.
     * @param <T>  the type of the configuration property value.
     * @return the {@link Optional} configuration property value.
     */
    <T> Optional<T> pluginConfiguration(String name);

    /**
     * Returns a map containing all the static configuration properties for the plugin type
     * associated to the current task or trigger.
     *
     * @return an unmodifiable map of key/value properties.
     */
    Map<String, Object> pluginConfigurations();

    /**
     * Gets the version of Kestra.
     *
     * @return the string version.
     */
    String version();

    /**
     * Gets access to the Key-Value store for the given namespace.
     *
     * @return The {@link KVStore}.
     */
    KVStore namespaceKv(String namespace);

    /**
     * Get access to local paths of the host machine.
     */
    LocalPath localPath();

    record TaskRunInfo(String executionId, String taskId, String taskRunId, Object value) {
    }

    record FlowInfo(String tenantId, String namespace, String id, Integer revision) {
        public static FlowInfo from(Map<String, Object> flowInfoMap) {
            return new FlowInfo(
                (String) flowInfoMap.get("tenantId"),
                (String) flowInfoMap.get("namespace"),
                (String) flowInfoMap.get("id"),
                (Integer) flowInfoMap.get("revision")
            );
        }
    }

    /**
     * Get access to the ACL checker.
     * Plugins are responsible for using the ACL checker when they access restricted resources, for example,
     * when Namespace ACLs are used (EE).
     */
    AclChecker acl();

    /**
     * Get access to the Assets handler.
     */
    AssetEmitter assets() throws IllegalVariableEvaluationException;

    /**
     * Clone this run context for a specific plugin.
     * @return a new run context with the plugin configuration of the given plugin.
     */
    RunContext cloneForPlugin(Plugin plugin);

    /**
     * @return an InputAndOutput that can be used to work with inputs and outputs.
     */
    InputAndOutput inputAndOutput();

    /**
     * Get access to the SDK handler which allows interacting easily with the Kestra API via the SDK.
     */
    SDK sdk();

    /**
     * Retrieves the current task run output.
     * WARNING: as the run context is created before running a task, this is not available for most task run.
     * This is available only for flowable tasks as they are called multiple times, and for retried tasks (attempt > 1)
     */
    Map<String, Object> currentOutput();
}
