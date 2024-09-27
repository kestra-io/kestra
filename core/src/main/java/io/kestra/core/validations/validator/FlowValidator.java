package io.kestra.core.validations.validator;

import io.kestra.core.models.flows.Data;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.validations.FlowValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Singleton
@Introspected
public class FlowValidator implements ConstraintValidator<FlowValidation, Flow> {

    @Override
    public boolean isValid(
        @Nullable Flow value,
        @NonNull AnnotationValue<FlowValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        List<String> violations = new ArrayList<>();

        // tasks unique id
        List<String> taskIds = value.allTasksWithChilds()
            .stream()
            .map(Task::getId)
            .toList();

        List<String> duplicateIds = getDuplicates(taskIds);

        if (!duplicateIds.isEmpty()) {
            violations.add("Duplicate task id with name [" + String.join(", ", duplicateIds) + "]");
        }

        duplicateIds = getDuplicates(value.allTriggerIds());

        if (!duplicateIds.isEmpty()) {
            violations.add("Duplicate trigger id with name [" + String.join(", ", duplicateIds) + "]");
        }

        value.allTasksWithChilds()
            .forEach(
                task -> {
                    if (task instanceof ExecutableTask<?> executableTask
                        && value.getId().equals(executableTask.subflowId().flowId())
                        && value.getNamespace().equals(executableTask.subflowId().namespace())) {
                        violations.add("Recursive call to flow [" + value.getNamespace() + "." + value.getId() + "]");
                    }
                }
            );

        // input unique name
        if (value.getInputs() != null) {
            List<String> duplicates = getDuplicates(value.getInputs().stream().map(Data::getId).toList());
            if (!duplicates.isEmpty()) {
                violations.add("Duplicate input with name [" + String.join(", ", duplicates) + "]");
            }
            checkFlowInputsDependencyGraph(value, violations);
        }
        // output unique name
        if (value.getOutputs() != null) {
            List<String> duplicates = getDuplicates(value.getOutputs().stream().map(Data::getId).toList());
            if (!duplicates.isEmpty()) {
                violations.add("Duplicate output with name [" + String.join(", ", duplicates) + "]");
            }
        }

        if (!violations.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid Flow: " + String.join(", ", violations))
                .addConstraintViolation();
            return false;
        } else {
            return true;
        }
    }

    private static void checkFlowInputsDependencyGraph(final Flow flow, final List<String> violations) {
        if (ListUtils.isEmpty(flow.getInputs())) return;

        Map<String, List<String>> graph = new HashMap<>();
        for (Input<?> input : flow.getInputs()) {
            graph.putIfAbsent(input.getId(), new ArrayList<>());
            if (input.getDependsOn() != null && !ListUtils.isEmpty(input.getDependsOn().inputs())) {
                graph.get(input.getId()).addAll(input.getDependsOn().inputs());
            }
        }

        graph.forEach((key, dependencies) -> {
            if (!dependencies.isEmpty()) {
                dependencies.forEach(id -> {
                    if (graph.get(id) == null) {
                        violations.add(String.format("Input with id '%s' depends on a non-existent input '%s'.", key, id));
                    }
                });
            }
            CycleDependency.findCycle(key, graph).ifPresent(list -> {
                violations.add(String.format("Cycle dependency detected for input with id '%s': %s", key, list));
            });
        });

    }

    private static List<String> getDuplicates(List<String> taskIds) {
        return taskIds.stream()
            .distinct()
            .filter(entry -> Collections.frequency(taskIds, entry) > 1)
            .toList();
    }

    /**
     * Utility class to detect cycle in dependencies across flow's inputs.
     */
    private static final class CycleDependency {

        /**
         * Static method for finding cycles in dependencies.
         *
         * @param id        The input ID to check.
         * @param graph     The input's dependencies.
         * @return          The optional path where a cycle was found.
         */
        public static Optional<List<String>> findCycle(String id, Map<String, List<String>> graph) {
            return findCycle(id, graph, new HashSet<>(), new HashSet<>(), new ArrayList<>());
        }

        public static Optional<List<String>> findCycle(String id,
                                                       Map<String, List<String>> graph,
                                                       Set<String> visiting,
                                                       Set<String> visited,
                                                       List<String> path) {
            if (visiting.contains(id)) {
                // Cycle detected, return the current path that forms the cycle
                int cycleStartIndex = path.indexOf(id);
                return Optional.of(path.subList(cycleStartIndex, path.size()));
            }

            if (visited.contains(id)) {
                return Optional.empty();
            }

            visiting.add(id);
            path.add(id);  // Add to current path

            // Visit all the dependencies (dependsOn)
            List<String> dependencies = graph.get(id);
            if (dependencies != null) {
                for (String dependency : dependencies) {
                    Optional<List<String>> cycle = findCycle(dependency, graph, visiting, visited, path);
                    if (cycle.isPresent()) {
                        return cycle;
                    }
                }
            }

            visiting.remove(id);
            visited.add(id);
            path.removeLast();
            return Optional.empty();
        }
    }
}
