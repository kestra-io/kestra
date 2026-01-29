package io.kestra.controller.grpc.services;

import com.fasterxml.jackson.core.type.TypeReference;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.kestra.controller.grpc.OpaqueData;
import io.kestra.controller.grpc.WorkerControllerService;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc;
import io.kestra.controller.messages.BatchMessage;
import io.kestra.controller.messages.FetchWorkerJobMessage;
import io.kestra.controller.messages.MessageFormat;
import io.kestra.controller.messages.WorkerJobBatchMessage;
import io.kestra.core.executor.WorkerJobRunningStateStore;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.runners.NoTransactionContext;
import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerInstance;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.runners.WorkerTaskRunning;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.runners.WorkerTriggerRunning;
import io.kestra.core.scheduler.TriggerEventQueue;
import io.kestra.core.scheduler.events.TriggerEvaluated;
import io.kestra.core.scheduler.service.TriggerExecutionPublisher;
import io.kestra.core.utils.Logs;
import io.kestra.core.worker.models.WorkerTriggerResult;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Slf4j
public class GrpcWorkerControllerService extends WorkerControllerServiceGrpc.WorkerControllerServiceImplBase implements WorkerControllerService {

    public static final TypeReference<BatchMessage<WorkerTaskResult>> WORKER_TASK_RESULT_BATCH_MESSAGE_TYPE_REFERENCE = new TypeReference<>() {
    };
    public static final TypeReference<BatchMessage<WorkerTriggerResult>> WORKER_TRIGGER_RESULT_BATCH_MESSAGE_TYPE_REFERENCE = new TypeReference<>() {
    };

    // QUEUES
    @Inject
    @Named(QueueFactoryInterface.WORKERJOB_NAMED)
    private WorkerJobQueueInterface workerJobQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    private QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    private TriggerEventQueue triggerEventQueue;

    // SERVICES
    @Inject
    private TriggerExecutionPublisher triggerExecutionPublisher;

    // STORES
    @Inject
    private WorkerJobRunningStateStore workerJobRunningStateStore;

    private final ConcurrentHashMap<String, Runnable> disposables = new ConcurrentHashMap<>();

    @Override
    public void fetchWorkerJobsStream(OpaqueData request, StreamObserver<OpaqueData> responseObserver) {
        final MessageFormat messageFormat = MessageFormat.resolve(request.getHeader().getMessageFormat());
        FetchWorkerJobMessage message = messageFormat.fromByteString(request.getMessage(), FetchWorkerJobMessage.class);

        ServerCallStreamObserver<OpaqueData> serverObserver = (ServerCallStreamObserver<OpaqueData>) responseObserver;

        log.info("Received worker-job request from worker [{}]", message.workerId());
        serverObserver.setOnCancelHandler(() -> {
            log.info("Worker [{}] disconnected or cancelled", message.workerId());
            Optional.ofNullable(disposables.remove(message.workerId())).ifPresent(Runnable::run);
        });

        // TODO 
        //  Currently consumer thread is managed directly by the WorkerJobQueue.
        //  It could be preferable that the WorkerControllerServer start a polling thread 
        //  for consuming the workerJobQueue (e.g., via a poll method) to be able to manage it more properly on cancel. 
        Runnable stopReceiving = this.workerJobQueue.receive(message.workerGroup(), Worker.class, either -> {
            if (either.isRight()) {
                log.error("Unable to deserialize a worker job: {}", either.getRight().getMessage());
                return;
            }
            WorkerJob job = either.getLeft();
            
            // TODO - rework the logging
            if (job instanceof WorkerTrigger it) {
                Logs.logTrigger(it.getTriggerContext(), log, Level.INFO, "Sending trigger to worker [{}]", message.workerId());
            }

            if (job instanceof WorkerTask it) {
                Logs.logTaskRun(it.getTaskRun(), Level.INFO, "Sending task to worker [{}]", message.workerId());
            }
            
            serverObserver.onNext(OpaqueData
                .newBuilder()
                .setHeader(request.getHeader())
                .setMessage(messageFormat.toByteString(new WorkerJobBatchMessage(List.of(job))))
                .build()
            );

            WorkerInstance workerInstance = new WorkerInstance(message.workerId(), message.workerGroup());
            if (job instanceof WorkerTask workerTask) {
                workerJobRunningStateStore.save(NoTransactionContext.INSTANCE, WorkerTaskRunning.of(workerTask, workerInstance, -1));
            } else if (job instanceof WorkerTrigger workerTrigger) {
                workerJobRunningStateStore.save(NoTransactionContext.INSTANCE, WorkerTriggerRunning.of(workerTrigger, workerInstance, -1));
            } else {
                log.error("Message is of type [{}] which should never occurs", job);
            }

        }, false);
        disposables.put(message.workerId(), () -> {
            stopReceiving.run();
            serverObserver.onCompleted();
        });
    }

    @Override
    public void sendWorkerTaskResults(OpaqueData request, StreamObserver<OpaqueData> responseObserver) {
        final MessageFormat messageFormat = MessageFormat.resolve(request.getHeader().getMessageFormat());
        BatchMessage<WorkerTaskResult> message = messageFormat.fromByteString(request.getMessage(), WORKER_TASK_RESULT_BATCH_MESSAGE_TYPE_REFERENCE);
        message.records().forEach(workerTaskResult -> {
            try {
                workerTaskResultQueue.emit(workerTaskResult);
            } catch (QueueException e) {
                throw new RuntimeException(e);
            }
        });
        responseObserver.onNext(OpaqueData.newBuilder().setHeader(request.getHeader()).build());
        responseObserver.onCompleted();
    }

    @Override
    public void sendWorkerTriggerResults(OpaqueData request, StreamObserver<OpaqueData> responseObserver) {
        final MessageFormat messageFormat = MessageFormat.resolve(request.getHeader().getMessageFormat());
        BatchMessage<WorkerTriggerResult> message = messageFormat.fromByteString(request.getMessage(), WORKER_TRIGGER_RESULT_BATCH_MESSAGE_TYPE_REFERENCE);
        message.records().forEach(workerTriggerResult -> {
            // Get if an Execution is attached to the TriggerResult.
            Execution execution = workerTriggerResult.execution();
            if (execution != null) {
                execution = execution.withTenantId(workerTriggerResult.id().getTenantId());
            }

            switch (workerTriggerResult.type()) {
                case POLLING -> triggerEventQueue.send(new TriggerEvaluated(workerTriggerResult.id(), execution));
                case REALTIME -> triggerExecutionPublisher.send(execution);
                default -> throw new IllegalStateException("Unexpected value: " + workerTriggerResult.type());
            }
            workerJobRunningStateStore.deleteByKey(NoTransactionContext.INSTANCE, workerTriggerResult.id().uid());
        });
        responseObserver.onNext(OpaqueData.newBuilder().setHeader(request.getHeader()).build());
        responseObserver.onCompleted();
    }

    @Override
    public void sendWorkerLogEntries(OpaqueData request, StreamObserver<OpaqueData> responseObserver) {
        // TODO
    }

    @Override
    public void sendWorkerMetricEntries(OpaqueData request, StreamObserver<OpaqueData> responseObserver) {
        // TODO
    }

    @PreDestroy
    public void close() {
        this.disposables.values().forEach(Runnable::run);
    }
}
