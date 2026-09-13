package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration check that {@code read-flow} returns a flow's source from the repository and rejects a
 * missing flow, exercising the tool → repository wiring with the real beans.
 */
@KestraTest(environments = "memory")
class ReadFlowToolTest {
    private static final String NAMESPACE = "io.kestra.test.ai";
    private static final AgentCallContext.Context CONTEXT = AgentCallContext.Context.ofTenant(MAIN_TENANT);

    @Inject
    private ReadFlowTool tool;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    void shouldExposeReadOnlyMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldReturnSourceWhenFlowExists() {
        // Given — a real flow
        String flowId = IdUtils.create();
        createFlow(flowId);

        // When
        ReadFlowTool.Result result = tool.readFlow(NAMESPACE, flowId, null, CONTEXT);

        // Then
        assertThat(result.namespace()).isEqualTo(NAMESPACE);
        assertThat(result.id()).isEqualTo(flowId);
        assertThat(result.source()).isNotBlank();
    }

    @Test
    void shouldThrowWhenFlowNotFound() {
        assertThatThrownBy(() -> tool.readFlow(NAMESPACE, "does-not-exist", null, CONTEXT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Flow not found");
    }

    private void createFlow(final String flowId) {
        flowRepository.create(
            GenericFlow.of(
                Flow.builder()
                    .id(flowId)
                    .namespace(NAMESPACE)
                    .tenantId(MAIN_TENANT)
                    .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("ok")).build()))
                    .build()
            )
        );
    }
}
