package io.kestra.core.services;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.hierarchies.*;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.GraphUtils;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Singleton
@Slf4j
public class GraphService {
    @Inject
    private FlowRepositoryInterface flowRepository;
    @Inject
    private TriggerRepositoryInterface triggerRepository;
    @Inject
    private PluginDefaultService pluginDefaultService;
    @Inject
    private RunContextFactory runContextFactory;

    public FlowGraph flowGraph(FlowWithSource flow, List<String> expandedSubflows) throws IllegalVariableEvaluationException {
        return this.flowGraph(flow, expandedSubflows, null);
    }

    public FlowGraph flowGraph(FlowWithSource flow, List<String> expandedSubflows, Execution execution) throws IllegalVariableEvaluationException {
        return FlowGraph.of(this.of(flow, Optional.ofNullable(expandedSubflows).orElse(Collections.emptyList()), new HashMap<>(), execution));
    }

    public FlowGraph executionGraph(FlowWithSource flow, List<String> expandedSubflows, Execution execution) throws IllegalVariableEvaluationException {
        return FlowGraph.of(this.of(flow, Optional.ofNullable(expandedSubflows).orElse(Collections.emptyList()), new HashMap<>(), execution));
    }

    public GraphCluster of(FlowWithSource flow, List<String> expandedSubflows, Map<String, FlowWithSource> flowByUid, Execution execution) throws IllegalVariableEvaluationException {
        return this.of(null, flow, expandedSubflows, flowByUid, execution);
    }

    public GraphCluster of(GraphCluster baseGraph, FlowWithSource flow, List<String> expandedSubflows, Map<String, FlowWithSource> flowByUid) throws IllegalVariableEvaluationException {
        return this.of(baseGraph, flow, expandedSubflows, flowByUid, null);
    }

    public GraphCluster of(GraphCluster baseGraph, FlowWithSource flow, List<String> expandedSubflows, Map<String, FlowWithSource> flowByUid, Execution execution) throws IllegalVariableEvaluationException {
        String tenantId = flow.getTenantId();
        flow = pluginDefaultService.injectDefaults(flow);
        List<Trigger> triggers = null;
        if (flow.getTriggers() != null) {
            triggers = triggerRepository.find(Pageable.UNPAGED, null, tenantId, flow.getNamespace(), flow.getId(), null);
        }
        GraphCluster graphCluster = GraphUtils.of(baseGraph, flow, execution, triggers);


        Stream<Map.Entry<GraphCluster, SubflowGraphTask>> subflowToReplaceByParent = graphCluster.allNodesByParent().entrySet().stream()
            .flatMap(entry -> {
                List<SubflowGraphTask> subflowGraphTasks = entry.getValue().stream()
                    .filter(node -> node instanceof SubflowGraphTask && expandedSubflows.contains(node.getUid()))
                    .map(SubflowGraphTask.class::cast)
                    .toList();

                if (subflowGraphTasks.isEmpty()) {
                    return Stream.empty();
                }

                return subflowGraphTasks.stream().map(subflowGraphTask -> Map.entry(entry.getKey(), subflowGraphTask));
            });

        FlowWithSource finalFlow = flow;
        subflowToReplaceByParent.map(throwFunction(parentWithSubflowGraphTask -> {
                SubflowGraphTask subflowGraphTask = parentWithSubflowGraphTask.getValue();
                Task task = (Task) subflowGraphTask.getTask();
                RunContext runContext = subflowGraphTask.executableTask().subflowId().flowUid().contains("{{") && execution != null ?
                    runContextFactory.of(finalFlow, task, execution, subflowGraphTask.getTaskRun()) :
                    null;
                subflowGraphTask = subflowGraphTask.withRenderedSubflowId(runContext);
                ExecutableTask.SubflowId subflowId = subflowGraphTask.executableTask().subflowId();

                if (subflowId.flowUid().contains("{{")) {
                    throw new IllegalArgumentException("Can't expand subflow task '" + task.getId() + "' because namespace and/or flowId contains dynamic values. This can only be viewed on an execution.");
                }

                FlowWithSource subflow = flowByUid.computeIfAbsent(
                    subflowId.flowUid(),
                    uid -> {
                        Optional<FlowWithSource> flowById;
                        // Prevent the need for FLOW READ access in case we're looking at an execution graph
                        if (execution != null) {
                            flowById = flowRepository.findByIdWithSourceWithoutAcl(
                                tenantId,
                                subflowId.namespace(),
                                subflowId.flowId(),
                                subflowId.revision()
                            );
                        } else {
                            flowById = flowRepository.findByIdWithSource(
                                tenantId,
                                subflowId.namespace(),
                                subflowId.flowId(),
                                subflowId.revision()
                            );
                        }

                        return flowById.orElseThrow(() -> new NoSuchElementException(
                            "Unable to find subflow " +
                                (subflowId.revision().isEmpty() ? subflowId.flowUidWithoutRevision() : subflowId.flowUid())
                                + " for task " + task.getId()
                        ));
                    }
                );
                subflow = pluginDefaultService.injectDefaults(subflow);

                SubflowGraphTask finalSubflowGraphTask = subflowGraphTask;
                return new TaskToClusterReplacer(
                    parentWithSubflowGraphTask.getKey(),
                    subflowGraphTask,
                    this.of(
                        new SubflowGraphCluster(subflowGraphTask.getUid(), subflowGraphTask),
                        subflow,
                        expandedSubflows.stream().filter(expandedSubflow -> expandedSubflow.startsWith(finalSubflowGraphTask.getUid() + ".")).toList(),
                        flowByUid
                    )
                );
            }))
            .forEach(TaskToClusterReplacer::replace);

        return graphCluster;
    }

    private record TaskToClusterReplacer(GraphCluster parentCluster, AbstractGraph taskToReplace,
                                         GraphCluster clusterForReplacement) {
        public void replace() {
            parentCluster.addNode(clusterForReplacement, false);
            parentCluster.getGraph().edges()
                .forEach(edge -> {
                    if (edge.getSource().equals(taskToReplace)) {
                        parentCluster.addEdge(clusterForReplacement.getEnd(), edge.getTarget(), edge.getValue());
                    } else if (edge.getTarget().equals(taskToReplace)) {
                        parentCluster.addEdge(edge.getSource(), clusterForReplacement.getRoot(), edge.getValue());
                    }
                });
            parentCluster.getGraph().removeNode(taskToReplace);

            if (taskToReplace.getBranchType() != null) {
                clusterForReplacement.updateWithChildren(taskToReplace.getBranchType());
            }
        }
    }
}
