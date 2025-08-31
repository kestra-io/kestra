package io.kestra.plugin.core.flow;

import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import java.util.Map;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
public class CurrentEachOutputFunctionTest {
    @SuppressWarnings("unchecked")
    @Test
    @ExecuteFlow("flows/valids/current-output.yaml")
    void parallel(Execution execution) {
        var output1 = (Map<String, Object>) execution.outputs().get("1-1-1_return");
        var outputv11 = (Map<String, Object>) output1.get("v11");
        var outputv11v21 = (Map<String, Object>) outputv11.get("v21");
        assertThat(((Map<String, Object>) outputv11v21.get("v31")).get("value")).isEqualTo("return-v11-v21-v31");
        assertThat(((Map<String, Object>) outputv11v21.get("v32")).get("value")).isEqualTo("return-v11-v21-v32");
        var outputv11v22 = (Map<String, Object>) outputv11.get("v22");
        assertThat(((Map<String, Object>) outputv11v22.get("v31")).get("value")).isEqualTo("return-v11-v22-v31");
        assertThat(((Map<String, Object>) outputv11v22.get("v32")).get("value")).isEqualTo("return-v11-v22-v32");
        var outputv12 = (Map<String, Object>) output1.get("v12");
        var outputv12v21 = (Map<String, Object>) outputv12.get("v21");
        assertThat(((Map<String, Object>) outputv12v21.get("v31")).get("value")).isEqualTo("return-v12-v21-v31");
        assertThat(((Map<String, Object>) outputv12v21.get("v32")).get("value")).isEqualTo("return-v12-v21-v32");
        var outputv12v22 = (Map<String, Object>) outputv12.get("v22");
        assertThat(((Map<String, Object>) outputv12v22.get("v31")).get("value")).isEqualTo("return-v12-v22-v31");
        assertThat(((Map<String, Object>) outputv12v22.get("v32")).get("value")).isEqualTo("return-v12-v22-v32");

        var output2 = (Map<String, Object>) execution.outputs().get("2-1_return");
        assertThat(((Map<String, Object>) output2.get("v41")).get("value")).isEqualTo("return-v41");
        assertThat(((Map<String, Object>) output2.get("v42")).get("value")).isEqualTo("return-v42");
    }
}