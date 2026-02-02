package io.kestra.core.validations.validator;

import io.kestra.core.models.flows.Data;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.NamespaceService;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.validations.FlowValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.kestra.core.models.Label.READ_ONLY;
import static io.kestra.core.models.Label.SYSTEM_PREFIX;

@Singleton
@Introspected
public class FlowValidator implements ConstraintValidator<FlowValidation, Flow> {
    public static List<String> RESERVED_FLOW_IDS = List.of(
        "pause",
        "resume",
        "force-run",
        "change-status",
        "kill",
        "executions",
        "search",
        "source",
        "disable",
        "enable"
    );

    @Inject
    private FlowService flowService;

    @Inject
    private NamespaceService namespaceService;

    @Override
    public boolean isValid(
        @Nullable Flow value,
        @NonNull AnnotationValue<FlowValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        List<String> violations = new ArrayList<>();

        if (value.getId() != null && RESERVED_FLOW_IDS.contains(value.getId())) {
            violations.add("Flow id is a reserved keyword: " + value.getId() + ". List of reserved keywords: " + String.join(", ", RESERVED_FLOW_IDS));
        }

        if (namespaceService.requireExistingNamespace(value.getTenantId(), value.getNamespace())) {
            violations.add("Namespace '" + value.getNamespace() + "' does not exist but is required to exist before a flow can be created in it.");
        }

        List<Task> allTasks = value.allTasksWithChilds();

        // tasks unique id
        List<String> taskIds = allTasks.stream()
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

        allTasks.stream()
            .filter(task -> task instanceof ExecutableTask<?> executableTask
                && value.getId().equals(executableTask.subflowId().flowId())
                && value.getNamespace().equals(executableTask.subflowId().namespace()))
            .forEach(task -> violations.add("Recursive call to flow [" + value.getNamespace() + "." + value.getId() + "]"));

        // input unique name
        duplicateIds = getDuplicates(ListUtils.emptyOnNull(value.getInputs()).stream().map(Data::getId).toList());
        if (!duplicateIds.isEmpty()) {
            violations.add("Duplicate input with name [" + String.join(", ", duplicateIds) + "]");
        }
        checkFlowInputsDependencyGraph(value, violations);

        // output unique name
        duplicateIds = getDuplicates(ListUtils.emptyOnNull(value.getOutputs()).stream().map(Data::getId).toList());
        if (!duplicateIds.isEmpty()) {
            violations.add("Duplicate output with name [" + String.join(", ", duplicateIds) + "]");
        }

        // preconditions unique id
        duplicateIds = getDuplicates(ListUtils.emptyOnNull(value.getTriggers()).stream()
            .filter(it -> it instanceof io.kestra.plugin.core.trigger.Flow)
            .map(it -> (io.kestra.plugin.core.trigger.Flow) it)
            .filter(it -> it.getPreconditions() != null && it.getPreconditions().getId() != null)
            .map(it -> it.getPreconditions().getId())
            .toList());
        if (!duplicateIds.isEmpty()) {
            violations.add("Duplicate preconditions with id [" + String.join(", ", duplicateIds) + "]");
        }

        // system labels
        ListUtils.emptyOnNull(value.getLabels()).stream()
            .filter(label -> label.key() != null && label.key().startsWith(SYSTEM_PREFIX) && !label.key().equals(READ_ONLY))
            .forEach(label -> violations.add("System labels can only be set by Kestra itself, offending label: " + label.key() + "=" + label.value()));

        List<Pattern> inputsWithMinusPatterns = ListUtils.emptyOnNull(value.getInputs())
            .stream()
            .filter(input -> input.getId().contains("-"))
            .map(input -> Pattern.compile("\\{\\{\\s*inputs." + input.getId() + "\\s*\\}\\}"))
            .collect(Collectors.toList());

        List<String> invalidTasks = allTasks.stream()
            .filter(task -> checkObjectFieldsWithPatterns(task, inputsWithMinusPatterns))
            .map(task -> task.getId())
            .collect(Collectors.toList());

        if (!invalidTasks.isEmpty()) {
            violations.add("Invalid input reference: use inputs[key-name] instead of inputs.key-name — keys with dashes require bracket notation, offending tasks:" +
                " [" + String.join(", ", invalidTasks) + "]");
        }

        List<Pattern> outputsWithMinusPattern = allTasks.stream()
            .filter(output -> Optional.ofNullable(output.getId()).orElse("").contains("-"))
            .map(output -> Pattern.compile("\\{\\{\\s*outputs\\." + output.getId() + "\\.[^}]+\\s*\\}\\}"))
            .collect(Collectors.toList());

        invalidTasks = allTasks.stream()
            .filter(task -> checkObjectFieldsWithPatterns(task, outputsWithMinusPattern))
            .map(task -> task.getId())
            .collect(Collectors.toList());

        violations.addAll(assetsViolations(allTasks));

        if (!invalidTasks.isEmpty()) {
            violations.add("Invalid output reference: use outputs[key-name] instead of outputs.key-name — keys with dashes require bracket notation, offending tasks:" +
                " [" + String.join(", ", invalidTasks) + "]");
        }

        List<String> invalidOutputs = ListUtils.emptyOnNull(value.getOutputs())
            .stream()
            .filter(task -> checkObjectFieldsWithPatterns(task, outputsWithMinusPattern))
            .map(task -> task.getId())
            .collect(Collectors.toList());

        if (!invalidOutputs.isEmpty()) {
            violations.add("Invalid output reference: use outputs[key-name] instead of outputs.key-name — keys with dashes require bracket notation, offending outputs:" +
                " [" + String.join(", ", invalidOutputs) + "]");
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

    protected List<String> assetsViolations(List<Task> allTasks) {
        return allTasks.stream().filter(task -> task.getAssets() != null)
            .map(taskWithAssets -> "Task '" + taskWithAssets.getId() + "' can't have any `assets` because assets are only available in Enterprise Edition.")
            .toList();
    }

    private static boolean checkObjectFieldsWithPatterns(Object object, List<Pattern> patterns) {
        if (object == null) {
            return true;

        }
        List<Field> fields = Arrays.asList(object.getClass().getDeclaredFields());

        return fields.stream()
            .anyMatch(field -> patterns.stream()
                .anyMatch(inputPattern -> {
                    field.setAccessible(true);
                    try {
                        Optional<?> value=Optional.ofNullable(field.get(object));

                        return value.filter(o -> inputPattern.matcher(o.toString()).find()).isPresent();

                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }));
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
         * @param id    The input ID to check.
         * @param graph The input's dependencies.
         * @return The optional path where a cycle was found.
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
