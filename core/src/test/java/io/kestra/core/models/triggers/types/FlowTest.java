package io.kestra.core.models.triggers.types;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.utils.IdUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

@MicronautTest
class FlowTest {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void success() {
        var flow = io.kestra.core.models.flows.Flow.builder()
            .id("flow-with-flow-trigger")
            .namespace("io.kestra.unittest")
            .revision(1)
            .labels(
                List.of(
                    new Label("flow-label-1", "flow-label-1"),
                    new Label("flow-label-2", "flow-label-2")
                )
            )
            .tasks(Collections.singletonList(Return.builder()
                .id("test")
                .type(Return.class.getName())
                .format("test")
                .build()))
            .build();
        var execution = Execution.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .flowId("flow-with-flow-trigger")
            .flowRevision(1)
            .state(State.of(State.Type.RUNNING, Collections.emptyList()))
            .build();
        var flowTrigger = Flow.builder()
            .id("flow")
            .type(Flow.class.getName())
            .build();

        Optional<Execution> evaluate = flowTrigger.evaluate(
            runContextFactory.of(),
            flow,
            execution
        );

        assertThat(evaluate.isPresent(), is(true));
        assertThat(evaluate.get().getFlowId(), is("flow-with-flow-trigger"));
        assertThat(evaluate.get().getLabels(), hasItem(new Label("flow-label-1", "flow-label-1")));
        assertThat(evaluate.get().getLabels(), hasItem(new Label("flow-label-2", "flow-label-2")));
    }
}
