package io.kestra.worker.senders;

import io.kestra.controller.grpc.OpaqueData;
import io.kestra.core.worker.models.WorkerTriggerResult;
import io.kestra.controller.messages.BatchMessage;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.senders.internals.LogStreamObserver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Component responsible for sending {@link WorkerTriggerResult}.
 */
@Singleton
public class TriggerResultWorkerIOSender extends GrpcWorkerIOSender<WorkerTriggerResult> {

    @Inject
    public TriggerResultWorkerIOSender(
        final WorkerControllerServiceGrpc.WorkerControllerServiceStub controllerServiceStub,
        final WorkerQueueRegistry workerQueueRegistry) {
        super(controllerServiceStub, workerQueueRegistry, TriggerResultWorkerIOSender.class.getSimpleName(), WorkerTriggerResult.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void send(List<WorkerTriggerResult> results) {
        if (results.isEmpty()) return;
        
        results.forEach(result -> {
            OpaqueData request = OpaqueData
                .newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerContext))
                .setMessage(MessageFormats.JSON.toByteString(BatchMessage.of(result)))
                .build();

            controllerServiceStub.sendWorkerTriggerResults(request, new LogStreamObserver<>());
        });
    }
}
