package io.kestra.core.storages;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVValue;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.Hashing;
import io.kestra.core.utils.Slugify;
import jakarta.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

public record StateStore(RunContext runContext) {

    public InputStream getState(String stateName, @Nullable String stateSubName, String taskRunValue) throws IOException, ResourceExpiredException {
        return this.getState(true, stateName, stateSubName, taskRunValue);
    }

    /**
     * Gets the state for the given state name, sub-name and task run value.
     *
     * @param flowScoped if true, will scope it to flow, otherwise to namespace
     * @param stateName       state name
     * @param stateSubName    state sub-name (optional)
     * @param taskRunValue    task run value
     * @return an InputStream of the state data
     */
    public InputStream getState(boolean flowScoped, String stateName, @Nullable String stateSubName, String taskRunValue) throws IOException, ResourceExpiredException {
        RunContext.FlowInfo flowInfo = runContext.flowInfo();
        try {
            // We relocate the old state store file to KV Store
            URI oldStateStoreUri = StateStore.oldStateStoreUri(flowInfo.namespace(), flowScoped, flowInfo.id(), stateName, taskRunValue, stateSubName);
            InputStream file = runContext.storage().getFile(oldStateStoreUri);
            this.deleteOldStateStoreFile(flowScoped, stateName, stateSubName, taskRunValue);
            byte[] bytes = file.readAllBytes();

            this.putState(flowScoped, stateName, stateSubName, taskRunValue, bytes);
            return new ByteArrayInputStream(bytes);
        } catch (IOException e) {
            String key = StateStore.statePrefix("_", flowScoped, flowInfo.id(), stateName + nameSuffix(stateSubName), taskRunValue);
            Optional<KVValue> kvStateValue = runContext.namespaceKv(flowInfo.namespace()).getValue(key);
            if (kvStateValue.isEmpty()) {
                throw new FileNotFoundException("State " + key + " not found");
            }
            Object value = kvStateValue.get().value();
            if (value instanceof String string) {
                return new ByteArrayInputStream(string.getBytes());
            } else {
                return new ByteArrayInputStream(((byte[]) Objects.requireNonNull(value)));
            }
        }
    }

    public String putState(String stateName, String stateSubName, String taskRunValue, byte[] value) throws IOException {
        return this.putState(true, stateName, stateSubName, taskRunValue, value);
    }

    /**
     * Sets the state for the given state name, sub-name and task run value.
     *
     * @param flowScoped if true, will scope it to flow, otherwise to namespace
     * @param stateName       state name
     * @param stateSubName    state sub-name (optional)
     * @param taskRunValue    task run value
     * @param value           the state value to store
     * @return the KV Store key at which the state is stored
     */
    public String putState(boolean flowScoped, String stateName, String stateSubName, String taskRunValue, byte[] value) throws IOException {
        // We delete the old state store file
        deleteOldStateStoreFile(flowScoped, stateName, stateSubName, taskRunValue);

        RunContext.FlowInfo flowInfo = runContext.flowInfo();
        String key = StateStore.statePrefix("_", flowScoped, flowInfo.id(), stateName + nameSuffix(stateSubName), taskRunValue);
        runContext.namespaceKv(flowInfo.namespace()).put(key, new KVValueAndMetadata(null, value));

        return key;
    }

    public boolean deleteState(String stateName, String stateSubName, String taskRunValue) throws IOException {
        return this.deleteState(true, stateName, stateSubName, taskRunValue);
    }

    /**
     * Deletes the stateName for the given name, sub-name and task run value.
     * @param flowScoped if true, will scope it to flow, otherwise to namespace
     * @param stateName state name
     * @param stateSubName state sub-name (optional)
     * @param taskRunValue task run value
     * @return true if the state exists and was deleted, false otherwise
     */
    public boolean deleteState(boolean flowScoped, String stateName, String stateSubName, String taskRunValue) throws IOException {
        RunContext.FlowInfo flowInfo = runContext.flowInfo();

        boolean oldStateStoreFileDeleted = deleteOldStateStoreFile(flowScoped, stateName, stateSubName, taskRunValue);
        if (!oldStateStoreFileDeleted) {
            return runContext.namespaceKv(flowInfo.namespace()).delete(StateStore.statePrefix("_", flowScoped, flowInfo.id(), stateName + nameSuffix(stateSubName), taskRunValue));
        }

        return true;
    }

    private static URI oldStateStoreUri(String namespace, boolean flowScoped, String flowId, String stateName, @Nullable String taskRunValue, String name) {
        return URI.create("kestra:/" + namespace.replace(".", "/") + "/" + StateStore.statePrefix("/", flowScoped, flowId, stateName, taskRunValue) + (name == null ? "" : ("/" + name)));
    }

    private static String statePrefix(String separator, boolean flowScoped, String flowId, String stateName, @Nullable String taskRunValue) {
        String flowIdPrefix = (!flowScoped || flowId == null) ? "" : (Slugify.of(flowId) + separator);
        return flowIdPrefix + "states" + separator + stateName + (taskRunValue == null ? "" : (separator + Hashing.hashToString(taskRunValue)));
    }

    private boolean deleteOldStateStoreFile(boolean flowScoped, String stateName, String stateSubName, String taskRunValue) throws IOException {
        RunContext.FlowInfo flowInfo = runContext.flowInfo();
        return runContext.storage().deleteFile(StateStore.oldStateStoreUri(flowInfo.namespace(), flowScoped, flowInfo.id(), stateName, taskRunValue, stateSubName));
    }

    private static String nameSuffix(String name) {
        return Optional.ofNullable(name).map(n -> "_" + n).orElse("");
    }
}
