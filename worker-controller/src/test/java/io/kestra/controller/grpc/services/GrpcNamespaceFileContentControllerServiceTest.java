package io.kestra.controller.grpc.services;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import io.kestra.controller.grpc.BooleanResponse;
import io.kestra.controller.grpc.NamespaceFileObjectRequest;
import io.kestra.controller.grpc.StreamChunk;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.StorageNamespaceFileBackend;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class GrpcNamespaceFileContentControllerServiceTest {

    private final StorageInterface storage = mock(StorageInterface.class);
    private final GrpcNamespaceFileContentControllerService service = new GrpcNamespaceFileContentControllerService(new StorageNamespaceFileBackend(storage));

    @ParameterizedTest
    @CsvSource({
        "main, io.kestra, ../../../../etc/passwd",
        "main, io.kestra, scripts/..\\..\\..\\..\\etc/passwd",
        "main, ../escaped, main.py",
        "'..', io.kestra, main.py",
    })
    @SuppressWarnings("unchecked")
    void shouldRejectALocationOutsideTheNamespaceFilesBeforeReachingTheStorage(String tenant, String namespace, String object) {
        NamespaceFileObjectRequest request = NamespaceFileObjectRequest.newBuilder()
            .setTenantId(tenant)
            .setNamespace(namespace)
            .setObject(object)
            .build();
        StreamObserver<StreamChunk> getObserver = mock(StreamObserver.class);
        StreamObserver<BooleanResponse> deleteObserver = mock(StreamObserver.class);

        service.get(request, getObserver);
        service.delete(request, deleteObserver);

        assertThat(errorOf(getObserver)).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(errorOf(deleteObserver)).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verifyNoInteractions(storage);
    }

    private static Status.Code errorOf(StreamObserver<?> observer) {
        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(error.capture());
        return ((StatusRuntimeException) error.getValue()).getStatus().getCode();
    }
}
