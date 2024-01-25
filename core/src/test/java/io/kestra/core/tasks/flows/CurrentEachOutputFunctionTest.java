package io.kestra.core.tasks.flows;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class CurrentEachOutputFunctionTest extends AbstractMemoryRunnerTest {
    @SuppressWarnings("unchecked")
    @Test
    void parallel() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "test-current-output", Duration.ofSeconds(30));

        var output1 = (Map<String, Object>) execution.outputs().get("1-1-1_return");
        var outputv11 = (Map<String, Object>) output1.get("v11");
        var outputv11v21 = (Map<String, Object>) outputv11.get("v21");
        assertThat(((Map<String, Object>) outputv11v21.get("v31")).get("value"), is(equalTo("return-v11-v21-v31")));
        assertThat(((Map<String, Object>) outputv11v21.get("v32")).get("value"), is(equalTo("return-v11-v21-v32")));
        var outputv11v22 = (Map<String, Object>) outputv11.get("v22");
        assertThat(((Map<String, Object>) outputv11v22.get("v31")).get("value"), is(equalTo("return-v11-v22-v31")));
        assertThat(((Map<String, Object>) outputv11v22.get("v32")).get("value"), is(equalTo("return-v11-v22-v32")));
        var outputv12 = (Map<String, Object>) output1.get("v12");
        var outputv12v21 = (Map<String, Object>) outputv12.get("v21");
        assertThat(((Map<String, Object>) outputv12v21.get("v31")).get("value"), is(equalTo("return-v12-v21-v31")));
        assertThat(((Map<String, Object>) outputv12v21.get("v32")).get("value"), is(equalTo("return-v12-v21-v32")));
        var outputv12v22 = (Map<String, Object>) outputv12.get("v22");
        assertThat(((Map<String, Object>) outputv12v22.get("v31")).get("value"), is(equalTo("return-v12-v22-v31")));
        assertThat(((Map<String, Object>) outputv12v22.get("v32")).get("value"), is(equalTo("return-v12-v22-v32")));

        var output2 = (Map<String, Object>) execution.outputs().get("2-1_return");
        assertThat(((Map<String, Object>) output2.get("v41")).get("value"), is(equalTo("return-v41")));
        assertThat(((Map<String, Object>) output2.get("v42")).get("value"), is(equalTo("return-v42")));
    }
}