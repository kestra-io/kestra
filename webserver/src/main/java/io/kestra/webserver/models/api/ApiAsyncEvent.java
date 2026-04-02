package io.kestra.webserver.models.api;

import io.kestra.core.events.EventId;

public record ApiAsyncEvent(String eventId) {
    public static ApiAsyncEvent from(EventId eventId) {
        return new ApiAsyncEvent(eventId.value().toString());
    }
}
