package io.kestra.webserver.models.api;

import io.kestra.core.models.executions.Execution;
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
            List.of(new LoopRun.Parent("exec-1", "task-parent", 1, null, "Q1", true)),
            "root-exec",
            true
        );

        var api = ApiLoopRun.of(loopRun);

        assertThat(api.taskId()).isEqualTo("outer");
        assertThat(api.rootExecutionId()).isEqualTo("root-exec");
        assertThat(api.parents().getFirst().value()).isEqualTo("Q1");
        assertThat(api.parents().getFirst().executionId()).isEqualTo("exec-1");
        assertThat(api.parents().getFirst().taskId()).isEqualTo("task-parent");
    }

    @Test
    void ofResolvesLegacyLoopRunFromParentChain() {
        var root = Execution.builder()
            .id("root-exec")
            .build();
        var level1 = Execution.builder()
            .id("level1-exec")
            .loopRun(new LoopRun(root, "task-1", "tr-1", 0, null, "a", null))
            .build();
        var legacyLoopRun = new LoopRun(
            level1,
            "task-2",
            "tr-2",
            1,
            null,
            "b",
            List.of(new LoopRun.Parent(0, null, "a")),
            null,
            false
        );

        var api = ApiLoopRun.of(legacyLoopRun);

        assertThat(api.rootExecutionId()).isEqualTo("root-exec");
        assertThat(api.parents()).hasSize(1);
        assertThat(api.parents().getFirst().executionId()).isEqualTo("level1-exec");
        assertThat(api.parents().getFirst().taskId()).isEqualTo("task-1");
    }
}
