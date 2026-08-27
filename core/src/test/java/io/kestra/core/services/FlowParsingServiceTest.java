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
    void shouldParseAFlowWhoseNamespaceIsDigitsOnly() throws FlowProcessingException {
        // Given - YAML parses an unquoted digits-only namespace as an Integer
        String source = """
            id: digits
            namespace: 132312312
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;

        // When
        FlowWithSource parsed = flowParsingService.parse(null, source, false);

        // Then - a digits-only namespace matches the namespace pattern, so it must survive parsing
        assertThat(parsed.getNamespace()).isEqualTo("132312312");
    }

    @Test
    void shouldPassANullNamespaceOnWhenTheSourceDeclaresNone() throws FlowProcessingException {
        // Given - an in-progress source with no namespace, as POST /flows/graph accepts from the editor.
        // injectPluginVersions is a no-op in OSS but editions override it (EE resolves governance
        // policies from this namespace), so what gets handed over has to stay null rather than "null".
        String source = """
            id: nons
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;

        var capturing = new CapturingFlowParsingService();

        // When
        capturing.parse(null, source, false);

        // Then
        assertThat(capturing.captured).isNull();
    }

    /** Records the namespace {@link FlowParsingService#readFlowAsMap} resolves out of the source. */
    private static class CapturingFlowParsingService extends FlowParsingService {
        private String captured;

        @Override
        public Map<String, Object> injectPluginVersions(String tenantId, String namespace, Map<String, Object> mapFlow) {
            this.captured = namespace;
            return mapFlow;
        }
    }

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
