package io.kestra.worker.senders;

import io.kestra.controller.grpc.OpaqueData;
import io.kestra.core.runners.WorkerTaskResult;
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
 * Component responsible for sending {@link WorkerTaskResult}.
 */
@Singleton
public class TaskResultWorkerIOSender extends GrpcWorkerIOSender<WorkerTaskResult> {
    
    @Inject
    public TaskResultWorkerIOSender(
        final WorkerControllerServiceGrpc.WorkerControllerServiceStub controllerServiceStub,
        final WorkerQueueRegistry workerQueueRegistry) {
        super(controllerServiceStub, workerQueueRegistry, TaskResultWorkerIOSender.class.getSimpleName(), WorkerTaskResult.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void send(List<WorkerTaskResult> results) {
        if (results.isEmpty()) return;
        
        results.forEach(result -> {
            OpaqueData request = OpaqueData
                .newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerContext))
                .setMessage(MessageFormats.JSON.toByteString(BatchMessage.of(result)))
                .build();

            controllerServiceStub.sendWorkerTaskResults(request, new LogStreamObserver<>());
        });
    }
}
