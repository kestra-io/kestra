package io.kestra.worker.reporter;

import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;

import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.reporter.ServerEvent;
import io.kestra.core.reporter.ServerEventSender;
import io.kestra.core.reporter.Type;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.worker.models.WorkerInfo;
import io.kestra.controller.grpc.WorkerReportRequest;
import io.kestra.controller.grpc.WorkerReportResponse;
import io.kestra.controller.grpc.WorkerReportingServiceGrpc;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC-based {@link ServerEventSender} for workers.
 * <p>
 * Instead of sending HTTP requests directly to the reporting endpoint, this sender
 * forwards events via gRPC to the controller, which then relays them.
 */
@Singleton
@Replaces(ServerEventSender.class)
@Requires(property = "kestra.server-type", value = "WORKER")
@Slf4j
public class GrpcServerEventSender extends ServerEventSender {

    private static final ObjectMapper OBJECT_MAPPER = JacksonMapper.ofJson();

    private final WorkerReportingServiceGrpc.WorkerReportingServiceBlockingStub reportingStub;
    private final WorkerInfo workerInfo;

    @Inject
    public GrpcServerEventSender(WorkerReportingServiceGrpc.WorkerReportingServiceBlockingStub reportingStub, WorkerInfo workerInfo) {
        this.reportingStub = reportingStub;
        this.workerInfo = workerInfo;
    }

    /** {@inheritDoc} */
    @Override
    public void send(final Instant now, final Type type, Object event) {
        try {
            ServerEvent serverEvent = buildServerEvent(now, event);
            byte[] payload = OBJECT_MAPPER.writeValueAsBytes(serverEvent);

            WorkerReportRequest request = WorkerReportRequest.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
                .setEventType(type.name())
                .setPayload(ByteString.copyFrom(payload))
                .build();

            if (log.isTraceEnabled()) {
                log.trace("Sending server events report via gRPC for event-type '{}'", type.name());
            }

            WorkerReportResponse response = reportingStub.sendReport(request);
            if (!response.getSuccess()) {
                log.debug("Controller rejected server events report for event-type '{}'", type.name());
            }
        } catch (Exception e) {
            log.debug("Failed to send worker server events report via gRPC for event-type '{}'", type.name(), e);
        }
    }
}
