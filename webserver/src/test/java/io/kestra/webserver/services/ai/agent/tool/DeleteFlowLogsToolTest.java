package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogDataStoreInterface;

import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(environments = "memory")
class DeleteFlowLogsToolTest {
    private static final String TENANT = "deleteflowlogs";
    private static final String NAMESPACE = "io.kestra.test.ai";

    @Inject
    private DeleteFlowLogsTool tool;

    @Inject
    private LogDataStoreInterface logRepository;

    @Test
    void shouldDeleteOnlyTheTriggerLogsOfTheFlowWhenTriggerIdGiven() {
        // Given — logs of two triggers of the flow, of a same-named flow in another namespace, and of another flow
        logRepository.save(logEntry(NAMESPACE, "flow-1", "schedule", "flow-1 schedule"));
        logRepository.save(logEntry(NAMESPACE, "flow-1", "webhook", "flow-1 webhook"));
        logRepository.save(logEntry(NAMESPACE + "x", "flow-1", "schedule", "sibling namespace"));
        logRepository.save(logEntry(NAMESPACE, "flow-2", "schedule", "flow-2 schedule"));

        // When
        var result = ToolCalls.call(tool, "delete_flow_logs", """
            { "namespace": "%s", "flowId": "flow-1", "triggerId": "schedule" }""".formatted(NAMESPACE), TENANT);

        // Then
        assertThat(result).containsEntry("namespace", NAMESPACE).containsEntry("flowId", "flow-1");
        assertThat(messages()).containsExactlyInAnyOrder("flow-1 webhook", "sibling namespace", "flow-2 schedule");
    }

    private List<String> messages() {
        return logRepository.find(Pageable.from(1, 50), TENANT, List.of()).getContent().stream().map(LogEntry::getMessage).toList();
    }

    private static LogEntry logEntry(final String namespace, final String flowId, final String triggerId, final String message) {
        return LogEntry.builder()
            .tenantId(TENANT)
            .namespace(namespace)
            .flowId(flowId)
            .triggerId(triggerId)
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .level(Level.INFO)
            .message(message)
            .build();
    }
}
