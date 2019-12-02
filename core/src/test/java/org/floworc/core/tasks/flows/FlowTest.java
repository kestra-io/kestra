package org.floworc.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import org.floworc.core.runners.AbstractMemoryRunnerTest;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.runners.InputsTest;
import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.RunOutput;
import org.floworc.core.runners.RunnerUtils;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class FlowTest extends AbstractMemoryRunnerTest {
    @Inject
    ApplicationContext applicationContext;

    @Inject
    RunnerUtils runnerUtils;

    @Test
    void run() throws Exception {
        RunContext runContext = new RunContext(
            this.applicationContext,
            ImmutableMap.of(
                "namespace", "org.floworc.tests",
                "flow", "inputs"
            )
        );

        Execution execution = runnerUtils.awaitExecution(() -> {
            Flow flow = Flow.builder()
                .namespace("{{namespace}}")
                .flowId("{{flow}}")
                .inputs(InputsTest.inputs)
                .build();

            RunOutput run = null;
            try {
                run = flow.run(runContext);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            String executionId = (String) run.getOutputs().get("executionId");
            assertThat(executionId == null, is(false));

            return executionId;
        }, Duration.ofSeconds(5));

        assertThat(execution.getTaskRunList(), hasSize(7));
    }
}