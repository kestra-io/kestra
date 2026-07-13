package io.kestra.webserver.services.ai.agent.tool;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadFlowToolTest {
    private static final String TENANT = "main";
    private static final String YAML = "id: flow-1\nnamespace: io.kestra.test\ntasks:\n  - id: hello\n    type: io.kestra.plugin.core.log.Log\n    message: hi";

    private FlowRepositoryInterface flowRepository;
    private ReadFlowTool tool;

    @BeforeEach
    void setUp() {
        flowRepository = mock(FlowRepositoryInterface.class);
        tool = new ReadFlowTool(flowRepository);
        AgentCallContext.set(AgentCallContext.Context.ofTenant(TENANT));
    }

    @AfterEach
    void tearDown() {
        AgentCallContext.clear();
    }

    private static FlowWithSource flowWithSource() {
        return FlowWithSource.of(
            Flow.builder().tenantId(TENANT).id("flow-1").namespace("io.kestra.test").build(),
            YAML
        );
    }

    @Test
    void shouldExposeReadOnlyMetadata() {
        // When / Then
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldReturnYamlSourceWhenFlowExists() {
        // Given
        when(flowRepository.findByIdWithSource(TENANT, "io.kestra.test", "flow-1", Optional.empty(), false))
            .thenReturn(Optional.of(flowWithSource()));

        // When
        String result = tool.readFlow("io.kestra.test", "flow-1", null, null);

        // Then
        assertThat(result).isEqualTo(YAML);
    }

    @Test
    void shouldForwardRevisionWhenProvided() {
        // Given
        when(flowRepository.findByIdWithSource(TENANT, "io.kestra.test", "flow-1", Optional.of(3), false))
            .thenReturn(Optional.of(flowWithSource()));

        // When
        String result = tool.readFlow("io.kestra.test", "flow-1", 3, null);

        // Then
        assertThat(result).isEqualTo(YAML);
    }

    @Test
    void shouldThrowWhenFlowNotFound() {
        // Given
        when(flowRepository.findByIdWithSource(TENANT, "io.kestra.test", "missing", Optional.empty(), false))
            .thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> tool.readFlow("io.kestra.test", "missing", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Flow not found: 'io.kestra.test.missing'");
    }

}
