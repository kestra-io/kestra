package io.kestra.core.models.executions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.utils.IdUtils;

/**
 * A Playground panel reporting that it still shows a run, or that it was closed, broadcast so every webserver node agrees on which runs are still watched.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaygroundClientEvent(String tenantId, String executionId, String clientId, Type type) implements BroadcastEvent {

    @Override
    public String key() {
        return IdUtils.fromParts(tenantId, executionId);
    }

    public enum Type {
        REPORT,
        RELEASE
    }
}
