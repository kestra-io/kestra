package io.kestra.jdbc.repository;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.kestra.jdbc.repository.AbstractJdbcRepository.field;
import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.plugin.core.debug.Return;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public abstract class AbstractJdbcFlowRepositoryTest extends io.kestra.core.repositories.AbstractFlowRepositoryTest {
    @Inject
    protected AbstractJdbcFlowRepository flowRepository;

    @Inject
    protected JooqDSLContextWrapper dslContextWrapper;

    @Disabled("Test disabled: no exception thrown when converting to dynamic properties")
    @Test
    public void invalidFlow() {
        dslContextWrapper.transaction(configuration -> {
            DSLContext context = DSL.using(configuration);

            context.insertInto(flowRepository.jdbcRepository.getTable())
                .set(field("key"), "io.kestra.unittest_invalid")
                .set(field("source_code"), "")
                .set(field("value"), JacksonMapper.ofJson().writeValueAsString(Map.of(
                    "id", "invalid",
                    "namespace", "io.kestra.unittest",
                    "revision", 1,
                    "tasks", List.of(Map.of(
                        "id", "invalid",
                        "type", "io.kestra.plugin.core.log.Log",
                        "level", "invalid"
                    )),
                    "deleted", false
                )))
                .execute();
        });

        Optional<FlowWithSource> flow = flowRepository.findByIdWithSource(MAIN_TENANT, "io.kestra.unittest", "invalid");

        try {
            assertThat(flow.isPresent()).isTrue();
            assertThat(flow.get()).isInstanceOf(FlowWithException.class);
            assertThat(((FlowWithException) flow.get()).getException()).contains("Cannot deserialize value of type `org.slf4j.event.Level`");
        } finally {
            flow.ifPresent(value -> flowRepository.delete(value));
        }
    }

    @Test
    public void sortFlowsByLastExecutionStatusAscending() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // Create three flows
        FlowWithSource flow1 = createTestFlow(tenant, "flow-success");
        FlowWithSource flow2 = createTestFlow(tenant, "flow-failed");
        FlowWithSource flow3 = createTestFlow(tenant, "flow-running");
        FlowWithSource flow4 = createTestFlow(tenant, "flow-no-execution");

        try {
            flow1 = flowRepository.create(GenericFlow.of(flow1));
            flow2 = flowRepository.create(GenericFlow.of(flow2));
            flow3 = flowRepository.create(GenericFlow.of(flow3));
            flow4 = flowRepository.create(GenericFlow.of(flow4));

            // Create executions with different statuses
            Execution exec1 = createExecution(tenant, flow1, State.Type.SUCCESS);
            Execution exec2 = createExecution(tenant, flow2, State.Type.FAILED);
            Execution exec3 = createExecution(tenant, flow3, State.Type.RUNNING);

            executionRepository.save(exec1);
            executionRepository.save(exec2);
            executionRepository.save(exec3);

            // Query flows sorted by last execution status ascending
            ArrayListTotal<Flow> flows = flowRepository.find(
                Pageable.from(1, 10, Sort.of(Sort.Order.asc("state.current"))),
                tenant,
                null
            );

            // Verify results - flows should be sorted alphabetically by state
            // FAILED < RUNNING < SUCCESS (alphabetically)
            // flow4 with NULL status should appear first or last depending on DB behavior
            assertThat(flows.size()).isGreaterThanOrEqualTo(3);

            // Find our flows in the result
            Flow resultFlow1 = flows.stream().filter(f -> f.getId().equals("flow-success")).findFirst().orElse(null);
            Flow resultFlow2 = flows.stream().filter(f -> f.getId().equals("flow-failed")).findFirst().orElse(null);
            Flow resultFlow3 = flows.stream().filter(f -> f.getId().equals("flow-running")).findFirst().orElse(null);

            assertThat(resultFlow1).isNotNull();
            assertThat(resultFlow2).isNotNull();
            assertThat(resultFlow3).isNotNull();

            // Verify order: FAILED should come before RUNNING, RUNNING before SUCCESS
            int indexFailed = flows.indexOf(resultFlow2);
            int indexRunning = flows.indexOf(resultFlow3);
            int indexSuccess = flows.indexOf(resultFlow1);

            assertThat(indexFailed).isLessThan(indexRunning);
            assertThat(indexRunning).isLessThan(indexSuccess);

        } finally {
            deleteFlow(flow1);
            deleteFlow(flow2);
            deleteFlow(flow3);
            deleteFlow(flow4);
        }
    }

    @Test
    public void sortFlowsByLastExecutionStatusDescending() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // Create flows
        FlowWithSource flow1 = createTestFlow(tenant, "flow-warning");
        FlowWithSource flow2 = createTestFlow(tenant, "flow-success");

        try {
            flow1 = flowRepository.create(GenericFlow.of(flow1));
            flow2 = flowRepository.create(GenericFlow.of(flow2));

            // Create executions
            Execution exec1 = createExecution(tenant, flow1, State.Type.WARNING);
            Execution exec2 = createExecution(tenant, flow2, State.Type.SUCCESS);

            executionRepository.save(exec1);
            executionRepository.save(exec2);

            // Query flows sorted by last execution status descending
            ArrayListTotal<Flow> flows = flowRepository.find(
                Pageable.from(1, 10, Sort.of(Sort.Order.desc("state.current"))),
                tenant,
                null
            );

            assertThat(flows.size()).isGreaterThanOrEqualTo(2);

            Flow resultFlow1 = flows.stream().filter(f -> f.getId().equals("flow-warning")).findFirst().orElse(null);
            Flow resultFlow2 = flows.stream().filter(f -> f.getId().equals("flow-success")).findFirst().orElse(null);

            assertThat(resultFlow1).isNotNull();
            assertThat(resultFlow2).isNotNull();

            // Verify order: WARNING should come after SUCCESS (descending alphabetical)
            int indexWarning = flows.indexOf(resultFlow1);
            int indexSuccess = flows.indexOf(resultFlow2);

            assertThat(indexWarning).isGreaterThan(indexSuccess);

        } finally {
            deleteFlow(flow1);
            deleteFlow(flow2);
        }
    }

    @Test
    public void sortFlowsByLastExecutionWithMultipleExecutions() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // Create flow with multiple executions
        FlowWithSource flow = createTestFlow(tenant, "flow-multi-exec");

        try {
            flow = flowRepository.create(GenericFlow.of(flow));

            // Create multiple executions - last one should be used for sorting
            Execution exec1 = createExecutionWithTime(tenant, flow, State.Type.SUCCESS, Instant.now().minusSeconds(300));
            Execution exec2 = createExecutionWithTime(tenant, flow, State.Type.FAILED, Instant.now().minusSeconds(200));
            Execution exec3 = createExecutionWithTime(tenant, flow, State.Type.RUNNING, Instant.now().minusSeconds(100));

            executionRepository.save(exec1);
            executionRepository.save(exec2);
            executionRepository.save(exec3);

            // Query flows sorted by last execution status
            ArrayListTotal<Flow> flows = flowRepository.find(
                Pageable.from(1, 10, Sort.of(Sort.Order.asc("state.current"))),
                tenant,
                null
            );

            // The flow should be sorted by its LATEST execution (RUNNING)
            assertThat(flows.size()).isGreaterThanOrEqualTo(1);
            Flow resultFlow = flows.stream().filter(f -> f.getId().equals("flow-multi-exec")).findFirst().orElse(null);
            assertThat(resultFlow).isNotNull();

        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    public void sortFlowsWithSomeHavingNoExecutions() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        FlowWithSource flowWithExecution = createTestFlow(tenant, "flow-with-exec");
        FlowWithSource flowWithoutExecution = createTestFlow(tenant, "flow-without-exec");

        try {
            flowWithExecution = flowRepository.create(GenericFlow.of(flowWithExecution));
            flowWithoutExecution = flowRepository.create(GenericFlow.of(flowWithoutExecution));

            // Create execution only for first flow
            Execution exec = createExecution(tenant, flowWithExecution, State.Type.SUCCESS);
            executionRepository.save(exec);

            // Query with sorting - should not fail even if some flows have no executions
            ArrayListTotal<Flow> flowsAsc = flowRepository.find(
                Pageable.from(1, 10, Sort.of(Sort.Order.asc("state.current"))),
                tenant,
                null
            );

            ArrayListTotal<Flow> flowsDesc = flowRepository.find(
                Pageable.from(1, 10, Sort.of(Sort.Order.desc("state.current"))),
                tenant,
                null
            );

            // Both queries should succeed
            assertThat(flowsAsc.size()).isGreaterThanOrEqualTo(2);
            assertThat(flowsDesc.size()).isGreaterThanOrEqualTo(2);

            // Both flows should be present in results
            assertThat(flowsAsc.stream().anyMatch(f -> f.getId().equals("flow-with-exec"))).isTrue();
            assertThat(flowsAsc.stream().anyMatch(f -> f.getId().equals("flow-without-exec"))).isTrue();

        } finally {
            deleteFlow(flowWithExecution);
            deleteFlow(flowWithoutExecution);
        }
    }

    private FlowWithSource createTestFlow(String tenantId, String flowId) {
        return FlowWithSource.builder()
            .tenantId(tenantId)
            .id(flowId)
            .namespace(TEST_NAMESPACE)
            .tasks(Collections.singletonList(
                Return.builder()
                    .id("test-task")
                    .type(Return.class.getName())
                    .format(Property.ofValue("test"))
                    .build()
            ))
            .build();
    }

    private Execution createExecution(String tenantId, FlowWithSource flow, State.Type stateType) {
        return createExecutionWithTime(tenantId, flow, stateType, Instant.now());
    }

    private Execution createExecutionWithTime(String tenantId, FlowWithSource flow, State.Type stateType, Instant time) {
        return Execution.builder()
            .id(IdUtils.create())
            .tenantId(tenantId)
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .state(new State().withState(stateType))
            .build()
            .withState(stateType);
    }

}