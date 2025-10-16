package io.kestra.core.services;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class TaskIdGenerationTest {

    @Inject
    private FlowService flowService;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    void shouldGenerateIdsForTasksWithoutId() throws FlowProcessingException {
        String source = """
            id: task_id_generation_test
            namespace: io.kestra.tests
            tasks:
              - type: io.kestra.plugin.core.log.Log
                message: Task without ID
              - type: io.kestra.plugin.core.log.Log
                message: Another task without ID
              - id: manual_id
                type: io.kestra.plugin.core.log.Log
                message: Task with ID
            """;

        FlowWithSource flow = flowService.importFlow("my-tenant", source);

        assertThat(flow.getTasks()).hasSize(3);
        assertThat(flow.getTasks().get(0).getId()).isEqualTo("task1");
        assertThat(flow.getTasks().get(1).getId()).isEqualTo("task2");
        assertThat(flow.getTasks().get(2).getId()).isEqualTo("manual_id");
    }

    @Test
    void shouldGenerateIdsForTriggersWithoutId() throws FlowProcessingException {
        String source = """
            id: trigger_id_generation_test
            namespace: io.kestra.tests
            tasks:
              - id: task1
                type: io.kestra.plugin.core.log.Log
                message: Hello world
            triggers:
              - type: io.kestra.core.models.triggers.types.Schedule
                cron: "0 0 * * *"
              - type: io.kestra.core.models.triggers.types.Schedule
                cron: "0 12 * * *"
              - id: manual_trigger
                type: io.kestra.core.models.triggers.types.Schedule
                cron: "0 18 * * *"
            """;

        FlowWithSource flow = flowService.importFlow("my-tenant", source);

        assertThat(flow.getTriggers()).hasSize(3);
        assertThat(flow.getTriggers().get(0).getId()).isEqualTo("trigger1");
        assertThat(flow.getTriggers().get(1).getId()).isEqualTo("trigger2");
        assertThat(flow.getTriggers().get(2).getId()).isEqualTo("manual_trigger");
    }

    @Test
    void shouldGenerateIdsForNestedTasks() throws FlowProcessingException {
        String source = """
            id: nested_task_id_generation_test
            namespace: io.kestra.tests
            tasks:
              - type: io.kestra.plugin.core.flow.ForEach
                value: [1, 2, 3]
                tasks:
                  - type: io.kestra.plugin.core.log.Log
                    message: Nested task without ID
                  - id: nested_manual
                    type: io.kestra.plugin.core.log.Log
                    message: Nested task with ID
              - type: io.kestra.plugin.core.log.Log
                message: Root level task without ID
            """;

        FlowWithSource flow = flowService.importFlow("my-tenant", source);

        assertThat(flow.getTasks()).hasSize(2);
        assertThat(flow.getTasks().get(0).getId()).isEqualTo("task1");
        assertThat(flow.getTasks().get(1).getId()).isEqualTo("task2");
        
        Flow.TaskTree forEach = (Flow.TaskTree) flow.getTasks().get(0);
        assertThat(forEach.getTasks()).hasSize(2);
        assertThat(forEach.getTasks().get(0).getId()).isEqualTo("subtask1");
        assertThat(forEach.getTasks().get(1).getId()).isEqualTo("nested_manual");
    }
}
