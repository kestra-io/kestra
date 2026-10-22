package io.kestra.core.repositories;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.checkpoint.Checkpoint;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest(transactional = false)
public abstract class AbstractCheckpointRepositoryTest {
    @Inject
    protected CheckpointRepositoryInterface checkpointRepository;

    @Test
    void shouldFindSavedCheckpoint() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Checkpoint checkpoint = new Checkpoint(tenant, IdUtils.create(), IdUtils.create(), "offset", "42");

        checkpointRepository.save(checkpoint);

        Optional<Checkpoint> found = checkpointRepository.findById(tenant, checkpoint.executionId(), checkpoint.taskRunId(), "offset");
        assertThat(found).contains(checkpoint);
    }

    @Test
    void shouldOverwriteValueWhenSavingSameKey() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        String taskRunId = IdUtils.create();

        checkpointRepository.save(new Checkpoint(tenant, executionId, taskRunId, "offset", "1"));
        checkpointRepository.save(new Checkpoint(tenant, executionId, taskRunId, "offset", "2"));

        assertThat(checkpointRepository.findById(tenant, executionId, taskRunId, "offset"))
            .get()
            .extracting(Checkpoint::value)
            .isEqualTo("2");
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Checkpoint checkpoint = new Checkpoint(tenant, IdUtils.create(), IdUtils.create(), "offset", "42");
        checkpointRepository.save(checkpoint);

        assertThat(checkpointRepository.findById(tenant, checkpoint.executionId(), checkpoint.taskRunId(), "other")).isEmpty();
        assertThat(checkpointRepository.findById(TestsUtils.randomTenant("other"), checkpoint.executionId(), checkpoint.taskRunId(), "offset")).isEmpty();
    }

    @Test
    void shouldListOnlyCheckpointsOfGivenTaskRun() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        String taskRunId = IdUtils.create();
        Checkpoint first = new Checkpoint(tenant, executionId, taskRunId, "offset", "1");
        Checkpoint second = new Checkpoint(tenant, executionId, taskRunId, "cursor", "2");
        Checkpoint otherTaskRun = new Checkpoint(tenant, executionId, IdUtils.create(), "offset", "3");
        List.of(first, second, otherTaskRun).forEach(checkpointRepository::save);

        assertThat(checkpointRepository.findByTaskRun(tenant, executionId, taskRunId)).containsExactlyInAnyOrder(first, second);
        assertThat(checkpointRepository.findByTaskRun(TestsUtils.randomTenant("other"), executionId, taskRunId)).isEmpty();
    }

    @Test
    void shouldPurgeOnlyCheckpointsOfGivenExecutions() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Checkpoint purged = new Checkpoint(tenant, IdUtils.create(), IdUtils.create(), "offset", "1");
        Checkpoint purgedOtherKey = new Checkpoint(tenant, purged.executionId(), purged.taskRunId(), "cursor", "2");
        Checkpoint kept = new Checkpoint(tenant, IdUtils.create(), IdUtils.create(), "offset", "3");
        List.of(purged, purgedOtherKey, kept).forEach(checkpointRepository::save);

        int count = checkpointRepository.purge(List.of(Execution.builder().id(purged.executionId()).tenantId(tenant).build()));

        assertThat(count).isEqualTo(2);
        assertThat(checkpointRepository.findById(tenant, purged.executionId(), purged.taskRunId(), "offset")).isEmpty();
        assertThat(checkpointRepository.findById(tenant, kept.executionId(), kept.taskRunId(), "offset")).contains(kept);
    }
}
