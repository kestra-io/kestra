package io.kestra.controller.grpc.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.controller.grpc.ConnectRequest;
import io.kestra.controller.grpc.ConnectResponse;
import io.kestra.core.worker.WorkerGroups;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcConnectControllerServiceTest {

    @Test
    void shouldReportRejectionOnObserverWhenResolveThrowsStatus() {
        StatusRuntimeException rejection = Status.FAILED_PRECONDITION
            .withDescription("Too many worker threads.")
            .asRuntimeException();
        GrpcConnectControllerService service = new GrpcConnectControllerService(Map::of) {
            @Override
            protected String resolveWorkerGroupId(ConnectRequest request) {
                throw rejection;
            }
        };
        RecordingObserver observer = new RecordingObserver();

        service.connect(ConnectRequest.getDefaultInstance(), observer);

        assertThat(observer.responses).isEmpty();
        assertThat(observer.completed).isFalse();
        assertThat(Status.fromThrowable(observer.error).getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(Status.fromThrowable(observer.error).getDescription()).isEqualTo("Too many worker threads.");
    }

    @Test
    void shouldRespondWithResolvedWorkerGroupWhenConnectSucceeds() {
        GrpcConnectControllerService service = new GrpcConnectControllerService(Map::of);
        RecordingObserver observer = new RecordingObserver();

        service.connect(ConnectRequest.getDefaultInstance(), observer);

        assertThat(observer.error).isNull();
        assertThat(observer.completed).isTrue();
        assertThat(observer.responses)
            .singleElement()
            .satisfies(response -> assertThat(response.getWorkerGroupId()).isEqualTo(WorkerGroups.DEFAULT_ID));
    }

    private static class RecordingObserver implements StreamObserver<ConnectResponse> {
        private final List<ConnectResponse> responses = new ArrayList<>();
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(ConnectResponse value) {
            responses.add(value);
        }

        @Override
        public void onError(Throwable t) {
            error = t;
        }

        @Override
        public void onCompleted() {
            completed = true;
        }
    }
}
