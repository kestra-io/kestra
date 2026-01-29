package io.kestra.worker.senders;

import io.kestra.controller.grpc.OpaqueData;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceStub;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.controller.messages.BatchMessage;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.senders.internals.LogStreamObserver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Component responsible for sending {@link MetricEntry}.
 */
@Singleton
public class MetricsWorkerIOSender extends GrpcWorkerIOSender<MetricEntry> {

    @Inject
    public MetricsWorkerIOSender(
        final WorkerControllerServiceStub controllerServiceStub,
        final WorkerQueueRegistry workerQueueRegistry) {
        super(controllerServiceStub, workerQueueRegistry, MetricsWorkerIOSender.class.getSimpleName(), MetricEntry.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void send(final List<MetricEntry> results) {
        if (results.isEmpty()) return;

        OpaqueData request = OpaqueData
            .newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerContext))
            .setMessage(MessageFormats.JSON.toByteString(BatchMessage.of(results)))
            .build();

        controllerServiceStub.sendWorkerMetricEntries(request, new LogStreamObserver<>());
    }
}
