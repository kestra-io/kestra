package io.kestra.core.models.flows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.kestra.core.models.Label;
import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class FlowWithExceptionTest {
    @Test
    void shouldPreserveLabelsWhenLoadingFails() throws Exception {
        JsonNode jsonNode = JacksonMapper.ofJson().readTree("""
            {
                "id": "failed-flow",
                "namespace": "io.kestra.unittest",
                "revision": 2,
                "disabled": true,
                "labels": [
                    {"key": "system.readOnly", "value": "true"},
                    {"key": "team", "value": "platform"}
                ],
                "source": "id: failed-flow\\nnamespace: io.kestra.unittest"
            }
            """);

        var flow = FlowWithException.from(jsonNode, new IllegalStateException("Invalid type: io.kestra.plugin.core.flow.ForEach"));

        assertThat(flow).isPresent();
        assertThat(flow.get().getLabels()).containsExactlyInAnyOrder(
            new Label("system.readOnly", "true"),
            new Label("team", "platform")
        );
        assertThat(flow.get().getException()).contains("Invalid type");
        assertThat(flow.get().getSource()).contains("failed-flow");
        assertThat(flow.get().getTasks()).isEmpty();
    }

    @Test
    void shouldPreserveMapFormLabelsWhenLoadingFails() throws Exception {
        JsonNode jsonNode = JacksonMapper.ofJson().readTree("""
            {
                "id": "failed-flow",
                "namespace": "io.kestra.unittest",
                "labels": {"system.readOnly": "true"}
            }
            """);

        var flow = FlowWithException.from(jsonNode, new IllegalStateException("boom"));

        assertThat(flow).isPresent();
        assertThat(flow.get().getLabels()).containsExactly(new Label("system.readOnly", "true"));
    }

    @Test
    void shouldKeepFallbackWhenLabelsAreMalformed() throws Exception {
        JsonNode jsonNode = JacksonMapper.ofJson().readTree("""
            {
                "id": "failed-flow",
                "namespace": "io.kestra.unittest",
                "labels": "not-a-list-or-map"
            }
            """);

        var flow = FlowWithException.from(jsonNode, new IllegalStateException("boom"));

        assertThat(flow).isPresent();
        assertThat(flow.get().getLabels()).isNull();
        assertThat(flow.get().getException()).isEqualTo("boom");
    }

    @Test
    void shouldKeepFallbackWhenLabelsContainNullEntries() throws Exception {
        JsonNode jsonNode = JacksonMapper.ofJson().readTree("""
            {
                "id": "failed-flow",
                "namespace": "io.kestra.unittest",
                "labels": [null]
            }
            """);

        var flow = FlowWithException.from(jsonNode, new IllegalStateException("boom"));

        assertThat(flow).isPresent();
        assertThat(flow.get().getLabels()).isNull();
        assertThat(flow.get().getException()).isEqualTo("boom");
    }

    @Test
    void shouldReturnEmptyWhenIdOrNamespaceIsMissing() throws Exception {
        JsonNode jsonNode = JacksonMapper.ofJson().readTree("""
            {"labels": [{"key": "system.readOnly", "value": "true"}]}
            """);

        assertThat(FlowWithException.from(jsonNode, new IllegalStateException("boom"))).isEmpty();
    }
}
