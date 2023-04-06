package io.kestra.core.validations;

import com.cronutils.model.Cron;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.tasks.flows.Switch;
import io.micronaut.context.annotation.Factory;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;

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
    ConstraintValidator<FlowValidation, Flow> flowValidation() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true;
            }

            List<String> violations = new ArrayList<>();
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
                violations.add("Duplicate task id with name [" + String.join(", ", duplicates) + "]");
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

