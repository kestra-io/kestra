package io.kestra.core.models.executions;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.kestra.core.models.assets.AssetIdentifier;
import io.kestra.core.models.assets.AssetsInOut;
import io.kestra.core.models.assets.Custom;
import io.kestra.core.models.flows.State;
import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRunTest {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    // Backward compatibility: executions serialized before `assetEmits` existed carry a single `assets`
    // object. It must fold into `assetEmits` so historical executions still deserialize.
    @Test
    void shouldFoldLegacySingleAssetsIntoAssetEmitsOnDeserialize() throws Exception {
        TaskRun base = TaskRun.builder()
            .tenantId("main").id("tr1").executionId("ex1").namespace("ns").flowId("f").taskId("t")
            .state(new State())
            .build();

        // craft a pre-change payload: a single `assets` object (not the new `assetEmits` array)
        ObjectNode node = (ObjectNode) MAPPER.valueToTree(base);
        ObjectNode legacyAssets = MAPPER.createObjectNode();
        legacyAssets.set(
            "inputs", MAPPER.createArrayNode()
                .add(MAPPER.valueToTree(new AssetIdentifier("main", "ns", "asset_a", "io.kestra.plugin.ee.assets.Table")))
        );
        // real old executions also carry polymorphic Asset outputs (e.g. Custom/Table), exercise that too
        legacyAssets.set("outputs", MAPPER.createArrayNode()
            .add(MAPPER.valueToTree(Custom.builder()
                .tenantId("main").namespace("ns").id("asset_b").type("io.kestra.plugin.ee.assets.Table").build())));
        node.set("assets", legacyAssets);

        TaskRun restored = MAPPER.treeToValue(node, TaskRun.class);

        assertThat(restored.getAssetEmits()).hasSize(1);
        assertThat(restored.getAssetEmits().getFirst().getInputs()).hasSize(1);
        assertThat(restored.getAssetEmits().getFirst().getInputs().getFirst().id()).isEqualTo("asset_a");
        assertThat(restored.getAssetEmits().getFirst().getOutputs()).hasSize(1);
        assertThat(restored.getAssetEmits().getFirst().getOutputs().getFirst().getId()).isEqualTo("asset_b");
    }

    // Modern payloads serialize as `assetEmits` and never write the legacy `assets` key.
    @Test
    void shouldSerializeAsAssetEmitsAndNotLegacyAssets() throws Exception {
        TaskRun modern = TaskRun.builder()
            .tenantId("main").id("tr1").executionId("ex1").namespace("ns").flowId("f").taskId("t")
            .state(new State())
            .assetEmits(
                List.of(
                    new AssetsInOut(
                        List.of(new AssetIdentifier("main", "ns", "asset_a", "io.kestra.plugin.ee.assets.Table")),
                        List.of()
                    )
                )
            )
            .build();

        String json = MAPPER.writeValueAsString(modern);
        assertThat(json).contains("assetEmits");
        assertThat(json).doesNotContain("\"assets\"");

        TaskRun roundTrip = MAPPER.readValue(json, TaskRun.class);
        assertThat(roundTrip.getAssetEmits()).hasSize(1);
        assertThat(roundTrip.getAssetEmits().getFirst().getInputs().getFirst().id()).isEqualTo("asset_a");
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