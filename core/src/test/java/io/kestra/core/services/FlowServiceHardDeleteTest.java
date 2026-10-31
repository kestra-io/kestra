
package io.kestra.core.services;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.event.Level;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@Execution(ExecutionMode.SAME_THREAD)
@io.micronaut.context.annotation.Property(name = "kestra.flows.hard-delete", value = "true")
class FlowServiceHardDeleteTest {

    private static final String TEST_NAMESPACE = "io.kestra.unittest";

    @Inject
    private FlowService flowService;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private LogDataStoreInterface logRepository;

    @Test
    void shouldDeleteFlowRevisionsAndLogs() throws FlowProcessingException, QueueException {
        String tenantId = TenantService.MAIN_TENANT;
        String flowId = IdUtils.create();
        String executionId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .tenantId(tenantId)
            .namespace(TEST_NAMESPACE)
            .tasks(List.of(
                Return.builder()
                    .id("test")
                    .type(Return.class.getName())
                    .format(Property.ofValue("test"))
                    .build()
            ))
            .build();

        FlowWithSource created = flowService.create(GenericFlow.of(flow));

        Flow updatedFlow = flow.toBuilder()
            .tasks(List.of(
                Return.builder()
                    .id("test")
                    .type(Return.class.getName())
                    .format(Property.ofValue("updated"))
                    .build()
            ))
            .build();

        FlowWithSource updated = flowService.update(
            GenericFlow.of(updatedFlow),
            created
        );

        logRepository.save(LogEntry.builder()
            .tenantId(tenantId)
            .namespace(TEST_NAMESPACE)
            .flowId(flowId)
            .executionId(executionId)
            .timestamp(Instant.now())
            .level(Level.INFO)
            .message("hard-delete-test")
            .build()
        );

        assertThat(logRepository.findByExecutionIdWithoutAcl(
            tenantId,
            executionId,
            Level.TRACE
        )).hasSize(1);

        flowService.delete(updated);

        assertThat(flowRepository.findRevisions(
            tenantId,
            TEST_NAMESPACE,
            flowId,
            true
        )).isEmpty();

        assertThat(flowRepository.lastRevision(
            tenantId,
            TEST_NAMESPACE,
            flowId
        )).isNull();

        if (logRepository.canPurge()) {
            assertThat(logRepository.findByExecutionIdWithoutAcl(
                tenantId,
                executionId,
                Level.TRACE
            )).isEmpty();
        }
    }
}
