package io.kestra.core.tasks.test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.flow.Sequential;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Test flowable task that generates a NPE on resolveState"
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "id: sequential",
                "namespace: io.kestra.tests",
                "",
                "tasks:",
                "  - id: sequential",
                "    type: io.kestra.core.tasks.test.BadSequential",
                "    tasks:",
                "      - id: 1st",
                "        type: io.kestra.plugin.core.debug.Return",
                "        format: \"{{task.id}} > {{taskrun.startDate}}\"",
                "      - id: 2nd",
                "        type: io.kestra.plugin.core.debug.Return",
                "        format: \"{{task.id}} > {{taskrun.id}}\"",
                "  - id: last",
                "    type: io.kestra.plugin.core.debug.Return",
                "    format: \"{{task.id}} > {{taskrun.startDate}}\""
            }
        )
    }
)
public class BadSequential extends Sequential {

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        throw new RuntimeException("BAM");
    }
}
