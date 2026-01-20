package io.kestra.core.models.triggers;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.RunContextFactory;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.kestra.core.models.triggers.StatefulTriggerService.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class StatefulTriggerInterfaceTest {
    @Inject
    RunContextFactory runContextFactory;
    
    @Test
    void shouldPersistAndReadState() {
        var flow = Flow.builder()
            .tenantId("main")
            .namespace("io.kestra.unittest")
            .id("test-flow")
            .revision(1)
            .build();

        var runContext = runContextFactory.of(flow, Map.of());

        var key = defaultKey("ns", "test-flow", "trigger-persist");
        var ttl = Optional.of(Duration.ofMinutes(5));
        var state = new HashMap<String, StatefulTriggerService.Entry>();

        var candidate = StatefulTriggerService.Entry.candidate("gs://bucket/file1.csv", "v1", Instant.now());
        var result = computeAndUpdateState(state, candidate, StatefulTriggerInterface.On.CREATE_OR_UPDATE);

        assertThat(result.fire(), is(true));
        assertThat(result.isNew(), is(true));

        writeState(runContext, key, state, ttl);
        var reloaded = readState(runContext, key, ttl);

        assertThat(reloaded, hasKey("gs://bucket/file1.csv"));
        assertThat(reloaded.get("gs://bucket/file1.csv").version(), is("v1"));

        var result2 = computeAndUpdateState(reloaded, candidate, StatefulTriggerInterface.On.CREATE_OR_UPDATE);
        assertThat(result2.fire(), is(false));
    }

    @Test
    void shouldExpireOldEntriesAfterTTL() {
        var flow = Flow.builder()
            .tenantId("main")
            .namespace("io.kestra.unittest")
            .id("test-flow")
            .revision(1)
            .build();

        var runContext = runContextFactory.of(flow, Map.of());

        var key = defaultKey("ns", "test-flow", "trigger-ttl");
        var ttl = Optional.of(Duration.ofMinutes(5));
        var now = Instant.now();

        var state = new HashMap<String, Entry>();
        state.put("gs://bucket/old.csv", new Entry("gs://bucket/old.csv", "v1", now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(2))));
        state.put("gs://bucket/new.csv", new Entry("gs://bucket/new.csv", "v1", now, now));

        writeState(runContext, key, state, ttl);
        var reloaded = readState(runContext, key, ttl);

        assertThat(reloaded, allOf(hasKey("gs://bucket/new.csv"), not(hasKey("gs://bucket/old.csv"))));
    }
}
