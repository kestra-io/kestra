package io.kestra.scheduler.stores;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.scheduler.utils.InMemoryTriggerStateStore;

import static org.assertj.core.api.Assertions.assertThat;

class CachedTriggerStateStoreTest {

    @Test
    void shouldReturnAllStatesWhenCacheHoldsOnlySomeOfThem() {
        // GIVEN three states, of which only the first one went through the cache
        InMemoryTriggerStateStore delegate = new InMemoryTriggerStateStore();
        List<TriggerId> ids = List.of(triggerId("a"), triggerId("b"), triggerId("c"));
        ids.forEach(id -> delegate.save(TriggerState.of(id, TriggerType.SCHEDULE, null, 0)));
        CachedTriggerStateStore cachedStore = new CachedTriggerStateStore(delegate, new SchedulerConfiguration(1, Duration.ofSeconds(5), 2));
        cachedStore.findByIdWithoutAcl(ids.getFirst());

        // WHEN
        List<TriggerState> states = cachedStore.findAllForVNodes(Set.of(0));

        // THEN
        assertThat(states).extracting(TriggerState::uid).containsExactlyInAnyOrderElementsOf(ids.stream().map(TriggerId::uid).toList());
    }

    private static TriggerId triggerId(String id) {
        return TriggerId.of("tenant", "io.kestra.tests", "flow", id);
    }
}
