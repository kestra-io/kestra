package io.kestra.webserver.models.api;

import io.kestra.core.models.executions.LoopRun;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiLoopRunTest {

    @Test
    void ofNullLoopRun() {
        assertThat(ApiLoopRun.of(null)).isNull();
    }

    @Test
    void ofMapsFields() {
        var loopRun = new LoopRun(
            null,
            "outer",
            "tr1",
            0,
            null,
            "EMEA",
            List.of(new LoopRun.Parent(1, null, "Q1"))
        );

        var api = ApiLoopRun.of(loopRun);

        assertThat(api.taskId()).isEqualTo("outer");
        assertThat(api.parents().getFirst().value()).isEqualTo("Q1");
    }
}
