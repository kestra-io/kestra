package io.kestra.core.runners;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.kestra.core.models.HasUID;

/**
 * Represents a Worker Instance.
 *
 * @param uid The service ID of the worker.
 * @param workerUuid The service ID of the worker.
 * @param workerGroup The worker group.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkerInstance(
    String uid,
    @Deprecated String workerUuid,
    String workerGroup) implements HasUID {

    public WorkerInstance(String uid) {
        this(uid, null);
    }

    public WorkerInstance(String uid, String workerGroup) {
        this(uid, null, workerGroup);
    }

    @Override
    public String uid() {
        return Optional.ofNullable(uid).orElse(workerUuid);
    }

    @Override
    public String workerUuid() {
        return uid();
    }
}
