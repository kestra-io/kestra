package io.kestra.webserver.services.ai.agent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.executor.command.UpdateLabels;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class UpdateExecutionLabelsTool implements McpPlatformTool {
    private final ExecutionActionSubmitter submitter;
    private final DispatchQueueInterface<ExecutionCommand> executionCommandQueue;
    private final ModelValidator modelValidator;

    @Inject
    public UpdateExecutionLabelsTool(
        final ExecutionActionSubmitter submitter,
        final DispatchQueueInterface<ExecutionCommand> executionCommandQueue,
        final ModelValidator modelValidator) {
        this.submitter = submitter;
        this.executionCommandQueue = executionCommandQueue;
        this.modelValidator = modelValidator;
    }

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.MUTATE;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.AUTO;
    }

    @Override
    public McpToolAnnotations annotations() {
        return McpToolAnnotations.IDEMPOTENT_UPDATE;
    }

    @Tool(
        name = "update_execution_labels",
        value = "Add, update or remove labels on an ended Kestra execution; labels not named are kept. System labels (keys starting with `system.`) are managed by Kestra and cannot be changed. "
            + "Fails when the execution has not ended yet. Blocks until the executor applies the change, then returns an object { executionId, labels } where `labels` maps each key to its value after the change."
    )
    public Result updateExecutionLabels(
        @P(name = "executionId", value = "The id of the execution to label") String executionId,
        @P(name = "labels", value = "The labels to add or update, as a map of key to value", required = false) Map<String, String> labels,
        @P(name = "removeKeys", value = "The keys of the labels to remove", required = false) List<String> removeKeys,
        final AgentCallContext.Context context) {
        Map<String, String> added = MapUtils.emptyOnNull(labels);
        List<String> removed = ListUtils.emptyOnNull(removeKeys);
        if (added.isEmpty() && removed.isEmpty()) {
            throw new IllegalArgumentException("Provide at least one label to add or update, or one label key to remove.");
        }
        Stream.concat(added.keySet().stream(), removed.stream())
            .filter(Label::isSystem)
            .findFirst()
            .ifPresent(key -> {
                throw new IllegalArgumentException("System labels can only be set by Kestra itself, offending label: '%s'.".formatted(key));
            });

        Execution execution = submitter.findExecution(context.tenant(), executionId);
        if (!execution.getState().isTerminated()) {
            throw new IllegalStateException(
                "Labels can only be changed on an ended execution. Execution '%s' is in state '%s'.".formatted(executionId, execution.getState().getCurrent())
            );
        }

        Map<String, String> merged = new LinkedHashMap<>();
        ListUtils.emptyOnNull(execution.getLabels()).forEach(label -> merged.put(label.key(), label.value()));
        removed.forEach(merged::remove);
        merged.putAll(added);
        List<Label> updated = merged.entrySet().stream().map(entry -> new Label(entry.getKey(), entry.getValue())).toList();
        updated.forEach(modelValidator::validate);

        submitter.submitAndWait(
            executionId, "label",
            (operationId, actor) -> executionCommandQueue.emit(UpdateLabels.from(execution, updated).withOperationId(operationId).withActor(actor))
        );

        return new Result(executionId, merged);
    }

    /**
     * @param executionId the execution whose labels changed
     * @param labels every label of the execution after the change, system labels included
     */
    public record Result(String executionId, Map<String, String> labels) {
    }
}
