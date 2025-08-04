package io.kestra.controller.grpc.server;

import io.grpc.stub.StreamObserver;
import io.kestra.controller.grpc.HeartbeatRequest;
import io.kestra.controller.grpc.HeartbeatResponse;
import io.kestra.controller.grpc.LivenessControllerServiceGrpc;
import io.kestra.controller.messages.HeartbeatMessage;
import io.kestra.controller.messages.HeartbeatMessageReply;
import io.kestra.controller.messages.MessageFormat;
import io.kestra.core.server.ServiceLivenessUpdater;
import io.kestra.core.server.ServiceStateTransition;
import io.kestra.core.services.MaintenanceService;
import io.kestra.core.utils.Disposable;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class GrpcLivenessControllerService extends LivenessControllerServiceGrpc.LivenessControllerServiceImplBase {

    private final ServiceLivenessUpdater serviceLivenessUpdater;
    
    private final AtomicBoolean maintenanceMode = new AtomicBoolean(false);

    private final Disposable maintenanceListenerDisposable;

    @Inject
    public GrpcLivenessControllerService(ServiceLivenessUpdater serviceLivenessUpdater,
                                         MaintenanceService maintenanceService) {
        this.serviceLivenessUpdater = serviceLivenessUpdater;
        this.maintenanceMode.set(maintenanceService.isInMaintenanceMode());
        this.maintenanceListenerDisposable = maintenanceService.listen(new MaintenanceService.MaintenanceListener() {
            @Override
            public void onMaintenanceModeEnter() {
                GrpcLivenessControllerService.this.maintenanceMode.set(true);
            }

            @Override
            public void onMaintenanceModeExit() {
                GrpcLivenessControllerService.this.maintenanceMode.set(false);
            }
        });
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void heartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
        final MessageFormat messageFormat = MessageFormat.resolve(request.getHeader().getMessageFormat());

        HeartbeatMessage message = messageFormat
            .fromByteString(request.getMessage(), HeartbeatMessage.class);

        ServiceStateTransition.Response response;
        if (message.newState() != null) {
            response = serviceLivenessUpdater.update(message.instance(), message.newState(), message.reason());
        } else {
            serviceLivenessUpdater.update(message.instance());
            response = new ServiceStateTransition.Response(ServiceStateTransition.Result.SUCCEEDED, message.instance());
        }

        responseObserver.onNext(HeartbeatResponse
            .newBuilder()
            .setHeader(request.getHeader())
            .setMessage(messageFormat.toByteString(new HeartbeatMessageReply(
                response.instance(),
                response.result(),
                maintenanceMode.get()
            )))
            .build()
        );
        responseObserver.onCompleted();
    }

    @PreDestroy
    public void close() {
        if (this.maintenanceListenerDisposable != null) {
            this.maintenanceListenerDisposable.dispose();
        }
    }
}
