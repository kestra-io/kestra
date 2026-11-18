package io.kestra.executor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.core.utils.IdUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavior every {@link ExecutionDelayStateStore} backend must honor, extended once per backend.
 */
@MicronautTest(transactional = false)
public abstract class AbstractExecutionDelayStateStoreTest {

    @Inject
    protected ExecutionDelayStateStore executionDelayStateStore;

    @Test
    void shouldOnlyProcessExpiredDelays() {
        ExecutionDelay expired = delay(IdUtils.create(), Instant.now().minusSeconds(60));
        ExecutionDelay pending = delay(IdUtils.create(), Instant.now().plusSeconds(3600));
        executionDelayStateStore.save(expired);
        executionDelayStateStore.save(pending);

        List<String> processed = processExpired();

        assertThat(processed).contains(expired.getExecutionId()).doesNotContain(pending.getExecutionId());
        assertThat(processExpired()).as("a processed delay is consumed").doesNotContain(expired.getExecutionId());
    }

    @Test
    void shouldMoveDelayEarlierWhenSavedUnlessProcessing() {
        ExecutionDelay delay = delay(IdUtils.create(), Instant.now().plusSeconds(3600));
        executionDelayStateStore.save(delay);

        assertThat(executionDelayStateStore.saveUnlessProcessing(delay(delay.getExecutionId(), Instant.now().minusSeconds(60)))).isTrue();

        assertThat(processExpired()).contains(delay.getExecutionId());
    }

    @Test
    void shouldMoveDelayLaterWhenSavedUnlessProcessing() {
        ExecutionDelay delay = delay(IdUtils.create(), Instant.now().minusSeconds(60));
        executionDelayStateStore.save(delay);

        assertThat(executionDelayStateStore.saveUnlessProcessing(delay(delay.getExecutionId(), Instant.now().plusSeconds(3600)))).isTrue();

        assertThat(processExpired()).doesNotContain(delay.getExecutionId());
    }

    protected List<String> processExpired() {
        List<String> processed = new ArrayList<>();
        executionDelayStateStore.processExpired(Instant.now(), consumed -> processed.add(consumed.getExecutionId()));
        return processed;
    }

    protected static ExecutionDelay delay(String executionId, Instant date) {
        return ExecutionDelay.builder()
            .executionId(executionId)
            .date(date)
            .state(State.Type.CREATED)
            .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
            .build();
    }
}
