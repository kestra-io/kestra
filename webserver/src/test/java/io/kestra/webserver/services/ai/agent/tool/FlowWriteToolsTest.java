package io.kestra.webserver.services.ai.agent.tool;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(environments = "memory")
class FlowWriteToolsTest {
    private static final String NAMESPACE = "io.kestra.test.ai.write";

    @Inject
    private CreateFlowTool createFlowTool;

    @Inject
    private UpdateFlowTool updateFlowTool;

    @Inject
    private EnableFlowTool enableFlowTool;

    @Inject
    private DisableFlowTool disableFlowTool;

    @Inject
    private DeleteFlowTool deleteFlowTool;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    void shouldSaveFlowWhenCreatingFromYaml() {
        String flowId = newFlowId();

        Map<String, Object> result = create(source(flowId, "hello"));

        assertThat(result).containsEntry("namespace", NAMESPACE).containsEntry("id", flowId).containsEntry("revision", 1);
        assertThat(flowRepository.findByIdWithSource(MAIN_TENANT, NAMESPACE, flowId)).get()
            .extracting(FlowWithSource::getSource).asString().contains("hello");
    }

    @Test
    void shouldRefuseToCreateWhenFlowAlreadyExists() {
        String flowId = newFlowId();
        create(source(flowId, "hello"));

        assertThatThrownBy(() -> create(source(flowId, "overwritten")))
            .hasMessageContaining(flowId);
        assertThat(flowRepository.findByIdWithSource(MAIN_TENANT, NAMESPACE, flowId)).get()
            .extracting(FlowWithSource::getSource).asString().doesNotContain("overwritten");
    }

    @Test
    void shouldReportValidationErrorsWhenCreatingInvalidFlow() {
        String flowId = newFlowId();

        assertThatThrownBy(() -> create("""
            id: %s
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
            """.formatted(flowId, NAMESPACE)))
            .hasMessageContaining("message");
        assertThat(flowRepository.findById(MAIN_TENANT, NAMESPACE, flowId)).isEmpty();
    }

    @Test
    void shouldSaveNewRevisionOnlyWhenUpdatedSourceDiffers() {
        String flowId = newFlowId();
        create(source(flowId, "hello"));

        Map<String, Object> updated = update(source(flowId, "changed"));
        Map<String, Object> repeated = update(source(flowId, "changed"));

        assertThat(updated).containsEntry("revision", 2);
        assertThat(repeated).containsEntry("revision", 2);
        assertThat(flowRepository.findByIdWithSource(MAIN_TENANT, NAMESPACE, flowId)).get()
            .extracting(FlowWithSource::getSource).asString().contains("changed");
    }

    @Test
    void shouldRefuseToUpdateWhenFlowDoesNotExist() {
        String flowId = newFlowId();

        assertThatThrownBy(() -> update(source(flowId, "hello")))
            .hasMessageContaining("Flow not found");
        assertThat(flowRepository.findById(MAIN_TENANT, NAMESPACE, flowId)).isEmpty();
    }

    @Test
    void shouldToggleDisabledAndReportUnchangedStateWhenDisablingAndEnabling() {
        String flowId = newFlowId();
        create(source(flowId, "hello"));
        String arguments = identity(flowId);

        Map<String, Object> disabled = ToolCalls.call(disableFlowTool, "disable_flow", arguments, MAIN_TENANT);
        boolean disabledAfterDisable = flowRepository.findById(MAIN_TENANT, NAMESPACE, flowId).orElseThrow().isDisabled();
        Map<String, Object> disabledAgain = ToolCalls.call(disableFlowTool, "disable_flow", arguments, MAIN_TENANT);
        Map<String, Object> enabled = ToolCalls.call(enableFlowTool, "enable_flow", arguments, MAIN_TENANT);

        assertThat(disabled).containsEntry("changed", true);
        assertThat(disabledAfterDisable).isTrue();
        assertThat(disabledAgain).containsEntry("changed", false);
        assertThat(enabled).containsEntry("changed", true);
        assertThat(flowRepository.findById(MAIN_TENANT, NAMESPACE, flowId).orElseThrow().isDisabled()).isFalse();
    }

    @Test
    void shouldRemoveFlowWhenDeleting() {
        String flowId = newFlowId();
        create(source(flowId, "hello"));

        Map<String, Object> result = ToolCalls.call(deleteFlowTool, "delete_flow", identity(flowId), MAIN_TENANT);

        assertThat(result).containsEntry("id", flowId).containsEntry("revision", 1);
        assertThat(flowRepository.findById(MAIN_TENANT, NAMESPACE, flowId)).isEmpty();
    }

    @Test
    void shouldFailWhenDeletingMissingFlow() {
        assertThatThrownBy(() -> ToolCalls.call(deleteFlowTool, "delete_flow", identity("missing"), MAIN_TENANT))
            .hasMessageContaining("Flow not found");
    }

    private Map<String, Object> create(final String source) {
        return ToolCalls.call(createFlowTool, "create_flow", yamlArgument(source), MAIN_TENANT);
    }

    private Map<String, Object> update(final String source) {
        return ToolCalls.call(updateFlowTool, "update_flow", yamlArgument(source), MAIN_TENANT);
    }

    private static String yamlArgument(final String source) {
        return JacksonMapper.ofJson().valueToTree(Map.of("flowYaml", source)).toString();
    }

    private static String identity(final String flowId) {
        return """
            { "namespace": "%s", "flowId": "%s" }""".formatted(NAMESPACE, flowId);
    }

    private static String newFlowId() {
        return "flow" + IdUtils.create().toLowerCase();
    }

    private static String source(final String flowId, final String message) {
        return """
            id: %s
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: %s
            """.formatted(flowId, NAMESPACE, message);
    }
}
