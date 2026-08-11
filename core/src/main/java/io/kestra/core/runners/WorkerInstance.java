package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.kestra.core.models.HasUID;

/**
 * Represents a Worker Instance.
 *
 * @param uid The worker identifier.
 * @param workerQueueId The worker queue id.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkerInstance(
    String uid,
    String workerQueueId) implements HasUID {

    @Override
    public String uid() {
        return uid;
    }
}
