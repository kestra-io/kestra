package io.kestra.controller.grpc.services;

import io.grpc.stub.StreamObserver;
import io.kestra.controller.grpc.OpaqueData;
import io.kestra.controller.grpc.TaskOutputForTaskRequest;
import io.kestra.controller.grpc.TaskOutputProviderServiceGrpc;
import io.kestra.controller.grpc.TaskOutputRequest;
import io.kestra.controller.grpc.WorkerControllerService;
import io.kestra.controller.messages.MessageFormat;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.worker.models.WorkerInfo;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * gRPC service implementation for task output operations.
 * Provides lazy output resolution to workers via gRPC.
 */
@Singleton
@Requires(property = "kestra.server-type", pattern = "(CONTROLLER|STANDALONE)")
public class GrpcTaskOutputProviderControllerService
    extends TaskOutputProviderServiceGrpc.TaskOutputProviderServiceImplBase
    implements WorkerControllerService {

    private static final Logger log = LoggerFactory.getLogger(GrpcTaskOutputProviderControllerService.class);
    private static final MessageFormat MESSAGE_FORMAT = MessageFormats.JSON;

    private final TaskOutputService taskOutputService;
    private final ExecutionRepositoryInterface executionRepository;
    private final WorkerInfo workerInfo;

    @Inject
    public GrpcTaskOutputProviderControllerService(TaskOutputService taskOutputService,
                                                    ExecutionRepositoryInterface executionRepository,
                                                    WorkerInfo workerInfo) {
        this.taskOutputService = taskOutputService;
        this.executionRepository = executionRepository;
        this.workerInfo = workerInfo;
    }

    @Override
    public void computeOutputs(TaskOutputRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            log.trace("Received computeOutputs request: tenantId={}, executionId={}",
                request.getTenantId(), request.getExecutionId());

            Execution execution = findExecution(request.getTenantId(), request.getExecutionId());
            Map<String, Object> outputs = taskOutputService.computeOutputs(execution);

            OpaqueData response = OpaqueData.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
                .setMessage(MESSAGE_FORMAT.toByteString(outputs))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error during computeOutputs for executionId={}", request.getExecutionId(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void computeOutputsForTask(TaskOutputForTaskRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            log.trace("Received computeOutputsForTask request: tenantId={}, executionId={}, taskId={}",
                request.getTenantId(), request.getExecutionId(), request.getTaskId());

            Execution execution = findExecution(request.getTenantId(), request.getExecutionId());
            Map<String, Object> outputs = taskOutputService.computeOutputsForTask(execution, request.getTaskId());

            OpaqueData response = OpaqueData.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
                .setMessage(MESSAGE_FORMAT.toByteString(outputs))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error during computeOutputsForTask for executionId={}, taskId={}", request.getExecutionId(), request.getTaskId(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void findTaskIdsWithOutput(TaskOutputRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            log.trace("Received findTaskIdsWithOutput request: tenantId={}, executionId={}",
                request.getTenantId(), request.getExecutionId());

            Execution execution = findExecution(request.getTenantId(), request.getExecutionId());
            Set<String> taskIds = taskOutputService.findTaskIdWithOutputByExecution(execution);

            OpaqueData response = OpaqueData.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
                .setMessage(MESSAGE_FORMAT.toByteString(taskIds))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error during findTaskIdsWithOutput for executionId={}", request.getExecutionId(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void getValueToTaskIds(TaskOutputRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            log.trace("Received getValueToTaskIds request: tenantId={}, executionId={}",
                request.getTenantId(), request.getExecutionId());

            Execution execution = findExecution(request.getTenantId(), request.getExecutionId());
            Map<String, List<String>> valueToTaskIds = buildValueToTaskIds(execution);

            OpaqueData response = OpaqueData.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
                .setMessage(MESSAGE_FORMAT.toByteString(valueToTaskIds))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error during getValueToTaskIds for executionId={}", request.getExecutionId(), e);
            responseObserver.onError(e);
        }
    }

    private Execution findExecution(String tenantId, String executionId) {
        return executionRepository.findByIdWithoutAcl(tenantId, executionId).orElse(null);
    }

    private static Map<String, List<String>> buildValueToTaskIds(Execution execution) {
        if (execution == null || execution.getTaskRunList() == null) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> vmap = new HashMap<>();
        for (TaskRun tr : execution.getTaskRunList()) {
            if (tr.getValue() != null) {
                vmap.computeIfAbsent(tr.getValue(), k -> new ArrayList<>()).add(tr.getTaskId());
            }
        }
        return vmap;
    }
}
