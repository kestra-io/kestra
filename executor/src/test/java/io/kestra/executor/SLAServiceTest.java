package io.kestra.executor;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.Violation;
import io.kestra.core.models.flows.sla.types.ExecutionAssertionSLA;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class SLAServiceTest {
    @Inject
    private SLAService slaService;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldReturnViolationWithDeclaredBehaviorWhenAssertionCannotBeEvaluated() {
        SLA sla = ExecutionAssertionSLA.builder()
            .id("broken")
            .type(SLA.Type.EXECUTION_ASSERTION)
            .behavior(SLA.Behavior.FAIL)
            ._assert("{{ nope.missing.thing }}")
            .build();
        Flow flow = Flow.builder()
            .tenantId("tenant")
            .namespace("io.kestra.unit-test")
            .id(IdUtils.create())
            .tasks(List.of(Log.builder().id("t").type(Log.class.getName()).message("hi").build()))
            .sla(List.of(sla))
            .build();
        Execution execution = Execution.builder()
            .tenantId("tenant")
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(1)
            .state(new State())
            .build();
        RunContext runContext = runContextFactory.of(flow, execution);

        List<Violation> violations = slaService.evaluateExecutionChangedSLA(runContext, flow, execution);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().behavior()).isEqualTo(SLA.Behavior.FAIL);
        assertThat(violations.getFirst().reason()).contains("could not be evaluated");
    }
}
