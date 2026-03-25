package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.kestra.core.models.HasUID;

/**
 * Represents a Worker Instance.
 *
 * @param uid The service ID of the worker.
 * @param workerGroup The worker group.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkerInstance(
    String uid,
    String workerGroup) implements HasUID {

    public WorkerInstance(String uid) {
        this(uid, null);
    }

    @Override
    public String uid() {
        return uid;
    }
}
