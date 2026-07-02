package io.kestra.core.exceptions;

import java.util.Collection;

/**
 * Thrown when a job declares {@code tags} but no WorkerGroup's tag set is a superset
 * of the required tags (and the tenant has access).
 */
public class NoMatchingWorkerQueueException extends KestraException {

    public NoMatchingWorkerQueueException(Collection<String> requiredTags) {
        this(requiredTags, null, null);
    }

    public NoMatchingWorkerQueueException(Collection<String> requiredTags, String tenant, String source) {
        super(buildMessage(requiredTags, tenant, source));
    }

    private static String buildMessage(Collection<String> requiredTags, String tenant, String source) {
        StringBuilder sb = new StringBuilder("No worker queue matches required tags ").append(requiredTags);
        if (tenant != null) sb.append(" [tenant=").append(tenant).append(']');
        if (source != null) sb.append(" [source=").append(source).append(']');
        return sb.toString();
    }
}
