package io.kestra.core.validations;

import com.cronutils.model.Cron;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.TaskValidationInterface;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.tasks.flows.Switch;
import io.micronaut.context.annotation.Factory;
import io.micronaut.validation.validator.constraints.ConstraintValidator;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

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
            Set<ConstraintViolation<?>> violations = new HashSet<>();
            value.getCases().values()
                .forEach(task -> {
                    if (task instanceof TaskValidationInterface) {
                        violations.addAll(((TaskValidationInterface<?>) task).failedConstraints());
                    }
                });

            if (violations.size() > 0) {
                throw new ConstraintViolationException(violations);
            } else {
                return true;
            }
        };
    }

    @Singleton
    ConstraintValidator<FlowValidation, Flow> flowValidation() {
        return (value, annotationMetadata, context) -> {
            Set<ConstraintViolation<?>> violations = new HashSet<>();
            List<Task> allTasks = value.allTasksWithChilds();

            // unique id
            List<String> ids = allTasks
                .stream()
                .map(Task::getId)
                .collect(Collectors.toList());

            List<String> duplicates = ids
                .stream()
                .distinct()
                .filter(entry -> Collections.frequency(ids, entry) > 1).collect(Collectors.toList());

            if (duplicates.size() > 0) {
                violations.add(ManualConstraintViolation.of(
                    "Duplicate task id with name [" +   String.join(", ", duplicates) + "]",
                    value,
                    io.kestra.core.models.flows.Flow.class,
                    "flow.tasks",
                    String.join(", ", duplicates)
                ));
            }

            allTasks
                .forEach(task -> {
                    if (task instanceof TaskValidationInterface) {
                        violations.addAll(((TaskValidationInterface<?>) task).failedConstraints());
                    }
                });

            if (violations.size() > 0) {
                context.messageTemplate("invalid Flow " + value.getId());
                throw new ConstraintViolationException(violations);
            } else {
                return true;
            }
        };
    }
}

