package io.kestra.worker.senders;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.controller.messages.BatchMessage;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc;
import io.kestra.controller.grpc.WorkerLogEntriesRequest;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.senders.internals.LogStreamObserver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Component responsible for sending {@link LogEntry}.
 */
@Singleton
public class LogEntryWorkerIOSender extends GrpcWorkerIOSender<LogEntry> {

    @Inject
    public LogEntryWorkerIOSender(
        final WorkerControllerServiceGrpc.WorkerControllerServiceStub controllerServiceStub,
        final WorkerQueueRegistry workerQueueRegistry) {
        super(controllerServiceStub, workerQueueRegistry, LogEntryWorkerIOSender.class.getSimpleName(), LogEntry.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void send(final List<LogEntry> results) {
        if (results.isEmpty()) return;

        WorkerLogEntriesRequest request = WorkerLogEntriesRequest
            .newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerContext))
            .setMessage(MessageFormats.JSON.toByteString(BatchMessage.of(results)))
            .build();

        controllerServiceStub.sendWorkerLogEntries(request, new LogStreamObserver<>());
    }
}
