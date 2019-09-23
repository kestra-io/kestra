package org.floworc.runner.memory;

import io.micronaut.test.annotation.MicronautTest;
import org.floworc.core.Utils;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.runners.StandAloneRunner;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@MicronautTest
class MemoryRunnerTest {
    @Inject
    private StandAloneRunner runner;

    @Test
    void full() throws IOException, InterruptedException {
        Flow flow = Utils.parse("flows/full.yaml");
        Execution execution = runner.runOne(flow);

        assertThat(execution.getTaskRunList(), hasSize(13));
    }

    @Test
    void errors() throws IOException, InterruptedException {
        Flow flow = Utils.parse("flows/errors.yaml");
        Execution execution = runner.runOne(flow);

        assertThat(execution.getTaskRunList(), hasSize(7));
    }

}