package io.kestra.controller.grpc.services;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.controller.grpc.OpaqueData;
import io.kestra.controller.grpc.WorkerConnectionInfo;
import io.kestra.controller.grpc.WorkerControllerService;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc;
import io.kestra.controller.grpc.WorkerJobRequest;
import io.kestra.controller.grpc.WorkerJobResponse;
import io.kestra.controller.messages.BatchMessage;
import io.kestra.controller.messages.MessageFormat;
import io.kestra.core.executor.WorkerJobRunningStateStore;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.MessageTooBigException;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.UnsupportedMessageException;
import io.kestra.core.runners.*;
import io.kestra.core.scheduler.events.TriggerEvaluated;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.scheduler.service.TriggerExecutionPublisher;
import io.kestra.core.worker.models.WorkerTriggerResult;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class GrpcWorkerControllerService extends WorkerControllerServiceGrpc.WorkerControllerServiceImplBase implements WorkerControllerService {

    @Inject
    private DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    private DispatchQueueInterface<MetricEntry> metricEntryQueue;

    @Inject
    private LogEntryEmitter logEntryEmitter;

    @Inject
    private TriggerEventQueue triggerEventQueue;

    @Inject
    private TriggerExecutionPublisher triggerExecutionPublisher;

    @Inject
    private WorkerJobRunningStateStore workerJobRunningStateStore;

    @Inject
    private WorkerJobDispatcher workerJobDispatcher;

    @Inject
    private RunContextLoggerFactory runContextLoggerFactory;

    /**
     * Bidirectional streaming RPC for job distribution using the pull/ack pattern.
     * <p>
     * The worker sends:
     * <ul>
     * <li>First message: connection info + initial permits</li>
     * <li>Subsequent messages: new permits + ACKs for received jobs</li>
     * </ul>
     * <p>
     * The controller sends jobs only when the worker has capacity (permits > 0).
     * Jobs are persisted to WorkerJobRunningStateStore BEFORE sending for recovery.
     */
    @Override
    public StreamObserver<WorkerJobRequest> streamWorkerJobs(StreamObserver<WorkerJobResponse> responseObserver) {
        ServerCallStreamObserver<WorkerJobResponse> serverObserver = (ServerCallStreamObserver<WorkerJobResponse>) responseObserver;

        // Context holder - populated when first message is received
        final AtomicReference<WorkerStreamContext<WorkerJobResponse>> contextRef = new AtomicReference<>();

        // Set up cancellation handler - must be done before returning the StreamObserver
        serverObserver.setOnCancelHandler(() ->
        {
            WorkerStreamContext<WorkerJobResponse> ctx = contextRef.get();
            if (ctx != null) {
                log.info("Worker [{}] stream cancelled", ctx.getWorkerId());
                workerJobDispatcher.unregisterWorker(ctx.getWorkerId());
            } else {
                log.info("Worker stream cancelled before initialization");
            }
        });

        return new StreamObserver<>() {
            private volatile boolean initialized = false;

            @Override
            public void onNext(WorkerJobRequest request) {
                if (!initialized) {
                    // First message must contain connection info
                    if (!request.hasConnectionInfo()) {
                        log.error("First message in stream must contain connectionInfo");
                        responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT.withDescription("First message must contain connectionInfo").asRuntimeException());
                        return;
                    }

                    WorkerConnectionInfo connInfo = request.getConnectionInfo();
                    String workerId = connInfo.getWorkerId();
                    // WorkerGroup is optional - use null/empty for default group
                    String workerGroup = connInfo.getWorkerGroup();
                    int maxConcurrency = connInfo.getMaxConcurrency();

                    log.info("Worker [{}] connected for, group='{}', maxConcurrency={}", workerId, WorkerGroup.forLog(workerGroup), maxConcurrency);

                    // Create context for this worker stream
                    WorkerStreamContext<WorkerJobResponse> context = new WorkerStreamContext<>(workerId, workerGroup, maxConcurrency, responseObserver);
                    contextRef.set(context);

                    // Register with dispatcher
                    workerJobDispatcher.registerWorker(context);
                    initialized = true;
                }

                WorkerStreamContext<WorkerJobResponse> context = contextRef.get();
                if (context == null) {
                    // This should never happen - defensive check
                    log.error("Received message before context initialization");
                    responseObserver.onError(io.grpc.Status.INTERNAL.withDescription("Context not initialized").asRuntimeException());
                    return;
                }

                // Process permits
                int permits = request.getPermits();
                if (permits > 0) {
                    workerJobDispatcher.onPermitsReceived(context, permits);
                }

                // Process ACKs
                List<String> acks = request.getAcknowledgedJobIdsList();
                if (!acks.isEmpty()) {
                    workerJobDispatcher.onAcksReceived(context, acks);
                }
            }

            @Override
            public void onError(Throwable t) {
                WorkerStreamContext<WorkerJobResponse> context = contextRef.get();
                if (context != null) {
                    log.warn("Worker [{}] stream error: {}", context.getWorkerId(), t.getMessage());
                    workerJobDispatcher.unregisterWorker(context.getWorkerId());
                } else {
                    log.warn("Worker stream error before initialization: {}", t.getMessage());
                }
            }

            @Override
            public void onCompleted() {
                WorkerStreamContext<WorkerJobResponse> context = contextRef.get();
                if (context != null) {
                    log.info("Worker [{}] stream completed normally", context.getWorkerId());
                    workerJobDispatcher.unregisterWorker(context.getWorkerId());
                }
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void sendWorkerTaskResults(OpaqueData request, StreamObserver<OpaqueData> responseObserver) {
        final MessageFormat messageFormat = MessageFormat.resolve(request.getHeader().getMessageFormat());
        BatchMessage<WorkerTaskResult> message = messageFormat.fromByteString(request.getMessage(), TypeReferences.WORKER_TASK_RESULT);
        message.records().forEach(workerTaskResult ->
        {
            try {
                workerTaskResultQueue.emit(workerTaskResult);
            } catch (QueueException e) {
                // If there is a QueueException it can either be caused by the message limit or another queue issue.
                // We fail the task and try to resend it.
                WorkerTaskResult failed = new WorkerTaskResult(workerTaskResult.getTaskRun().fail(), workerTaskResult.getOutputs());
                if (e instanceof MessageTooBigException) {
                    // If it's a message too big, we remove the outputs
                    failed = failed.withOutputs(null);
                }
                if (e instanceof UnsupportedMessageException) {
                    // we expect the offending char is in the output so we remove it
                    failed = failed.withOutputs(null);
                }
                RunContextLogger contextLogger = runContextLoggerFactory.create(workerTaskResult);
                contextLogger.logger().error("Unable to emit the worker task result to the queue: {}", e.getMessage(), e);
                try {
                    this.workerTaskResultQueue.emit(failed);
                } catch (QueueException ex) {
                    log.error("Unable to emit the worker task result for task {} taskrun {}", failed.getTaskRun().getTaskId(), failed.getTaskRun().getId(), e);
                }
            }
        });
        responseObserver.onNext(OpaqueData.newBuilder().setHeader(request.getHeader()).build());
        responseObserver.onCompleted();
    }

    @Override
    public void sendWorkerTriggerResults(OpaqueData request, StreamObserver<OpaqueData> responseObserver) {
        final MessageFormat messageFormat = MessageFormat.resolve(request.getHeader().getMessageFormat());
        BatchMessage<WorkerTriggerResult> message = messageFormat.fromByteString(request.getMessage(), TypeReferences.WORKER_TRIGGER_RESULT);
        message.records().forEach(workerTriggerResult ->
        {
            var evaluation = workerTriggerResult.evaluation();

            switch (workerTriggerResult.type()) {
                case POLLING -> triggerEventQueue.send(new TriggerEvaluated(workerTriggerResult.id(), evaluation));
                case REALTIME -> {
                    if (evaluation != null) {
                        triggerExecutionPublisher.send(evaluation.toExecution(workerTriggerResult.id()));
                    }
                }
                default -> throw new IllegalStateException("Unexpected value: " + workerTriggerResult.type());
            }
            workerJobRunningStateStore.deleteByKey(NoTransactionContext.INSTANCE, workerTriggerResult.id().uid());
        });
        responseObserver.onNext(OpaqueData.newBuilder().setHeader(request.getHeader()).build());
        responseObserver.onCompleted();
    }

    @Override
    public void sendWorkerLogEntries(OpaqueData request, StreamObserver<OpaqueData> responseObserver) {
        final MessageFormat messageFormat = MessageFormat.resolve(request.getHeader().getMessageFormat());
        BatchMessage<LogEntry> message = messageFormat.fromByteString(request.getMessage(), TypeReferences.LOG_ENTRY);
        if (!message.records().isEmpty()) {
            logEntryEmitter.emits(message.records());
        }
        responseObserver.onNext(OpaqueData.newBuilder().setHeader(request.getHeader()).build());
        responseObserver.onCompleted();
    }

    @Override
    public void sendWorkerMetricEntries(OpaqueData request, StreamObserver<OpaqueData> responseObserver) {
        final MessageFormat messageFormat = MessageFormat.resolve(request.getHeader().getMessageFormat());
        BatchMessage<MetricEntry> message = messageFormat.fromByteString(request.getMessage(), TypeReferences.METRIC_ENTRY);
        if (!message.records().isEmpty()) {
            metricEntryQueue.emitAsync(message.records());
        }
        responseObserver.onNext(OpaqueData.newBuilder().setHeader(request.getHeader()).build());
        responseObserver.onCompleted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        workerJobDispatcher.close();
    }

    /**
     * TypeReferences for deserialization of BatchMessages with different record types.
     */
    interface TypeReferences {
        TypeReference<BatchMessage<WorkerTaskResult>> WORKER_TASK_RESULT = new TypeReference<>() {
        };

        TypeReference<BatchMessage<WorkerTriggerResult>> WORKER_TRIGGER_RESULT = new TypeReference<>() {
        };

        TypeReference<BatchMessage<MetricEntry>> METRIC_ENTRY = new TypeReference<>() {
        };

        TypeReference<BatchMessage<LogEntry>> LOG_ENTRY = new TypeReference<>() {
        };
    }
}
