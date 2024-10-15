package io.kestra.core.models.validations;

import io.kestra.core.models.tasks.Task;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KestraConstraintViolationException extends ConstraintViolationException {

    public KestraConstraintViolationException(Set<? extends ConstraintViolation<?>> constraintViolations) {
        super(constraintViolations);
    }

    @Override
    public String getMessage() {
        StringBuilder message = new StringBuilder();
        for (ConstraintViolation<?> violation : getConstraintViolations()) {
            String errorMessage = violation.getMessage();
            try {
                if (violation.getLeafBean() instanceof Task) {
                    errorMessage = replaceTask(violation.getPropertyPath().toString(), ((Task) violation.getLeafBean()).getId()) + " " + violation.getMessage();
                }
            } catch (Exception e) {
                // In case we don't succeed at replacing the task id, we just use the default message
            }
            message.append(errorMessage).append("\n");
        }
        return message.toString();
    }

    private String replaceTask(String errorMessage, String taskId) {
        String regex = "tasks\\[\\d+\\]";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(errorMessage);

        return matcher.replaceAll(taskId);
    }
}