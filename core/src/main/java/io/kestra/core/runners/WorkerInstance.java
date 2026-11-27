package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.kestra.core.models.HasUID;

/**
 * The worker holding a running job, recorded by the controller when it sends the job to that worker and released once
 * the job's terminal result is processed or the job is resubmitted.
 *
 * @param uid the worker the job was sent to.
 * @param workerQueueId the worker queue the job was dispatched from, or {@code null} for the default queue.
 * @param streamGeneration the generation of the worker's job stream the job was sent on, or {@code 0} when unknown
 *        (an entry written before generations existed, or a worker that does not number its streams).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkerInstance(
    String uid,
    String workerQueueId,
    Long streamGeneration) implements HasUID {

    public WorkerInstance {
        streamGeneration = streamGeneration == null ? 0L : streamGeneration;
    }

    public WorkerInstance(String uid, String workerQueueId) {
        this(uid, workerQueueId, 0L);
    }

    @Override
    public String uid() {
        return uid;
    }
}
