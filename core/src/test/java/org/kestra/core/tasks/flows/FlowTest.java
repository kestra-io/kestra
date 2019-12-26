package org.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.runners.InputsTest;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunOutput;
import org.kestra.core.runners.RunnerUtils;
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

    @Inject
    FlowRepositoryInterface flowRepositoryInterface;

    @Test
    void run() throws Exception {
        RunContext runContext = new RunContext(
            this.applicationContext,
            ImmutableMap.of(
                "namespace", "org.kestra.tests",
                "flow", "inputs"
            )
        );

        Execution execution = runnerUtils.awaitExecution(
            flowRepositoryInterface.findById("org.kestra.tests", "inputs").get(),
            () -> {
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
            }, Duration.ofSeconds(5)
        );

        assertThat(execution.getTaskRunList(), hasSize(7));
    }
}