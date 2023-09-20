package io.kestra.core.validations;

import com.cronutils.model.Cron;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.tasks.flows.Dag;
import io.kestra.core.tasks.flows.Switch;
import io.kestra.core.tasks.flows.WorkingDirectory;
import io.micronaut.context.annotation.Factory;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

@Factory
public class ValidationFactory {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Singleton
    ConstraintValidator<DateFormat, String> dateTimeValidator() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true; // nulls are allowed according to spec
            }

            try {
                Date now = new Date();
                SimpleDateFormat dateFormat = new SimpleDateFormat(value);
                dateFormat.format(now);
            } catch (Exception e) {
                context.messageTemplate("invalid date format value '({validatedValue})': " + e.getMessage());

                return false;
            }
            return true;
        };
    }

    @Singleton
    ConstraintValidator<CronExpression, CharSequence> cronExpressionValidator() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true;
            }

            try {
                Cron parse = io.kestra.core.models.triggers.types.Schedule.CRON_PARSER.parse(value.toString());
                parse.validate();
            } catch (IllegalArgumentException e) {
                context.messageTemplate("invalid cron expression '({validatedValue})': " + e.getMessage());

                return false;
            }

            return true;
        };
    }

    @Singleton
    ConstraintValidator<Schedule, io.kestra.core.models.triggers.types.Schedule> scheduleValidator() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true;
            }

            if (value.getBackfill() != null && value.getBackfill().getStart() != null && value.getLateMaximumDelay() != null) {
                context.messageTemplate("invalid schedule: backfill and lateMaximumDelay are incompatible options");

                return false;
            }

            return true;
        };
    }

    @Singleton
    ConstraintValidator<JsonString, String> jsonStringValidator() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true;
            }

            try {
                OBJECT_MAPPER.readTree(value);
            } catch (IOException e) {
                context.messageTemplate("invalid json '({validatedValue})': " + e.getMessage());

                return false;
            }
            return true;
        };
    }

    @Singleton
    ConstraintValidator<SwitchTaskValidation, Switch> switchTaskValidation() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true;
            }

            if ((value.getCases() == null || value.getCases().size() == 0) && (value.getDefaults() == null || value.getDefaults().size() == 0)) {
                context.messageTemplate("No task defined, neither cases or default have any tasks");

                return false;
            }

            return true;
        };
    }

    @Singleton
    ConstraintValidator<DagTaskValidation, Dag> dagTaskValidation() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true;
            }

            if (value.getTasks() == null || value.getTasks().isEmpty()) {
                context.messageTemplate("No task defined");

                return false;
            }

            List<Dag.DagTask> taskDepends = value.getTasks();

            // Check for not existing taskId
            List<String> invalidDependencyIds = value.dagCheckNotExistTask(taskDepends);
            if (!invalidDependencyIds.isEmpty()) {
                String errorMessage = "Not existing task id in dependency: " + String.join(", ", invalidDependencyIds);
                context.messageTemplate(errorMessage);

                return false;
            }

            // Check for cyclic dependencies
            ArrayList<String> cyclicDependency = value.dagCheckCyclicDependencies(taskDepends);
            if (!cyclicDependency.isEmpty()) {
                context.messageTemplate("Cyclic dependency detected: " + String.join(", ", cyclicDependency));

                return false;
            }

            return true;
        };
    }

    @Singleton
    ConstraintValidator<WorkingDirectoryTaskValidation, WorkingDirectory> workingDirectoryTaskValidation() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true;
            }

            if (value.getTasks() == null || value.getTasks().isEmpty()) {
                context.messageTemplate("The 'tasks' property cannot be empty");
                return false;
            }

            if (value.getTasks().stream().anyMatch(task -> !(task instanceof RunnableTask<?>))) {
                context.messageTemplate("Only runnable tasks are allowed as children of a WorkingDirectory task");
                return false;
            }

            return true;
        };
    }

    @Singleton
    ConstraintValidator<FlowValidation, Flow> flowValidation() {
        return (value, annotationMetadata, context) -> {
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
                .stream()
                .forEach(
                    task -> {
                        if (task instanceof io.kestra.core.tasks.flows.Flow taskFlow
                            && value.getId().equals(taskFlow.getFlowId())
                            && value.getNamespace().equals(taskFlow.getNamespace())) {
                            violations.add("Recursive call to flow [" + value.getId() + "]");
                        }
                    }
                );

            // input unique name
            if (value.getInputs() != null) {
                List<String> inputNames = value.getInputs()
                    .stream()
                    .map(Input::getName)
                    .toList();
                List<String> inputDuplicates = inputNames
                    .stream()
                    .distinct()
                    .filter(entry -> Collections.frequency(inputNames, entry) > 1)
                    .toList();
                if (inputDuplicates.size() > 0) {
                    violations.add("Duplicate input with name [" + String.join(", ", inputDuplicates) + "]");
                }
            }

            if (violations.size() > 0) {
                context.messageTemplate("Invalid Flow: " + String.join(", ", violations));
                return false;
            } else {
                return true;
            }
        };
    }

    private static List<String> getDuplicates(List<String> taskIds) {
        return taskIds.stream()
            .distinct()
            .filter(entry -> Collections.frequency(taskIds, entry) > 1)
            .collect(Collectors.toList());
    }

    @Singleton
    ConstraintValidator<Regex, String> patternValidator() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true;
            }

            try {
                Pattern.compile(value);
            } catch (PatternSyntaxException e) {
                context.messageTemplate("invalid pattern [" + value + "]");
                return false;
            }

            return true;
        };
    }

    @Singleton
    @Named("workerGroupValidator")
    ConstraintValidator<WorkerGroupValidation, WorkerGroup> workerGroupValidator() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true;
            }

            context.messageTemplate("Worker Group is an Enterprise Edition functionality");
            return false;
        };
    }
}

