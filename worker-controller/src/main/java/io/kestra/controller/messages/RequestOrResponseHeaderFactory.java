package io.kestra.controller.messages;

import java.util.UUID;

import io.kestra.controller.grpc.RequestOrResponseHeader;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.worker.models.WorkerContext;

/**
 * Factory class for creating instances of {@link RequestOrResponseHeader}.
 */
public class RequestOrResponseHeaderFactory {

    /**
     * Creates a new {@link RequestOrResponseHeader} instance with the given worker context.
     *
     * @param workerContext the context of the worker providing details such as worker ID
     *        required for constructing the header.
     * @return a {@link RequestOrResponseHeader} instance initialized with client-specific
     *         fields and metadata.
     */
    public static RequestOrResponseHeader create(WorkerContext workerContext) {
        return create(workerContext.workerId());
    }

    /**
     * Creates a new {@link RequestOrResponseHeader} instance with the given client ID.
     *
     * @param clientId the client ID (typically the worker ID) for constructing the header.
     * @return a {@link RequestOrResponseHeader} instance initialized with client-specific
     *         fields and metadata.
     */
    public static RequestOrResponseHeader create(String clientId) {
        return RequestOrResponseHeader
            .newBuilder()
            .setClientId(clientId)
            .setClientVersion(KestraContext.getContext().getVersion())
            .setMessageFormat(MessageFormats.JSON.name())
            .setCorrelationId(UUID.randomUUID().toString())
            .build();
    }
}
