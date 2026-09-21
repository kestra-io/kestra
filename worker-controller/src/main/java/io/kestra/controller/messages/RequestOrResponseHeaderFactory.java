package io.kestra.controller.messages;

import java.util.UUID;

import io.kestra.controller.grpc.RequestOrResponseHeader;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.utils.EditionProvider;
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
            .setEdition(toProtoEdition(KestraContext.getContext().getEdition()))
            .build();
    }

    /**
     * Maps the core {@link EditionProvider.Edition} to its proto counterpart, falling back to
     * {@link RequestOrResponseHeader.Edition#EDITION_UNSPECIFIED} when {@code edition} is {@code null} (e.g. a test double
     * standing in for {@link KestraContext} that only stubs the methods it exercises).
     */
    private static RequestOrResponseHeader.Edition toProtoEdition(EditionProvider.Edition edition) {
        return switch (edition) {
            case OSS -> RequestOrResponseHeader.Edition.EDITION_OSS;
            case EE -> RequestOrResponseHeader.Edition.EDITION_EE;
            case null -> RequestOrResponseHeader.Edition.EDITION_UNSPECIFIED;
        };
    }
}
