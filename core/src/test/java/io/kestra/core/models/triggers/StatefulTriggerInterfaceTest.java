package io.kestra.core.models.triggers;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class StatefulTriggerInterfaceTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StatefulTriggerService statefulTriggerService;

    @Test
    void shouldPersistAndReadState() throws Exception {
        var flow = Flow.builder()
            .namespace("io.kestra.unittest")
            .id("test-flow")
            .revision(1)
            .build();

        var runContext = runContextFactory.of(flow, Map.of(
            "flow", Map.of(
                "tenantId", "main",
                "namespace", "io.kestra.unittest",
                "id", "test-flow",
                "revision", 1
            )
        ));

        var key = statefulTriggerService.defaultKey("ns", "test-flow", "trigger-persist");
        var ttl = Optional.of(Duration.ofMinutes(5));
        var state = new HashMap<String, StatefulTriggerService.Entry>();

        var candidate = StatefulTriggerService.Entry.candidate("gs://bucket/file1.csv", "v1", Instant.now());
        var result = statefulTriggerService.computeAndUpdateState(state, candidate, StatefulTriggerInterface.On.CREATE_OR_UPDATE);

        assertThat(result.fire(), is(true));
        assertThat(result.isNew(), is(true));

        statefulTriggerService.writeState(runContext, key, state, ttl);
        var reloaded = statefulTriggerService.readState(runContext, key, ttl);

        assertThat(reloaded, hasKey("gs://bucket/file1.csv"));
        assertThat(reloaded.get("gs://bucket/file1.csv").version(), is("v1"));

        var result2 = statefulTriggerService.computeAndUpdateState(reloaded, candidate, StatefulTriggerInterface.On.CREATE_OR_UPDATE);
        assertThat(result2.fire(), is(false));
    }

    @Test
    void shouldExpireOldEntriesAfterTTL() throws Exception {
        var flow = Flow.builder()
            .namespace("io.kestra.unittest")
            .id("test-flow")
            .revision(1)
            .build();

        var runContext = runContextFactory.of(flow, Map.of(
            "flow", Map.of(
                "tenantId", "main",
                "namespace", "io.kestra.unittest",
                "id", "test-flow",
                "revision", 1
            )
        ));

        var key = statefulTriggerService.defaultKey("ns", "test-flow", "trigger-ttl");
        var ttl = Optional.of(Duration.ofMinutes(5));
        var now = Instant.now();

        var state = new HashMap<String, StatefulTriggerService.Entry>();
        state.put("gs://bucket/old.csv", new StatefulTriggerService.Entry("gs://bucket/old.csv", "v1", now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(2))));
        state.put("gs://bucket/new.csv", new StatefulTriggerService.Entry("gs://bucket/new.csv", "v1", now, now));

        statefulTriggerService.writeState(runContext, key, state, ttl);
        var reloaded = statefulTriggerService.readState(runContext, key, ttl);

        assertThat(reloaded, allOf(hasKey("gs://bucket/new.csv"), not(hasKey("gs://bucket/old.csv"))));
    }
}
