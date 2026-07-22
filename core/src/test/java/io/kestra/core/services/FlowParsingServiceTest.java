package io.kestra.core.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.core.log.Log;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class FlowParsingServiceTest {
    @Inject
    private FlowParsingService flowParsingService;

    @Test
    void shouldNotInjectAnythingGivenFlowMap() throws FlowProcessingException {
        // Given
        Map<String, Object> task = new HashMap<>();
        task.put("id", "log");
        task.put("type", Log.class.getName());
        task.put("message", "hello");

        Map<String, Object> flow = new HashMap<>();
        flow.put("id", "test");
        flow.put("namespace", "io.kestra.unittest");
        flow.put("tasks", new ArrayList<>(List.of(task)));

        // When
        Map<String, Object> injected = flowParsingService.injectPluginVersions(null, "io.kestra.unittest", flow);

        // Then nothing is injected: plugin versioning is EE-only, the open-source implementation is a no-op
        assertThat(injected).isSameAs(flow);
        assertThat(task).doesNotContainKey("version");
    }

    @Test
    void shouldIgnorePluginDefaultsWhenParsingLeniently() throws FlowProcessingException {
        // Given a legacy flow source using the removed 'pluginDefaults' keyword
        String source = """
            id: test
            namespace: io.kestra.unittest
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: explicit
            pluginDefaults:
              - type: io.kestra.plugin.core.log.Log
                values:
                  level: WARN
            """;

        // When parsed leniently (read path), the flow still parses but nothing is injected
        FlowWithSource parsed = flowParsingService.parse(null, source, false);

        // Then the task keeps its own default level: the WARN default value is not injected
        Log task = (Log) parsed.getTasks().getFirst();
        assertThat(task.getLevel()).isEqualTo(Property.ofValue(Level.INFO));
        assertThat(parsed.getSource()).isEqualTo(source);
    }
}
