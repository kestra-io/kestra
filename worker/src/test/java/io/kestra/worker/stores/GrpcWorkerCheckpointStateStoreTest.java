package io.kestra.worker.stores;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.controller.grpc.CheckpointServiceGrpc.CheckpointServiceBlockingStub;
import io.kestra.core.checkpoint.Checkpoint;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.CheckpointStateStore;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@Property(name = "test.context.id", value = "grpc-checkpoint")
class GrpcWorkerCheckpointStateStoreTest extends AbstractGrpcMetaStoreTest {

    @Inject
    CheckpointServiceBlockingStub checkpointStub;

    @Inject
    CheckpointStateStore checkpointStateStore;

    private GrpcWorkerCheckpointStateStore grpcWorkerCheckpointStore;

    @Override
    protected void initClientStore() {
        grpcWorkerCheckpointStore = new GrpcWorkerCheckpointStateStore(checkpointStub, clientWorkerInfo());
    }

    @Test
    void shouldPersistCheckpointWhenSetThroughGrpc() {
        // Given
        Checkpoint checkpoint = new Checkpoint(TestsUtils.randomTenant(), IdUtils.create(), IdUtils.create(), "offset", "42");

        // When
        grpcWorkerCheckpointStore.set(checkpoint);

        // Then
        assertThat(checkpointStateStore.get(checkpoint.tenantId(), checkpoint.executionId(), checkpoint.taskRunId(), "offset")).contains(checkpoint);
    }

    @Test
    void shouldOverwriteValueWhenSetTwiceThroughGrpc() {
        // Given
        Checkpoint first = new Checkpoint(TestsUtils.randomTenant(), IdUtils.create(), IdUtils.create(), "offset", "1");
        grpcWorkerCheckpointStore.set(first);

        // When
        grpcWorkerCheckpointStore.set(new Checkpoint(first.tenantId(), first.executionId(), first.taskRunId(), "offset", "2"));

        // Then
        assertThat(grpcWorkerCheckpointStore.get(first.tenantId(), first.executionId(), first.taskRunId(), "offset"))
            .get()
            .extracting(Checkpoint::value)
            .isEqualTo("2");
    }

    @Test
    void shouldReturnCheckpointWhenGetGivenExistingKey() {
        // Given
        Checkpoint checkpoint = new Checkpoint(TestsUtils.randomTenant(), IdUtils.create(), IdUtils.create(), "offset", "42");
        checkpointStateStore.set(checkpoint);

        // When
        Optional<Checkpoint> result = grpcWorkerCheckpointStore.get(checkpoint.tenantId(), checkpoint.executionId(), checkpoint.taskRunId(), "offset");

        // Then
        assertThat(result).contains(checkpoint);
    }

    @Test
    void shouldReturnEmptyWhenGetGivenAbsentKey() {
        // Given
        Checkpoint checkpoint = new Checkpoint(TestsUtils.randomTenant(), IdUtils.create(), IdUtils.create(), "offset", "42");
        checkpointStateStore.set(checkpoint);

        // When
        Optional<Checkpoint> result = grpcWorkerCheckpointStore.get(checkpoint.tenantId(), checkpoint.executionId(), checkpoint.taskRunId(), "absent");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldListOnlyCheckpointsOfTaskRunWhenListGivenSeveralTaskRuns() {
        // Given
        String tenant = TestsUtils.randomTenant();
        String executionId = IdUtils.create();
        String taskRunId = IdUtils.create();
        Checkpoint first = new Checkpoint(tenant, executionId, taskRunId, "offset", "1");
        Checkpoint second = new Checkpoint(tenant, executionId, taskRunId, "cursor", "2");
        checkpointStateStore.set(first);
        checkpointStateStore.set(second);
        checkpointStateStore.set(new Checkpoint(tenant, executionId, IdUtils.create(), "offset", "3"));

        // When
        List<Checkpoint> result = grpcWorkerCheckpointStore.list(tenant, executionId, taskRunId);
        List<Checkpoint> otherTenant = grpcWorkerCheckpointStore.list(TestsUtils.randomTenant(), executionId, taskRunId);

        // Then
        assertThat(result).containsExactlyInAnyOrder(first, second);
        assertThat(otherTenant).isEmpty();
    }
}
