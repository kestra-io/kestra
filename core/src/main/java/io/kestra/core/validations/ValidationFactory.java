package io.kestra.core.validations;

import com.cronutils.model.Cron;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.tasks.flows.Switch;
import io.kestra.core.tasks.flows.WorkingDirectory;
import io.micronaut.context.annotation.Factory;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
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
    ConstraintValidator<WorkingDirectoryTaskValidation, WorkingDirectory> workingDirectoryTaskValidation() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true;
            }

            if(value.getTasks().stream().anyMatch(task -> !(task instanceof RunnableTask<?>))) {
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

            // task unique id
            List<String> taskIds = value.allTasksWithChilds()
                .stream()
                .map(Task::getId)
                .toList();
            List<String> taskDuplicates = taskIds
                .stream()
                .distinct()
                .filter(entry -> Collections.frequency(taskIds, entry) > 1)
                .toList();
            if (taskDuplicates.size() > 0) {
                violations.add("Duplicate task id with name [" + String.join(", ", taskDuplicates) + "]");
            }

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

    @Singleton
    ConstraintValidator<Regex, String> patternValidator() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true;
            }

            try {
                Pattern.compile(value);
            } catch(PatternSyntaxException e) {
                context.messageTemplate("invalid pattern [" + value + "]");
                return false;
            }

            return true;
        };
    }
}

