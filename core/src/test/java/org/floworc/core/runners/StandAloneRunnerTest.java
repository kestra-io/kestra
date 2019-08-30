package org.floworc.core.runners;

import org.junit.jupiter.api.Test;
import org.floworc.core.Utils;
import org.floworc.core.executions.Execution;
import org.floworc.core.flows.Flow;
import org.floworc.core.runners.types.StandAloneRunner;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class StandAloneRunnerTest {
    private final StandAloneRunner runner = new StandAloneRunner(
        new File(Objects.requireNonNull(Utils.class.getClassLoader().getResource("flows")).toURI())
    );

    StandAloneRunnerTest() throws URISyntaxException {

    }

    @Test
    void run() throws IOException, InterruptedException {
        Flow flow = Utils.parse("flows/full.yaml");
        Execution execution = runner.run(flow);

        assertThat(execution.getTaskRunList(), hasSize(13));
    }

}