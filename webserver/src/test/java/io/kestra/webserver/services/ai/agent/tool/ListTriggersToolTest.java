package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@KestraTest(environments = "memory", startRunner = false, startScheduler = false)
class ListTriggersToolTest {
    private static final String NAMESPACE = "io.kestra.test.ai.triggers";

    @Inject
    private ListTriggersTool tool;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private AbstractJdbcTriggerRepository triggerRepository;

    @Test
    @SuppressWarnings("unchecked")
    void shouldReportRuntimeDisabledTriggersAsDisabledWhenFilteringByNamespace() {
        String flowId = createFlow(NAMESPACE);
        String otherFlowId = createFlow("io.kestra.test.ai.other");
        saveTrigger(NAMESPACE, flowId, "enabled", false, false);
        saveTrigger(NAMESPACE, flowId, "runtime-disabled", true, false);
        saveTrigger(NAMESPACE, flowId, "source-disabled", false, true);
        saveTrigger("io.kestra.test.ai.other", otherFlowId, "elsewhere", false, false);

        Map<String, Object> result = ToolCalls.call(tool, "list_flow_triggers", """
            { "NAMESPACE": { "operator": "EQUALS", "value": "%s" } }""".formatted(NAMESPACE), MAIN_TENANT);

        assertThat((List<Map<String, Object>>) result.get("triggers"))
            .extracting(trigger -> trigger.get("triggerId"), trigger -> trigger.get("disabled"), trigger -> trigger.get("disabledInSource"))
            .containsExactlyInAnyOrder(
                tuple("enabled", false, false),
                tuple("runtime-disabled", true, false),
                tuple("source-disabled", true, true)
            );
        assertThat(((Number) result.get("total")).longValue()).isEqualTo(3);
    }

    private String createFlow(final String namespace) {
        String flowId = "flow" + IdUtils.create().toLowerCase();
        flowRepository.create(GenericFlow.fromYaml(MAIN_TENANT, """
            id: %s
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(flowId, namespace)));
        return flowId;
    }

    private void saveTrigger(final String namespace, final String flowId, final String triggerId, final boolean disabled, final boolean sourceDisabled) {
        triggerRepository.save(
            TriggerState.builder()
                .tenantId(MAIN_TENANT)
                .namespace(namespace)
                .flowId(flowId)
                .triggerId(triggerId)
                .type(TriggerType.SCHEDULE)
                .disabled(disabled)
                .sourceDisabled(sourceDisabled)
                .build()
        );
    }
}
