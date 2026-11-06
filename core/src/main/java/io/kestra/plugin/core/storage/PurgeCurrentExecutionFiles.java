package io.kestra.plugin.core.storage;

import java.net.URI;
import java.util.*;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.SystemTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.ExecutableUtils;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Purge files created by the current Execution.",
    description = """
        Deletes all internal-storage files produced by this Execution (inputs, outputs, triggers). No-op if nothing was generated."""
)
@Plugin(
    examples = {
        @Example(
            title = "Purge all files created by this execution.",
            full = true,
            code = """
                    id: purge_execution_files
                    namespace: company.team

                    tasks:
                      - id: download
                        type: io.kestra.plugin.core.http.Download
                        uri: https://huggingface.co/datasets/kestra/datasets/raw/main/json/user_events.json

                      - id: purge
                        type: io.kestra.plugin.core.storage.PurgeCurrentExecutionFiles
                """
        ),
        @Example(
            title = "Purge all files created by this execution and its child executions.",
            full = true,
            code = """
                    id: purge_execution_files
                    namespace: company.team

                    tasks:
                      - id: subflow
                        type: io.kestra.plugin.core.flow.Subflow
                        namespace: company.team
                        flowId: my_subflow

                      - id: purge
                        type: io.kestra.plugin.core.storage.PurgeCurrentExecutionFiles
                        includeChildExecutions: true
                """
        )
    }
)
public class PurgeCurrentExecutionFiles extends Task implements RunnableTask<PurgeCurrentExecutionFiles.Output>, SystemTask {

    @Schema(
        title = "Whether to purge files from child executions.",
        description = "If true, also deletes internal storage files created by all child (subflow) executions of this execution recursively."
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Boolean> includeChildExecutions = Property.ofValue(false);

    @Override
    public PurgeCurrentExecutionFiles.Output run(RunContext runContext) throws Exception {
        List<URI> deletedUris = new ArrayList<>(runContext.storage().deleteExecutionFiles());

        boolean purgeChildren = runContext.render(this.includeChildExecutions).as(Boolean.class).orElse(false);
        if (purgeChildren) {
            String currentExecutionId = Optional.ofNullable(runContext.taskRunInfo())
                .map(RunContext.TaskRunInfo::executionId)
                .orElseGet(() -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> executionMap = (Map<String, Object>) runContext.getVariables().get("execution");
                    return executionMap != null ? (String) executionMap.get("id") : null;
                });

            if (currentExecutionId != null) {
                String tenantId = Optional.ofNullable(runContext.flowInfo())
                    .map(RunContext.FlowInfo::tenantId)
                    .orElse(MAIN_TENANT);
                String currentNamespace = Optional.ofNullable(runContext.flowInfo())
                    .map(RunContext.FlowInfo::namespace)
                    .orElse(null);

                ExecutionRepositoryInterface executionRepository = ((DefaultRunContext) runContext).services().additionalService(ExecutionRepositoryInterface.class);

                Set<String> visited = new HashSet<>();
                visited.add(currentExecutionId);
                Queue<String> queue = new ArrayDeque<>();
                queue.add(currentExecutionId);

                while (!queue.isEmpty()) {
                    String parentExecutionId = queue.poll();
                    List<Execution> children = executionRepository.findAllByTriggerExecutionId(tenantId, parentExecutionId)
                        .filter(ExecutableUtils::isSubflow)
                        .collectList()
                        .block();

                    if (children != null) {
                        for (Execution child : children) {
                            if (visited.add(child.getId())) {
                                if (child.getNamespace() != null && !child.getNamespace().equals(currentNamespace)) {
                                    runContext.acl().allowNamespace(child.getNamespace()).check();
                                }
                                List<URI> childUris = runContext.storage().deleteExecutionFiles(child);
                                if (childUris != null) {
                                    deletedUris.addAll(childUris);
                                }
                                queue.add(child.getId());
                            }
                        }
                    }
                }
            }
        }

        return Output.builder()
            .uris(new ArrayList<>(new LinkedHashSet<>(deletedUris)))
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The deleted file URIs from Kestra's internal storage"
        )
        private final List<URI> uris;
    }
}
