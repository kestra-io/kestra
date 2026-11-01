package io.kestra.plugin.core.flow;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.plugins.Jackson3PluginModule;
import io.kestra.core.serializers.JacksonMapper;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyPauseTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveScopedHandlersWhenRoundTrippingPause(boolean jackson3) throws Exception {
        String source = """
            {
              "id": "legacy-pause",
              "namespace": "io.kestra.tests",
              "tasks": [{
                "id": "pause",
                "type": "io.kestra.plugin.core.flow.Pause",
                "errors": [{"id": "error", "type": "io.kestra.plugin.core.log.Log", "message": "Failed"}],
                "finally": [{"id": "cleanup", "type": "io.kestra.plugin.core.log.Log", "message": "Finished"}]
              }]
            }
            """;
        JsonMapper httpMapper = JsonMapper.builder().addModule(new Jackson3PluginModule()).build();
        Flow flow = jackson3 ? httpMapper.readValue(source, Flow.class) : JacksonMapper.ofJson().readValue(source, Flow.class);
        String serialized = jackson3 ? httpMapper.writeValueAsString(flow) : JacksonMapper.ofJson().writeValueAsString(flow);
        Flow restored = jackson3 ? httpMapper.readValue(serialized, Flow.class) : JacksonMapper.ofJson().readValue(serialized, Flow.class);

        assertThat(restored.getTasks().getFirst()).isInstanceOf(LegacyPause.class).isInstanceOf(FlowableTask.class);
        assertThat(restored.getTasks().getFirst().getType()).isEqualTo(Pause.class.getName());
        assertThat(restored.allTasksWithChilds()).extracting(Task::getId).containsExactly("pause", "error", "cleanup");
    }
}
