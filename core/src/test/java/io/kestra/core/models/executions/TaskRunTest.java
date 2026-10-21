package io.kestra.core.models.executions;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.kestra.core.models.assets.AssetIdentifier;
import io.kestra.core.models.assets.Custom;
import io.kestra.core.models.flows.State;
import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRunTest {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Test
    void shouldIgnoreAssetsStoredOnTaskRunsBeforeTheyWereEmittedAtCallTime() throws Exception {
        TaskRun base = TaskRun.builder()
            .tenantId("main").id("tr1").executionId("ex1").namespace("ns").flowId("f").taskId("t")
            .state(new State())
            .build();
        ObjectNode node = MAPPER.valueToTree(base);
        ObjectNode bundle = MAPPER.createObjectNode();
        bundle.set("inputs", MAPPER.createArrayNode().add(MAPPER.valueToTree(new AssetIdentifier("main", "ns", "asset_a", "io.kestra.plugin.ee.assets.Table"))));
        bundle.set("outputs", MAPPER.createArrayNode().add(MAPPER.valueToTree(Custom.builder().tenantId("main").namespace("ns").id("asset_b").type("io.kestra.plugin.ee.assets.Table").build())));
        node.set("assets", bundle);
        node.set("assetEmits", MAPPER.createArrayNode().add(bundle));

        TaskRun restored = MAPPER.treeToValue(node, TaskRun.class);

        assertThat(restored.getId()).isEqualTo("tr1");
        assertThat(MAPPER.writeValueAsString(restored)).doesNotContain("asset_a", "asset_b");
    }

    @Test
    void onRunningResendNoAttempts() {
        TaskRun taskRun = TaskRun.builder()
            .state(new State())
            .build()
            .onRunningResend();

        assertThat(taskRun.getAttempts().size()).isEqualTo(1);
        assertThat(taskRun.getAttempts().getFirst().getState().getHistories().getFirst()).isEqualTo(taskRun.getState().getHistories().getFirst());
        assertThat(taskRun.getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.RESUBMITTED);
    }

    @Test
    void onRunningResendRunning() {
        TaskRun taskRun = TaskRun.builder()
            .state(new State())
            .attempts(
                Collections.singletonList(
                    TaskRunAttempt.builder()
                        .state(new State().withState(State.Type.RUNNING))
                        .build()
                )
            )
            .build()
            .onRunningResend();

        assertThat(taskRun.getAttempts().size()).isEqualTo(1);
        assertThat(taskRun.getAttempts().getFirst().getState().getHistories().getFirst()).isNotEqualTo(taskRun.getState().getHistories().getFirst());
        assertThat(taskRun.getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.RESUBMITTED);
    }

    @Test
    void onRunningResendTerminated() {
        TaskRun taskRun = TaskRun.builder()
            .state(new State())
            .attempts(
                Collections.singletonList(
                    TaskRunAttempt.builder()
                        .state(new State().withState(State.Type.SUCCESS))
                        .build()
                )
            )
            .build()
            .onRunningResend();

        assertThat(taskRun.getAttempts().size()).isEqualTo(2);
        assertThat(taskRun.getAttempts().get(1).getState().getHistories().getFirst()).isNotEqualTo(taskRun.getState().getHistories().getFirst());
        assertThat(taskRun.getAttempts().get(1).getState().getCurrent()).isEqualTo(State.Type.RESUBMITTED);
    }

}