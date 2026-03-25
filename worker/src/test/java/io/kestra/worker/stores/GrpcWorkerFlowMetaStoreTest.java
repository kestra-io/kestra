package io.kestra.worker.stores;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.controller.grpc.WorkerFlowMetaStoreServiceGrpc.WorkerFlowMetaStoreServiceBlockingStub;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.services.FlowService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.debug.Return;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class GrpcWorkerFlowMetaStoreTest extends AbstractGrpcMetaStoreTest {

    @Inject
    WorkerFlowMetaStoreServiceBlockingStub flowMetaStoreStub;

    @Inject
    FlowService flowService;

    private GrpcWorkerFlowMetaStore grpcWorkerFlowMetaStore;

    @Override
    protected void initClientStore() {
        grpcWorkerFlowMetaStore = new GrpcWorkerFlowMetaStore(flowMetaStoreStub, clientWorkerInfo());
    }

    @Test
    void shouldReturnTrueWhenIsNamespaceExistsGivenExistingNamespace() throws Exception {
        // Given
        String namespace = TestsUtils.randomNamespace();
        FlowWithSource flow = flowService.create(GenericFlow.of(createFlow(namespace)));

        // When
        boolean result = grpcWorkerFlowMetaStore.isNamespaceExists(TenantService.MAIN_TENANT, namespace);

        // Then
        assertThat(result).isTrue();

        flowService.delete(flow);
    }

    @Test
    void shouldReturnFalseWhenIsNamespaceExistsGivenAbsentNamespace() {
        // When
        boolean result = grpcWorkerFlowMetaStore.isNamespaceExists(TenantService.MAIN_TENANT, TestsUtils.randomNamespace());

        // Then
        assertThat(result).isFalse();
    }

    private FlowWithSource createFlow(String namespace) {
        return FlowWithSource.builder()
            .tenantId(TenantService.MAIN_TENANT)
            .namespace(namespace)
            .id(IdUtils.create())
            .tasks(
                List.of(
                    Return.builder()
                        .id("return")
                        .format(Property.ofValue("format"))
                        .type(Return.class.getName())
                        .build()
                )
            )
            .build();
    }
}
