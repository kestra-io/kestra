package io.kestra.controller.grpc.services;

import io.kestra.controller.grpc.WorkerControllerService;
import io.kestra.core.reporter.ServerEventSender;
import io.kestra.controller.grpc.WorkerReportRequest;
import io.kestra.controller.grpc.WorkerReportResponse;
import io.kestra.controller.grpc.WorkerReportingServiceGrpc;

import io.grpc.stub.StreamObserver;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC service that receives server events reports from workers and relays them
 * to the reporting endpoint via the {@link ServerEventSender}.
 * <p>
 * The worker sends a pre-built {@code ServerEvent} as JSON bytes. This service
 * forwards those bytes using {@link ServerEventSender#sendRaw(byte[], String)},
 * which adds the appropriate headers (including EE license headers when applicable).
 */
@Singleton
@Slf4j
public class GrpcWorkerReportingService
    extends WorkerReportingServiceGrpc.WorkerReportingServiceImplBase
    implements WorkerControllerService {

    private final ServerEventSender serverEventSender;

    @Inject
    public GrpcWorkerReportingService(ServerEventSender serverEventSender) {
        this.serverEventSender = serverEventSender;
    }

    /** {@inheritDoc} */
    @Override
    public void sendReport(WorkerReportRequest request, StreamObserver<WorkerReportResponse> responseObserver) {
        try {
            String eventType = request.getEventType();
            byte[] payload = request.getPayload().toByteArray();

            serverEventSender.sendRaw(payload, eventType);

            responseObserver.onNext(
                WorkerReportResponse.newBuilder()
                    .setHeader(request.getHeader())
                    .setSuccess(true)
                    .build()
            );
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.warn("Failed to relay worker server events report", e);
            responseObserver.onNext(
                WorkerReportResponse.newBuilder()
                    .setSuccess(false)
                    .build()
            );
            responseObserver.onCompleted();
        }
    }
}
